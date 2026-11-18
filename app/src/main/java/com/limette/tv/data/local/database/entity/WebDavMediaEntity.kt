package com.limette.tv.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.limette.tv.domain.model.MediaType

@Entity(
    tableName = "webdav_media",
    indices = [
        Index(value = ["libraryId"]),
        Index(value = ["filePath"], unique = true),
        Index(value = ["fileFingerprint"])
    ]
)
data class WebDavMediaEntity(
    @PrimaryKey val id: String,
    val libraryId: String,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val genres: String? = null, // 逗号分隔
    val type: MediaType = MediaType.MOVIE,  // 新增：媒体类型
    val isMovie: Boolean = true,  // 保留用于向后兼容
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val tmdbId: String? = null,   // 历史兼容字段：本地扫描恒为空，仅作旧剧集分组键
    val filePath: String,
    val fileName: String,
    val fileSize: Long = 0,
    val fileFingerprint: String? = null, // 文件指纹（路径+大小+修改时间的MD5）
    val source: String = "local", // smb / webdav
    val scrapedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
