package com.epam.goldeneye.texttospeach

import android.content.ContentValues.TAG
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*
import java.util.concurrent.LinkedBlockingQueue


interface IComputerVoice {
    fun say(text: String)
    fun shutdown()
}

class ComputerVoice(context: Context) : IComputerVoice {

    companion object {
        private const val UTTERANCE_ID = "com.epam.goldeneye.UTTERANCE_ID"
    }

    private val onInitListener = TextToSpeech.OnInitListener { status ->
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            textToSpeech?.language = Locale.US
            releaseQueue()
        } else {
            isInitialized = false
            Log.d(TAG, "Could not open TTS Engine (onInit status=$status). Ignoring text to speech")
            textToSpeech = null
        }
    }

    private var textToSpeech: TextToSpeech? = TextToSpeech(context, onInitListener)

    private var isInitialized: Boolean = false
    private val speechQueue = LinkedBlockingQueue<String>()

    override fun say(text: String) {
        if (isInitialized) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
        } else {
            defer(text)
        }
    }

    override fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    private fun defer(text: String) {
        speechQueue.add(text)
    }

    private fun releaseQueue() {
        while (speechQueue.isNotEmpty()) {
            say(speechQueue.take())
        }
    }
}