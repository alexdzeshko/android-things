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
import android.view.View;
import android.widget.Toast;

import com.google.android.things.contrib.driver.apa102.Apa102;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.pwmspeaker.Speaker;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.pio.Gpio;

import java.io.IOException;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    // LED configuration.
    private static final int NUM_LEDS = RainbowHat.LEDSTRIP_LENGTH;
    private static final int LED_BRIGHTNESS = 1; // 0 ... 31
    public static final float SEGMENT_DISPLAY_BRIGHTNESS = 1f;
    public static final int SOUND_LENGTH = 50;

    private AlphanumericDisplay mSegmentDisplay;

    private Apa102 mLedstrip;
    int[] mLedColors = new int[NUM_LEDS];

    private Speaker mSpeaker;
    private Beeper mBeeper;

    private Gpio mLedA, mLedB;
    private ButtonInputDriver mButtonAInputDriver, mButtonBInputDriver;

//    private Handler mHandler = new Handler();

    private SensorManager mSensorManager;
    private TemperatureEventListener mTemperatureListener = new TemperatureEventListener();
    private PressureEventListener mPressureListener = new PressureEventListener();
    private DynamicSensorCallback mDynamicSensorCallback = new DynamicSensorCallback() {
        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            Log.d(TAG, "onDynamicSensorConnected() called with: sensor = [" + sensor.getType() + "," + sensor.getStringType() + "]");
            if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                Log.i(TAG, "Temperature sensor connected");
                mSensorManager.registerListener(mTemperatureListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else if (sensor.getType() == Sensor.TYPE_PRESSURE) {
                Log.i(TAG, "Pressure sensor connected");
                mSensorManager.registerListener(mPressureListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    };

    private DisplayMode mDisplayMode = DisplayMode.TEMPERATURE;
    public static final int MAX_PRESSURE = 1030;
    public static final int MIN_PRESSURE = 970;

    private enum DisplayMode {
        TEMPERATURE,
        PRESSURE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        setupButtons();
        setupButtonsLeds();
        setupAlphanumericDisplay();
        setupLedStrip();
        showAllRainbowLights();
        startTemperaturePressureRequest();
        setupSpeaker();
        playIntro();

        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                Toast.makeText(MainActivity.this, "Hello!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void playIntro() {
        mBeeper.beep(Beeper.Tones.INSTANCE.getDRAMATIC_THEME(), 80L);
    }

    private void playButtonClick() {
        mBeeper.beep(new double[]{Beeper.Tones.G4}, 80L);
        /*try {
            mSpeaker.play(*//* G4 *//* 391.995);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        mSpeaker.stop();
                    } catch (IOException pE) {
                        pE.printStackTrace();
                    }
                }
            }, SOUND_LENGTH);
        } catch (IOException e) {
            Log.e(TAG, "Error playing note", e);
        }*/
    }

    private void showAllRainbowLights() {

        float[] hsv = {1f, 1f, 1f};
        for (int i = 0; i < mLedColors.length; i++) { // Assigns gradient colors.
            hsv[0] = i * 360.f / mLedColors.length;
            mLedColors[i] = Color.HSVToColor(0, hsv);
        }
        applyRainbowColors();

    }

    private void applyRainbowColors() {
        try {
            mLedstrip.write(mLedColors);
        } catch (IOException e) {
            Log.e(TAG, "Error setting LED colors", e);
        }
    }

    private void showRainbowLight(final int ledIndex, int color) {
        mLedColors[ledIndex] = color;
        applyRainbowColors();
    }

    private void resetRainbow() {
        for (int i = 0; i < mLedColors.length; i++) {
            mLedColors[i] = Color.BLACK;
        }
        applyRainbowColors();
    }

    private void setupButtons() {

        try {
            mButtonAInputDriver = RainbowHat.createButtonAInputDriver(KeyEvent.KEYCODE_A);
            mButtonAInputDriver.register();

            mButtonBInputDriver = RainbowHat.createButtonBInputDriver(KeyEvent.KEYCODE_B);
            mButtonBInputDriver.register();
            Log.d(TAG, "Initialized GPIO Button that generates a keypress with KEYCODE_A");
        } catch (IOException e) {
            throw new RuntimeException("Error initializing GPIO button", e);
        }

    }

    private void setupButtonsLeds() {
        // GPIO led
        try {
            mLedA = RainbowHat.openLedRed();
            mLedB = RainbowHat.openLedGreen();
            switchLed(mLedA, true);
            switchLed(mLedB, false);
        } catch (IOException e) {
            throw new RuntimeException("Error initializing led", e);
        }
    }

    private void setupAlphanumericDisplay() {
        try {
            mSegmentDisplay = RainbowHat.openDisplay();
            mSegmentDisplay.setBrightness(SEGMENT_DISPLAY_BRIGHTNESS);
            mSegmentDisplay.setEnabled(true);
            mSegmentDisplay.clear();
            mSegmentDisplay.display("Welcome");
        } catch (IOException e) {
            Log.e(TAG, "Error configuring display", e);
        }
    }

    private void setupLedStrip() {
        try {
            Log.d(TAG, "Initializing LED strip");
            mLedstrip = RainbowHat.openLedStrip();
            mLedstrip.setBrightness(LED_BRIGHTNESS);
        } catch (IOException e) {
            Log.e(TAG, "Error initializing LED strip", e);
        }
    }

    private void startTemperaturePressureRequest() {
        startService(new Intent(this, TemperaturePressureService.class));
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (mSensorManager != null) {
            mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback);
        }
    }

    private void setupSpeaker() {
        try {
            mSpeaker = RainbowHat.openPiezo();
            mSpeaker.stop(); // in case the PWM pin was enabled already
            mBeeper = new Beeper(mSpeaker);
        } catch (IOException e) {
            Log.e(TAG, "Error initializing speaker");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyButtons();
        destroyAlphanumericDisplay();
        destroyLedStrip();
        stopTemperaturePressureRequest();
        destroySpeaker();
    }

    private void destroyButtons() {
        IO.closePlease(mButtonAInputDriver, mButtonBInputDriver);
    }

    private void destroyAlphanumericDisplay() {
        IO.closePlease(mSegmentDisplay);
    }

    private void destroyLedStrip() {
        IO.closePlease(mLedstrip);
    }

    private void destroySpeaker() {
        IO.closePlease(mSpeaker);
    }

    private void stopTemperaturePressureRequest() {
        stopService(new Intent(this, TemperaturePressureService.class));
        if (mSensorManager != null) {
            mSensorManager.unregisterDynamicSensorCallback(mDynamicSensorCallback);
            mSensorManager.unregisterListener(mTemperatureListener);
            mSensorManager.unregisterListener(mPressureListener);
        }
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        playButtonClick();
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp() called with: keyCode = [" + keyCode + "], event = [" + event + "]");
        if (keyCode == KeyEvent.KEYCODE_A) {
            mDisplayMode = DisplayMode.TEMPERATURE;
            updateDisplay(mTemperatureListener.recentTemperature);
            switchLed(mLedA, true);
            switchLed(mLedB, false);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_B) {
            mDisplayMode = DisplayMode.PRESSURE;
            updateDisplay(mPressureListener.recentPressure);
            switchLed(mLedA, false);
            switchLed(mLedB, true);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void switchLed(final Gpio led, final boolean isOn) {
        try {
            led.setValue(isOn);
        } catch (IOException e) {
            Log.e(TAG, "error updating LED", e);
        }
    }

    private class TemperatureEventListener implements SensorEventListener {

        float recentTemperature;

        @Override
        public void onSensorChanged(SensorEvent event) {
            final float currentTemperature = event.values[0];
            if (Math.abs(recentTemperature - currentTemperature) > 0.05f) {
                Log.i(TAG, "temperature changed: " + currentTemperature);
                if (mDisplayMode == DisplayMode.TEMPERATURE) {
                    updateDisplay(currentTemperature);
                }
            }
            recentTemperature = currentTemperature;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.i(TAG, "temperature accuracy changed: " + accuracy);
        }
    }

    private class PressureEventListener implements SensorEventListener {

        float recentPressure;

        @Override
        public void onSensorChanged(SensorEvent event) {
            final float currentPressure = event.values[0];
            if (Math.abs(recentPressure - currentPressure) > 0.1f) {
                Log.d(TAG, "pressure changed: " + recentPressure);
                signalPressure(currentPressure);
                if (mDisplayMode == DisplayMode.PRESSURE) {
                    updateDisplay(currentPressure);
                }
            }
            recentPressure = currentPressure;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "pressure accuracy changed: " + accuracy);
        }
    }

    private void signalPressure(final float pPressure) {
        resetRainbow();

        final int delta = (int) Math.min(MAX_PRESSURE, Math.max(0, pPressure - MIN_PRESSURE)) / 10;
        final int index = Math.min(NUM_LEDS - 1, Math.max(0, NUM_LEDS - 1 - delta));

        Log.d(TAG, "signalPressure: i " + index + " d " + delta);
        showRainbowLight(index, Color.BLUE);
    }

    private void updateDisplay(final float value) {
        try {
            mSegmentDisplay.display((double) value);
        } catch (IOException pE) {
            Log.e(TAG, "updateDisplay: ", pE);
        }
    }
}
