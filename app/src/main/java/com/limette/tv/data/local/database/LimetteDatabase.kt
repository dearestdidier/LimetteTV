package com.limette.tv.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.limette.tv.data.local.database.dao.EpisodeDao
import com.limette.tv.data.local.database.dao.FavoriteDao
import com.limette.tv.data.local.database.dao.MovieDao
import com.limette.tv.data.local.database.dao.SkipConfigDao
import com.limette.tv.data.local.database.dao.WatchHistoryDao
import com.limette.tv.data.local.database.dao.WebDavMediaDao
import com.limette.tv.data.local.database.entity.EpisodeEntity
import com.limette.tv.data.local.database.entity.FavoriteEntity
import com.limette.tv.data.local.database.entity.MovieEntity
import com.limette.tv.data.local.database.entity.SkipConfigEntity
import com.limette.tv.data.local.database.entity.WatchHistoryEntity
import com.limette.tv.data.local.database.entity.WebDavMediaEntity

/**
 * 播放器 / 观看记录链路沿用的数据库。
 * Movie、Episode 表为观看进度与剧集分组保留；纯本地扫描只写
 * webdav_media、watch_history、favorites、skip_config，不会发起任何网络请求。
 */
@Database(
    entities = [
        MovieEntity::class,
        EpisodeEntity::class,
        WatchHistoryEntity::class,
        WebDavMediaEntity::class,
        SkipConfigEntity::class,
        FavoriteEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LimetteDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun webDavMediaDao(): WebDavMediaDao
    abstract fun skipConfigDao(): SkipConfigDao
    abstract fun favoriteDao(): FavoriteDao
}
