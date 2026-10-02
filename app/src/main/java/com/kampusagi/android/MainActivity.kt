package com.kampusagi.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.core.designsystem.theme.KampusAgiTheme
import com.kampusagi.android.data.settings.ThemeStore
import com.kampusagi.android.presentation.app.KampusAgiApp
import com.kampusagi.android.presentation.app.RootViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: RootViewModel by viewModels()

    @Inject lateinit var themeStore: ThemeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value.isLoading }
        if (savedInstanceState == null) handleAuthLink(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val themeMode by themeStore.themeMode.collectAsStateWithLifecycle()
            KampusAgiTheme(themeMode = themeMode) {
                KampusAgiApp(state = state, viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthLink(intent)
    }

    /** Email confirmation and password reset links from Supabase Auth, and taps on system notifications. */
    private fun handleAuthLink(intent: Intent?) {
        if (intent?.action == AppConfig.ACTION_OPEN_NOTIFICATIONS) {
            viewModel.onOpenNotificationsIntent()
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.toString()?.let(viewModel::onAuthLink)
    }
}
