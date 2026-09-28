# ⏸️ Lekhani Ecosystem Master Plan (ON HOLD)

> **Status**: **ON HOLD**  
> **Last Verified**: 2026-09-29  
> **Reference Version**: `v1.1.0` across all repositories  

This document preserves the comprehensive architecture, current state, and step-by-step roadmap across the entire Lekhani ecosystem for when active development resumes.

---

## 1. 🏗️ Ecosystem Architecture & Repositories

Lekhani is partitioned into three specialized repositories:

```
                       ┌────────────────────────────────────────────────────────┐
                       │               sintaulsiam/lekhani-engine               │
                       │   Platform-Agnostic Pure Rust Engine (Tier 1)          │
                       │   • lekhani-parser: 11 ns Avro Trie grammar            │
                       │   • lekhani-ai: N-gram LM, Kneser-Ney, homophones      │
                       │   • lekhani-core: IME state machine & layouts          │
                       └──────────────────────────┬─────────────────────────────┘
                                                  │
                 ┌────────────────────────────────┴────────────────────────────────┐
                 │ consumes git tag "v1.1.0"                                       │ consumes git tag "v1.1.0"
                 ▼                                                                 ▼
┌──────────────────────────────────────────────┐                 ┌──────────────────────────────────────────────┐
│        sintaulsiam/lekhani-android           │                 │             sintaulsiam/lekhani              │
│   Mobile IME Keyboard (Tier 2 & 3)           │                 │   Desktop Linux Keyboard                     │
│   • crates/lekhani-android (UniFFI / JNI)    │                 │   • ibus-lekhani & fcitx5-lekhani            │
│   • Touch heuristics (glide typing, fat-key) │                 │   • lekhani-gui (Slint desktop UI)           │
│   • Offline Voice ASR & AudioRecord          │                 │   • lekhani-cli & lekhani-settings           │
│   • Material 3 Expressive Compose UI         │                 └──────────────────────────────────────────────┘
└──────────────────────────────────────────────┘
```

| Repository | Local Path | Remote URL | Role |
|---|---|---|---|
| **`lekhani-engine`** | `/mnt/data/lekhani-engine` | [sintaulsiam/lekhani-engine](https://github.com/sintaulsiam/lekhani-engine) | Upstream core engine crates (`lekhani-core`, `lekhani-parser`, `lekhani-ai`). |
| **`lekhani-android`** | `/mnt/data/lekhani-android` | [sintaulsiam/lekhani-android](https://github.com/sintaulsiam/lekhani-android) | Android IME app and UniFFI native bridge. |
| **`lekhani` (Linux)** | `/mnt/data/lekhani` | [sintaulsiam/lekhani](https://github.com/sintaulsiam/lekhani) | Desktop Linux keyboard (IBus, Fcitx5, Slint UI, CLI). |

---

## 2. ✅ Current Verified State

1. **Engine Decoupling & Unbroken Lineage**:
   - `lekhani-engine` holds **109 commits** representing its complete unbroken lineage (initial Linux commits + all Android speedups and Trie parser improvements).
   - Tagged and pushed to GitHub at `v1.1.0` (`9f17b66`).
2. **Linux Repository Verified**:
   - Upstream engine members decoupled; workspace points to `lekhani-engine` v1.1.0 via git.
   - `cargo test --workspace` passes **100% cleanly** (0 failures, backward compatibility intact).
3. **Android Repository Verified**:
   - `Cargo.lock` locked to `lekhani-engine` v1.1.0 (`9f17b66`).
   - All 30 native Rust unit tests in `lekhani-android` pass.
   - Kotlin Compose compilation (`:app:compileDebugKotlin`) completes with zero errors.
   - Phases 0 through 11 in [`ROADMAP.md`](file:///mnt/data/lekhani-android/ROADMAP.md) are completed.

---

## 3. 📋 The Plan (Resume When Ready)

### Phase A: Housekeeping & Architectural Purity (First Priority on Resume)
- [ ] **Purify `lekhani-ai` from Platform Paths**:
  - In `lekhani-engine/lekhani-ai/src/lm.rs` (lines 1260–1261), remove hardcoded `/data/data/com.lekhani.android/...` search paths.
  - Require the consumer (`AndroidLekhaniSession` in `lekhani-android`) to pass the dictionary directory path during session init.
- [ ] **Clean Android Assets Directory**:
  - In `lekhani-android/data/`, remove desktop Linux residual directories (`fcitx5/`, `ibus/`, `systemd/`, `.desktop`) so they are not packaged into the Android APK assets.
- [ ] **Commit Android UI Refinements**:
  - Review and commit outstanding changes in `LayoutFlowScreen.kt`.

### Phase B: Crates.io Publishing & Distribution Strategy
- [ ] **Publish `v1.1.0` to crates.io** (Only when ready for immutable public release):
  1. `cd /mnt/data/lekhani-engine/lekhani-parser && cargo publish`
  2. `cd /mnt/data/lekhani-engine/lekhani-ai && cargo publish`
  3. `cd /mnt/data/lekhani-engine/lekhani-core && cargo publish`
  4. Update `Cargo.toml` in `lekhani` and `lekhani-android` to use `version = "1.1.0"` from crates.io.
- [ ] **Model Assets Strategy**:
  - Keep binary models (`bengali_lm.bin`, `dictionary.bin`, `english_dict.bin`) in `lekhani-android/data/dictionaries` for zero-friction developer setup.
  - Attach release tarball (`dictionaries-v1.1.0.tar.gz`) to GitHub Releases on `sintaulsiam/lekhani-engine`.

### Phase C: Phase 12 Completion & Store Launch (Android)
- [ ] **Latency & Resource Profiling**:
  - Measure against AGENTS.md §1 budgets: `< 3 ms` touch-to-screen latency, `< 40 ms` cold boot, and `< 35 MB` peak RSS.
- [ ] **Automated Integration Tests**:
  - Run instrumented test matrix across Android API levels (24 to 34+).
- [ ] **F-Droid Recipe**:
  - Prepare reproducible build recipe verifying zero network usage (`INTERNET` permission completely absent).
- [ ] **Google Play Store / Open-Source Launch**:
  - Store assets, screenshots, and privacy policy declaration.

---

## 4. 🛠️ Developer Workflow Quick-Reference

### Daily Hacking (Instant, Zero Network/Git Overhead)
Enable local path overrides in `lekhani-android/Cargo.toml` (or `.cargo/config.toml`):
```toml
[patch."https://github.com/sintaulsiam/lekhani-engine"]
lekhani-core   = { path = "../lekhani-engine/lekhani-core" }
lekhani-parser = { path = "../lekhani-engine/lekhani-parser" }
lekhani-ai     = { path = "../lekhani-engine/lekhani-ai" }
```
*Edits in `lekhani-engine` will immediately compile in Android/Linux with zero seconds wait.*

### Pushing Changes
1. Commit & push changes in `/mnt/data/lekhani-engine`:
   ```bash
   git commit -am "feat: description"
   git tag -a v1.x.x -m "release"
   git push origin main --tags
   ```
2. Update the tag reference in `lekhani-android` / `lekhani`:
   ```bash
   cargo update -p lekhani-core
   ```
