# Timeline agentique (Android, Arabic-first)

A daily timeline instead of a chatbot: prayer times, Hifz review, halal label scan, zakat canvas and travel
rulings, with an on-device Gemma model used only for reading photos. Kotlin + Jetpack Compose, min SDK 26.

## Quick start
```bash
bash scripts/fetch_quran.sh            # Tanzil Uthmani text -> app/src/main/assets/quran/ (Hifz needs it)
gradle wrapper --gradle-version 8.11.1 # once: generates gradlew (the wrapper jar is not in this zip)
./gradlew :app:testDebugUnitTest :app:assembleDebug -PMODEL_URL="https://.../model.task"
```
Open in Android Studio (Ladybug or newer, JDK 17) and run. In the app: Settings -> pick your madhhab,
allow location (or enter home coordinates), then download the model.

## What is deterministic vs model
| Feature | Who decides |
|---|---|
| Prayer times, Qibla | Adhan library |
| Qasr / jam' / stay limit | `TravelRules` (madhhab chosen by the user) |
| Zakat | `ZakatCalculator`, every line shown |
| Hifz scheduling | SM-2 style scheduler |
| Which ayat a photo shows | `MushafMatcher` against the Tanzil corpus |
| Halal flags | `halal_rules.json` + `HalalAnalyzer` |
| Reading text from a photo | Gemma (MediaPipe LLM Inference), transcription only |
The model never produces Quran text, rulings or confidence scores.

## Structure
`core/` settings, location, notifications, locale | `prayer/` times, alarms, travel rules | `quran/` corpus, matcher,
Hifz | `halal/` rules + analyzer | `zakat/` | `llm/` engine + model download | `agent/` timeline rules | `ui/` Compose.

## CI (GitHub Actions)
`.github/workflows/ci.yml`: fetch corpus, unit tests, lint, debug APK artifact. Optional repo variable `MODEL_URL`.
Tags `v*` make a missing corpus a hard failure. Release signing is not set up yet.

## Adding English later
Add `en` to `AppLocale.supported`, create `res/values-en/{strings,arrays}.xml`, add `<locale android:name="en"/>`
to `res/xml/locales_config.xml`, and add `en` notes to `halal_rules.json`. Layout direction switches automatically.

## Known limits / TODO before shipping
- **Never compiled here**: written without Gradle or the Android SDK. First CI run may need small fixes, most likely in
  `llm/LlmEngine.kt` (MediaPipe GenAI API and version) and dependency versions.
- **Scholarly review pending**: `halal_rules.json` (`reviewed:false`, no sources attached) and `TravelRules` thresholds.
  The UI says so next to every result.
- **Quran font**: bundle a proper Uthmani font (e.g. KFGQPC / Amiri Quran, OFL); system fonts render marks poorly.
- Tanzil terms: keep the text unmodified and credit tanzil.net. Do not commit it to a public repo without checking.
- Model token is stored in app-private DataStore, unencrypted. Gated Hugging Face models need it.
- Exact-alarm permission: check Google Play policy for prayer reminders before publishing.
- R8 is off; add keep rules for MediaPipe/protobuf before enabling.
