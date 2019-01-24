/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private LocalBroadcastManager mLocalBroadcastManager;
    private int mConnectionState = STATE_DISCONNECTED;
    private static final int BLE_CMD_TIMEOUT_MS = 5000;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final byte[] IMAGE_BEGIN_CMD = {0};
    private static final byte[] IMAGE_END_CMD = {1};
    private static final byte[] REQUEST_BYTE_COUNT = {2};
    private static final byte[] IMAGE_UNDO_CMD = {3};

    private static final String CHR_RAW_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String CHR_CHK_UUID = "6e400004-b5a3-f393-e0a9-e50e24dcca9e";

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_WRITE_RESPONSE_AVAILABLE =
            "com.example.bluetooth.le.ACTION_WRITE_RESPONSE_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
                intent.putExtra(ACTION_DATA_AVAILABLE, characteristic.getValue());
                mLocalBroadcastManager.sendBroadcast(intent);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onCharacteristicWrite (BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           int status) {
            final Intent intent = new Intent(ACTION_WRITE_RESPONSE_AVAILABLE);
            intent.putExtra(ACTION_WRITE_RESPONSE_AVAILABLE, status);
            mLocalBroadcastManager.sendBroadcast(intent);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {

            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        mLocalBroadcastManager.sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        mLocalBroadcastManager.sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        //mBluetoothGatt.requestMtu(100);
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    //TODO = fix
    public boolean setConnectionPriority() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        return mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
    }

    public boolean requestMTUsize(byte mtuSize) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        return mBluetoothGatt.requestMtu(mtuSize);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    private class BluetoothCommandLock {
        private Semaphore _semaphore;

        BluetoothCommandLock(int semaphoreValue) {
            _semaphore = new Semaphore(semaphoreValue);
        }

        boolean getLock() {
            try {
                _semaphore.acquire();
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage());
                return false;
            }
            return true;
        }

        boolean getLock(long timeout_ms) {
            try {
                _semaphore.tryAcquire(timeout_ms, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage());
                return false;
            }
            return true;
        }

        boolean releaseLock() {
            _semaphore.release();
            return true;
        }
    }


    //TODO = write bytes silently?
    private boolean writeBytesSilently(BluetoothGattCharacteristic gattCharacteristic, byte[] bytes) {
        final BluetoothCommandLock _mutex = new BluetoothCommandLock(0);
        final AtomicBoolean result = new AtomicBoolean(false);

        Log.d(TAG, "Writing bytes to characteristic");

        BroadcastReceiver writeBytesReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() == ACTION_WRITE_RESPONSE_AVAILABLE) {
                    Log.i(TAG, "Bytes written to characteristic successfully");
                    result.set(true);
                    /*
                    if (intent.getIntExtra(ACTION_WRITE_RESPONSE_AVAILABLE,
                            BluetoothGatt.GATT_WRITE_NOT_PERMITTED) == BluetoothGatt.GATT_SUCCESS) {
                        result.set(true);
                    }*/
                }
                _mutex.releaseLock();
            }
        };

        mLocalBroadcastManager.registerReceiver(writeBytesReceiver, makeGattWriteFilter());
        gattCharacteristic.setValue(bytes);
        writeCharacteristic(gattCharacteristic);
        if(!_mutex.getLock(BLE_CMD_TIMEOUT_MS)) {
            Log.e(TAG,"Writing bytes to characteristic timeout");
            result.set(false);
        };
        mLocalBroadcastManager.unregisterReceiver(writeBytesReceiver);
        return result.get();
    }

    private boolean writeBytes(BluetoothGattCharacteristic gattCharacteristic, byte[] bytes) {
        final BluetoothCommandLock _mutex = new BluetoothCommandLock(0);
        final AtomicBoolean result = new AtomicBoolean(false);

        Log.d(TAG, "Writing bytes to characteristic");

        BroadcastReceiver writeBytesReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() == ACTION_WRITE_RESPONSE_AVAILABLE) {
                    Log.i(TAG, "Bytes written to characteristic successfully");
                    if (intent.getIntExtra(ACTION_WRITE_RESPONSE_AVAILABLE,
                            BluetoothGatt.GATT_WRITE_NOT_PERMITTED) == BluetoothGatt.GATT_SUCCESS) {
                        result.set(true);
                    }
                }
                _mutex.releaseLock();
            }
        };

        mLocalBroadcastManager.registerReceiver(writeBytesReceiver, makeGattWriteFilter());
        gattCharacteristic.setValue(bytes);
        writeCharacteristic(gattCharacteristic);
        if(!_mutex.getLock(BLE_CMD_TIMEOUT_MS)) {
            Log.e(TAG,"Writing bytes to characteristic timeout");
            result.set(false);
        };
        mLocalBroadcastManager.unregisterReceiver(writeBytesReceiver);
        return result.get();
    }

    //TODO = Support multi-threading
    private boolean readBytes(final BluetoothGattCharacteristic gattCharacteristic) {
        final BluetoothCommandLock _mutex = new BluetoothCommandLock(0);
        final AtomicBoolean result = new AtomicBoolean(false);

        Log.d(TAG, "Reading bytes from characteristic");

        BroadcastReceiver readBytesReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() == ACTION_DATA_AVAILABLE){
                    Log.i(TAG, "Bytes read");
                    gattCharacteristic.setValue(intent.getByteArrayExtra(ACTION_DATA_AVAILABLE));
                    result.set(true);
                }
                _mutex.releaseLock();
            }
        };

        mLocalBroadcastManager.registerReceiver(readBytesReceiver, makeGattWriteFilter());
        readCharacteristic(gattCharacteristic);
        if (!_mutex.getLock(BLE_CMD_TIMEOUT_MS)) {
             result.set(false);
             Log.e(TAG, "Reading bytes from characteristic timeout");
        }
        mLocalBroadcastManager.unregisterReceiver(readBytesReceiver);
        return result.get();
    }

    /*
    private boolean writeReadImageVerify(BluetoothGattCharacteristic gattCharacteristic, int value) {

        final BluetoothCommandLock _mutex = new BluetoothCommandLock(1);
        final AtomicBoolean result = new AtomicBoolean(false);
        BroadcastReceiver mWriteReadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.v("onReceive", "onReceive");
                if (intent.getAction() == ACTION_WRITE_RESPONSE_AVAILABLE) {
                    if (intent.getIntExtra(ACTION_WRITE_RESPONSE_AVAILABLE,
                            BluetoothGatt.GATT_WRITE_NOT_PERMITTED) == BluetoothGatt.GATT_SUCCESS) {
                        result.set(true);
                    }
                } else if (intent.getAction() == ACTION_DATA_AVAILABLE){
                    byte[] value = intent.getByteArrayExtra(ACTION_DATA_AVAILABLE);
                    result.set(true);
                }
                _mutex.releaseLock();
            }
        };

        mLocalBroadcastManager.registerReceiver(mWriteReadReceiver, makeGattWriteFilter());

        _mutex.getLock();

        // Write value
       // gattCharacteristic.setValue(35, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        gattCharacteristic.setValue(REQUEST_BYTE_COUNT);
        writeCharacteristic(gattCharacteristic);

        _mutex.getLock();

        // Read value
        readCharacteristic(gattCharacteristic);

        _mutex.getLock();

        mLocalBroadcastManager.unregisterReceiver(mWriteReadReceiver);
        return result.get();
    }*/


    private boolean writeImageChunk(BluetoothGattCharacteristic CHR_RAW, BluetoothGattCharacteristic CHR_CHK, byte[] chunk) {
        return false;
    }

    private boolean imageChunkVerify(BluetoothGattCharacteristic characteristic,
                                      int lengthOfBytes) {
        boolean success = false;
        if (readBytes(characteristic)) {
            byte[] responseArray = characteristic.getValue();
            int responseNumber = ByteBuffer.wrap(responseArray).order(ByteOrder.LITTLE_ENDIAN).getInt();
            Log.d(TAG, "Size of response - " + Integer.toString(responseNumber));
            Log.d(TAG, "Exepcted size - " + Integer.toString(lengthOfBytes));
            if (lengthOfBytes == responseNumber) {
                success = true;
            }
        }
        return success;
    }

    public boolean sendImage(BluetoothGattCharacteristic gattCharacteristic, final byte[] bytes) {

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        final AtomicBoolean cancel = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(1);
        boolean errorOccurred = false;
        boolean success = false;
        InputStream inputStream = new ByteArrayInputStream(bytes);
        InputStream chunkStream = null;

        BluetoothGattCharacteristic CHR_RAW = null;
        BluetoothGattCharacteristic CHR_CHK = null;

        CHR_RAW = getCharacteristic(UUID.fromString(CHR_RAW_UUID));
        CHR_CHK = getCharacteristic(UUID.fromString(CHR_CHK_UUID));

        if (CHR_RAW == null || CHR_CHK == null) {
            Log.e(TAG, "Chars are null!");
            return false;
        }

        if (!setConnectionPriority()) {
            Log.e(TAG, "Unable to set connection priority");
            return false;
        }

        //TODO = change MTU to 100
        if (!requestMTUsize((byte)20)) {
            Log.e(TAG, "Unable to set MTU size");
            return false;
        }


        CHR_RAW.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        CHR_CHK.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

        if (writeBytes(CHR_CHK, IMAGE_BEGIN_CMD)) {
            try {
                byte[] byteChunkArray = new byte[4000];
                while (inputStream.read(byteChunkArray) != -1) {
                    Log.i(TAG, "Reading 4K byte chunk");
                    //TODO = implement retry mechanism

                    byte[] mtuChunk = new byte[20];
                    chunkStream = new ByteArrayInputStream(byteChunkArray);
                    while (chunkStream.read(mtuChunk) != -1) {
                        Log.v(TAG, "Writing 20 byte chunk...");
                        writeBytesSilently(CHR_RAW, mtuChunk);
                    }

                    if (!imageChunkVerify(CHR_CHK, byteChunkArray.length)) {
                        Log.e(TAG, "Chunk size does not match!");
                        errorOccurred = true;
                        break;
                    }

                    byteChunkArray = new byte[4000];
                }

                //TODO = bitwise OR operation
                if (errorOccurred) {
                    success = false;
                } else {
                    success = writeBytes(CHR_CHK, IMAGE_END_CMD);
                }
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage());
            }
            finally {
                try {
                    if (inputStream != null) inputStream.close();
                    if (chunkStream != null) chunkStream.close();
                } catch (Exception e) { }
            }
        }


        /*
        Log.i(TAG, "STARTING SEND");
        try {
            if (writeBytes(CHR_CHK, IMAGE_BEGIN_CMD)) {
                while ((inputStream.read(bytesToWrite)) != -1) {
                    Log.v(TAG, "Sending data...");
                    /// To attempt 3 times
                    int retryCount = 0;
                    do {
                        //TODO = need to implement undo command
                        if ((writeBytes(CHR_RAW, bytesToWrite))
                                && (writeBytes(CHR_CHK, REQUEST_BYTE_COUNT))) {
                            boolean chksum = readBytes(CHR_CHK);
                            if (chksum) {
                                byte[] responseArray = CHR_CHK.getValue();
                                int responseNumber = ByteBuffer.wrap(responseArray).getInt();
                                if (bytesToWrite.length == responseNumber) {
                                    break;
                                }
                                else {

                                }
                            }
                        }
                    } while (retryCount < 3);

                    // 0. Write 0 to CHR_CHK
                    // 1. Write 4K bytes to CHR_RAW
                    // 2. Write num of bytes to CHR_CHK [must be two byte array]
                    // 3. Read from CHR_CHK to verify that value written in 2 still matches.
                    // 4. Increment by 4K bytes and repeat steps 1-3 until no more bytes are available
                    // 5. Once no more bytes are available, write 1 to CHR_CHK

                }

                if (!cancel.get()) {
                    cancel.set(writeBytes(CHR_CHK, IMAGE_END_CMD));
                }
            }

            inputStream.close();
            result = (cancel.get() ? false : true);

        } catch (IOException ioEx) {
            Log.e(TAG, ioEx.getMessage());
        }*/

        //mLocalBroadcastManager.unregisterReceiver(mWriteImageReceiver);

        Log.i(TAG, "END SEND");
        return success;
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    private BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        List<BluetoothGattService> services = getSupportedGattServices();
        if (services == null) return null;

        for (int i = 0; i < services.size(); i++) {
            List<BluetoothGattCharacteristic> characteristics = services.get(i).getCharacteristics();
            for (int j = 0; j < characteristics.size(); j++) {
                BluetoothGattCharacteristic characteristic = characteristics.get(j);
                if (characteristic.getUuid().equals(uuid)) {
                    return characteristic;
                }
            }
        }

        return null;
    }

    private static IntentFilter makeGattWriteFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_WRITE_RESPONSE_AVAILABLE);
        intentFilter.addAction(ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


}
