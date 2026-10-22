# OpenEar — Product Vision, Requirements, and Implementation Plan

> **Purpose:** Give an AI coding agent a complete, practical specification for building OpenEar: a privacy-first, offline Android dictation keyboard for Indian English and Hinglish.
>
> **Primary outcome:** A user taps a microphone key in OpenEar while typing in another app, speaks naturally, and receives editable text in the current text field—without Google's speech recognition and without sending audio to a server.

## 1. Product summary

**App name:** OpenEar  
**Platform:** Android  
**Core technology:** Kotlin, Jetpack Compose for app screens, Android `InputMethodService` for the keyboard, and `whisper.cpp` through JNI/NDK for on-device speech recognition.  
**Design:** Simple, modern, monochrome black-and-white UI, with accessible contrast and restrained use of system status colors.  
**Privacy:** Speech recognition and audio processing happen on the device. No account, analytics, ads, cloud transcription, or network permission is required for core dictation. Model downloads and app updates may use the network only when the user explicitly chooses those features.

OpenEar is primarily a **dictation keyboard**, not a general voice assistant. It should be selectable as the Android default keyboard and usable inside apps such as WhatsApp, Telegram, email, notes, browsers, and other apps that accept text input through Android's keyboard system.

## 2. Product goals

### Must-have goals

1. Provide a keyboard microphone button that records speech and inserts recognized text into the focused field.
2. Work completely offline after the app and selected speech model have been installed.
3. Prioritize Indian English, Hindi, and natural Hindi-English code-switching (Hinglish).
4. Keep the app lightweight at rest and avoid unnecessary background work.
5. Provide clear recording state, cancellation, error messages, and user-controlled permissions.
6. Build and test the APK in GitHub Actions so the developer does not need Android Studio, Android SDK, or a large IDE installed locally.
7. Publish APKs as downloadable workflow artifacts and, on tagged releases, optionally attach them to a GitHub Release.
8. Include automated tests, build checks, license notices, and setup documentation.
9. Offer a small, readable, monochrome interface with good accessibility.
10. Let users choose a model and understand the trade-off between speed, storage, memory, and accuracy.

### Non-goals for the first release

- Do not build a cloud service or require an OpenAI API key.
- Do not claim to match Google Keyboard's speed or accuracy on every device/language.
- Do not use accessibility services to inject text into other apps.
- Do not secretly capture audio, run a permanent microphone service, or record while the user is not actively dictating.
- Do not add an LLM rewriting layer by default. Transcription should preserve what the user said rather than silently paraphrasing it.
- Do not promise to work inside secure fields, password fields, every custom editor, or apps that deliberately restrict third-party keyboards.
- Do not bundle every model into the APK. Models are large and should be managed separately.

## 3. Critical Android integration strategy

### 3.1 Build a real Android keyboard (IME)

The main product must implement Android's `InputMethodService`. This is the correct supported mechanism for appearing anywhere a user can select a keyboard, including many text fields in third-party apps.

The user experience:

1. User installs OpenEar.
2. The onboarding screen explains that OpenEar is a keyboard and that speech recognition runs locally.
3. User taps **Enable OpenEar**. Open Android's system input-method settings using the appropriate settings intent.
4. User enables OpenEar in system settings.
5. User taps **Choose keyboard**. Open the Android input-method picker.
6. User selects OpenEar as the current keyboard.
7. In a compatible app, the user taps a text field, opens OpenEar, and taps its microphone button.
8. OpenEar records speech, transcribes locally, and inserts the result into the active input connection.
9. The user can edit the inserted text normally and send/submit it themselves.

Do not attempt to silently enable OpenEar or set it as the default keyboard. Android requires user action for this.

### 3.2 Understand the limits of “works everywhere”

OpenEar should be available wherever Android permits a user-selected third-party IME. It cannot guarantee operation in every app or every field. Some apps use custom editors, suppress third-party keyboards, or mark fields as password/sensitive. Android may restrict keyboard access to sensitive fields. The app must respect these platform protections.

The initial scope is **keyboard-level integration**, not an overlay floating over every app. An overlay/accessibility workaround is out of scope because it is less reliable, raises privacy concerns, and can violate user expectations or platform policies.

### 3.3 Keyboard UI

The keyboard should initially be a focused dictation surface rather than a full QWERTY replacement. It should provide:

- Large, easy-to-tap microphone button.
- Clear state labels: **Tap to speak**, **Listening…**, **Transcribing…**, **Text inserted**, and actionable errors.
- Stop/finish and cancel controls while recording.
- Optional small keyboard panel with basic text-editing controls: backspace, space, enter, and a way to switch back to the user's previous keyboard.
- A visible **Switch keyboard** control using Android's supported IME picker.
- A compact status row showing the selected model and whether it is ready.
- A route to OpenEar settings without interrupting the current dictation.
- Layouts that adapt to portrait, landscape, small screens, and system font scaling.
- No ads, promotional banners, or unnecessary visual clutter.

Consider an optional future **full keyboard layout** only after the dictation IME is stable. Do not block the first release on implementing a full Gboard replacement.

## 4. Technology choices

### Recommended stack

- **Language:** Kotlin for application and keyboard logic.
- **UI:** Jetpack Compose for the main app/settings/onboarding. Use a custom IME view compatible with the project's Android UI approach; Compose can be used inside an IME if it remains stable and lightweight, otherwise use a simple custom View for the keyboard surface.
- **Android integration:** `InputMethodService`, `InputConnection`, `AudioRecord`, Android `Settings` intents and input-method picker.
- **Speech inference:** `whisper.cpp` native C/C++ library built for Android with the Android NDK and CMake; call it through a small JNI layer.
- **Concurrency:** Kotlin coroutines for UI orchestration; a dedicated background dispatcher/thread for native inference. Never run audio capture or inference on the main thread.
- **Preferences:** Jetpack DataStore for small settings.
- **Tests:** JUnit for Kotlin unit tests, AndroidX test/Espresso where useful, and native tests for JNI/audio preprocessing where practical.
- **CI:** GitHub Actions, Gradle Wrapper, JDK, Android SDK command-line tools, NDK/CMake as required by the build.
- **Minimum Android version:** Target a practical modern baseline (recommend minSdk 26 initially, unless dependency or device research justifies a different baseline). Always target a currently supported SDK level required by Google Play if Play distribution is planned. Keep target SDK configurable and documented.
- **App architecture:** Small modular architecture, avoiding unnecessary dependency-heavy frameworks.

### Repository integration strategy

Use the upstream [`ggml-org/whisper.cpp`](https://github.com/ggml-org/whisper.cpp) project as the native engine. Review its current Android example and build files before choosing how to integrate it.

Preferred options, in order:

1. Vendor or include a pinned upstream revision as a Git submodule/subtree, then build its required native components through CMake/Gradle.
2. If a submodule makes GitHub Actions or contributor setup fragile, use a pinned source archive/revision downloaded by CI and verified with a checksum.
3. Avoid copying a large, untracked collection of upstream source files into OpenEar without recording provenance and license notices.

Pin upstream versions/commits instead of building against a moving `master` branch. Document how to update the engine safely. Do not edit upstream engine code unless necessary; keep OpenEar-specific code in a thin wrapper.

## 5. Model selection and lifecycle

### Initial model

Start with a **multilingual Whisper `base` model** converted to the format expected by `whisper.cpp`. Do not use `base.en`, because English-only models are unsuitable for Hindi/Hinglish code-switching.

Offer `tiny` as a low-resource option and `small` as an optional higher-accuracy model for capable devices. Actual performance and Hinglish accuracy must be benchmarked on real Android hardware; do not make unsupported performance promises.

The upstream `whisper.cpp` project recommends tiny/base for Android as a starting point. Approximate upstream memory figures are environment-dependent and are not guarantees for Android:
- `tiny`: around 75 MiB model file, roughly 273 MB memory in the upstream reference table.
- `base`: around 142 MiB model file, roughly 388 MB memory in the upstream reference table.
- `small`: around 466 MiB model file, roughly 852 MB memory in the upstream reference table.

Model files may have different sizes after quantization. Test quality before choosing a default quantized variant.

### Model management requirements

- Do not put a large model inside the APK by default; that inflates downloads and installation size.
- On first run, explain that a model must be downloaded once. Offer a model download screen with size, expected quality/speed trade-off, storage check, progress, pause/cancel, retry, and checksum verification.
- Make it explicit that the initial model download uses the network, but subsequent transcription is offline.
- Allow the user to import a compatible model file from Android's document picker as an advanced option.
- Store models in app-private storage where possible; do not request broad storage access.
- Use a manifest/metadata record with expected file size and SHA-256 checksum for official downloads.
- Never load a partial, corrupt, or unverified model.
- Show model state: not installed, downloading, verifying, ready, update available, or failed.
- Let users delete a model and reclaim space.
- Do not silently download a larger model.
- Preserve settings and transcripts (if history is enabled) when a model is changed.
- Handle insufficient storage gracefully before downloading.
- If a download source is configured, use HTTPS and a pinned/versioned source. Do not hardcode a fragile link to an unversioned file.
- Keep the core runtime free of network calls after the model is installed. The app should not need the INTERNET permission unless model downloading or update checking is implemented. If it is included, explain why and ensure transcription code never sends audio or transcript content over the network.

### Indian language and Hinglish behavior

- Use multilingual Whisper, not an English-only model.
- Test Indian English, Hindi, and Hindi-English code-switching with different accents, speaking rates, and background noise.
- Do not automatically translate speech to English.
- Do not silently rewrite grammar or change meaning.
- Provide a setting for transcription language: **Auto-detect**, **English**, **Hindi**, and any other language that the chosen model supports and the app can test properly. Make it clear that forcing a language may reduce results for code-switching.
- Default to multilingual auto-detection, then use evaluation data to determine whether a user-selectable Hindi/English mode improves results.
- Test both Roman-script Hinglish (e.g. “kal meeting kitne baje hai?”) and Devanagari Hindi with English words. Whisper may choose a script inconsistently; document this limitation and never promise perfect script control without a verified method.
- Add a lightweight optional punctuation/spacing setting only if it can be implemented without changing the spoken meaning.
- Add optional custom vocabulary or prompt hints only if supported reliably by the current `whisper.cpp` API; document that hints can bias results and are not a guaranteed correction dictionary.
- Avoid a cloud-based correction service.

## 6. Audio capture and transcription pipeline

### Recording

- Request `RECORD_AUDIO` only when the user first taps the microphone (or explain it immediately before that action).
- Record only after explicit user action.
- Use Android `AudioRecord` with a supported microphone input and a sensible sample rate; convert to the model's required mono PCM format/rate in a tested preprocessing layer.
- Handle devices that do not support the preferred audio configuration by trying a documented fallback.
- Keep recording in memory or in a temporary app-private file; do not retain audio after transcription unless the user explicitly chooses an export/save action.
- Release `AudioRecord` and audio resources reliably on stop, cancel, exception, app interruption, and lifecycle changes.
- Handle phone calls, audio focus/interruption, permission revocation, device rotation, and keyboard dismissal.
- Show a persistent, unmistakable listening indicator while recording. Respect Android microphone privacy indicators.
- Limit recording duration based on memory and practical latency. If a maximum is needed, show the limit before recording and provide a clear warning as it approaches.
- Use voice activity detection only if it is available and tested; do not add a heavy dependency just for a small UX improvement.

### Transcription

- Run native inference off the main thread.
- Start with a simple “record, stop, transcribe, insert” workflow. It is more reliable for the first version than attempting continuous streaming.
- Reuse the loaded model/context while practical, but release native memory when the keyboard/app is no longer active for a configurable idle period or when the OS is under memory pressure.
- Do not initialize/load a model every time the keyboard redraws.
- Ensure JNI lifecycle and native pointers are safe, scoped, and never double-freed.
- Add cancellation where the native API supports it. If inference cannot be interrupted safely, explain that cancellation stops insertion and discards the eventual result.
- Insert recognized text using `InputConnection.commitText()` only if the same valid input connection/focused field is still active. If focus has changed, do not insert text into an unintended app/field.
- Do not automatically press Send, Enter, or Submit after dictation. The user remains in control.
- Provide a setting for whether a trailing space should be added after a successful insertion.
- Avoid duplicate insertion if callbacks or lifecycle events happen more than once.
- Treat empty or very low-quality results as recoverable; never insert placeholder text.
- Catch native errors and show short, nontechnical messages with retry guidance.

### Text editing and output actions

- Insert text directly into the focused field.
- Provide Copy to clipboard for the standalone app's transcription result and for history items.
- Provide Android Sharesheet export for a transcription when the user explicitly chooses Share.
- Support selecting and copying text in the standalone transcription/history screen.
- Allow users to discard and retry a transcription before insertion, if this can be done without making the IME awkward.
- Do not write to the clipboard automatically in the normal keyboard flow.
- Do not save a transcript to history by default unless the user enables history.

## 7. Privacy and permissions

### Permissions

Request only permissions needed for a clear feature:

- `RECORD_AUDIO`: required for dictation, requested at the point of use.
- `INTERNET`: optional, only if model download/update features are included. No audio or transcript content may be uploaded.
- Notifications: avoid requiring them for ordinary foreground keyboard dictation. If a separate recording foreground service becomes necessary due to platform restrictions, follow current Android foreground-service and microphone permission rules, show an explicit notification, and explain the reason.
- Storage: do not request broad storage permissions. Use app-private storage and Android's Storage Access Framework for importing/exporting files.
- Do not request contacts, SMS, phone, location, accessibility, usage access, or unrelated permissions.

### Privacy principles

- Offline inference is the default and core promise.
- No analytics, ad SDK, tracking SDK, account system, or crash-report upload in the initial version.
- No network endpoint receives audio or transcripts.
- Do not log transcript contents, raw audio, model prompts, or sensitive text in release builds.
- Never persist raw recordings by default.
- If optional local history is implemented, it is off by default, local-only, deletable, and clearly explained. Consider excluding history from Android cloud backup or make backup behavior explicit.
- Provide **Clear history**, **Delete downloaded models**, and **Reset settings** controls.
- State clearly in onboarding and settings what stays on-device and when the app accesses the microphone.
- Keep the microphone inactive when not actively dictating. Stop capture when the user cancels or the relevant keyboard session ends.
- Avoid requesting Android Accessibility Service permission. It is not needed for an IME and would create unnecessary trust and policy concerns.

## 8. UI and visual design

### Visual identity

- Product name: **OpenEar**
- A simple ear/waveform or microphone mark can be used, but keep the icon minimal and high-contrast.
- Black, white, and neutral grays only for normal UI; reserve system colors for important error/success status where accessibility requires them.
- Support Android light and dark themes, with the same monochrome design language.
- Large touch targets, legible typography, clear focus indicators, screen-reader labels, and support for font scaling.
- No gradients, excessive cards, ads, decorative animations, or clutter.

### Main app screens

1. **Welcome/onboarding**
   - Explain offline dictation in plain language.
   - Explain the difference between installing/enabling a keyboard and granting microphone permission.
   - Show a privacy summary.
   - Actions: Enable OpenEar, Choose OpenEar, Continue setup.

2. **Setup checklist**
   - Keyboard enabled?
   - OpenEar selected as current keyboard?
   - Microphone permission granted?
   - Model installed?
   - Test dictation complete?
   - Each item opens the correct Android settings screen or app screen.

3. **Model screen**
   - Available models, size, installed state, download progress, checksum verification, delete action, and storage information.

4. **Settings**
   - Language mode.
   - Selected model.
   - Add trailing space.
   - Optional local history toggle.
   - Haptic feedback toggle (off or subtle by default).
   - Theme: system/light/dark.
   - Privacy explanation and permissions status.
   - Licenses/open-source notices.
   - App version and diagnostics that do not include personal text/audio.

5. **Test dictation screen**
   - Record, stop, view transcript, copy, share, retry, and clear.
   - Used for testing without switching to another app.

6. **Keyboard surface**
   - Minimal dictation controls and model status.
   - No unrelated app content.
   - Clear switch-keyboard affordance.
   - Robust handling of different keyboard heights and input field types.

7. **Optional history**
   - Disabled by default.
   - Local-only searchable list if implemented.
   - Copy, share, delete one, clear all.
   - Never save audio by default.

## 9. Performance, battery, storage, and memory

The product goal is to be fast and efficient without falsely promising real-time transcription on every phone.

- Prefer `tiny` on low-memory/older phones and `base` as the quality/speed starting point. Let users select their preference.
- Benchmark quantized models; choose the best balance using actual tests rather than assuming quantization always improves every device.
- Load the model only when needed and cache the native context while active.
- Avoid continuously listening, background polling, wake locks, and always-on services.
- Use bounded audio buffers and stream chunks into the recording buffer where possible.
- Avoid retaining duplicate copies of full audio arrays.
- Use a small number of native inference threads by default; tune based on device benchmarks and thermal behavior.
- Show a busy state during inference and prevent double-tapping from starting concurrent runs.
- Release resources when the keyboard is hidden or the OS requests memory, while balancing the cost of reloading.
- Avoid loading the model just to display the keyboard.
- Use Android memory callbacks and catch low-memory situations.
- Check available storage before downloading a model.
- Keep APK size small by downloading models separately.
- Do not include sample audio or debug model files in production APKs.
- Measure cold start, model-load time, transcription time, peak memory, APK size, and battery/thermal behavior.
- Provide a “low resource mode” if tests show it is useful: smaller model, fewer threads, shorter recording limit, and release model sooner.
- Never lower audio quality or truncate the transcript silently to save resources.

## 10. GitHub Actions: build APK without local Android tooling

The developer should be able to edit files in a browser or use a cloud coding agent, push to GitHub, and download a built APK from Actions. No local Android Studio/Visual Studio/Android SDK installation should be required.

### CI requirements

Create `.github/workflows/android.yml` that:

1. Runs on `push`, `pull_request`, and manual `workflow_dispatch`.
2. Uses Ubuntu GitHub-hosted runners.
3. Checks out the repository.
4. Sets up a supported JDK version compatible with the pinned Gradle/Android Gradle Plugin.
5. Installs the required Android SDK packages, NDK, and CMake versions using pinned/configurable versions.
6. Enables Gradle caching.
7. Runs formatting/lint checks if configured.
8. Runs unit tests.
9. Builds debug APK on every pull request and push.
10. Builds release APK on the main branch or a release/tag workflow.
11. Uploads APKs and test reports as workflow artifacts even when tests fail, where feasible.
12. Uses least-privilege `GITHUB_TOKEN` permissions.
13. Never prints signing secrets or stores keystores in the repository.
14. Uses pinned third-party actions by full commit SHA for stronger supply-chain security where practical; document update steps.
15. Uses Gradle Wrapper checked into the repository. Ensure the wrapper is executable and its distribution/version is pinned.

### Artifact and release strategy

- For development/testing, create a debug APK and upload it as `OpenEar-debug-apk`.
- For a release build, create an unsigned or properly signed release APK according to the chosen distribution plan. Do not pretend an unsigned APK is suitable for all installation/update scenarios.
- If signing is required, use GitHub repository secrets for the keystore/base64 and passwords; decode only during the job, keep files out of logs, and delete temporary key files after signing.
- Do not commit private signing keys.
- For tagged versions such as `v0.1.0`, optionally create a GitHub Release with APK and checksum files.
- Include SHA-256 checksums for APK artifacts.
- Upload test results and lint reports to make failures easy to diagnose.
- Cache Gradle dependencies, not generated secrets.
- Pin model-download metadata and avoid downloading the large speech model during ordinary CI tests unless a separate integration-test job explicitly requires it.

### Build workflow acceptance criteria

- A clean GitHub checkout builds without needing any local machine-specific path.
- CI builds the native Android libraries and Kotlin app.
- CI produces a downloadable APK artifact.
- CI fails if unit tests fail or compilation fails.
- CI prints useful diagnostics on failure.
- A documented manual workflow run is possible from GitHub Actions.
- No production model is accidentally bundled into the APK.

## 11. Testing strategy — implement and run tests before declaring completion

The AI coding agent must write actual tests, run them, inspect failures, and fix the code. Do not claim tests passed unless the test commands actually completed successfully in the available environment. If local tooling is unavailable, configure CI and clearly state that GitHub Actions must run the tests.

### Unit tests

- Settings defaults and persistence.
- Model metadata parsing and checksum validation.
- Model-state transitions: missing, downloading, verifying, ready, failed.
- Audio conversion to expected mono PCM/sample rate, using small deterministic test fixtures.
- Empty audio and invalid audio handling.
- Transcript formatting and trailing-space behavior.
- No duplicate text insertion when completion callbacks repeat.
- Permission-denied and permission-revoked paths.
- Cancellation behavior.
- Native bridge input validation and error propagation.
- Storage availability checks.
- History disabled means no transcript is persisted.
- Privacy/logging helper does not emit transcript/audio contents.

### Android/instrumentation tests

- App launches and onboarding renders.
- Setup checklist reflects permission/model state.
- Microphone permission denied → clear explanation and retry path.
- Missing model → transcription is blocked with a useful message.
- Model download failure → retry and cancel work.
- A test IME or instrumentation harness verifies text insertion into a controlled editable field where feasible.
- Switching keyboard and returning to OpenEar does not crash.
- Rotation, background/foreground transitions, and keyboard dismissal release recording resources.
- Accessibility labels and touch targets are checked manually or with available tooling.

### Native tests

- JNI library loads on supported ABIs.
- Native context initialization errors are handled.
- Model load/unload lifecycle is safe.
- Invalid path, missing model, empty buffer, and repeated close are handled without crashes.
- Add a small transcription smoke test in a separate CI job only if a licensed/approved fixture and model download are available. Do not make normal CI brittle by depending on a huge model download.

### Hinglish/Indian evaluation set

Create a documented, manually curated evaluation set with consented or synthetic phrases, not private recordings. Include phrases such as:

- “Kal meeting kitne baje start hogi?”
- “Please mujhe woh document WhatsApp par bhej dena.”
- “Aaj office thoda late pahunchunga.”
- “Is file ko download karke email kar do.”
- Hindi sentences in Devanagari and Indian English examples.

Include several accents and quiet/noisy conditions where test recordings are ethically sourced and licensed. Evaluate word error rate where a reliable reference transcript exists, but also manually review code-switching, script choice, names, punctuation, and meaning preservation. Do not bake these example phrases into app behavior as special cases.

### Test commands and reports

Document the actual Gradle commands supported by the project, for example:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

If Android instrumentation tests require an emulator, configure them in a separate CI job with an emulator action or Android emulator setup. Ensure the workflow's SDK/NDK/Gradle versions match the project. Save XML/HTML test reports as Actions artifacts.

## 12. Error handling and accessibility

Provide concise, actionable messages for:

- Microphone permission denied.
- Microphone busy/unavailable.
- Recording interrupted.
- No speech detected.
- Model missing/corrupt/incompatible.
- Insufficient storage.
- Download cancelled/failed.
- Native inference failure.
- Keyboard not enabled or not selected.
- Focused input field disappeared.
- Device memory pressure.
- Unsupported or restricted input field.

The app must not crash or insert misleading text when any of these occur. Add screen-reader descriptions, visible focus, adequate contrast, and large targets. Do not communicate recording state through color alone.

## 13. Security and release hygiene

- Keep dependencies to a minimum and pin versions.
- Review licenses for all dependencies and include third-party notices.
- Do not include secrets or credentials in source code.
- Do not log raw audio, transcript text, or text from other apps.
- Do not use `INTERNET` for transcription.
- Validate imported model paths and downloaded model checksums.
- Do not load arbitrary native libraries or executable content from imported files.
- Restrict exported Android components to only those that need to be public.
- Use an explicit app-specific `FileProvider` only if a feature requires safe file sharing.
- Ensure temporary audio files are deleted on success, failure, and cancellation.
- Keep release builds free of debug logging and sample data.
- Document the threat model: OpenEar processes spoken content locally, but text inserted into WhatsApp/Telegram/etc. is subsequently handled by those apps under their own privacy policies.

## 14. License and attribution requirements

- `whisper.cpp` is MIT-licensed; retain its license and notices when distributing derived/bundled code.
- Whisper model weights are also released under the MIT license by OpenAI; include model attribution and relevant license information with downloads/distribution.
- Check the exact licenses of Android dependencies, native dependencies, fonts, icons, and any sample audio.
- Do not redistribute third-party sample recordings without checking their licenses.
- Include an **Open-source licenses** screen or document.
- Add a `THIRD_PARTY_NOTICES.md` file and update it when dependencies change.
- Do not imply OpenEar is an official OpenAI product. State that it is an independent community project using Whisper/whisper.cpp.

## 15. Suggested repository layout

Adapt the structure to the upstream Android example and the chosen Gradle/CMake arrangement. Avoid gratuitous complexity.

```text
OpenEar/
├── .github/
│   └── workflows/
│       └── android.yml
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/.../openear/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── onboarding/
│   │   │   │   ├── settings/
│   │   │   │   ├── model/
│   │   │   │   ├── audio/
│   │   │   │   ├── transcription/
│   │   │   │   ├── keyboard/
│   │   │   │   ├── privacy/
│   │   │   │   └── storage/
│   │   │   ├── cpp/
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   └── openear_jni.cpp
│   │   │   └── res/
│   │   ├── test/
│   │   └── androidTest/
├── gradle/
├── gradlew
├── settings.gradle.kts
├── build.gradle.kts
├── README.md
├── idea.md
├── PRIVACY.md
├── THIRD_PARTY_NOTICES.md
└── LICENSE
```

This is a suggested logical layout, not a mandate to move files in a way that breaks the upstream Android sample. The agent should inspect the current upstream project before implementing.

## 16. Milestones

### Milestone 1 — Repository and CI skeleton

- Create a minimal Android project using Kotlin.
- Pin Gradle/AGP/JDK/SDK/NDK/CMake versions.
- Add GitHub Actions build workflow.
- Produce a basic debug APK artifact.
- Add initial unit tests and make CI green.

**Done when:** A GitHub push produces a downloadable debug APK without local Android tools.

### Milestone 2 — Local transcription proof of concept

- Integrate `whisper.cpp` using a pinned revision.
- Add a JNI bridge.
- Add model management for multilingual `tiny`/`base`.
- Add a standalone test-dictation screen.
- Record audio, transcribe locally, and show results.

**Done when:** A user can install the APK, install a model, dictate in airplane mode, and see a transcript.

### Milestone 3 — Keyboard/IME integration

- Implement `InputMethodService`.
- Add setup flows to enable/select OpenEar.
- Add microphone recording from the keyboard.
- Insert text into a controlled test field and compatible external apps.
- Handle focus loss, cancellation, permission denial, and lifecycle changes.

**Done when:** Dictation inserts text into supported third-party app fields without copying/pasting manually.

### Milestone 4 — Indian-context quality and performance

- Test Hindi, Indian English, and Hinglish.
- Compare tiny/base and any quantized candidates.
- Measure latency, peak memory, model load time, and thermal behavior on representative devices.
- Tune audio preprocessing and thread count.
- Document limitations and recommended settings for low-resource devices.

**Done when:** The default model is chosen using measured results, not guesswork.

### Milestone 5 — Polish and release readiness

- Finalize monochrome UI, accessibility, settings, and license screen.
- Add privacy and release documentation.
- Run unit tests, lint, native checks, and instrumentation tests where available.
- Verify debug/release APK artifacts and checksums.
- Create a tagged release workflow and signing instructions.

**Done when:** CI is green, no known critical crashes remain, privacy behavior is verified, and installation/testing instructions are clear.

## 17. Acceptance criteria for version 0.1

Version 0.1 is successful when all of the following are true:

- [ ] The app builds in GitHub Actions without requiring local Android Studio/SDK installation.
- [ ] GitHub Actions uploads a downloadable debug APK.
- [ ] The app uses Kotlin and a simple monochrome UI.
- [ ] The user can enable and select OpenEar as an Android keyboard.
- [ ] The user can grant microphone permission explicitly.
- [ ] A multilingual Whisper model can be installed and verified.
- [ ] The app can transcribe speech locally in airplane mode after model installation.
- [ ] Transcribed text can be inserted into a supported active text field.
- [ ] The app never uploads audio or transcript text.
- [ ] No always-on microphone or hidden background recording exists.
- [ ] Denied permissions, missing model, cancelled recording, and native failures are handled gracefully.
- [ ] The model can be removed to reclaim storage.
- [ ] Automated unit tests are present and run in CI.
- [ ] The agent reports exact test/build commands and actual results; it does not claim success for tests it did not run.
- [ ] License and privacy notices are included.
- [ ] Limitations around secure fields, custom editors, script selection, accuracy, and device performance are documented.

## 18. Instructions to the AI coding agent

Build a working product incrementally. Inspect the current repository and upstream `whisper.cpp` Android example first. Do not invent APIs or assume upstream paths are stable.

Before changing code:
1. Inspect the current project structure and build setup.
2. Check the latest upstream Android example, native build requirements, and licenses.
3. Write a short implementation plan and identify any build risks.
4. Keep changes small enough to validate in CI.

During implementation:
1. Implement Milestone 1 first and ensure CI can build an APK.
2. Add tests alongside each feature.
3. Integrate the native engine with a pinned version and safe JNI lifecycle.
4. Build model downloading/import as a separate, explicit operation.
5. Implement the actual Android IME; a standalone recorder app alone does not meet the product goal.
6. Use only supported Android APIs and respect sensitive input fields.
7. Keep the product offline-first and do not add analytics or cloud transcription.
8. Run tests and builds after each milestone. Fix failures instead of hiding or skipping them.
9. If a command cannot run in the available environment, say so clearly and configure GitHub Actions to run it.
10. End with a summary of implemented features, files changed, test/build results, APK artifact location in GitHub Actions, known limitations, and the next steps.

Do not mark a milestone complete just because files were generated. It is complete only when its acceptance criteria have been verified.

## 19. Reference links

- `whisper.cpp`: https://github.com/ggml-org/whisper.cpp
- Official Android sample: https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android
- Android input method guide: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- Android runtime permissions: https://developer.android.com/training/permissions/requesting
- Android foreground services: https://developer.android.com/develop/background-work/services/foreground-services
- GitHub Actions for Android: https://docs.github.com/actions
- Gradle Wrapper: https://docs.gradle.org/current/userguide/gradle_wrapper.html

---

**Product principle:** OpenEar should make private, on-device dictation easy to use in everyday Android text fields, especially for Indian English and Hinglish. Be transparent about limitations, never compromise user consent, and prioritize a reliable keyboard workflow over flashy features.
