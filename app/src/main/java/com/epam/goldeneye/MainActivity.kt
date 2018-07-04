package com.epam.goldeneye

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import com.epam.goldeneye.bluetooth.BluetoothConnector
import com.epam.goldeneye.bluetooth.IBluetoothConnector
import com.epam.goldeneye.rainbowhat.Beeper
import com.epam.goldeneye.rainbowhat.IRainbowConnector
import com.epam.goldeneye.rainbowhat.RainbowConnector
import com.epam.goldeneye.rainbowhat.RainbowConnector.RainbowButton.*
import com.epam.goldeneye.texttospeach.ComputerVoice
import com.epam.goldeneye.texttospeach.IComputerVoice
import com.epam.opencv.detector.face.ui.FaceDetectionActivity

class MainActivity : Activity(), RainbowConnector.ServiceManager {

    private val handler = Handler()
    private lateinit var voice: IComputerVoice
    private lateinit var bluetoothConnector: IBluetoothConnector
    private val rainbowConnector: IRainbowConnector = RainbowConnector({
        sensorManager
    }, this)

    private var displayMode = DisplayMode.TEMPERATURE

    private val sensorManager by lazy {
        getSystemService(SensorManager::class.java)
    }

    private enum class DisplayMode {
        TEMPERATURE,
        PRESSURE;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        voice = ComputerVoice(this)
        bluetoothConnector = BluetoothConnector(this)

        rainbowConnector.initialize()
        rainbowConnector.switchLed(A,displayMode == DisplayMode.TEMPERATURE)
        rainbowConnector.switchLed(B, false)
        rainbowConnector.temperatureListener = { temp ->
            if (displayMode == DisplayMode.TEMPERATURE) {
                rainbowConnector.updateDisplay(temp)
            }
        }

        rainbowConnector.pressureListener = { pressure ->
            rainbowConnector.lightLedPressure(pressure)
            if (displayMode == DisplayMode.PRESSURE) {
                rainbowConnector.updateDisplay(pressure)
            }
        }

        rainbowConnector.onButtonPressed = {rainbowButton ->
            when (rainbowButton) {
                A -> {
                    displayMode = if (displayMode == DisplayMode.TEMPERATURE) DisplayMode.PRESSURE else DisplayMode.TEMPERATURE
                    rainbowConnector.updateDisplay(if(displayMode == DisplayMode.TEMPERATURE) rainbowConnector.getRecentTemperature() else rainbowConnector.getRecentPressure())
                    rainbowConnector.switchLed(A,displayMode == DisplayMode.TEMPERATURE)
                }
                B -> {
                    rainbowConnector.switchLed(B, true)
                    startFaceDetection()
                }
                C -> {
                    //do bluetooth connect
                    bluetoothConnector.enableDiscoverable()
                    rainbowConnector.switchLed(C, true)
                    delayed(500) { rainbowConnector.switchLed(C, false)}
                }
            }

        }
//        playIntro()

        voice.say("Hello, my master!")

        findViewById<View>(R.id.btn).setOnClickListener { startFaceDetection() }
    }

    private fun startFaceDetection() {
        startActivity(Intent(this, FaceDetectionActivity::class.java))
    }

    private fun delayed(timeout: Long, block: () -> Unit) {
        handler.postDelayed({block.invoke()}, timeout)
    }

    override fun <S : Service> startService(service: Class<S>) {
        startService(Intent(this, service))
    }

    override fun <S : Service> stopService(service: Class<S>) {
        stopService(Intent(this, service))
    }

    private fun playIntro() {
        rainbowConnector.beep(Beeper.Tones.DRAMATIC_THEME)
    }

    private fun playButtonClick() {
        rainbowConnector.beep(doubleArrayOf(Beeper.Tones.G4))
    }

    override fun onDestroy() {
        super.onDestroy()
        rainbowConnector.shutdown()
        voice.shutdown()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        playButtonClick()
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return rainbowConnector.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
