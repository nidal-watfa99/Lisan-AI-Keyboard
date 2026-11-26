# Lisan AI Keyboard

Lisan is a native Android keyboard and companion app for multilingual typing, dictation and translation. It is an actual Android input method, not a web preview. This package is version `0.2.0`, a test build; see the [feature matrix](docs/FEATURES.md) and [test instructions and results](docs/QA.md) before distributing it.

## Install and enable

1. On Android 8.0 or later, open the separately supplied `Lisan_AI_Keyboard_0.2.0_Debug.apk`. If Android asks, temporarily allow the file manager or browser to install apps from that source. Disable that allowance again after installation.
2. Open Lisan and choose keyboard layouts. Arabic and English are enabled by default; French, German, Dutch and Turkish are also built in.
3. Tap **Enable in Android** and enable Lisan in the system list. Return to Lisan, tap **Choose keyboard** and select Lisan. Android controls these steps.
4. Focus the real text field on Home. Tap the globe key to cycle through enabled layouts; hold it to choose a layout or open the Android IME picker. Hold Delete to remove a word and hold Enter to hide the keyboard.
5. Open **Themes** to try each design on the live board, then select **Apply to keyboard**. The initial theme is Premium Dark. Settings let you adjust dimensions, typography, colors and the toolbar.
6. In **Voice & microphone**, choose a language and download or manually import the [Whisper Tiny multilingual model](docs/ENGINES.md) (32.2 MB). Automatic offline mode chooses Whisper when installed; otherwise it uses Android's on-device recognizer. Grant microphone permission only for dictation. A second tap ends the phrase; another tap starts a new phrase. An optional Android provider may use the network and requires opt-in.
7. Open **Translate** to set source and target temporarily, or save them as defaults. The initial target is the device language when supported. Install models under **Models** or approve a download when prompted. In the keyboard, choose field text, copied text or speech and edit the draft before inserting the translation. The standalone screen also offers an editable result and, if separately configured, [an optional HTTPS translation server](docs/ENGINES.md).

Automatic translation is powered by Google Translate via on-device ML Kit. See [Google’s usage guidelines](https://developers.google.com/ml-kit/language/translation/translation-terms) and [Google translation documentation](https://docs.cloud.google.com/translate/docs).

## Build

Open this directory in Android Studio with JDK 17, Android SDK Platform 36, Android SDK Build-Tools 35.0.0, Platform Tools, NDK `28.2.13676358`, and CMake `3.22.1`. Set `ANDROID_HOME` if building from a terminal. The initial sync requires access to Google Maven, Maven Central, the Gradle Plugin Portal and the pinned wrapper distribution. SDK tools and runtime models are not redistributed in this ZIP; the native Whisper source is included.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

On Windows use `gradlew.bat`. Debug APK output is `app/build/outputs/apk/debug/app-debug.apk`. The second command requires a connected emulator or authorized Android phone. `assembleRelease` builds a release artifact; configure your own Android signing credentials outside source control before production distribution. No signing key or API key is supplied.

## More information

- [Arabic README](README.ar.md)
- [Architecture and design](docs/ARCHITECTURE.md)
- [Feature matrix](docs/FEATURES.md)
- [QA](docs/QA.md)
- [Privacy policy](PRIVACY.md)
- [Dependencies, typefaces and notices](docs/THIRD_PARTY.md)

Contact the developer through the in-app report form at `nidalwatfa99@gmail.com`. The app does not assume a published store listing.
