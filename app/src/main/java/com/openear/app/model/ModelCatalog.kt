package com.openear.app.model

/**
 * One officially downloadable Whisper model (multilingual, ggml format).
 * The SHA-256 values and sizes come from the Hugging Face LFS metadata of the
 * pinned upstream revision below; downloads are rejected unless they match.
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val summary: String,
)

object ModelCatalog {
    /** Pinned revision of huggingface.co/ggerganov/whisper.cpp, so the URL never moves. */
    const val UPSTREAM_REVISION = "5359861c739e955e79d9a303bcbc70fb988958b1"
    const val IMPORTED_ID = "imported"

    private fun hf(file: String) =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/$UPSTREAM_REVISION/$file"

    val all: List<ModelInfo> = listOf(
        ModelInfo(
            id = "tiny.en-q5_1",
            displayName = "Tiny English (quantized)",
            fileName = "ggml-tiny.en-q5_1.bin",
            url = hf("ggml-tiny.en-q5_1.bin"),
            sha256 = "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b",
            sizeBytes = 32_166_155L,
            summary = "Fastest option. English only, 32 MB. Recommended for the lowest latency.",
        ),
        ModelInfo(
            id = "base.en-q5_1",
            displayName = "Base English (quantized)",
            fileName = "ggml-base.en-q5_1.bin",
            url = hf("ggml-base.en-q5_1.bin"),
            sha256 = "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f",
            sizeBytes = 59_721_011L,
            summary = "English only, 60 MB. More accurate than Tiny, somewhat slower.",
        ),
        ModelInfo(
            id = "tiny",
            displayName = "Tiny (multilingual)",
            fileName = "ggml-tiny.bin",
            url = hf("ggml-tiny.bin"),
            sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
            sizeBytes = 77_691_713L,
            summary = "Fastest and smallest. Best for low-memory phones; least accurate, especially for Hinglish.",
        ),
        ModelInfo(
            id = "base",
            displayName = "Base (multilingual)",
            fileName = "ggml-base.bin",
            url = hf("ggml-base.bin"),
            sha256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
            sizeBytes = 147_951_465L,
            summary = "Recommended starting point: a balance of speed, size and accuracy.",
        ),
        ModelInfo(
            id = "small",
            displayName = "Small (multilingual)",
            fileName = "ggml-small.bin",
            url = hf("ggml-small.bin"),
            sha256 = "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
            sizeBytes = 487_601_967L,
            summary = "Higher accuracy, but slower and needs roughly 850 MB of free RAM. For capable phones.",
        ),
    )

    fun byId(id: String?): ModelInfo? = all.firstOrNull { it.id == id }
}
