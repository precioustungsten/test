# Testing and evaluation

Commands (run in CI; locally they need JDK 17 + Android SDK/NDK):
```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew lintDebug
./gradlew assembleDebug       # debug APK
./gradlew connectedDebugAndroidTest   # emulator; CI: run workflow manually with run_instrumentation=true
```

## Hinglish / Indian-English evaluation (Milestone 4, manual)
Use synthetic or consented recordings only. Suggested phrases (do not hard-code them in the app):
- "Kal meeting kitne baje start hogi?"
- "Please mujhe woh document WhatsApp par bhej dena."
- "Aaj office thoda late pahunchunga."
- "Is file ko download karke email kar do."
- Hindi sentences in Devanagari; Indian-English sentences.

For tiny / base / small, on real devices, record: model load time, transcription time for a 10 s clip, peak memory, thermal behaviour, and review code-switching, script choice (Roman vs Devanagari), names, punctuation, meaning preservation; compute WER where a reference exists. Choose the default model from these results.
