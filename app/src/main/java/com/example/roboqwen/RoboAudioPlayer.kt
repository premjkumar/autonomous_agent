package com.example.roboqwen  

import android.content.Context  
import androidx.annotation.OptIn  
import androidx.media3.common.MediaItem  
import androidx.media3.common.MimeTypes  
import androidx.media3.common.util.UnstableApi  
import androidx.media3.exoplayer.ExoPlayer  

class RoboAudioPlayer(context: Context) {  
    private var exoPlayer: ExoPlayer? = ExoPlayer.Builder(context).build()  

    @OptIn(UnstableApi::class)  
    fun playAudioStream(streamUrl: String) {  
        stopPlayback()  
          
        val mediaItem = MediaItem.Builder()  
            .setUri(streamUrl)  
            .setMimeType(MimeTypes.AUDIO_M4A)  
            .build()  
              
        exoPlayer?.apply {  
            setMediaItem(mediaItem)  
            prepare()  
            playWhenReady = true  
        }  
    }  

    fun stopPlayback() {  
        exoPlayer?.stop()  
        exoPlayer?.clearMediaItems()  
    }  

    fun releasePlayer() {  
        exoPlayer?.release()  
        exoPlayer = null  
    }  
}
