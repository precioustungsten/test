# OpenEar privacy

- **Speech recognition runs on your device** using whisper.cpp. Audio is never uploaded.
- The microphone is used **only while you are dictating** (after you tap the mic key or Start). It stops when you finish, cancel, switch field, or hide the keyboard.
- Audio is kept **in memory only** and discarded right after transcription. Nothing is saved to disk.
- **No accounts, analytics, ads, tracking or crash-report upload.** Transcript text and audio are never logged.
- The `INTERNET` permission exists **only** for the model download you start yourself. Downloads are HTTPS, from a pinned upstream revision, and rejected unless the SHA-256 matches. Transcription code makes no network calls.
- No history is stored in this version. Backups are disabled.
- OpenEar never presses Send/Enter for you and never writes to the clipboard in the keyboard flow.
- It refuses to dictate into password fields.

## Threat model
OpenEar processes speech locally. Text you insert into WhatsApp, Telegram, etc. is afterwards handled by those apps under their own privacy policies. A keyboard can in principle see what is typed in a field, so OpenEar deliberately has no code that reads field contents, and you should still only install builds you trust (verify the published SHA-256).
