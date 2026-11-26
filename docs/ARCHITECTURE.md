# Architecture and design

## Deployment

One Android app module produces an APK containing a companion `MainActivity` and one protected Android `InputMethodService`. Code is separated by boundary within the module rather than distributed across multiple Gradle projects. Gradle pins the Android Gradle plugin, Kotlin, Compose BOM and engine libraries. `minSdk` is 26, `targetSdk` is 35, and `compileSdk` is 36.

| Boundary | Purpose |
|---|---|
| `keyboard/Board.kt` | Lightweight native board shared between Android IME and in-app `AndroidView` previews. Resolves palette, typography, accessibility descriptions, touch/long press and bounded dimensions. |
| `keyboard/LisanIme.kt` | Input connections, Android editor actions, Unicode grapheme handling, security flags, translation/clipboard/emoji/editing panes and speech-to-field routing. |
| `core/` | Layouts and dictionary heuristics, validated settings schema, and process-wide DataStore `StateFlow`. |
| `voice/` | Provider interface, Android recognizer adapter and an independent `AudioRecord`/Whisper provider. Native inference is compiled via CMake/NDK. Model management verifies a pinned SHA-256. PCM is held transiently in memory, not written to disk. |
| `translation/` | Asynchronous ML Kit client with explicit model management and bundled language identification. `CloudTranslator` is an optional standalone-screen HTTPS adapter, entered only through per-request confirmation. |
| `privacy/` | Local AtomicFile and AES-GCM encryption using an Android Keystore key. No cleartext data in exports. |
| `emoji/` | Categorized Unicode sequences, local aliases and supported variants. |
| `*Screens.kt` | Bilingual Material 3 companion controls. State is managed with Compose, coroutine scopes and the persistent settings flow. |

## Editor policy

The service identifies numeric/password/no-learning editors with `EditorInfo` and avoids text reads for flagged sensitive editors. Field generations protect asynchronous callbacks from inserting into a different editor. Explicit reads only fetch bounded text, and consent is required for missing translation model downloads and non-local speech selection. Pressing a normal key delegates a local edit through the current `InputConnection`; the app does not implement its own cross-app accessibility capture. Users must grant Android keyboard privileges in their system settings.

## Brand and layout

The vector mark combines a mint speech bubble, keyboard strokes and a lavender spark. Adaptive application icon and splash assets are in `app/src/main/res`. The companion visual palette includes ink `#101826`, mint `#8AEDCF` and lavender `#C4B8FF`. The gallery previews Premium Dark, Modern Light, Gradient AI, Minimal Professional, Forest Atelier, Frosted Glass and Personal Studio. For each preset, the same board code draws keys; no screenshots are used as interaction surfaces. Custom theme JSON validates colors and practical dimension ranges.

## Future platform and providers

Keep language layout data, the settings schema and the test cases portable; iOS is not included. `TranslationEngine` and `VoiceEngine` separate the local engine paths from the UI. A deployable cloud translation service requires a compatible server provided by the operator; see [engine setup](ENGINES.md). No generative writing provider is configured.
