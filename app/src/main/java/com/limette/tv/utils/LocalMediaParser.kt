package com.limette.tv.utils

import com.limette.tv.domain.model.MediaType

/** 纯本地文件名解析，不访问任何网络服务。 */
object LocalMediaParser {

    data class Parsed(
        val title: String,
        val year: Int?,
        val seasonNumber: Int?,
        val episodeNumber: Int?,
        val type: MediaType
    )

    private val seasonEpisode = Regex("(?i)[sS](\\d{1,2})[._\\- ]?[eE](\\d{1,3})")
    private val chineseEpisode = Regex("第\\s*(\\d{1,3})\\s*集")
    private val yearPattern = Regex("(19|20)\\d{2}")
    private val noise = Regex(
        "(?i)(2160p|1080p|720p|480p|4k|8k|bluray|blu-ray|web-?dl|webrip|hdtv|remux|x264|x265|h264|h265|hevc|10bit|hdr|dv|aac|ac3|dts|ddp|truehd|atmos|中字|国语|双语|内嵌|字幕|无删减|完整版)"
    )

    fun parse(fileName: String, relativePath: String = ""): Parsed {
        val raw = fileName.substringBeforeLast('.')
        val se = seasonEpisode.find(raw)
        val season = se?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("(?i)season\\s*(\\d{1,2})|第\\s*(\\d{1,2})\\s*季").find(relativePath)
                ?.let { it.groupValues.getOrNull(1)?.toIntOrNull() ?: it.groupValues.getOrNull(2)?.toIntOrNull() }
        val episode = se?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: chineseEpisode.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()

        var title = raw
            .replace(seasonEpisode, "")
            .replace(noise, "")
            .replace(Regex("[._\\[\\]()（）]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('-', ' ')
        if (title.isBlank()) title = raw

        val year = yearPattern.findAll(raw)
            .map { it.value.toIntOrNull() }
            .firstOrNull { it != null && it in 1900..2100 }

        // 剧集系列按「季」归并用的标题
        val seriesTitle = if (season != null) {
            title.replace(Regex("(?i)\\s*(s?\\d{1,2}|第\\d{1,2}季|season\\s*\\d{1,2})\\s*$"), "").trim()
                .ifBlank { relativePath.substringBeforeLast('/').substringAfterLast('/') }
        } else title

        return Parsed(
            title = if (episode != null) "$seriesTitle 第${episode}集" else title,
            year = year,
            seasonNumber = season,
            episodeNumber = episode,
            type = if (episode != null || season != null) MediaType.TV_SHOW else MediaType.MOVIE
        )
    }
}
