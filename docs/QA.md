# الاختبارات | Test guide and results

## Reproduce

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The first command builds the test APK, verifies the pure typing and prediction functions, and runs Android Lint. The second executes Android-dependent tests on a connected device; its separate instrumentation APK is not needed to install Lisan.

## Build verification

Results for this version are recorded under [Release verification](RELEASE_VERIFICATION.md). APK signing, ZIP alignment and ELF alignment checks are packaging checks, not runtime benchmarks.

The Android instrumentation tests are separate from the host JNI tests. The latter call the same native bridge against 16 kHz mono PCM16 WAV files and check for a phrase fragment. This exercises model loading, cancellation and a real transcription, but not Android's `AudioRecord`, IME lifecycle, permissions or the microphone.

## What the suites cover

- **JVM:** Layout and typing tests, conservative Arabic correction, silence handling, HTTPS URL guards, exact request body and redirect rejection over a loopback TLS connection. The loopback response is a contract fixture, not evidence of translation quality.
- **Android:** Android binding metadata, sensitive-field classification and toolbar suppression, real key callbacks in the four themes, theme preservation, encrypted vault, native library load, bundled Arabic language ID, missing-model consent, and an optional English–Arabic ML Kit round trip with explicit download.

Run the Android suite with model downloading enabled on a disposable device or test profile:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.downloadTranslationModels=true
```

For host JNI testing, build the [v1.8.2 Whisper source](https://github.com/ggml-org/whisper.cpp/tree/v1.8.2) for your host, build `app/src/main/cpp/lisan_voice.cpp` against JDK JNI headers and the host `libwhisper`, then compile and run `tools/voice-smoke/WhisperNative.java`. Arguments are shared library, model, WAV, language, and expected phrase fragment. No WAV fixtures or model weights are redistributed in the archive.

## Manual matrix before production distribution

1. Install the APK on a low-memory Android 8–10 phone and a modern Android 14+ phone. Enable and select Lisan through Android settings. Check both a small phone and a tablet, portrait and landscape. Compare the system IME to all four required theme previews.
2. Verify typing, selection, punctuation, delete, Arabic joins, long-press letter alternatives, vocalization marks and cursor controls in an Android system text field, a browser, and text editors such as WhatsApp, Telegram and Gmail. Also check email input, password/PIN and an incognito or no-learning field. Confirm sensitive panels never appear in those protected fields.
3. Decline mic permission; retry after granting. Dictate in Arabic and English, start/stop/resume, and swap editors during processing. Verify no insertion in the new editor. Where available, check on-device mode without a network and opt-in network speech separately. Check Android 14+ language switching against the actual recognition provider.
4. Install Arabic and one additional model; verify English↔Arabic and another pair after switching to airplane mode. Exercise model removal and Wi-Fi-only download. Test manual text, clipboard, field/selection, and voice-to-translation flows.
5. Copy text from another app. Verify nothing is listed until an explicit read/save. Confirm clips marked sensitive are not copied by the direct-read path. Exercise library edits, pins and deletion. Check an imported profile never opts you into network speech.
6. Verify app sharing uses Android Sharesheet, the support form shows a review before opening email, and the archive has no credentials or raw personal inputs.

## Release gate

Passing the included tests is not equivalent to a production guarantee. Real microphone recognition, downloadable model end-to-end behavior, third-party applications, startup latency, battery/low-memory behavior and OEM-specific input connections require the device matrix above.
