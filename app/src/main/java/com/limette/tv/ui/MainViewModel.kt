package com.limette.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.limette.tv.data.feiniu.FeiNiuClient
import com.limette.tv.data.local.database.dao.WatchHistoryDao
import com.limette.tv.data.local.database.dao.WebDavMediaDao
import com.limette.tv.data.local.database.entity.WebDavMediaEntity
import com.limette.tv.data.preferences.FeiNiuConfig
import com.limette.tv.data.preferences.FeiNiuPreferences
import com.limette.tv.data.preferences.MediaSourceRepository
import com.limette.tv.data.preferences.PlayerSettingsPreferences
import com.limette.tv.data.remote.LocalImageAuth
import com.limette.tv.data.remote.RemoteFileClientFactory
import com.limette.tv.data.repository.MediaLibraryRepository
import com.limette.tv.data.repository.ScanState
import com.limette.tv.domain.model.MediaSource
import com.limette.tv.domain.service.FavoriteService
import com.limette.tv.domain.service.WatchHistoryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mediaSourceRepository: MediaSourceRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val webDavMediaDao: WebDavMediaDao,
    private val feiNiuPreferences: FeiNiuPreferences,
    private val feiNiuClient: FeiNiuClient,
    private val playerSettingsPreferences: PlayerSettingsPreferences,
    private val favoriteService: FavoriteService,
    private val watchHistoryService: WatchHistoryService,
    private val watchHistoryDao: WatchHistoryDao,
    private val clientFactory: RemoteFileClientFactory
) : ViewModel() {

    val sources: StateFlow<List<MediaSource>> = mediaSourceRepository.sources
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val scanState: StateFlow<ScanState> = mediaLibraryRepository.scanState

    val feiNiuConfig: StateFlow<FeiNiuConfig> = feiNiuPreferences.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeiNiuConfig())

    val rememberPlaybackPosition: Flow<Boolean> = playerSettingsPreferences.rememberPlaybackPosition
    val autoSkipIntroOutro: Flow<Boolean> = playerSettingsPreferences.autoSkipIntroOutro
    val seekDurationSeconds: Flow<Int> = playerSettingsPreferences.seekDurationSeconds

    /** 首页：所有已启用媒体源的全部视频文件，不做分类、不截断。 */
    val allVideos: StateFlow<List<WebDavMediaEntity>> = sources
        .flatMapLatest { list ->
            val ids = list.filter { it.enabled }.map { it.id }
            if (ids.isEmpty()) flowOf(emptyList())
            else webDavMediaDao.getByLibraryIds(ids)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentHistory = watchHistoryService.getRecentWatchHistory(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites = favoriteService.getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // 启动后在后台刷新一次索引，首页先直接展示已入库数据
        viewModelScope.launch {
            runCatching {
                mediaLibraryRepository.pruneOrphanSources()
                if (sources.value.any { it.enabled }) mediaLibraryRepository.scanAll()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { runCatching { mediaLibraryRepository.scanAll() } }
    }

    suspend fun addOrUpdateSource(source: MediaSource) {
        mediaSourceRepository.add(source)
        LocalImageAuth.register(source)
        refresh()
    }

    suspend fun removeSource(id: String) {
        mediaSourceRepository.getById(id)?.let { LocalImageAuth.unregister(it) }
        mediaSourceRepository.remove(id)
        mediaLibraryRepository.clearSource(id)
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        mediaSourceRepository.setEnabled(id, enabled)
        if (!enabled) mediaLibraryRepository.clearSource(id)
        else refresh()
    }

    suspend fun saveFeiNiuConfig(config: FeiNiuConfig) {
        feiNiuPreferences.save(config)
        // 重新拉一次飞牛海报索引，并回填已入库视频的封面
        refresh()
    }

    suspend fun startPositionFor(mediaId: String): Long =
        runCatching { watchHistoryDao.getLatestWatchHistoryByMovieId(mediaId)?.progress ?: 0L }
            .getOrDefault(0L)

    suspend fun findMedia(id: String): WebDavMediaEntity? =
        runCatching { webDavMediaDao.getById(id) }.getOrNull()

    suspend fun isFavorite(mediaId: String): Boolean = favorites.value.any { it.mediaId == mediaId }

    suspend fun toggleFavorite(media: WebDavMediaEntity): Boolean {
        return favoriteService.toggleFavorite(
            mediaId = media.id,
            title = media.title,
            posterUrl = media.posterUrl,
            backdropUrl = media.backdropUrl,
            overview = media.overview,
            year = media.year?.toString(),
            genres = media.genres?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            mediaType = media.type,
            rating = media.rating
        )
    }

    fun clearWatchHistory() {
        viewModelScope.launch { runCatching { watchHistoryService.clearAllWatchHistory() } }
    }

    fun setRememberPlaybackPosition(enabled: Boolean) {
        viewModelScope.launch { playerSettingsPreferences.setRememberPlaybackPosition(enabled) }
    }

    fun setAutoSkipIntroOutro(enabled: Boolean) {
        viewModelScope.launch { playerSettingsPreferences.setAutoSkipIntroOutro(enabled) }
    }

    fun setSeekDurationSeconds(seconds: Int) {
        viewModelScope.launch { playerSettingsPreferences.setSeekDurationSeconds(seconds) }
    }

    fun toggleFavoriteInScope(media: WebDavMediaEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(toggleFavorite(media)) }
    }

    /** 媒体库编辑弹窗里的「测试连接」。 */
    suspend fun testSource(source: MediaSource): Result<Int> = runCatching {
        val client = clientFactory.create(source.copy(enabled = true))
        client.listFiles("").getOrThrow().size
    }

    fun search(query: String): List<WebDavMediaEntity> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        return allVideos.value.filter {
            it.title.lowercase().contains(q) || it.fileName.lowercase().contains(q) ||
                it.filePath.lowercase().contains(q)
        }
    }
}
