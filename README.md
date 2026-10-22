# OpenEar

Privacy-first, offline dictation **keyboard** for Android (tuned for English; Hindi/Hinglish via multilingual models), powered by [whisper.cpp](https://github.com/ggml-org/whisper.cpp). Kotlin + Jetpack Compose + `InputMethodService` + JNI. Full spec: [idea.md](idea.md).

## Get the APK (no Android Studio needed)
1. Push this repo to GitHub, open **Actions → Android CI**. Each push builds it.
2. Download the artifact **OpenEar-debug-apk** (`OpenEar-debug.apk` + `.sha256`) and install it (allow "install unknown apps").
3. Open OpenEar and follow **Setup**: enable keyboard → choose keyboard → allow microphone → download a model (one-time, internet) → test.
4. After that, dictation works offline (airplane mode).

> Clone with submodules: `git clone --recurse-submodules <url>`. In GitHub's web UI the submodule is fetched automatically by CI.

## Releases
Tag `v0.1.0` → CI builds a release APK + `SHA256SUMS.txt` and attaches them to a GitHub Release.
For an installable *signed* release add repo secrets `OPENEAR_KEYSTORE_BASE64`, `OPENEAR_KEYSTORE_PASSWORD`, `OPENEAR_KEY_ALIAS`, `OPENEAR_KEY_PASSWORD`. Without them the release APK is **unsigned** (not installable); use the debug APK for testing.
Create a key: `keytool -genkeypair -v -keystore openear.jks -alias openear -keyalg RSA -keysize 4096 -validity 10000` then `base64 -w0 openear.jks`. Never commit the keystore.

## Implemented (v0.1)
Dictation IME (mic, stop, cancel, delete, space, enter, switch keyboard), record→transcribe→insert with session guards, setup checklist, model download (resumable, HTTPS, SHA-256 verified) / import / delete, test-dictation screen with copy/share, settings (language, trailing space, haptics, theme, max length), privacy + licenses screens, unit tests, CI.

## v0.2: latency work
English-first defaults, English quantized models, fast mode (trimmed encoder window), model preload while speaking, leaner decoding, thread setting and a timing readout. See docs/LATENCY.md.

## Known limitations
- **Not yet built or run on a device by the author**: verify the CI result and test on a phone; see docs/TESTING.md.
- Dictation-only keyboard (no QWERTY). No streaming; one record→stop→transcribe cycle (max 30/60/120 s).
- Single generic native build (no armv8.2 fp16 variant yet) and no streaming: see docs/LATENCY.md for what is left.
- Not available in password fields; some apps restrict third-party keyboards. Delete key does not auto-repeat.
- Hinglish accuracy and Roman/Devanagari script choice are not controllable; tiny/base can be weak, test small. No translation or rewriting is ever done.
- No history, no custom vocabulary, no native tests beyond `NativeBridgeTest` (instrumented).
- Settings use SharedPreferences (not DataStore) so the keyboard can read them synchronously.
- Model imports are format-checked only, not checksum-verified.
