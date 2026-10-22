package com.openear.app.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Tracks model states for the UI and runs explicit, user-started downloads. */
class ModelManager(
    private val store: ModelStore,
    private val scope: CoroutineScope,
    freeSpace: () -> Long,
    private val onModelReady: (String) -> Unit,
) {
    private val downloader = ModelDownloader(store, freeSpace)
    private val jobs = HashMap<String, Job>()
    private val _states = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _states.update { current ->
            val next = HashMap<String, ModelState>()
            for (info in ModelCatalog.all) {
                val active = jobs[info.id]?.isActive == true
                next[info.id] = if (active) current[info.id] ?: ModelState.Verifying else store.state(info)
            }
            next[ModelCatalog.IMPORTED_ID] =
                if (store.isImportedReady()) ModelState.Ready else ModelState.NotInstalled
            next
        }
    }

    private fun set(id: String, state: ModelState) {
        _states.update { it + (id to state) }
    }

    fun start(info: ModelInfo) {
        if (jobs[info.id]?.isActive == true) return
        val existing = store.partFile(info).let { if (it.isFile) it.length() else 0L }
        set(info.id, ModelState.Downloading(existing, info.sizeBytes))
        jobs[info.id] = scope.launch {
            var lastPercent = -1L
            val outcome = try {
                downloader.download(
                    info,
                    onProgress = { bytes, total ->
                        val percent = if (total > 0) bytes * 100 / total else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            set(info.id, ModelState.Downloading(bytes, total))
                        }
                    },
                    onVerifying = { set(info.id, ModelState.Verifying) },
                )
            } catch (e: CancellationException) {
                set(info.id, store.state(info)) // paused: the partial file is kept
                throw e
            }
            when (outcome) {
                is ModelDownloader.Outcome.Success -> {
                    set(info.id, ModelState.Ready)
                    onModelReady(info.id)
                }
                is ModelDownloader.Outcome.Failure -> set(info.id, ModelState.Failed(outcome.reason.userMessage()))
            }
        }
    }

    /** Stops the download but keeps the partial file so it can be resumed. */
    fun pause(info: ModelInfo) {
        jobs[info.id]?.cancel()
    }

    /** Stops the download and removes the partial file. */
    fun cancelAndDiscard(info: ModelInfo) {
        val job = jobs[info.id]
        scope.launch {
            job?.cancelAndJoin()
            store.discardPartial(info)
            set(info.id, ModelState.NotInstalled)
        }
    }

    fun delete(info: ModelInfo) {
        val job = jobs[info.id]
        scope.launch {
            job?.cancelAndJoin()
            store.delete(info)
            set(info.id, ModelState.NotInstalled)
        }
    }

    fun deleteImported() {
        store.deleteImported()
        refresh()
    }

    fun deleteAll() {
        val running = jobs.values.toList()
        scope.launch {
            running.forEach { it.cancelAndJoin() }
            store.deleteAll()
            refresh()
        }
    }

    fun importFinished(result: ModelStore.ImportResult) {
        refresh()
        if (result == ModelStore.ImportResult.Imported) onModelReady(ModelCatalog.IMPORTED_ID)
    }

    private fun ModelDownloader.Reason.userMessage(): String = when (this) {
        ModelDownloader.Reason.InsecureUrl -> "Blocked: the download address is not secure."
        ModelDownloader.Reason.InsufficientStorage -> "Not enough free storage for this model."
        ModelDownloader.Reason.Network -> "Download failed. Check your connection and retry."
        ModelDownloader.Reason.Corrupt -> "The downloaded file failed verification and was deleted. Retry."
    }
}
