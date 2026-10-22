package com.openear.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.openear.app.ui.OpenEarRoot

class MainActivity : ComponentActivity() {
    // Bumped on every resume so the setup checklist re-reads system state after the
    // user returns from Android's keyboard or permission settings.
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OpenEarApp
        setContent { OpenEarRoot(app = app, resumeTick = resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        (application as OpenEarApp).modelManager.refresh()
    }
}
