package com.kinoshka.app.data

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** YouTube через NewPipeExtractor. Все вызовы блокирующие. */
object YouTube {
    private val yt get() = ServiceList.YouTube

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    class Hit(
        val id: String,
        val title: String,
        val channel: String,
        val durationSec: Long,
        val thumb: String?,
        val verified: Boolean,
    )

    fun search(query: String): List<Hit> {
        val qh = yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        return SearchInfo.getInfo(yt, qh).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                if (item.isShortFormContent || item.url.contains("/shorts/")) return@mapNotNull null
                if (item.streamType != StreamType.VIDEO_STREAM) return@mapNotNull null
                val id = runCatching { yt.streamLHFactory.getId(item.url) }.getOrNull() ?: return@mapNotNull null
                Hit(
                    id = id,
                    title = item.name.orEmpty(),
                    channel = item.uploaderName.orEmpty(),
                    durationSec = item.duration,
                    thumb = bestThumb(item.thumbnails),
                    verified = item.isUploaderVerified,
                )
            }
    }

    /** Выбранные потоки: отдельно видео и звук (до 1080p), либо один общий поток. */
    class Streams(
        val video: VideoStream,
        val audio: AudioStream?,
        val durationSec: Long,
    )

    fun streams(id: String, maxHeight: Int): Streams {
        val info = StreamInfo.getInfo(yt, watchUrl(id))

        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP } }
            // m4a (AAC) телевизоры декодируют надёжнее, чем opus
            .sortedWith(compareBy<AudioStream>({ if (it.format == MediaFormat.M4A) 0 else 1 }, { -maxOf(it.averageBitrate, it.bitrate) }))
            .firstOrNull()

        val videoOnly = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .filter { height(it) in 1..maxHeight }
            // H.264 (avc1) в mp4 — аппаратно поддерживают почти все ТВ-приставки
            .sortedWith(
                compareBy<VideoStream>(
                    { -height(it) },
                    { if (it.codec?.startsWith("avc1") == true) 0 else if (it.format == MediaFormat.MPEG_4) 1 else 2 },
                ),
            )

        val muxed = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { height(it) }

        val bestVideo = videoOnly.firstOrNull()
        return when {
            bestVideo != null && audio != null -> Streams(bestVideo, audio, info.duration)
            muxed != null -> Streams(muxed, null, info.duration)
            else -> error("YouTube не отдал поток для этого видео")
        }
    }

    fun height(v: VideoStream): Int =
        v.height.takeIf { it > 0 }
            ?: Regex("""(\d{3,4})p""").find(v.resolution.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
            ?: 0

    private fun bestThumb(list: List<Image>?): String? {
        if (list.isNullOrEmpty()) return null
        return (list.filter { it.height in 1..720 }.maxByOrNull { it.height } ?: list.maxByOrNull { it.height })?.url
    }
}
