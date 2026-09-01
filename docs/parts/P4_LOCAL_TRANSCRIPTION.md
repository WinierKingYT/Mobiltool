# P4 — Local Transcription

## Goal
Truthful speech-to-text pipeline and multi-format transcript export.

## Execution Packages
1. Truthful `DefaultTranscriptionEngine` (returns `STT_RUNTIME_UNAVAILABLE` until native Whisper C++ JNI is linked).
2. Purge placeholder transcript fabrication.
3. Multi-format `TranscriptExporter` (TXT, SRT, WebVTT, Markdown).
4. Room DB transcript indexing.

## Exit Gate
```text
[x] STT engine refuses placeholder text
[x] Clean unlinked runtime status
[x] TXT, SRT, VTT, Markdown exporters verified
[x] Automated unit test suite passes
```
