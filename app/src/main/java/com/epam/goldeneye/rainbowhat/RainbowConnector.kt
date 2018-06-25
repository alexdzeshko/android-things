package com.epam.goldeneye.rainbowhat

import android.app.Service
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.KeyEvent
import com.epam.goldeneye.rainbowhat.RainbowConnector.RainbowButton.*
import com.epam.goldeneye.utils.IO
import com.epam.goldeneye.utils.closePlease
import com.google.android.things.contrib.driver.apa102.Apa102
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay
import com.google.android.things.contrib.driver.pwmspeaker.Speaker
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat
import com.google.android.things.pio.Gpio
import java.io.IOException

interface IRainbowConnector {
    var temperatureListener: ((Float) -> Unit)?
    var pressureListener: ((Float) -> Unit)?
    var onButtonPressed: ((RainbowConnector.RainbowButton) -> Unit)?
    fun getRecentTemperature(): Float
    fun getRecentPressure(): Float
    fun initialize()
    fun beep(tone: DoubleArray)
    fun updateDisplay(value: Float)
    fun switchLed(led: RainbowConnector.RainbowButton, isOn: Boolean)
    fun lightLedPressure(pPressure: Float)
    fun shutdown()
    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean
}

class RainbowConnector(private val sensorManagerProvider: () -> SensorManager,
                       private val serviceManager: ServiceManager)
    : IRainbowConnector {

    enum class RainbowButton {
        A, B, C
    }

    override var onButtonPressed: ((RainbowButton) -> Unit)? = null

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyUp() called with: keyCode = [$keyCode], event = [$event]")
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                onButtonPressed?.invoke(A)
                true
            }
            KeyEvent.KEYCODE_B -> {
                onButtonPressed?.invoke(B)
                true
            }
            KeyEvent.KEYCODE_C -> {
                onButtonPressed?.invoke(C)
                true
            }
            else -> false
        }
    }

    interface ServiceManager {
        fun <S : Service> startService(service: Class<S>)
        fun <S : Service> stopService(service: Class<S>)
    }

    private var segmentDisplay: AlphanumericDisplay? = null

    private var ledstrip: Apa102? = null
    private var ledColors = IntArray(NUM_LEDS)

    private var speaker: Speaker? = null
    private var beeper: Beeper? = null

    private var ledA: Gpio? = null
    private var ledB: Gpio? = null
    private var ledC: Gpio? = null
    private var buttonAInputDriver: ButtonInputDriver? = null
    private var buttonBInputDriver: ButtonInputDriver? = null
    private var buttonCInputDriver: ButtonInputDriver? = null

    //    private Handler mHandler = new Handler();

    private var sensorManager: SensorManager? = null
    private val temperatureEventListener = TemperatureEventListener()
    private val pressureEventListener = PressureEventListener()
    private val dynamicSensorCallback = object : SensorManager.DynamicSensorCallback() {
        override fun onDynamicSensorConnected(sensor: Sensor) {
            Log.d(TAG, "onDynamicSensorConnected() called with: sensor = [" + sensor.type + "," + sensor.stringType + "]")
            if (sensor.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                Log.i(TAG, "Temperature sensor connected")
                sensorManager?.registerListener(temperatureEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            } else if (sensor.type == Sensor.TYPE_PRESSURE) {
                Log.i(TAG, "Pressure sensor connected")
                sensorManager?.registerListener(pressureEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }


    override var temperatureListener: ((Float) -> Unit)? = null
    override var pressureListener: ((Float) -> Unit)? = null
    override fun getRecentTemperature(): Float = temperatureEventListener.recentTemperature
    override fun getRecentPressure(): Float = pressureEventListener.recentPressure

    override fun initialize() {
        setupButtons()
        setupButtonsLeds()
        setupAlphanumericDisplay()
        setupLedStrip()
        showAllRainbowLights()
        startTemperaturePressureRequest()
        setupSpeaker()
    }

    private fun showAllRainbowLights() {

        val hsv = floatArrayOf(1f, 1f, 1f)
        for (i in ledColors.indices) { // Assigns gradient colors.
            hsv[0] = i * 360f / ledColors.size
            ledColors[i] = Color.HSVToColor(0, hsv)
        }
        applyRainbowColors()

    }

    private fun applyRainbowColors() {
        try {
            ledstrip?.write(ledColors)
        } catch (e: IOException) {
            Log.e(TAG, "Error setting LED colors", e)
        }

    }

    private fun showRainbowLight(ledIndex: Int, color: Int) {
        ledColors[ledIndex] = color
        applyRainbowColors()
    }

    private fun resetRainbow() {
        for (i in ledColors.indices) {
            ledColors[i] = Color.BLACK
        }
        applyRainbowColors()
    }

    private fun setupButtons() {

        try {
            buttonAInputDriver = RainbowHat.createButtonAInputDriver(KeyEvent.KEYCODE_A)
            buttonAInputDriver?.register()

            buttonBInputDriver = RainbowHat.createButtonBInputDriver(KeyEvent.KEYCODE_B)
            buttonBInputDriver?.register()

            buttonCInputDriver = RainbowHat.createButtonCInputDriver(KeyEvent.KEYCODE_C)
            buttonCInputDriver?.register()
        } catch (e: IOException) {
            throw RuntimeException("Error initializing GPIO button", e)
        }

    }

    private fun setupButtonsLeds() {
        // GPIO led
        try {
            ledA = RainbowHat.openLedRed()
            ledB = RainbowHat.openLedGreen()
            ledC = RainbowHat.openLedBlue()
            switchLed(ledA, true)
            switchLed(ledB, false)
            switchLed(ledC, false)
        } catch (e: IOException) {
            throw RuntimeException("Error initializing led", e)
        }

    }

    private fun setupAlphanumericDisplay() {
        try {
            segmentDisplay = RainbowHat.openDisplay()
            segmentDisplay?.setBrightness(SEGMENT_DISPLAY_BRIGHTNESS)
            segmentDisplay?.setEnabled(true)
            segmentDisplay?.clear()
            segmentDisplay?.display("Welcome")
        } catch (e: IOException) {
            Log.e(TAG, "Error configuring display", e)
        }

    }

    private fun setupLedStrip() {
        try {
            Log.d(TAG, "Initializing LED strip")
            ledstrip = RainbowHat.openLedStrip()
            ledstrip?.brightness = LED_BRIGHTNESS
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing LED strip", e)
        }

    }

    private fun startTemperaturePressureRequest() {
        serviceManager.startService(TemperaturePressureService::class.java)
        sensorManager = sensorManagerProvider.invoke()
        sensorManager?.registerDynamicSensorCallback(dynamicSensorCallback)
    }

    private fun setupSpeaker() {
        try {
            speaker = RainbowHat.openPiezo()
            speaker?.stop() // in case the PWM pin was enabled already
            beeper = Beeper(speaker)
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing speaker")
        }

    }

    override fun beep(tone: DoubleArray) {
        beeper?.beep(tone)
    }

    override fun updateDisplay(value: Float) {
        try {
            segmentDisplay?.display(value.toDouble())
        } catch (pE: IOException) {
            Log.e(TAG, "updateDisplay: ", pE)
        }

    }

    private fun switchLed(led: Gpio?, isOn: Boolean) {
        try {
            led?.value = isOn
        } catch (e: IOException) {
            Log.e(TAG, "error updating LED", e)
        }

    }

    override fun switchLed(led: RainbowButton, isOn: Boolean) {
        when (led) {
            A -> switchLed(ledA, isOn)
            B -> switchLed(ledB, isOn)
            C -> switchLed(ledC, isOn)
        }
    }

    override fun lightLedPressure(pPressure: Float) {
        resetRainbow()

        val delta = Math.min(MAX_PRESSURE.toFloat(), Math.max(0f, pPressure - MIN_PRESSURE)).toInt() / 10
        val index = Math.min(NUM_LEDS - 1, Math.max(0, NUM_LEDS - 1 - delta))

//        Log.d(TAG, "signalPressure: i $index d $delta")
        showRainbowLight(index, Color.BLUE)
    }

    override fun shutdown() {
        destroyButtons()
        destroyAlphanumericDisplay()
        destroyLedStrip()
        stopTemperaturePressureRequest()
        destroySpeaker()
    }

    private fun destroyButtons() {
        IO.closePlease(buttonAInputDriver, buttonBInputDriver)
    }


    private fun destroyAlphanumericDisplay() {
        segmentDisplay?.closePlease()
    }

    private fun destroyLedStrip() {
        ledstrip?.closePlease()
    }

    private fun destroySpeaker() {
        speaker?.closePlease()
    }

    private fun stopTemperaturePressureRequest() {
        serviceManager.stopService(TemperaturePressureService::class.java)
        sensorManager?.apply {
            unregisterDynamicSensorCallback(dynamicSensorCallback)
            unregisterListener(temperatureEventListener)
            unregisterListener(pressureEventListener)
        }
        temperatureListener = null
        pressureListener = null
    }

    private inner class TemperatureEventListener : SensorEventListener {

        internal var recentTemperature: Float = 0.toFloat()

        override fun onSensorChanged(event: SensorEvent) {
            val currentTemperature = event.values[0]
            if (Math.abs(recentTemperature - currentTemperature) > 0.05f) {
//                Log.i(TAG, "temperature changed: $currentTemperature")
                temperatureListener?.invoke(currentTemperature)
            }
            recentTemperature = currentTemperature
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            Log.i(TAG, "temperature accuracy changed: $accuracy")
        }
    }

    private inner class PressureEventListener : SensorEventListener {

        internal var recentPressure: Float = 0.toFloat()

        override fun onSensorChanged(event: SensorEvent) {
            val currentPressure = event.values[0]
            if (Math.abs(recentPressure - currentPressure) > 0.1f) {
//                Log.d(TAG, "pressure changed: $recentPressure")
                pressureListener?.invoke(currentPressure)
            }
            recentPressure = currentPressure
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            Log.d(TAG, "pressure accuracy changed: $accuracy")
        }
    }

    companion object {
        private val TAG = RainbowConnector::class.java.simpleName
        // LED configuration.
        private const val NUM_LEDS = RainbowHat.LEDSTRIP_LENGTH
        private const val LED_BRIGHTNESS = 1 // 0 ... 31
        const val SEGMENT_DISPLAY_BRIGHTNESS = 1f
        const val MAX_PRESSURE = 1030
        const val MIN_PRESSURE = 970
    }


}