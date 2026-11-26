# تحقق إصدار 0.2.0 | Release verification

## Verified on the build host

| Check | Result |
|---|---|
| JVM automated tests | 13 passed, including speech silence handling, Arabic correction protections, and the optional HTTPS client's request and redirect behavior. The HTTPS server used a fixture response, not a live translation provider. |
| Offline speech, application JNI bridge | The pinned Whisper model transcribed an English recording and a synthetic Arabic WAV on Linux; phrase-fragment assertions passed. Automatic language detection selected Arabic on the Arabic sample. This was a smoke test, not a word-error-rate evaluation. The Arabic output had noticeable spelling and word errors. |
| Build and static analysis | Debug APK and Android instrumentation APK built. Android Lint: 0 errors, 19 warnings. |
| APK | Version 0.2.0, code 2, min SDK 26, debug-signed. APK signature, ZIP integrity and 16 KiB ZIP alignment verified. ARM64 and x86-64 speech library load-segment alignment was 16 KiB. |
| Source archive | Extracted the source into a separate project directory, rebuilt the debug APK with the pinned Gradle Wrapper and reran the 13 JVM tests. |

English test audio was the [JFK fixture supplied by whisper.cpp](https://github.com/ggml-org/whisper.cpp/blob/v1.8.2/samples/jfk.wav). The Arabic QA sentence was generated locally with [Piper's Arabic Kareem voice](https://huggingface.co/rhasspy/piper-voices/tree/main/ar/ar_JO/kareem/low). Neither audio clip is part of the APK or source ZIP.

## Device qualification

The sandbox Android 11 emulator was software-emulated. Package installation and instrumentation did not complete reliably in the test window; no completed Android end-to-end microphone, model-download, or cross-language ML Kit run is recorded. Passing host tests also does not validate microphone privacy behavior, Android 13/14 provider-specific APIs, or third-party messaging applications. Use [the QA matrix](QA.md) on a physical device before distribution. No production signing or store publication is part of this release.
