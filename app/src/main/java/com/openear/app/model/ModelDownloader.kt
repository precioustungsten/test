package com.openear.app.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Explicit, user-initiated model download. HTTPS only, resumable via HTTP Range,
 * and the result is installed only after the SHA-256 matches. Cancelling the
 * coroutine keeps the partial file so the download can be resumed later.
 */
class ModelDownloader(
    private val store: ModelStore,
    private val freeSpace: () -> Long,
) {
    enum class Reason { InsecureUrl, InsufficientStorage, Network, Corrupt }

    sealed interface Outcome {
        data object Success : Outcome
        data class Failure(val reason: Reason) : Outcome
    }

    suspend fun download(
        info: ModelInfo,
        onProgress: (bytes: Long, total: Long) -> Unit,
        onVerifying: () -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        if (!info.url.startsWith("https://")) return@withContext Outcome.Failure(Reason.InsecureUrl)

        val part = store.partFile(info)
        var existing = if (part.isFile) part.length() else 0L
        if (existing > info.sizeBytes) {
            part.delete()
            existing = 0L
        }
        if (!ModelStore.hasEnoughSpace(info.sizeBytes - existing, freeSpace())) {
            return@withContext Outcome.Failure(Reason.InsufficientStorage)
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(info.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            val code = conn.responseCode
            if (conn.url.protocol != "https") return@withContext Outcome.Failure(Reason.InsecureUrl)
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                return@withContext Outcome.Failure(Reason.Network)
            }
            val resuming = code == HttpURLConnection.HTTP_PARTIAL
            if (!resuming) existing = 0L

            var done = existing
            conn.inputStream.use { input ->
                FileOutputStream(part, resuming).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        onProgress(done, info.sizeBytes)
                    }
                }
            }
        } catch (e: IOException) {
            return@withContext Outcome.Failure(Reason.Network)
        } finally {
            conn?.disconnect()
        }

        onVerifying()
        return@withContext when (store.verifyAndInstall(info)) {
            ModelStore.InstallResult.Installed -> Outcome.Success
            ModelStore.InstallResult.MissingPartial -> Outcome.Failure(Reason.Network)
            else -> Outcome.Failure(Reason.Corrupt)
        }
    }
}
