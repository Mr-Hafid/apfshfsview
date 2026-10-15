package com.apfshfsview.reader.media

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.apfshfsview.reader.R
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

@OptIn(UnstableApi::class)
class VolumePlayerViewFactory(private val players: PlayerRegistry) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val playerId = ((args as? Map<*, *>)?.get("playerId") as? Number)?.toLong()
        val playerView = LayoutInflater.from(context).inflate(R.layout.volume_player_view, null) as PlayerView
        playerView.isClickable = false
        playerView.isFocusable = false
        playerView.player = playerId?.let(players::player)
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
