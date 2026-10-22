package com.openear.app

import android.app.Application
import android.os.StatFs
import com.openear.app.keyboard.ModelProvider
import com.openear.app.keyboard.ModelRef
import com.openear.app.model.ModelManager
import com.openear.app.model.ModelStore
import com.openear.app.settings.Settings
import com.openear.app.settings.SharedPrefsStore
import com.openear.app.transcription.TranscriptionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Process-wide singletons shared by the app UI and the keyboard service. */
class OpenEarApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var modelStore: ModelStore
        private set
    lateinit var modelManager: ModelManager
        private set
    lateinit var engine: TranscriptionEngine
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val modelProvider = ModelProvider {
        modelStore.resolve(settings.modelId)?.let { ModelRef(it.id, it.label, it.file.path) }
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(SharedPrefsStore(getSharedPreferences("openear_settings", MODE_PRIVATE)))
        modelStore = ModelStore(File(filesDir, "models"))
        engine = TranscriptionEngine(
            idleMillis = { settings.idleReleaseMinutes * 60_000L },
            fastMode = { settings.fastMode },
        )
        modelManager = ModelManager(
            store = modelStore,
            scope = appScope,
            freeSpace = { StatFs(filesDir.path).availableBytes },
            onModelReady = { id ->
                // Never silently switch away from a working model; only fill in a missing one.
                if (modelStore.resolve(settings.modelId) == null) settings.modelId = id
            },
        )
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 15 = running critical; 40+ = process in background cache. Free native memory in both.
        if (level == 15 || level >= 40) engine.release()
    }
}
