package com.limette.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.limette.tv.domain.model.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mediaSourceDataStore: DataStore<Preferences> by preferencesDataStore(name = "limette_media_sources")

/**
 * 媒体源配置仓库。所有配置保存在本机 DataStore，不依赖任何云端服务。
 * 启用状态存于 [MediaSource.enabled]，因此天然支持同时启用 SMB + WebDAV 多个源。
 */
@Singleton
class MediaSourceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val sourcesKey = stringPreferencesKey("sources_json")
    private val listType = object : TypeToken<List<MediaSource>>() {}.type

    val sources: Flow<List<MediaSource>> = context.mediaSourceDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val raw = prefs[sourcesKey].orEmpty()
            if (raw.isBlank()) emptyList()
            else runCatching { gson.fromJson<List<MediaSource>>(raw, listType) ?: emptyList() }
                .getOrDefault(emptyList())
        }

    suspend fun getSources(): List<MediaSource> = sources.first()

    suspend fun getById(id: String): MediaSource? = getSources().firstOrNull { it.id == id }

    suspend fun add(source: MediaSource) {
        updateList { current -> current.filterNot { it.id == source.id } + source }
    }

    suspend fun update(source: MediaSource) = add(source)

    suspend fun setEnabled(id: String, enabled: Boolean) {
        updateList { current -> current.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }

    suspend fun remove(id: String) {
        updateList { current -> current.filterNot { it.id == id } }
    }

    private suspend fun updateList(transform: (List<MediaSource>) -> List<MediaSource>) {
        context.mediaSourceDataStore.edit { prefs ->
            val currentRaw = prefs[sourcesKey].orEmpty()
            val current: List<MediaSource> = if (currentRaw.isBlank()) emptyList()
            else runCatching { gson.fromJson<List<MediaSource>>(currentRaw, listType) ?: emptyList() }
                .getOrDefault(emptyList())
            prefs[sourcesKey] = gson.toJson(transform(current))
        }
    }
}
