<div align="center">

# Lisan · لسان

### A private, multilingual Android keyboard — offline dictation, on-device translation,<br>a complete emoji & symbol library and a full design studio.

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Version](https://img.shields.io/badge/version-0.2.0-0B1220?style=for-the-badge)
![License](https://img.shields.io/badge/license-Proprietary-lightgrey?style=for-the-badge)

**English** · [العربية](README.ar.md)

</div>

---

Lisan is a **real Android input method** (`InputMethodService`), not a web preview. It pairs a fast, customizable keyboard with a companion app for settings, themes, voice models and translation. Arabic and English are first-class citizens: right-to-left typing, Arabic and Western digits, and an interface in both languages.

> **Status:** version `0.2.0` is a test build. Read the [feature matrix](docs/FEATURES.md) and the [QA notes](docs/QA.md) before distributing it.

## ✨ Highlights

| | |
|---|---|
| ⌨️ **Six layouts** | Arabic, English, French, German, Dutch and Turkish. Switch with the globe key; hold it to pick a layout or open the system picker. |
| 😀 **Complete emoji catalogue** | Nearly 4,000 Unicode emoji sequences with skin tones and flags, search, favourites and recents. |
| Ω **3,500+ symbols** | 18 groups: arrows, mathematics, Greek, currency, punctuation, Arabic marks (﷽ ﷺ ۞ ﴾ ﴿ ﷼), shapes, box drawing, technical, games, music, Braille and more. |
| 🎙️ **Offline dictation** | On-device Whisper (Tiny, multilingual, 32.2 MB) with Android's recognizer as a fallback. Whisper never stores or sends your audio. |
| 🌍 **On-device translation** | Translate field text, copied text or speech with Google ML Kit, then edit the draft before inserting it. |
| 🎨 **Design studio** | Seven live-preview themes, adjustable size, typography, colours, borders and shadows, plus personal themes with JSON export/import. |
| 🔤 **Fonts & text styles** | Cairo, Tajawal, Amiri, system fonts or your own `.ttf`/`.otf`; 20 decorative styles (bold, script, gothic, circled…) that work in any app. |
| 📋 **Private clipboard** | An encrypted local library (Android Keystore) with search, categories, pinning and translate-and-insert. No background monitoring. |
| ✍️ **Smart typing** | Spelling suggestions and optional, conservative auto-correction for Arabic and English with one-tap undo, personal words and an ignore list. |
| 😎 **Text faces** | A library of kaomoji for quick expressive messages. |

## 🔒 Privacy first

- No account and **no keystroke or password logging**.
- Password fields and editors that opt out of learning never expose translation, dictation, clipboard or prediction.
- Translation and language detection run **on the device**. The network is only used to download models you choose (ML Kit, Whisper).
- The clipboard is read only when you ask. Saved items are encrypted locally and are not part of the JSON settings backup.
- Crash traces stay on the device and are sent only if you attach them to a report yourself.

Read the full [privacy policy](PRIVACY.md).

## 📲 Install and enable

1. Download the APK (`app-debug.apk`) from the project's releases or from the **Build APK** workflow artifact `Lisan-AI-Keyboard-debug`. Requires **Android 8.0 or later**.
2. Open the file. If Android asks, allow your browser or file manager to install apps **for this one time**, then turn the permission off again.
3. Open **Lisan** and choose your layouts. Arabic and English are on by default.
4. Tap **Enable in Android**, switch Lisan on in the system list, return to the app and tap **Choose keyboard**. Android requires you to do these two steps yourself.
5. Type in the test field on the home screen, then try Lisan in any other app.

## 🧭 Using Lisan

- **Globe key:** tap to cycle layouts; hold to choose a layout or open the Android keyboard picker.
- **Delete:** hold to remove a whole word. **Enter:** hold to hide the keyboard.
- **Toolbar:** translate · microphone · clipboard · emoji · cursor tools · language · settings. Reorder it from the settings.
- **Emoji panel:** pick a group, search, or tap **Ω** for the full symbol library. Symbols and emoji your device fonts cannot draw are hidden automatically.
- **Themes:** open **Themes**, preview each design on the live board, then tap **Apply to keyboard**. The default is *Premium Dark*.
- **Dictation:** in **Voice & microphone** pick a language and download the Whisper model (or import it manually). Tap the microphone to finish a phrase, tap again to start a new one. Grant the microphone permission only for dictation.
- **Translation:** in **Translate** choose source and target for one session or save them as defaults. Install language models under **Models** or accept the download prompt.

## 🛠️ Build from source

Open the project in Android Studio with **JDK 17**, Android SDK Platform **36**, Build-Tools **35.0.0**, NDK `28.2.13676358` and CMake `3.22.1`. Set `ANDROID_HOME` when building from a terminal. The first sync needs access to Google Maven, Maven Central and the Gradle Plugin Portal.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # needs a device or emulator
```

On Windows use `gradlew.bat`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The repository also ships a GitHub Actions workflow (**Build APK**) that builds the debug APK on every push to `main`. Configure your own signing credentials outside source control before any production release; no signing key is supplied.

**Stack:** Kotlin · Jetpack Compose · `InputMethodService` · DataStore · Android Keystore · Google ML Kit Translation · whisper.cpp (native, `arm64-v8a` and `armeabi-v7a`).

## 📚 Documentation

[Architecture](docs/ARCHITECTURE.md) · [Features](docs/FEATURES.md) · [Speech & translation engines](docs/ENGINES.md) · [QA](docs/QA.md) · [Privacy](PRIVACY.md) · [Security](SECURITY.md) · [Third-party notices](docs/THIRD_PARTY.md)

## 👤 Author

**Nidal Watfa** — independent software developer from Syria.
*Vanilla Evolution — strength in simplicity and performance.*

| | |
|---|---|
| 📧 Email | [nidalwatfa99@gmail.com](mailto:nidalwatfa99@gmail.com) |
| 🐙 GitHub | [nidal-watfa99](https://github.com/nidal-watfa99?tab=repositories) |
| 🌐 Portfolio | [nidal-watfa99.github.io/NIDAL-WATFA](https://nidal-watfa99.github.io/NIDAL-WATFA/?lang=en) |
| 🪨 Codeberg | [nidalwatfa](https://codeberg.org/nidalwatfa) |
| 💼 LinkedIn | [nidal-watfa](https://www.linkedin.com/in/nidal-watfa) |
| 🐦 X | [@NidalWatfa12501](https://x.com/NidalWatfa12501) |
| 📱 Telegram | [@nidal12watfa](https://t.me/nidal12watfa) |
| 🆔 ORCID | [0009-0003-2462-6630](https://orcid.org/0009-0003-2462-6630) |

Found a bug or have an idea? Use **Contact the developer** inside the app, or write to the email above.

## 📄 License

Copyright © 2026 Nidal Watfa. All rights reserved. Lisan is distributed under a proprietary license — see [LICENSE](LICENSE).
