# Latency notes (v0.2)

What changed to cut the delay after you stop speaking:

1. **Shorter encoder window ("Fast mode", default on).** Whisper pads every clip to 30 s before encoding. We pass `audio_ctx` sized to the clip (+2 s margin, min ~10 s, rounded to 64 frames), which removes most of the encoder work for short dictation. Turn it off in Settings if accuracy drops.
2. **English by default.** Forcing English skips Whisper's language-detection step. English-only quantized models (`tiny.en-q5_1`, `base.en-q5_1`) are offered; they are smaller and usually faster/more accurate for English than multilingual ones.
3. **Model preloaded while you speak.** The model starts loading when recording starts, so it is ready at stop (previously the first run after idle paid the load cost after you stopped).
4. **Leaner decoding.** No timestamp tokens, single segment for clips under 28 s, greedy with no temperature-fallback retries, non-speech tokens suppressed.
5. **Thread control.** Settings -> CPU threads (Auto uses up to 4).
6. **Timing readout.** The Test tab shows audio length, total time, model-load time and approximate encode/decode time for the last run. Use it to compare settings and models, and send those numbers when asking for more tuning.

Not done yet (next steps if still too slow): CPU-specific native build (ARMv8.2 fp16/dotprod), chunked pseudo-streaming with VAD, Moonshine/sherpa-onnx engine.
