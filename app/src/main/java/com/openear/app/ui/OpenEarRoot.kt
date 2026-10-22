package com.openear.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.openear.app.OpenEarApp

private enum class Tab(val label: String) { Setup("Setup"), Models("Models"), Test("Test"), Settings("Settings") }

@Composable
fun OpenEarRoot(app: OpenEarApp, resumeTick: Int) {
    var theme by remember { mutableStateOf(app.settings.theme) }
    var tab by rememberSaveable { mutableStateOf(Tab.Setup.name) }
    val current = Tab.valueOf(tab)

    OpenEarTheme(theme) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = t == current,
                            onClick = { tab = t.name },
                            icon = {},
                            label = { Text(t.label) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (current) {
                Tab.Setup -> SetupScreen(app, resumeTick, modifier, onOpenModels = { tab = Tab.Models.name }, onOpenTest = { tab = Tab.Test.name })
                Tab.Models -> ModelsScreen(app, modifier)
                Tab.Test -> TestScreen(app, resumeTick, modifier)
                Tab.Settings -> SettingsScreen(app, modifier, onThemeChanged = { theme = it })
            }
        }
    }
}
