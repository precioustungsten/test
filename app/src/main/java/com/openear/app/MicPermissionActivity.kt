package com.openear.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * A keyboard cannot show a permission dialog itself, so it opens this transparent
 * screen. Shown only after the user taps the mic and no permission is granted.
 */
class MicPermissionActivity : ComponentActivity() {
    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request.launch(Manifest.permission.RECORD_AUDIO)
    }
}
