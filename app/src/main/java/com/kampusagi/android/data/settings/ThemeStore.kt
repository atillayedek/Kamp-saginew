package com.kampusagi.android.data.settings

import android.content.Context
import com.kampusagi.android.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The appearance setting is a per-device preference, so it lives on the device only. */
@Singleton
class ThemeStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val mode = MutableStateFlow(read())

    val themeMode: StateFlow<ThemeMode> = mode.asStateFlow()

    fun set(value: ThemeMode) {
        prefs.edit().putString(KEY, value.name).apply()
        mode.value = value
    }

    private fun read(): ThemeMode =
        prefs.getString(KEY, null)?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } } ?: ThemeMode.SYSTEM

    private companion object {
        const val FILE = "appearance"
        const val KEY = "theme_mode"
    }
}
