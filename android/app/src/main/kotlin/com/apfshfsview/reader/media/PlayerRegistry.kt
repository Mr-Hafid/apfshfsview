package com.apfshfsview.reader.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.apfshfsview.core.fs.FileSystem
import com.apfshfsview.reader.bridge.PlayerStateMessage

@OptIn(UnstableApi::class)
class PlayerRegistry(private val context: Context, private val onState: (PlayerStateMessage) -> Unit) {
    private val players = HashMap<Long, ExoPlayer>()
    private val volumeOfPlayer = HashMap<Long, Long>()
    private var nextPlayerId = 1L
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            players.forEach { (playerId, player) -> if (player.isPlaying) publish(playerId, player) }
            if (players.isNotEmpty()) handler.postDelayed(this, TICK_MILLIS)
        }
    }

    fun create(volumeHandle: Long, fileSystem: FileSystem, nodeId: Long): Long {
        val playerId = nextPlayerId++
        val mediaSource = ProgressiveMediaSource.Factory { VolumeDataSource(fileSystem) }
            .createMediaSource(MediaItem.fromUri(VolumeDataSource.uriFor(nodeId)))
        val player = ExoPlayer.Builder(context).build()
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publish(playerId, player)
            }
        })
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
        val wasIdle = players.isEmpty()
        players[playerId] = player
        volumeOfPlayer[playerId] = volumeHandle
        if (wasIdle) handler.postDelayed(ticker, TICK_MILLIS)
        return playerId
    }

    fun player(playerId: Long): ExoPlayer? = players[playerId]

    fun play(playerId: Long) {
        players[playerId]?.let { player ->
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun pause(playerId: Long) {
        players[playerId]?.pause()
    }

    fun seekTo(playerId: Long, positionMillis: Long) {
        players[playerId]?.seekTo(positionMillis)
    }

    fun dispose(playerId: Long) {
        players.remove(playerId)?.release()
        volumeOfPlayer.remove(playerId)
        if (players.isEmpty()) handler.removeCallbacks(ticker)
    }

    fun disposeForVolumes(volumeHandles: Set<Long>) {
        volumeOfPlayer.filterValues { it in volumeHandles }.keys.toList().forEach(::dispose)
    }

    fun disposeAll() {
        players.keys.toList().forEach(::dispose)
    }

    private fun publish(playerId: Long, player: Player) {
        onState(
            PlayerStateMessage(
                playerId = playerId,
                positionMillis = player.currentPosition.coerceAtLeast(0),
                durationMillis = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                hasEnded = player.playbackState == Player.STATE_ENDED,
                errorMessage = player.playerError?.errorCodeName,
            ),
        )
    }

    private companion object {
        const val TICK_MILLIS = 500L
    }
}
