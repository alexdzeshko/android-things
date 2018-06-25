package com.epam.goldeneye

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import com.epam.goldeneye.rainbowhat.Beeper
import com.epam.goldeneye.rainbowhat.RainbowConnector

class MainActivity : Activity(), RainbowConnector.ServiceManager {

    override fun <S : Service> startService(service: Class<S>) {
        startService(Intent(this, service))
    }

    override fun <S : Service> stopService(service: Class<S>) {
        stopService(Intent(this, service))
    }

    private val rainbowConnector: RainbowConnector = RainbowConnector({
        sensorManager
    }, this)

    private var displayMode = DisplayMode.TEMPERATURE
    private val sensorManager by lazy {
        getSystemService(SensorManager::class.java)
    }

    private enum class DisplayMode {
        TEMPERATURE,
        PRESSURE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        rainbowConnector.initialize()
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

        playIntro()

        findViewById<View>(R.id.btn).setOnClickListener { Toast.makeText(this@MainActivity, "Hello!", Toast.LENGTH_SHORT).show() }
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
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        playButtonClick()
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyUp() called with: keyCode = [$keyCode], event = [$event]")
        if (keyCode == KeyEvent.KEYCODE_A) {
            displayMode = DisplayMode.TEMPERATURE
            rainbowConnector.updateDisplay(rainbowConnector.getRecentTemperature())
            rainbowConnector.switchLedA(true)
            rainbowConnector.switchLedB(false)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_B) {
            displayMode = DisplayMode.PRESSURE
            rainbowConnector.updateDisplay(rainbowConnector.getRecentPressure())
            rainbowConnector.switchLedA(false)
            rainbowConnector.switchLedB(true)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        // LED configuration.
    }
}
