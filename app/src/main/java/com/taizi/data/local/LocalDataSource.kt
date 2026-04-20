package com.taizi.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.taizi.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "taizi_settings")
private val Context.libraryCacheStore: DataStore<Preferences> by preferencesDataStore(name = "library_cache")

/**
 * Local data source for settings and library cache
 */
class LocalDataSource(private val context: Context) {

    // Settings keys
    companion object {
        val ROM_ROOT = stringPreferencesKey("rom_root")
        val CACHE_ENABLED = booleanPreferencesKey("cache_enabled")
        val THEME_ACCENT = stringPreferencesKey("theme_accent")
        val WALLPAPER_BLUR = booleanPreferencesKey("wallpaper_blur")
        val SHOW_BIOS = booleanPreferencesKey("show_bios")
        val FOLDER_DEPTH = intPreferencesKey("folder_depth")
        val AUTO_SCAN_ON_BOOT = booleanPreferencesKey("auto_scan_on_boot")
        val DUAL_SCREEN_MODE = stringPreferencesKey("dual_screen_mode")
        val SCRAPER_ENABLED = booleanPreferencesKey("scraper_enabled")
        val SCRAPER_ACCOUNT = stringPreferencesKey("scraper_account")
        val CUSTOM_MAPPINGS = stringPreferencesKey("custom_mappings")
        val LIBRARY_CACHE = stringPreferencesKey("library_cache_json")
    }

    // Settings
    val settings: Flow<Map<Preferences.Key<*>, Any?>> = context.dataStore.data.map { it.asMap() }

    suspend fun setRomRoot(path: String) {
        context.dataStore.edit { it[ROM_ROOT] = path }
    }

    suspend fun setScraperEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SCRAPER_ENABLED] = enabled }
    }

    suspend fun setScraperAccount(account: String) {
        context.dataStore.edit { it[SCRAPER_ACCOUNT] = account }
    }

    suspend fun getScraperAccount(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[SCRAPER_ACCOUNT]
    }

    suspend fun getScraperEnabled(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[SCRAPER_ENABLED] ?: false
    }

    // Library Cache as JSON
    private val gson = Gson()

    suspend fun getLibraryCache(): Library? = withContext(Dispatchers.IO) {
        val prefs = context.libraryCacheStore.data.first()
        val json = prefs[LIBRARY_CACHE] ?: return@withContext null
        try {
            gson.fromJson(json, Library::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveLibraryCache(library: Library) = withContext(Dispatchers.IO) {
        val json = gson.toJson(library)
        context.libraryCacheStore.edit { prefs ->
            prefs[LIBRARY_CACHE] = json
        }
    }

    suspend fun clearCache() {
        context.libraryCacheStore.edit { it.remove(LIBRARY_CACHE) }
    }

    // User mappings for unknown systems
     suspend fun getCustomMappings(): Map<String, String> {
         val prefs = context.dataStore.data.first()
         val json = prefs[CUSTOM_MAPPINGS] ?: return emptyMap()
         return try {
             val type = object : TypeToken<Map<String, String>>() {}.type
             @Suppress("OVERLOAD_RESOLUTION_AMBIGUITY")
             gson.fromJson(json, type) as? Map<String, String> ?: emptyMap()
         } catch (e: Exception) {
             emptyMap()
         }
     }

    suspend fun saveCustomMappings(mappings: Map<String, String>) {
        context.dataStore.edit { prefs ->
            val json = gson.toJson(mappings)
            prefs[CUSTOM_MAPPINGS] = json
        }
    }
}
