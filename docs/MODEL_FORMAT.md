# N-gram / AI Model Format Decision

**Status**: DECIDED  
**Phase**: 1 (prerequisite for Phase 4 — Candidate Strip & AI Intelligence)  
**Author**: Lekhani Engineering

---

## Context

`lekhani-ai` (Phase 0, published on crates.io) contains a local N-gram language
model and homophone ranker.  Before beginning Phase 4 (Candidate Strip), we must
decide how to package, load, and query this model on-device so that:

1. **Cold-load time** stays under 40 ms (AGENTS.md §1 performance budget).
2. **RSS memory footprint** stays under 35 MB peak.
3. **Query latency** stays under 3 ms per keystroke.
4. **No network access** is used — model files are bundled, never downloaded.

---

## Options Evaluated

| Option | Format | Load strategy | Cold-load | RAM | Query | APK size | Verdict |
|--------|--------|---------------|-----------|-----|-------|----------|---------|
| **A** | Binary trie (custom) | `mmap` from assets | ~5 ms | ~4 MB | ~0.3 ms | ~2 MB | ✅ **CHOSEN** |
| B | Flat sorted wordlist + bigram table | `mmap` | ~8 ms | ~6 MB | ~0.8 ms | ~3 MB | Runner-up |
| C | ONNX int8 (onnxruntime) | Full load into RAM | ~180 ms | ~22 MB | ~4 ms | ~18 MB | ❌ Too slow / heavy |
| D | SQLite FTS5 | File open | ~60 ms | ~12 MB | ~2.5 ms | ~5 MB | ❌ Violates 40 ms cold-boot |

---

## Decision: Binary Trie + `mmap` (Option A)

### Rationale

- **`mmap` from the Android Asset Manager** means the OS page-faults only the
  trie nodes actually traversed during a query — no up-front full-file read.
  This keeps both cold-load time and steady-state RSS well within budget.
- A **prefix trie over Unicode grapheme clusters** is the natural data structure
  for Avro and Probaho prefix completion: each edge is a grapheme cluster string,
  each leaf stores a frequency count and a set of up to 4 ranked homophones.
- The trie is **built offline** (at release time) by `lekhani-ai`'s build tool
  from a labeled Bengali corpus, and serialized to a compact binary format with
  a fixed-width node array for O(1) child lookup.
- **Zero runtime allocations** during prefix traversal: the trie walk operates
  on a `&[u8]` slice into the mmap'd region with no heap allocation.

### File Layout (inside APK assets)

```
assets/
  lekhani_ngram.trie      — binary trie (target: < 2 MB)
  lekhani_bigrams.bin     — bigram frequency table (target: < 512 KB)
```

### Implementation Checkpoints (before Phase 4)

- [ ] `lekhani-ai` exposes a `TrieModel::from_mmap(fd: RawFd) -> Result<Self>` API.
- [ ] Benchmark cold-load on a Snapdragon 680 (low-end target): must be < 40 ms.
- [ ] Benchmark RSS after loading on Snapdragon 680: must be < 6 MB for the model alone.
- [ ] Benchmark per-keystroke query on 6-prefix depth: must be < 0.5 ms.
- [ ] Verify no network access is triggered (Android Network Security Config audit).

---

## Rejected: ONNX / Neural N-gram

A transformer-based or ONNX int8 model was explicitly rejected because:
- `onnxruntime-android` cold-load is ~180 ms minimum — 4.5× over budget.
- The runtime itself adds ~8 MB RSS before any model weights.
- Bengali phonetic prediction does not require neural contextual embeddings at
  Phase 4 scope; a well-tuned trigram trie with homophone re-ranking achieves
  comparable accuracy for the common case at a fraction of the compute cost.

This decision should be revisited at Phase 4+ if trigram accuracy proves
insufficient for colloquial suffix peeling (`kortesi`, `jaitasi`).
