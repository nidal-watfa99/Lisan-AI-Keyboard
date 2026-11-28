# Third-party dependencies and font notices

Dependency versions used for this build are pinned in `build.gradle` and `app/build.gradle`. The app uses no service credentials and does not redistribute the Android SDK or Google language model files. Review the upstream license and terms for each Google binary before public distribution.

| Component | Version in project | License / terms |
|---|---|---|
| Android Gradle Plugin and SDK | AGP 8.13.2, Platform 36, Build-Tools 35.0.0 | [AGP release documentation](https://developer.android.com/build/releases/agp-8-13-0-release-notes); Android SDK tools obtained separately under Android SDK terms. |
| Gradle Wrapper | 8.13 | Apache License 2.0. Scripts and wrapper redistribution retain their notices. |
| Kotlin and Compose compiler plugin | 2.2.21 | Apache License 2.0. |
| AndroidX Activity, Compose UI, Material 3, DataStore, AndroidX Test | Activity 1.11.0, Compose BOM 2025.10.01, DataStore 1.1.7, runner 1.6.2, test ext 1.2.1 | AndroidX libraries are ordinarily Apache License 2.0; refer to upstream artifact notices. |
| Kotlinx coroutines Play Services | 1.10.2 | Apache License 2.0. |
| JUnit | 4.13.2 | Eclipse Public License 1.0. |
| JSON-java (JVM QA only) | 20250107 | [Public domain notice in upstream repository](https://github.com/stleary/JSON-java/blob/20250107/LICENSE); not packaged in the APK. |
| Google ML Kit | Translation 17.0.3 and bundled Language ID 17.0.6 | Google-distributed binaries; see [Android translation](https://developers.google.com/ml-kit/language/translation/android), [Android language ID](https://developers.google.com/ml-kit/language/identification/android), and [on-device translation guidelines](https://developers.google.com/ml-kit/language/translation/translation-terms). Models are downloaded from the provider only when requested. |
| whisper.cpp and ggml sources | v1.8.2 | [Whisper.cpp MIT terms](https://github.com/ggml-org/whisper.cpp/blob/v1.8.2/LICENSE), included in `app/src/main/cpp/vendor/whisper/LICENSE`. [Whisper Tiny multilingual Q5_1 weights](https://huggingface.co/ggerganov/whisper.cpp) are retrieved separately; original [OpenAI Whisper](https://github.com/openai/whisper) is MIT-licensed. |

## Bundled fonts

- Cairo (variable), Tajawal Regular, and Amiri Regular are from Google Fonts and redistributed under the SIL Open Font License 1.1. Exact copyright and license notices are in `app/src/main/assets/fonts/`.
- System, serif and monospace options use fonts on the Android device; they are not redistributed.
- Application marks, Android icon and keyboard theme layouts are original resources in this source package.
- The separate “Powered by Google Translate” badge, shown with machine translations, is Google branding from the [Google Cloud attribution requirements and badge download](https://docs.cloud.google.com/translate/attribution); it is not part of the Lisan mark.

To inspect resolved transitive dependencies using the same artifact repositories:

```sh
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

## Emoji data

- `app/src/main/assets/emoji.txt` is generated from the Unicode `emoji-test.txt` (Emoji 18.0), © Unicode, Inc., Unicode License v3.
- `app/src/main/assets/symbols.txt` lists the Unicode symbol blocks (arrows, mathematics, currency, shapes, Braille, ...) and was generated from the Unicode Character Database, © Unicode, Inc., Unicode License v3. Symbols a device font cannot draw are hidden in the panel.

