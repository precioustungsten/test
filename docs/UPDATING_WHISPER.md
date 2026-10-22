# Updating the pinned whisper.cpp

whisper.cpp is a git submodule at `app/src/main/cpp/whisper.cpp`, pinned to a release tag (currently `v1.9.5`).

```bash
cd app/src/main/cpp/whisper.cpp
git fetch --tags && git checkout vX.Y.Z
cd ../../../../..
git add app/src/main/cpp/whisper.cpp && git commit -m "Bump whisper.cpp to vX.Y.Z"
```
Then check `openear_jni.cpp` still compiles (API in `include/whisper.h`), update `WHISPER_VERSION` in `CMakeLists.txt` and `THIRD_PARTY_NOTICES.md`, push, and let CI build. Never track `master`.
Model URLs/checksums live in `ModelCatalog.kt` and are pinned to a Hugging Face revision.
