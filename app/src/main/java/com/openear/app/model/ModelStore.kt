package com.openear.app.model

import java.io.File
import java.io.InputStream

/**
 * App-private model storage. A model counts as installed only when the file has
 * the expected size AND a marker file records that its SHA-256 was verified.
 * Partial, corrupt or unverified files are never reported as ready.
 */
class ModelStore(private val dir: File) {

    init {
        dir.mkdirs()
    }

    enum class InstallResult { Installed, WrongSize, ChecksumMismatch, MissingPartial }
    enum class ImportResult { Imported, NotAModel, Failed }

    data class Resolved(val id: String, val label: String, val file: File)

    fun modelFile(info: ModelInfo) = File(dir, info.fileName)
    fun partFile(info: ModelInfo) = File(dir, info.fileName + ".part")
    private fun markerFile(info: ModelInfo) = File(dir, info.fileName + ".sha256")
    fun importedFile() = File(dir, IMPORTED_FILE)

    fun isInstalled(info: ModelInfo): Boolean {
        val f = modelFile(info)
        val m = markerFile(info)
        return f.isFile && f.length() == info.sizeBytes && m.isFile &&
            m.readText().trim().equals(info.sha256, ignoreCase = true)
    }

    fun state(info: ModelInfo): ModelState {
        if (isInstalled(info)) return ModelState.Ready
        val part = partFile(info)
        if (part.isFile && part.length() in 1 until info.sizeBytes) {
            return ModelState.Partial(part.length(), info.sizeBytes)
        }
        return ModelState.NotInstalled
    }

    /** Verifies the downloaded .part file and, only if it is exactly right, promotes it. */
    fun verifyAndInstall(info: ModelInfo): InstallResult {
        val part = partFile(info)
        if (!part.isFile) return InstallResult.MissingPartial
        if (part.length() != info.sizeBytes) {
            part.delete()
            return InstallResult.WrongSize
        }
        if (!Sha256.matches(part, info.sha256)) {
            part.delete()
            return InstallResult.ChecksumMismatch
        }
        val target = modelFile(info)
        target.delete()
        markerFile(info).delete()
        if (!part.renameTo(target)) {
            part.delete()
            return InstallResult.MissingPartial
        }
        markerFile(info).writeText(info.sha256)
        return InstallResult.Installed
    }

    fun discardPartial(info: ModelInfo) {
        partFile(info).delete()
    }

    fun delete(info: ModelInfo) {
        modelFile(info).delete()
        markerFile(info).delete()
        partFile(info).delete()
    }

    fun isImportedReady(): Boolean = importedFile().let { it.isFile && hasGgmlMagic(it) }

    /** Copies a user-chosen file into private storage after checking it looks like a ggml model. */
    fun importFrom(input: InputStream): ImportResult {
        val temp = File(dir, "$IMPORTED_FILE.tmp")
        return try {
            temp.outputStream().use { out -> input.copyTo(out) }
            if (!hasGgmlMagic(temp)) {
                temp.delete()
                return ImportResult.NotAModel
            }
            val target = importedFile()
            target.delete()
            if (temp.renameTo(target)) ImportResult.Imported else ImportResult.Failed
        } catch (e: java.io.IOException) {
            temp.delete()
            ImportResult.Failed
        }
    }

    fun deleteImported() {
        importedFile().delete()
    }

    fun usedBytes(): Long = dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** The model file for [modelId] if (and only if) it is ready to load. */
    fun resolve(modelId: String): Resolved? {
        if (modelId == ModelCatalog.IMPORTED_ID) {
            return if (isImportedReady()) Resolved(modelId, "Imported model", importedFile()) else null
        }
        val info = ModelCatalog.byId(modelId) ?: return null
        return if (isInstalled(info)) Resolved(info.id, info.displayName, modelFile(info)) else null
    }

    companion object {
        const val IMPORTED_FILE = "imported.ggml"

        /** ggml model files start with the little-endian magic 0x67676d6c ("lmgg" on disk). */
        fun hasGgmlMagic(file: File): Boolean {
            if (!file.isFile || file.length() < 4) return false
            val head = ByteArray(4)
            val n = file.inputStream().use { it.read(head) }
            return n == 4 && head[0] == 0x6c.toByte() && head[1] == 0x6d.toByte() &&
                head[2] == 0x67.toByte() && head[3] == 0x67.toByte()
        }

        /** Pure storage check, so it is testable. A margin leaves room for the OS. */
        fun hasEnoughSpace(requiredBytes: Long, availableBytes: Long, marginBytes: Long = 50L * 1024 * 1024): Boolean =
            requiredBytes >= 0 && availableBytes >= requiredBytes + marginBytes
    }
}
