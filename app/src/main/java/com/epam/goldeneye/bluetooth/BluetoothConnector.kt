package com.epam.goldeneye.bluetooth

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.google.android.things.bluetooth.BluetoothProfileManager
import java.lang.reflect.InvocationTargetException
import java.util.*


interface IBluetoothConnector {
    var bluetoothListener: BluetoothConnector.BluetoothListener?
    fun shutdown()
    fun enableDiscoverable()
}

class BluetoothConnector(private var context: Context?) : IBluetoothConnector {

    interface BluetoothListener {
        fun onDeviceConnected(device: BluetoothDevice)
        fun onDeviceDisconected(device: BluetoothDevice)
    }

    private val mBluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var mA2DPSinkProxy: BluetoothProfile? = null
    override var bluetoothListener: BluetoothListener? = null

    private val DEVICE_DISPLAYABLE_NAME = "Android Things device"
    private val DISCOVERABLE_TIMEOUT_MS = 300
    private val REQUEST_CODE_ENABLE_DISCOVERABLE = 100
    val A2DP_SINK_PROFILE = 11

    val AVRCP_CONTROLLER_PROFILE = 12

    val ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED"

    val ACTION_PLAYING_STATE_CHANGED = "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED"

    val STATE_PLAYING = 10

    val STATE_NOT_PLAYING = 11

    fun getPreviousAdapterState(intent: Intent): Int {
        return intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1)
    }

    fun getCurrentAdapterState(intent: Intent): Int {
        return intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
    }

    fun getPreviousProfileState(intent: Intent): Int {
        return intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)
    }

    fun getCurrentProfileState(intent: Intent): Int {
        return intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
    }

    fun getDevice(intent: Intent): BluetoothDevice? {
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

    /**
     * Provides a way to call the disconnect method in the BluetoothA2dpSink class that is
     * currently hidden from the public API. Avoid relying on this for production level code, since
     * hidden code in the API is subject to change.
     *
     * @param profile
     * @param device
     * @return
     */
    fun disconnect(profile: BluetoothProfile, device: BluetoothDevice): Boolean {
        return try {
            val m = profile.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
            m.invoke(profile, device)
            true
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "No disconnect method in the ${profile.javaClass.name} class, ignoring request.")
            false
        } catch (e: InvocationTargetException) {
            Log.w(TAG, "Could not execute method 'disconnect' in profile ${profile.javaClass.name}, ignoring request.", e)
            false
        } catch (e: IllegalAccessException) {
            Log.w(TAG, "Could not execute method 'disconnect' in profile ${profile.javaClass.name}, ignoring request.", e)
            false
        }

    }

    private val mAdapterStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val oldState = getPreviousAdapterState(intent)
            val newState = getCurrentAdapterState(intent)
            Log.d(TAG, "Bluetooth Adapter changing state from $oldState to $newState")
            if (newState == BluetoothAdapter.STATE_ON) {
                Log.i(TAG, "Bluetooth Adapter is ready")
                initA2DPSink()
            }
        }
    }


    private val mSinkProfileStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CONNECTION_STATE_CHANGED) {

                val oldState = getPreviousProfileState(intent)
                val newState = getCurrentProfileState(intent)
                val device = getDevice(intent)

                Log.d(TAG, "Bluetooth A2DP sink changing connection state from $oldState to $newState device $device")

                device?.let {
                    val deviceName = Objects.toString(it.name, "a device")
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> bluetoothListener?.onDeviceConnected(device)
                        BluetoothProfile.STATE_DISCONNECTED -> bluetoothListener?.onDeviceDisconected(device)
                        else -> {}
                    }
                }
            }
        }
    }

    /**
     * Handle an intent that is broadcast by the Bluetooth A2DP sink profile whenever a device
     * starts or stops playing through the A2DP sink.
     * Action is [ACTION_PLAYING_STATE_CHANGED] and
     * extras describe the old and the new playback states. You can use it to indicate that
     * there's something playing. You don't need to handle the stream playback by yourself.
     */
    private val mSinkProfilePlaybackChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_PLAYING_STATE_CHANGED) {
                val oldState = getPreviousProfileState(intent)
                val newState = getCurrentProfileState(intent)
                val device = getDevice(intent)
                Log.d(TAG, "Bluetooth A2DP sink changing playback state from $oldState to $newState device $device")
                device?.let {
                    when (newState) {
                        STATE_PLAYING -> Log.i(TAG, "Playing audio from device " + device.address)
                        STATE_NOT_PLAYING -> Log.i(TAG, "Stopped playing audio from " + device.address)
                        else -> {}
                    }
                }
            }
        }
    }


    init {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "No default Bluetooth adapter. Device likely does not support bluetooth.")
        } else {
            context?.registerReceiver(mAdapterStateChangeReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            context?.registerReceiver(mSinkProfileStateChangeReceiver, IntentFilter(ACTION_CONNECTION_STATE_CHANGED))
            context?.registerReceiver(mSinkProfilePlaybackChangeReceiver, IntentFilter(ACTION_PLAYING_STATE_CHANGED))

            if (mBluetoothAdapter.isEnabled) {
                Log.d(TAG, "Bluetooth Adapter is already enabled.")
                initA2DPSink()
            } else {
                Log.d(TAG, "Bluetooth adapter not enabled. Enabling.")
                mBluetoothAdapter.enable()
            }
        }

    }

    override fun shutdown() {
        mA2DPSinkProxy?.let { mBluetoothAdapter?.closeProfileProxy(0, it) }
        context?.apply {
            unregisterReceiver(mAdapterStateChangeReceiver)
            unregisterReceiver(mSinkProfileStateChangeReceiver)
            unregisterReceiver(mSinkProfilePlaybackChangeReceiver)
        }
        context = null
    }

    private fun setupBTProfiles() {
        val bluetoothProfileManager = BluetoothProfileManager.getInstance()
        val enabledProfiles = bluetoothProfileManager.enabledProfiles
        if (!enabledProfiles.contains(A2DP_SINK_PROFILE)) {
            Log.d(TAG, "Enabling A2dp sink mode.")
            val toEnable = arrayListOf(A2DP_SINK_PROFILE, AVRCP_CONTROLLER_PROFILE)
            val toDisable = arrayListOf(BluetoothProfile.A2DP)
            bluetoothProfileManager.enableAndDisableProfiles(toEnable, toDisable)
        } else {
            Log.d(TAG, "A2dp sink profile is enabled.")
        }
    }

    private fun initA2DPSink() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter not available or not enabled.")
            return
        }
        setupBTProfiles()
        Log.d(TAG, "Set up Bluetooth Adapter name and profile")
        mBluetoothAdapter.name = DEVICE_DISPLAYABLE_NAME
        mBluetoothAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                mA2DPSinkProxy = proxy
                enableDiscoverable()
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, A2DP_SINK_PROFILE)

    }

    override fun enableDiscoverable() {
        Log.d(TAG, "Registering for discovery.")
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_TIMEOUT_MS)
        (context as Activity).startActivityForResult(discoverableIntent, REQUEST_CODE_ENABLE_DISCOVERABLE)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_CODE_ENABLE_DISCOVERABLE) {
            Log.d(TAG, "Enable discoverable returned with result $resultCode")

            // ResultCode, as described in BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE, is either
            // RESULT_CANCELED or the number of milliseconds that the device will stay in
            // discoverable mode. In a regular Android device, the user will see a popup requesting
            // authorization, and if they cancel, RESULT_CANCELED is returned. In Android Things,
            // on the other hand, the authorization for pairing is always given without user
            // interference, so RESULT_CANCELED should never be returned.
            if (resultCode == RESULT_CANCELED) {
                Log.e(TAG, """Enable discoverable has been cancelled by the user. This should never happen in an Android Things device.""")
                return
            }
            Log.i(TAG, "Bluetooth adapter successfully set to discoverable mode. Any A2DP source can find it with the name $DEVICE_DISPLAYABLE_NAME and pair for the next $DISCOVERABLE_TIMEOUT_MS ms. Try looking for it on your phone, for example.")

            // There is nothing else required here, since Android framework automatically handles
            // A2DP Sink. Most relevant Bluetooth events, like connection/disconnection, will
            // generate corresponding broadcast intents or profile proxy events that you can
            // listen to and react appropriately.

//            speak("Bluetooth audio sink is discoverable for $DISCOVERABLE_TIMEOUT_MS milliseconds. Look for a device named $DEVICE_DISPLAYABLE_NAME")

        }
    }

    fun disconnectConnectedDevices() {
        mA2DPSinkProxy?.let {
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled) {
                return
            }
            for (device in it.connectedDevices) {
                Log.i(TAG, "Disconnecting device $device")
                disconnect(it, device)
            }
        }
    }
}