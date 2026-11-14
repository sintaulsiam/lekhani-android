#!/usr/bin/env bash
# ==============================================================================
# Lekhani Avro Live Testbench Runner & Auto-Compiler
#
# Automatically compiles the native Rust engine, launches the local HTTP
# testbench server, and opens the visual playground in your default browser.
#
# Usage:
#   ./run_testbench.sh           # Fast debug build & launch
#   ./run_testbench.sh --release # Optimized release build & launch
#   ./run_testbench.sh --watch   # Auto-recompile & restart on file save
#   ./run_testbench.sh --help    # Show usage information
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PORT="${PORT:-8765}"
BUILD_MODE="debug"
WATCH_MODE=false
OPEN_BROWSER=true

# Parse command line flags
for arg in "$@"; do
  case "$arg" in
    --release|-r)
      BUILD_MODE="release"
      ;;
    --watch|-w)
      WATCH_MODE=true
      ;;
    --no-browser)
      OPEN_BROWSER=false
      ;;
    --port=*)
      PORT="${arg#*=}"
      ;;
    --help|-h)
      echo "Lekhani Native Engine Visual Testbench Runner"
      echo ""
      echo "Usage:"
      echo "  ./run_testbench.sh [options]"
      echo ""
      echo "Options:"
      echo "  --release, -r    Compile with cargo --release (maximum performance)"
      echo "  --watch, -w      Auto-recompile and restart server on any code change"
      echo "  --no-browser     Do not automatically open the browser"
      echo "  --port=PORT      Custom port to listen on (default: 8765)"
      echo "  --help, -h       Show this help message"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg (use --help for available options)"
      exit 1
      ;;
  esac
done

echo "============================================================"
echo "  লেখনী Lekhani Native Engine Visual Testbench"
echo "  Mode: $BUILD_MODE | Port: $PORT | Watch: $WATCH_MODE"
echo "============================================================"

# Function to kill any existing process on target port
kill_existing_server() {
  if command -v fuser >/dev/null 2>&1; then
    fuser -k "${PORT}/tcp" >/dev/null 2>&1 || true
  elif command -v lsof >/dev/null 2>&1; then
    local pid
    pid=$(lsof -ti :"${PORT}" || true)
    if [ -n "$pid" ]; then
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
  fi
}

# Function to compile the dev_server binary
compile_server() {
  echo "==> Compiling native engine (lekhani-android + lekhani-core)..."
  if [ "$BUILD_MODE" = "release" ]; then
    cargo build --release -p lekhani-android --bin dev_server
    BINARY="target/release/dev_server"
  else
    cargo build -p lekhani-android --bin dev_server
    BINARY="target/debug/dev_server"
  fi
  echo "==> Build successful: $BINARY"
}

# Ensure playground artifact is accessible in working directory
if [ -f "/home/smsiam/.gemini/antigravity/brain/8554ac01-5661-43a3-9c31-5b2be6ce014f/avro_playground.html" ]; then
  cp -f "/home/smsiam/.gemini/antigravity/brain/8554ac01-5661-43a3-9c31-5b2be6ce014f/avro_playground.html" "$SCRIPT_DIR/avro_playground.html"
fi

# Initial compilation
kill_existing_server
compile_server

SERVER_PID=""

# Cleanup on exit
cleanup() {
  echo ""
  echo "==> Shutting down testbench server..."
  if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null || true
  fi
  kill_existing_server
  exit 0
}
trap cleanup SIGINT SIGTERM EXIT

# Start server process
start_server() {
  kill_existing_server
  echo "==> Launching Lekhani Dev Server on http://127.0.0.1:${PORT}..."
  PORT="$PORT" "$SCRIPT_DIR/$BINARY" &
  SERVER_PID=$!
  sleep 0.8

  # Verify server is responding
  if curl -s "http://127.0.0.1:${PORT}/api/status" >/dev/null 2>&1; then
    echo "==> Server is live and healthy at http://127.0.0.1:${PORT}"
  else
    echo "==> Warning: Server started (PID $SERVER_PID), waiting for listener..."
  fi
}

start_server

# Open browser if requested
if [ "$OPEN_BROWSER" = true ]; then
  URL="http://127.0.0.1:${PORT}"
  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$URL" >/dev/null 2>&1 &
  elif command -v python3 >/dev/null 2>&1; then
    python3 -m webbrowser "$URL" >/dev/null 2>&1 &
  fi
fi

echo ""
echo "============================================================"
echo "  READY! Open in your browser: http://127.0.0.1:${PORT}"
echo "  Press Ctrl+C to stop the server"
echo "============================================================"

# If watch mode requested, monitor files for changes
if [ "$WATCH_MODE" = true ]; then
  echo "==> Watch mode active. Monitoring Rust sources for changes..."
  WATCH_PATHS=(
    "crates/lekhani-android/src"
    "data/dictionaries"
  )
  if [ -d "../lekhani-engine" ]; then
    WATCH_PATHS+=("../lekhani-engine/lekhani-core/src" "../lekhani-engine/lekhani-parser/src" "../lekhani-engine/lekhani-ai/src")
  fi

  if command -v inotifywait >/dev/null 2>&1; then
    while true; do
      inotifywait -r -e modify,create,delete "${WATCH_PATHS[@]}" >/dev/null 2>&1 || true
      echo ""
      echo "==> Detected code change! Recompiling..."
      if compile_server; then
        echo "==> Restarting server with new code..."
        start_server
        echo "==> Hot-reload complete!"
      else
        echo "==> Compilation failed. Fix errors and save again."
      fi
    done
  else
    echo "Notice: inotifywait not found. Install inotify-tools for auto-recompile on save."
    wait "$SERVER_PID"
  fi
else
  wait "$SERVER_PID"
fi
