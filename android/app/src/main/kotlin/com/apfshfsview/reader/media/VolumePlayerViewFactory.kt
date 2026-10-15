package com.apfshfsview.reader.media

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

@OptIn(UnstableApi::class)
class VolumePlayerViewFactory(private val players: PlayerRegistry) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val playerId = ((args as? Map<*, *>)?.get("playerId") as? Number)?.toLong()
        val playerView = PlayerView(context).apply {
            useController = false
            setShutterBackgroundColor(Color.BLACK)
            setBackgroundColor(Color.BLACK)
            player = playerId?.let(players::player)
        }
        return object : PlatformView {
            override fun getView(): View = playerView

            override fun dispose() {
                playerView.player = null
            }
        }
    }

    companion object {
        const val VIEW_TYPE = "com.apfshfsview.reader/volume-player"
    }
}
