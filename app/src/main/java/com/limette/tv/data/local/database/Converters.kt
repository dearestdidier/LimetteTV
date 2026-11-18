package com.limette.tv.data.local.database

import androidx.room.TypeConverter
import com.limette.tv.domain.model.MediaType

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String {
        return value.name
    }

    @TypeConverter
    fun toMediaType(value: String): MediaType {
        return try {
            MediaType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MediaType.OTHER // 如果解析失败，默认返回OTHER
        }
    }
}
