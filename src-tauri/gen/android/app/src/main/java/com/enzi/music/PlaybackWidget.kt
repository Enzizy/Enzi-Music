package com.enzi.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import kotlin.math.roundToInt

class CompactPlaybackWidget : PlaybackWidget(WidgetLayout.WIDE)

class ArtworkPlaybackWidget : PlaybackWidget(WidgetLayout.ARTWORK)

internal enum class WidgetLayout(val resource: Int, val showCover: Boolean = true, val showSkip: Boolean = true) {
    SMALL(R.layout.widget_compact, showCover = false, showSkip = false),
    SHORT(R.layout.widget_compact, showSkip = false),
    WIDE(R.layout.widget_compact),
    ARTWORK(R.layout.widget_artwork),
    TALL_NARROW(R.layout.widget_tall, showSkip = false),
    TALL(R.layout.widget_tall);

    companion object {
        fun fit(width: Float, height: Float, default: WidgetLayout): WidgetLayout = when {
            width <= 0 || height <= 0 -> default
            width >= 164 && height >= 240 -> TALL
            width >= 260 && height >= 106 -> ARTWORK
            width >= 164 && height >= 176 -> TALL
            height >= 176 -> TALL_NARROW
            width >= 260 -> WIDE
            width >= 176 -> SHORT
            else -> SMALL
        }
    }
}

abstract class PlaybackWidget internal constructor(private val defaultLayout: WidgetLayout) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids, defaultLayout, widgetArtwork())
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, fittedViews(context, options, defaultLayout, widgetArtwork()))
    }

    override fun onReceive(context: Context, intent: Intent) {
        val command = when (intent.action) {
            PREVIOUS -> "previous"
            PLAY -> "play"
            PAUSE -> "pause"
            NEXT -> "next"
            else -> return super.onReceive(context, intent)
        }
        // A launcher can still have controls from before Android reclaimed the app process.
        if (!PlaybackService.dispatchWidgetAction(command)) refresh(context)
    }

    companion object {
        private const val PREVIOUS = "com.enzi.music.widget.PREVIOUS"
        private const val PLAY = "com.enzi.music.widget.PLAY"
        private const val PAUSE = "com.enzi.music.widget.PAUSE"
        private const val NEXT = "com.enzi.music.widget.NEXT"
        private const val MAX_ART_SIZE = 384
        private const val MAX_HOST_SIZES = 16

        private data class Track(val title: String, val artist: String, val playing: Boolean, val art: Bitmap?)

        // Both the service and widget receivers run on the application's main thread.
        private var track: Track? = null

        internal fun show(context: Context, title: String, artist: String, playing: Boolean, art: Bitmap?) {
            val next = Track(title, artist, playing, art)
            if (track == next) return
            track = next
            refresh(context)
        }

        internal fun clear(context: Context) {
            if (track == null) return
            track = null
            refresh(context)
        }

        private fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val compact = ComponentName(context, CompactPlaybackWidget::class.java)
            val artwork = ComponentName(context, ArtworkPlaybackWidget::class.java)
            val compactIds = manager.getAppWidgetIds(compact)
            val artworkIds = manager.getAppWidgetIds(artwork)
            if (compactIds.isEmpty() && artworkIds.isEmpty()) return

            val bitmap = widgetArtwork()
            updateWidgets(context, manager, compactIds, WidgetLayout.WIDE, bitmap)
            updateWidgets(context, manager, artworkIds, WidgetLayout.ARTWORK, bitmap)
        }

        private fun updateWidgets(
            context: Context, manager: AppWidgetManager, ids: IntArray, default: WidgetLayout, bitmap: Bitmap?
        ) {
            for (id in ids) {
                manager.updateAppWidget(id, fittedViews(context, manager.getAppWidgetOptions(id), default, bitmap))
            }
        }

        private fun fittedViews(context: Context, options: Bundle, default: WidgetLayout, bitmap: Bitmap?): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes = BundleCompat.getParcelableArrayList(
                    options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java
                ).orEmpty().distinct()
                if (sizes.size in 1..MAX_HOST_SIZES) {
                    val layouts = sizes.associateWith { WidgetLayout.fit(it.width, it.height, default) }
                    if (layouts.values.distinct().size == 1) {
                        return views(context, layouts.values.first(), bitmap)
                    }
                    return RemoteViews(layouts.mapValues { (_, layout) -> views(context, layout, bitmap) })
                }
            }

            // Some launchers only report the bounds, even on Android 12 and later.
            val portrait = WidgetLayout.fit(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat(),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat(),
                default
            )
            val landscape = WidgetLayout.fit(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat(),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat(),
                default
            )
            val portraitViews = views(context, portrait, bitmap)
            return if (portrait == landscape) portraitViews
            else RemoteViews(views(context, landscape, bitmap), portraitViews)
        }

        private fun views(context: Context, layout: WidgetLayout, bitmap: Bitmap?) =
            RemoteViews(context.packageName, layout.resource).apply {
                val current = track
                val open = PlaybackService.openApp(context)
                setOnClickPendingIntent(R.id.widget_root, open)
                setTextViewText(R.id.widget_title, current?.title ?: context.getString(R.string.app_name))
                setTextViewText(R.id.widget_artist, current?.artist ?: context.getString(R.string.widget_idle))
                setViewVisibility(R.id.widget_cover, if (layout.showCover) View.VISIBLE else View.GONE)
                if (layout.showCover && bitmap != null) setImageViewBitmap(R.id.widget_cover, bitmap)
                else setImageViewResource(R.id.widget_cover, 0)

                val playing = current?.playing == true
                val toggleIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                setImageViewResource(R.id.widget_toggle, toggleIcon)
                val toggleLabel = when {
                    current == null -> R.string.widget_open
                    playing -> R.string.widget_pause
                    else -> R.string.widget_play
                }
                setContentDescription(R.id.widget_toggle, context.getText(toggleLabel))
                val canSkip = current != null && layout.showSkip
                val skipVisibility = if (canSkip) View.VISIBLE else View.GONE
                setViewVisibility(R.id.widget_previous, skipVisibility)
                setViewVisibility(R.id.widget_next, skipVisibility)

                val toggle = if (current == null) open else pending(context, if (playing) PAUSE else PLAY)
                setOnClickPendingIntent(R.id.widget_toggle, toggle)
                setOnClickPendingIntent(R.id.widget_previous, if (canSkip) pending(context, PREVIOUS) else null)
                setOnClickPendingIntent(R.id.widget_next, if (canSkip) pending(context, NEXT) else null)
            }

        private fun pending(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0, Intent(context, CompactPlaybackWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE
            )

        private fun widgetArtwork(): Bitmap? {
            val art = track?.art ?: return null
            val scale = MAX_ART_SIZE.toFloat() / maxOf(art.width, art.height)
            if (scale >= 1f) return art
            val width = (art.width * scale).roundToInt().coerceAtLeast(1)
            val height = (art.height * scale).roundToInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(art, width, height, true)
        }
    }
}
