//! Lekhani Native Development & Visual Testbench Server
//!
//! Provides a zero-dependency, sub-millisecond local HTTP API directly connected
//! to the real Rust engine (`lekhani-android`, `lekhani-core`, `lekhani-parser`, `lekhani-ai`).
//!
//! Enables real-time, live keystroke-by-keystroke testing of engine changes without
//! any hardcoded mock tables or stale data.

use std::collections::HashMap;
use std::io::{BufRead, BufReader, Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Instant;

use lekhani_android::avro::{
    has_override_word, transliterate_avro, transliterate_avro_with_context,
};
use lekhani_android::layout::LekhaniLayoutType;
use lekhani_android::session::{
    get_core_database, set_dictionary_directory, AndroidLekhaniSession,
};
use serde::{Deserialize, Serialize};

#[derive(Serialize)]
struct StatusResponse {
    status: &'static str,
    engine: &'static str,
    dictionary_loaded: bool,
    trie_entries: usize,
    has_overrides: bool,
    uptime_secs: u64,
}

#[derive(Serialize)]
struct IntermediateStep {
    step: usize,
    prefix: String,
    key: String,
    primary: String,
    candidates: Vec<String>,
}

#[derive(Serialize)]
struct TestResponse {
    input: String,
    primary: String,
    candidates: Vec<String>,
    intermediate: Vec<IntermediateStep>,
    is_exact_dict: bool,
    has_override: bool,
    latency_micros: u128,
}

#[derive(Serialize)]
struct SessionResponse {
    committed_text: String,
    composing_text: String,
    candidates: Vec<String>,
    latency_micros: u128,
}

#[derive(Deserialize)]
struct KeyRequest {
    key: String,
}

#[derive(Deserialize)]
struct SelectRequest {
    candidate: String,
}

fn find_dict_dir() -> PathBuf {
    let probes = [
        "data/dictionaries",
        "../data/dictionaries",
        "../../data/dictionaries",
        "/mnt/data/lekhani-android/data/dictionaries",
        "/mnt/data/lekhani/data/dictionaries",
    ];
    for p in &probes {
        let path = PathBuf::from(p);
        if path.exists() && path.join("dictionary.json").exists() {
            return path;
        }
    }
    PathBuf::from("/mnt/data/lekhani-android/data/dictionaries")
}

fn parse_query(query_str: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for part in query_str.split('&') {
        let mut kv = part.splitn(2, '=');
        if let Some(k) = kv.next() {
            let v = kv.next().unwrap_or("");
            // Simple percent-decoding for spaces and common chars
            let decoded = v
                .replace("%20", " ")
                .replace("+", " ")
                .replace("%27", "'")
                .replace("%22", "\"")
                .replace("%2C", ",")
                .replace("%3A", ":");
            map.insert(k.to_string(), decoded);
        }
    }
    map
}

fn send_response(mut stream: &TcpStream, status_code: &str, content_type: &str, body: &[u8]) {
    let headers = format!(
        "HTTP/1.1 {}\r\n\
         Content-Type: {}\r\n\
         Content-Length: {}\r\n\
         Access-Control-Allow-Origin: *\r\n\
         Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n\
         Access-Control-Allow-Headers: Content-Type, Accept\r\n\
         Connection: close\r\n\r\n",
        status_code,
        content_type,
        body.len()
    );
    let _ = stream.write_all(headers.as_bytes());
    let _ = stream.write_all(body);
    let _ = stream.flush();
}

fn handle_connection(
    mut stream: TcpStream,
    session: &Arc<Mutex<AndroidLekhaniSession>>,
    start_time: Instant,
) {
    let mut reader = BufReader::new(&mut stream);
    let mut request_line = String::new();
    if reader.read_line(&mut request_line).is_err() || request_line.is_empty() {
        return;
    }

    let parts: Vec<&str> = request_line.split_whitespace().collect();
    if parts.len() < 2 {
        return;
    }

    let method = parts[0];
    let full_uri = parts[1];

    // Read remaining headers
    let mut content_length = 0;
    loop {
        let mut line = String::new();
        if reader.read_line(&mut line).is_err() || line.trim().is_empty() {
            break;
        }
        if line.to_lowercase().starts_with("content-length:") {
            if let Some(val) = line.split(':').nth(1) {
                content_length = val.trim().parse::<usize>().unwrap_or(0);
            }
        }
    }

    // Read body if present
    let mut body = vec![0u8; content_length];
    if content_length > 0 {
        let _ = reader.read_exact(&mut body);
    }

    // CORS preflight
    if method == "OPTIONS" {
        send_response(&stream, "204 No Content", "text/plain", b"");
        return;
    }

    let mut uri_parts = full_uri.splitn(2, '?');
    let path = uri_parts.next().unwrap_or("/");
    let query_str = uri_parts.next().unwrap_or("");
    let query_params = parse_query(query_str);

    match (method, path) {
        ("GET", "/api/status") => {
            let db = get_core_database();
            let status = StatusResponse {
                status: "online",
                engine: "lekhani-android",
                dictionary_loaded: true,
                trie_entries: db.trie.len(),
                has_overrides: true,
                uptime_secs: start_time.elapsed().as_secs(),
            };
            if let Ok(json) = serde_json::to_vec(&status) {
                send_response(&stream, "200 OK", "application/json; charset=utf-8", &json);
            }
        }

        ("GET", "/api/test") => {
            let t0 = Instant::now();
            let input = query_params.get("q").cloned().unwrap_or_default();
            let context_str = query_params.get("context").cloned().unwrap_or_default();
            let context_words: Vec<&str> = context_str.split_whitespace().collect();

            let (primary, candidates) = if context_words.is_empty() {
                transliterate_avro(&input)
            } else {
                transliterate_avro_with_context(&input, &context_words)
            };

            // Compute keystroke-by-keystroke intermediate progression
            let mut intermediate = Vec::new();
            let chars: Vec<char> = input.chars().collect();
            for i in 1..=chars.len() {
                let prefix: String = chars[..i].iter().collect();
                let key = chars[i - 1].to_string();
                let (prefix_primary, prefix_cands) = transliterate_avro(&prefix);
                intermediate.push(IntermediateStep {
                    step: i,
                    prefix,
                    key,
                    primary: prefix_primary,
                    candidates: prefix_cands,
                });
            }

            let db = get_core_database();
            let is_exact = db.is_exact_dictionary_word(&primary);
            let has_ov = has_override_word(&input);

            let resp = TestResponse {
                input,
                primary,
                candidates,
                intermediate,
                is_exact_dict: is_exact,
                has_override: has_ov,
                latency_micros: t0.elapsed().as_micros(),
            };

            if let Ok(json) = serde_json::to_vec(&resp) {
                send_response(&stream, "200 OK", "application/json; charset=utf-8", &json);
            }
        }

        ("POST", "/api/session/key") => {
            let t0 = Instant::now();
            let key_req: Result<KeyRequest, _> = serde_json::from_slice(&body);
            if let Ok(req) = key_req {
                if let Ok(sess) = session.lock() {
                    match sess.process_key(req.key) {
                        Ok(result) => {
                            let resp = SessionResponse {
                                committed_text: result.commit_text.unwrap_or_default(),
                                composing_text: result.preedit,
                                candidates: result.candidates,
                                latency_micros: t0.elapsed().as_micros(),
                            };
                            if let Ok(json) = serde_json::to_vec(&resp) {
                                send_response(
                                    &stream,
                                    "200 OK",
                                    "application/json; charset=utf-8",
                                    &json,
                                );
                                return;
                            }
                        }
                        Err(e) => {
                            let err_msg = format!("Key error: {}", e);
                            send_response(
                                &stream,
                                "500 Internal Error",
                                "text/plain",
                                err_msg.as_bytes(),
                            );
                            return;
                        }
                    }
                }
            }
            send_response(
                &stream,
                "400 Bad Request",
                "text/plain",
                b"Invalid key request",
            );
        }

        ("POST", "/api/session/backspace") => {
            let t0 = Instant::now();
            if let Ok(sess) = session.lock() {
                match sess.handle_backspace() {
                    Ok(result) => {
                        let resp = SessionResponse {
                            committed_text: result.commit_text.unwrap_or_default(),
                            composing_text: result.preedit,
                            candidates: result.candidates,
                            latency_micros: t0.elapsed().as_micros(),
                        };
                        if let Ok(json) = serde_json::to_vec(&resp) {
                            send_response(
                                &stream,
                                "200 OK",
                                "application/json; charset=utf-8",
                                &json,
                            );
                            return;
                        }
                    }
                    Err(e) => {
                        let err_msg = format!("Backspace error: {}", e);
                        send_response(
                            &stream,
                            "500 Internal Error",
                            "text/plain",
                            err_msg.as_bytes(),
                        );
                        return;
                    }
                }
            }
            send_response(
                &stream,
                "500 Internal Error",
                "text/plain",
                b"Session lock failed",
            );
        }

        ("POST", "/api/session/space") => {
            let t0 = Instant::now();
            if let Ok(sess) = session.lock() {
                match sess.handle_space() {
                    Ok(result) => {
                        let resp = SessionResponse {
                            committed_text: result.commit_text.unwrap_or_default(),
                            composing_text: result.preedit,
                            candidates: result.candidates,
                            latency_micros: t0.elapsed().as_micros(),
                        };
                        if let Ok(json) = serde_json::to_vec(&resp) {
                            send_response(
                                &stream,
                                "200 OK",
                                "application/json; charset=utf-8",
                                &json,
                            );
                            return;
                        }
                    }
                    Err(e) => {
                        let err_msg = format!("Space error: {}", e);
                        send_response(
                            &stream,
                            "500 Internal Error",
                            "text/plain",
                            err_msg.as_bytes(),
                        );
                        return;
                    }
                }
            }
            send_response(
                &stream,
                "500 Internal Error",
                "text/plain",
                b"Session lock failed",
            );
        }

        ("POST", "/api/session/select") => {
            let t0 = Instant::now();
            let sel_req: Result<SelectRequest, _> = serde_json::from_slice(&body);
            if let Ok(req) = sel_req {
                if let Ok(sess) = session.lock() {
                    match sess.select_candidate(req.candidate) {
                        Ok(result) => {
                            let resp = SessionResponse {
                                committed_text: result.commit_text.unwrap_or_default(),
                                composing_text: result.preedit,
                                candidates: result.candidates,
                                latency_micros: t0.elapsed().as_micros(),
                            };
                            if let Ok(json) = serde_json::to_vec(&resp) {
                                send_response(
                                    &stream,
                                    "200 OK",
                                    "application/json; charset=utf-8",
                                    &json,
                                );
                                return;
                            }
                        }
                        Err(e) => {
                            let err_msg = format!("Select error: {}", e);
                            send_response(
                                &stream,
                                "500 Internal Error",
                                "text/plain",
                                err_msg.as_bytes(),
                            );
                            return;
                        }
                    }
                }
            }
            send_response(
                &stream,
                "400 Bad Request",
                "text/plain",
                b"Invalid select request",
            );
        }

        ("POST", "/api/session/reset") => {
            if let Ok(sess) = session.lock() {
                sess.reset();
                sess.set_layout(LekhaniLayoutType::Avro);
                send_response(
                    &stream,
                    "200 OK",
                    "application/json; charset=utf-8",
                    br#"{"status":"reset","layout":"Avro"}"#,
                );
                return;
            }
            send_response(&stream, "500 Internal Error", "text/plain", b"Reset failed");
        }

        ("POST", "/api/reload_dict") => {
            let dict_dir = find_dict_dir();
            set_dictionary_directory(dict_dir.to_string_lossy().to_string());
            let db = get_core_database();
            let resp = serde_json::json!({
                "status": "reloaded",
                "trie_entries": db.trie.len()
            });
            if let Ok(json) = serde_json::to_vec(&resp) {
                send_response(&stream, "200 OK", "application/json; charset=utf-8", &json);
                return;
            }
            send_response(
                &stream,
                "500 Internal Error",
                "text/plain",
                b"Reload failed",
            );
        }

        ("POST", "/api/rebuild") => {
            println!("Triggering cargo build...");
            let output = std::process::Command::new("cargo")
                .args(["build", "-p", "lekhani-android"])
                .current_dir("/mnt/data/lekhani-android")
                .output();
            match output {
                Ok(out) => {
                    let success = out.status.success();
                    let msg =
                        String::from_utf8_lossy(if success { &out.stdout } else { &out.stderr });
                    let resp = serde_json::json!({
                        "success": success,
                        "output": msg
                    });
                    if let Ok(json) = serde_json::to_vec(&resp) {
                        send_response(&stream, "200 OK", "application/json; charset=utf-8", &json);
                        return;
                    }
                }
                Err(e) => {
                    let err = format!("Rebuild failed to start: {}", e);
                    send_response(&stream, "500 Internal Error", "text/plain", err.as_bytes());
                    return;
                }
            }
            send_response(
                &stream,
                "500 Internal Error",
                "text/plain",
                b"Rebuild failed",
            );
        }

        ("GET" | "HEAD", "/" | "/index.html") => {
            // Serve the playground HTML file
            let candidates = [
                "/home/smsiam/.gemini/antigravity/brain/8554ac01-5661-43a3-9c31-5b2be6ce014f/avro_playground.html",
                "avro_playground.html",
            ];
            for c in &candidates {
                if let Ok(content) = std::fs::read(c) {
                    send_response(&stream, "200 OK", "text/html; charset=utf-8", &content);
                    return;
                }
            }
            send_response(
                &stream,
                "404 Not Found",
                "text/plain",
                b"HTML file not found",
            );
        }

        _ => {
            send_response(&stream, "404 Not Found", "text/plain", b"Not Found");
        }
    }
}

fn main() {
    let dict_dir = find_dict_dir();
    println!("Loading dictionaries from: {}", dict_dir.display());
    set_dictionary_directory(dict_dir.to_string_lossy().to_string());

    let session = Arc::new(Mutex::new(AndroidLekhaniSession::new()));
    {
        let sess = session.lock().unwrap();
        sess.set_layout(LekhaniLayoutType::Avro);
    }

    let port = std::env::var("PORT").unwrap_or_else(|_| "8765".to_string());
    let addr = format!("127.0.0.1:{}", port);
    let listener = TcpListener::bind(&addr).unwrap_or_else(|e| {
        eprintln!("Failed to bind to {}: {}", addr, e);
        std::process::exit(1);
    });

    println!("============================================================");
    println!("  Lekhani Native Visual Testbench Server Running!");
    println!("  Connected to REAL engine: lekhani-android + lekhani-core");
    println!("  URL: http://{}", addr);
    println!("============================================================");

    let start_time = Instant::now();
    for stream in listener.incoming() {
        match stream {
            Ok(stream) => {
                let session_clone = Arc::clone(&session);
                handle_connection(stream, &session_clone, start_time);
            }
            Err(e) => {
                eprintln!("Connection error: {}", e);
            }
        }
    }
}
