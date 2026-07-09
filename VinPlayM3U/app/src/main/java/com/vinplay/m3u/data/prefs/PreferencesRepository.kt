package com.vinplay.m3u.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vinplay_prefs")

data class UserPrefs(
    val dynamicColor: Boolean = true,
    val lockOrientationInPlayer: Boolean = false,
    val autoPipOnLeave: Boolean = true
)

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LOCK_ORIENTATION = booleanPreferencesKey("lock_orientation")
        val AUTO_PIP = booleanPreferencesKey("auto_pip")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map { p ->
        UserPrefs(
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            lockOrientationInPlayer = p[Keys.LOCK_ORIENTATION] ?: false,
            autoPipOnLeave = p[Keys.AUTO_PIP] ?: true
        )
    }

    suspend fun setDynamicColor(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setLockOrientation(enabled: Boolean) =
        context.dataStore.edit { it[Keys.LOCK_ORIENTATION] = enabled }

    suspend fun setAutoPip(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_PIP] = enabled }
}
