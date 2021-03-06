package com.epam.goldeneye.rainbowhat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;

import java.io.IOException;

/**
 * To use this service, start it from your component (like an activity):
 * <pre>{@code
 * this.startService(new Intent(this, TemperaturePressureService.class))
 * }</pre>
 */
public class TemperaturePressureService extends Service {

    private static final String TAG = TemperaturePressureService.class.getSimpleName();

    private Bmx280SensorDriver mTemperatureSensorDriver;

    @Override
    public void onCreate() {
        setupTemperaturePressureSensor();
    }

    private void setupTemperaturePressureSensor() {
        try {
            mTemperatureSensorDriver = RainbowHat.createSensorDriver();
            mTemperatureSensorDriver.registerTemperatureSensor();
            mTemperatureSensorDriver.registerPressureSensor();
        } catch (IOException e) {
            Log.e(TAG, "Error configuring sensor", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyTemperaturePressureSensor();
    }

    private void destroyTemperaturePressureSensor() {
        if (mTemperatureSensorDriver != null) {
            mTemperatureSensorDriver.unregisterTemperatureSensor();
            mTemperatureSensorDriver.unregisterPressureSensor();
            try {
                mTemperatureSensorDriver.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing sensor", e);
            } finally {
                mTemperatureSensorDriver = null;
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

}
