package com.epam.goldeneye.rainbowhat

import android.os.Handler
import android.os.HandlerThread
import com.google.android.things.contrib.driver.pwmspeaker.Speaker

class Beeper(private val speaker: Speaker?) {

    companion object {
        private const val PLAYBACK_NOTE_DELAY = 80L
    }

    private val handlerThread: HandlerThread = HandlerThread("pwm-playback")
    private val handler: Handler

    init {
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    fun beep(tone: DoubleArray, noteDelay: Long = PLAYBACK_NOTE_DELAY) {
        handler.removeCallbacksAndMessages(null)
        speaker?.stop()

        handler.post(PlaybackRunnable(tone, noteDelay))
    }

    inner class PlaybackRunnable(private val tone: DoubleArray, private val noteDelay: Long) : Runnable {

        var noteIndex = 0

        override fun run() {
            speaker?.let {

                if (tone.size == noteIndex) {
                    speaker.stop()
                    return@let
                }

                val note = tone[noteIndex]
                if (note > 0) {
                    speaker.play(note)
                } else {
                    speaker.stop()
                }
                handler.postDelayed(this, noteDelay)
                noteIndex++
            }
        }
    }

    object Tones {
        private const val REST = -1.0
        const val G4 = 391.995
        const val E4_FLAT = 311.127

        val DRAMATIC_THEME = doubleArrayOf(G4, REST, G4, REST, G4, REST, E4_FLAT, E4_FLAT)

    }
}