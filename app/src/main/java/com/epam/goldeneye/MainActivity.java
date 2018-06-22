package com.epam.goldeneye;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.SensorManager.DynamicSensorCallback;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import com.google.android.things.contrib.driver.apa102.Apa102;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.android.things.contrib.driver.cap12xx.Cap12xxInputDriver;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.pwmspeaker.Speaker;
import com.google.android.things.contrib.driver.ssd1306.Ssd1306;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String I2C_BUS = BoardDefaults.getI2cBus();
    private static final String GPIO_FOR_DATA = BoardDefaults.getButtonGpioPin();
    private static final String GPIO_FOR_CLOCK = BoardDefaults.getLedGpioPin();
    // LED configuration.
    private static final int NUM_LEDS = 7;
    private static final int LED_BRIGHTNESS = 0; // 0 ... 31
    private static final Apa102.Mode LED_MODE = Apa102.Mode.BGR;
    private static final String PWM_BUS = BoardDefaults.getSpeakerPwmPin();
    private static final String SPI_BUS = BoardDefaults.getSpiBus();
    public static final float SEGMENT_DISPLAY_BRIGHTNESS = 1f;
    private Cap12xxInputDriver mInputDriver;
    private AlphanumericDisplay mSegmentDisplay;
    private Ssd1306 mOledDisplay;
    private Apa102 mLedstrip;
    private int[] mLedColors;
    private SensorManager mSensorManager;
    private TemperatureEventListener mTemperatureListener = new TemperatureEventListener();
    private DynamicSensorCallback mDynamicSensorCallback = new DynamicSensorCallback() {
        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            Log.d(TAG, "onDynamicSensorConnected() called with: sensor = [" + sensor.getType() + ","+ sensor.getStringType()+"]");
            if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                Log.i(TAG, "Temperature sensor connected");
                mTemperatureListener = new TemperatureEventListener();
                mSensorManager.registerListener(mTemperatureListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else if (sensor.getType() == Sensor.TYPE_PRESSURE) {
                mSensorManager.registerListener(mPressureListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    };
    private Speaker mSpeaker;
    private DisplayMode mDisplayMode = DisplayMode.TEMPERATURE;
    private Gpio mLedBtnA;
    private ButtonInputDriver mButtonInputDriver;

    private enum DisplayMode {
        TEMPERATURE,
        PRESSURE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupButtons();
        setupButtonsLeds();
        setupAlphanumericDisplay();
//        setupOledDisplay();
        setupLedStrip();
        showLights();
        startTemperaturePressureRequest();
        setupSpeaker();
//        playSound();
    }

    private void playSound() {
        try {
            mSpeaker.play(/* G4 */ 391.995);
        } catch (IOException e) {
            Log.e(TAG, "Error playing note", e);
        }
    }

    private void showLights() {
        float[] hsv = {1f, 1f, 1f};
        for (int i = 0; i < mLedColors.length; i++) { // Assigns gradient colors.
            hsv[0] = i * 360.f / mLedColors.length;
            mLedColors[i] = Color.HSVToColor(0, hsv);
        }
        try {
            mLedstrip.write(mLedColors);
        } catch (IOException e) {
            Log.e(TAG, "Error setting LED colors", e);
        }
    }

    private void setupButtons() {

        try {
            mButtonInputDriver = new ButtonInputDriver(BoardDefaults.getButtonGpioPin(), Button.LogicState.PRESSED_WHEN_LOW, KeyEvent.KEYCODE_A);
            mButtonInputDriver.register();
            Log.d(TAG, "Initialized GPIO Button that generates a keypress with KEYCODE_A");
        } catch (IOException e) {
            throw new RuntimeException("Error initializing GPIO button", e);
        }

        /*// Set input key codes
        int[] keyCodes = {
                KeyEvent.KEYCODE_1,
                KeyEvent.KEYCODE_2,
                KeyEvent.KEYCODE_3,
                KeyEvent.KEYCODE_4,
                KeyEvent.KEYCODE_5,
                KeyEvent.KEYCODE_6,
                KeyEvent.KEYCODE_7,
                KeyEvent.KEYCODE_8
        };

        try {
            mInputDriver = new Cap12xxInputDriver(
                    I2C_BUS,
                    null,
                    Cap12xx.Configuration.CAP1208,
                    keyCodes);

            // Disable repeated events
            mInputDriver.setRepeatRate(Cap12xx.REPEAT_DISABLE);
            // Block touches above 4 unique inputs
            mInputDriver.setMultitouchInputMax(4);

            mInputDriver.register();

        } catch (IOException e) {
            Log.w(TAG, "Unable to open driver connection", e);
        }*/
    }

    private void setupButtonsLeds() {
        // GPIO led
        try {
            PeripheralManager pioManager = PeripheralManager.getInstance();
            mLedBtnA = pioManager.openGpio(BoardDefaults.getLedGpioPin());
            mLedBtnA.setEdgeTriggerType(Gpio.EDGE_NONE);
            mLedBtnA.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mLedBtnA.setActiveType(Gpio.ACTIVE_HIGH);
        } catch (IOException e) {
            throw new RuntimeException("Error initializing led", e);
        }
    }

    private void setupAlphanumericDisplay() {
        try {
            mSegmentDisplay = new AlphanumericDisplay(I2C_BUS);
            mSegmentDisplay.setBrightness(SEGMENT_DISPLAY_BRIGHTNESS);
            mSegmentDisplay.setEnabled(true);
            mSegmentDisplay.clear();
            mSegmentDisplay.display("KURT");
        } catch (IOException e) {
            Log.e(TAG, "Error configuring display", e);
        }
    }

    private void setupOledDisplay() {
        try {
            mOledDisplay = new Ssd1306(I2C_BUS);
        } catch (IOException e) {
            Log.e(TAG, "Error while opening screen", e);
        }
        Log.d(TAG, "OLED screen activity created");
    }

    private void setupLedStrip() {
        mLedColors = new int[NUM_LEDS];
        try {
            Log.d(TAG, "Initializing LED strip");
            mLedstrip = new Apa102(SPI_BUS, LED_MODE);
            mLedstrip.setBrightness(LED_BRIGHTNESS);
        } catch (IOException e) {
            Log.e(TAG, "Error initializing LED strip", e);
        }
    }

    private void startTemperaturePressureRequest() {
        this.startService(new Intent(this, TemperaturePressureService.class));
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (mSensorManager != null) {
            mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback);
        }
    }

    private void setupSpeaker() {
        try {
            mSpeaker = new Speaker(PWM_BUS);
            mSpeaker.stop(); // in case the PWM pin was enabled already
        } catch (IOException e) {
            Log.e(TAG, "Error initializing speaker");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyCapacitiveTouchButtons();
        destroyAlphanumericDisplay();
        destroyOledDisplay();
        destroyLedStrip();
        stopTemperaturePressureRequest();
        destroySpeaker();
    }

    private void destroyCapacitiveTouchButtons() {
        if (mInputDriver != null) {
            mInputDriver.unregister();

            try {
                mInputDriver.close();
                mButtonInputDriver.close();
            } catch (IOException e) {
                Log.w(TAG, "Unable to close touch driver", e);
            } finally {
                mInputDriver = null;
                mButtonInputDriver = null;
            }
        }
    }

    private void destroyAlphanumericDisplay() {
        if (mSegmentDisplay != null) {
            Log.i(TAG, "Closing display");
            try {
                mSegmentDisplay.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing display", e);
            } finally {
                mSegmentDisplay = null;
            }
        }
    }

    private void destroyOledDisplay() {
        if (mOledDisplay != null) {
            try {
                mOledDisplay.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing SSD1306", e);
            } finally {
                mOledDisplay = null;
            }
        }
    }

    private void destroyLedStrip() {
        if (mLedstrip != null) {
            try {
                mLedstrip.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception closing LED strip", e);
            } finally {
                mLedstrip = null;
            }
        }
    }

    private void stopTemperaturePressureRequest() {
        this.stopService(new Intent(this, TemperaturePressureService.class));
        mSensorManager.unregisterDynamicSensorCallback(mDynamicSensorCallback);
        mSensorManager.unregisterListener(mTemperatureListener);
    }

    private void destroySpeaker() {
        if (mSpeaker != null) {
            try {
                mSpeaker.stop();
                mSpeaker.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing speaker", e);
            } finally {
                mSpeaker = null;
            }
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp() called with: keyCode = [" + keyCode + "], event = [" + event + "]");
        if (keyCode == KeyEvent.KEYCODE_A) {
            mDisplayMode = DisplayMode.TEMPERATURE;
            updateDisplay(mTemperatureListener.historyTemperature);
            try {
                mLedBtnA.setValue(false);
            } catch (IOException e) {
                Log.e(TAG, "error updating LED", e);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private class TemperatureEventListener implements SensorEventListener {

        float historyTemperature;

        @Override
        public void onSensorChanged(SensorEvent event) {
            final float currentTemperature = event.values[0];
            if (Math.abs(historyTemperature - currentTemperature) > 0.05f) {
                historyTemperature = currentTemperature;
                Log.i(TAG, "sensor changed: " + currentTemperature);
                if (mDisplayMode == DisplayMode.TEMPERATURE) {
                    updateDisplay(historyTemperature);
                }
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.i(TAG, "sensor accuracy changed: " + accuracy);
        }
    }

    private SensorEventListener mPressureListener = new SensorEventListener() {

        float historyPressure;

        @Override
        public void onSensorChanged(SensorEvent event) {
            historyPressure = event.values[0];
//            Log.d(TAG, "sensor changed: " + historyPressure);
            if (mDisplayMode == DisplayMode.PRESSURE) {
                updateDisplay(historyPressure);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "accuracy changed: " + accuracy);
        }
    };

    private void updateDisplay(final float value) {
        try {
            mSegmentDisplay.display((double) value);
        } catch (IOException pE) {
            Log.e(TAG, "updateDisplay: ", pE);
        }
    }
}
