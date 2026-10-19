package de.flashcards.open

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.flashcards.open.data.repo.Settings
import de.flashcards.open.ui.nav.AppNavHost
import de.flashcards.open.ui.theme.FlashcardsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as FlashcardsApp).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = Settings())
            FlashcardsTheme(settings.themeMode, settings.dynamicColor) {
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    container.messenger.messages.collect { snackbar.showSnackbar(it) }
                }
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost()
                        SnackbarHost(
                            snackbar,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp),
                        )
                    }
                }
            }
        }
    }

    /** Select / Esc act as “back” everywhere (controller, keyboard). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        isBackKey(keyCode) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isBackKey(keyCode)) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isBackKey(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_BUTTON_SELECT || keyCode == KeyEvent.KEYCODE_ESCAPE
}
