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

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.apache.commons.lang3.ObjectUtils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.lang.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    Long ts_curr_Long = 0L;
    Long ts_prev_Long = 0L;
    Long samp_per_sec_av = 0L;
    int reads = 0;
    int sample_number_Int = 0;
    int sample_number_prev = 0;
    private TextView mConnectionState;
    private TextView mImpedanceTxt;
    private Button mStreamingBtn;
    private Button mConfigBtn;
    private Button mRegBtn;
    private Button mBtnImpedance;
    private TextView mVisuChan;
    private Spinner mChanSpin;
    private GraphView mGraphView;
    private String mDeviceName;
    private String mDeviceAddress;
    private int mImpedanceChan = 1;
    private boolean mIsImpedance = false;
    private volatile boolean mDisplayGraph = false;
    BlockingQueue<byte[]> dataQueue = new LinkedBlockingQueue<>(10000);
    public BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private LineGraphSeries mLineGraphSeries = new LineGraphSeries<>();
    private GraphDialog mGraphDialog;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private LocalBroadcastManager mLocalBroadcastManager;
    private StreamReadRunnable mReadStreamRunnable = null;

    // private Buffer dataBuff = new CircularFifoBuffer(500);

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    private final String WRITE_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    private final String STREAMING_READ_UUID = "5c3a659e-897e-45e1-b016-007107c96df6";
    private final String STREAMING_START_UUID ="5c3a659e-897e-45e1-b016-007107c96df7";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "onServiceDisconnected");
            mBluetoothLeService = null;
            if (mReadStreamRunnable != null) {
                mReadStreamRunnable.cancelStream();
            }
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                mStreamingBtn.setVisibility(View.VISIBLE);
                mConfigBtn.setVisibility(View.VISIBLE);
                mRegBtn.setVisibility(View.VISIBLE);
                mBtnImpedance.setVisibility(View.VISIBLE);
                mChanSpin.setVisibility(View.VISIBLE);
                mImpedanceTxt.setVisibility(View.VISIBLE);
                mVisuChan.setVisibility(View.VISIBLE);

                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            }
        }
    };
    private int mChannelGain  = 0;
    private byte mChanMask = 0;
    private int mImpedanceIntervalVal = 0;
    private boolean mImpedanceOn = false;
    private int mRate = 0;
    private final String CONFIG_UUID ="5c3a659e-897e-45e1-b016-007107c96df4";

    private boolean getConfig() {

        if (mBluetoothLeService != null) {
            BluetoothGattCharacteristic readChar =
                    mBluetoothLeService.getCharacteristic((UUID.fromString(CONFIG_UUID)));
            if (readChar != null) {
                if (mBluetoothLeService.readBytes(readChar)) {
                    byte[] cmd = readChar.getValue();
                    if (cmd.length > 0) {
                        // update all values
                        mChannelGain = cmd[0] & 0xff;
                        mChanMask = (byte) (cmd[1] & 0xff);
                        mImpedanceIntervalVal = cmd[2] & 0xff;
                        if (cmd[3] != 0) {
                            mImpedanceOn = true;
                        } else {
                            mImpedanceOn = false;
                        }
                        mRate = (cmd[4] << 8);
                        mRate = mRate + (cmd[5] & 0xff);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void sendstart() {
        new Thread() {
            public void run() {
                BluetoothGattCharacteristic characteristic;
                while(getConfig() == false);
                do {
                    characteristic = mBluetoothLeService.getCharacteristic(UUID.fromString(STREAMING_START_UUID));
                    if (characteristic != null) {
                        byte[] cmd = new byte[1];
                        cmd[0] = (byte)0x81;
                        mBluetoothLeService.writeBytes(characteristic, cmd);
                        mBluetoothLeService.writeBytes(characteristic, cmd);
                    }
                }while(characteristic == null);
            }
        }.start();
    }
    private void sendImpedanceStart() {
        new Thread() {
            public void run() {
                BluetoothGattCharacteristic characteristic;
                getConfig();
                do {
                    characteristic = mBluetoothLeService.getCharacteristic(UUID.fromString(STREAMING_START_UUID));
                    if (characteristic != null) {
                        byte[] cmd = new byte[1];
                        cmd[0] = (byte) (0xc0 + ((byte) mImpedanceChan));
                        mBluetoothLeService.writeBytes(characteristic, cmd);
                    }
                }while(characteristic == null);
            }
        }.start();
    }

    private void sendstop() {
        new Thread() {
            public void run() {
                BluetoothGattCharacteristic characteristic;

                do {
                    characteristic = mBluetoothLeService.getCharacteristic(UUID.fromString(STREAMING_START_UUID));
                    if (characteristic != null) {
                        byte[] cmd = new byte[1];
                        cmd[0] = (byte)0x80;
                            mBluetoothLeService.writeBytes(characteristic, cmd);
                            // Don't know why the first one isn't seen...
                            mBluetoothLeService.writeBytes(characteristic, cmd);
                        }
                }while(characteristic == null);
            }
        }.start();
    }
    private int channelReconstruct(byte byte2, byte byte1, byte byte0){
        int channel = 0;

        if (byte2 <0 ){
            channel = (0xff<<24) + ((byte2&0xff)<<16) + ((byte1&0xff)<<8) + ((byte0&0xff));
        } else {
            channel = ((byte2&0xff)<<16) + ((byte1&0xff)<<8) + ((byte0&0xff));
        }
        return channel;
    }
    private void dataHandlerThread() {
        new Thread() {
            public void run() {

                do {
                    try {
                        byte[] data = dataQueue.take();
                        // read first byte to determine sample size
                        int mask = data[0];
                        int numberChan = Integer.bitCount(mask);
//                        Log.i(TAG, "Number of channels:" + Long.toString(numberChan));
                        // correct for loop to max number of packets
                        int offset = 1;
                        // get each sample
                        int samplePerPacket = data.length/(3*numberChan + 4); // 4 is timestamp
//                        Log.i(TAG, "Number of Samples:" + Long.toString(samplePerPacket));
//                        Log.i(TAG, "packet size:" + Long.toString(data.length));
                        for (int i = 0; i < samplePerPacket; i++) {
                            ByteBuffer bufferTimestamp = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN);
                            int timestamp = bufferTimestamp.getInt();
                            offset += 4;
                            // For now we don't use Sample number as not useful and takes bandwidth for nothing...
//                            ByteBuffer bufferSample = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN);
//                            int sampleNum = bufferSample.getInt();
//                            offset += 4;
                            List<Integer> channels = new ArrayList<>();
                            for (int j = 0; j < numberChan; j++) {
                                int channel = channelReconstruct(data[offset], data[offset + 1], data[offset + 2]);
//                                Log.i(TAG, "Channel:" + Integer.toString(channel));
//                                Log.i(TAG, "timestamp:" + Integer.toString(timestamp));
                                channels.add(channel);
                                offset += 3;
                            }
                            eegSample sample = new eegSample(timestamp, 0, mask, channels);
                            addPlotSample(sample, mChannelGain,mImpedanceChan);
                            if (mIsImpedance == true) {
                                // process Impedance
                                CalcImpedance(sample);
                            }
                        }
                    } catch (InterruptedException e) {
                        //Do nothing for now
                    }

                }while(mReadStreamRunnable !=null);
            }
        }.start();
    }
    // variables for impedance calculations
    private static final int AMP_AVERAGE = 50;
    int mInitialTimestamp = 0;
    boolean mRestartPeriod = true;
    int mChanMin = 0;
    int mChanMax = 0;
    double mAmplitudeV = 0;
    int sample_number = 0;
    double mImpedance = 0;
    private void CalcImpedance(eegSample sample) {
        // first sample -> initial_t = sample.timestamp
        if (mRestartPeriod == true) {
            mInitialTimestamp = sample.getTimestamp();
            mChanMin = mChanMax = sample.getSingleChan();
            mRestartPeriod = false;
        }
        mChanMin = Math.min(mChanMin, sample.getSingleChan());
        mChanMax = Math.max(mChanMax, sample.getSingleChan());
        if (sample.getTimeDiff(mInitialTimestamp) >= 3205) { // 3205 is 31.2Hz in ms*100
            int amp = mChanMax-mChanMin;
            // V = code * Vref / (gain * 2^23-1)
//            Log.e(TAG, "amp = " + Integer.toString(amp));
            mAmplitudeV = mAmplitudeV + (amp * 4.5 / (mChannelGain * 8388607.0)); // Vref = 4.5V Assume gain of 1 for now. Will be fixed later on
            mRestartPeriod = true;
            sample_number++;
            if (sample_number >= AMP_AVERAGE) {
                // calculate impedance (V/I - 4.4k)
                mAmplitudeV = mAmplitudeV/AMP_AVERAGE;
                mImpedance = (mAmplitudeV/0.000000048) - 4400.0; //4400 is internal impdance
                mAmplitudeV = 0;
                // update impedance value on UI
                updateImpedance(mImpedance);
                sample_number = 0;
            }
        }

    }
    private void addPlotSample(final eegSample sample, int gain, int chanNumber) {
        if ((mGraphDialog != null) && (mGraphDialog.isShowing())) {
           mGraphDialog.sampleAvailable(sample, gain, chanNumber);
       }
    }
    // Simple thread to read EEG samples and add it to buffer
    private void readPacket() {
        if (mBluetoothLeService != null) {
            BluetoothGattCharacteristic readChar =
                    mBluetoothLeService.getCharacteristic((UUID.fromString(STREAMING_READ_UUID)));
            if (readChar != null) {
                if (mBluetoothLeService.readBytes(readChar)) {
//                    Log.d(TAG, "Bytes ready");
                    byte[] responseByteArr = readChar.getValue();
                    if (responseByteArr.length > 1) {
                        // Add data to circular buffer and do no more processing...
                        dataQueue.add(responseByteArr);
                    }
                } else {
                    Log.e(TAG, "Failed to read bytes");
                }
                Log.i(TAG, "Streaming stopped");
            } else {
                Log.e(TAG, "No matching characteristic found");
            }
        } else {
            Log.e(TAG, "Bluetooth service is null, exiting stream");
        }
    }

    private void readBLESingle() {
        new Thread() {
            public void run() {
                readPacket();
            }
        }.start();
    }
    private void readBLEStream() {

        if (mReadStreamRunnable == null) {
            sendstart();
            Log.d(TAG, "Stream start");
            mStreamingBtn.setText(R.string.menu_stop);
            mReadStreamRunnable = new StreamReadRunnable(UUID.fromString(STREAMING_READ_UUID));
            mReadStreamRunnable.run();
            dataHandlerThread();
        } else {
            Log.d(TAG, "Stream stop");
            mReadStreamRunnable.cancelStream();
            mReadStreamRunnable = null;
            mStreamingBtn.setText(R.string.stream_read_start);
            samp_per_sec_av = 0L;
            sample_number_prev = 0;
            sendstop();
        }
    }
    private void readBLEImpedance() {

        if (mReadStreamRunnable == null) {
            mIsImpedance = true;
            sendImpedanceStart();
            Log.d(TAG, "Impdance start");
            mBtnImpedance.setText(R.string.menu_stop);
            mReadStreamRunnable = new StreamReadRunnable(UUID.fromString(STREAMING_READ_UUID));
            mReadStreamRunnable.run();
            dataHandlerThread();
        } else {
            mIsImpedance = false;
            Log.d(TAG, "Stream stop");
            mReadStreamRunnable.cancelStream();
            mReadStreamRunnable = null;
            mBtnImpedance.setText(R.string.stream_imp_start);
            samp_per_sec_av = 0L;
            sample_number_prev = 0;
            sendstop();
        }
    }
    private class StreamReadRunnable implements Runnable {

        private AtomicBoolean mStopStreaming = new AtomicBoolean(false);
        private UUID mReadCharUUID;

        StreamReadRunnable(UUID readCharUUID) {
            mReadCharUUID = readCharUUID;
        }

        void cancelStream() {
            mStopStreaming.set(true);
        }

        @Override
        public void run() {
            new Thread() {
                public void run() {
                    while(!mStopStreaming.get()){
                        readPacket();
                    }
                }
            }.start();
        }
    }


    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if (characteristic.getUuid().toString().contains(WRITE_UUID)) {
                            Log.i(TAG, "Found the correct characteristic");
                        }
                        /*
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }*/
                        return true;
                    }
                    return false;
                }
            };

    private void clearUI() {
        mStreamingBtn.setVisibility(View.GONE);
        mConfigBtn.setVisibility(View.GONE);
        mRegBtn.setVisibility(View.GONE);
        mBtnImpedance.setVisibility(View.GONE);
        mChanSpin.setVisibility(View.GONE);
        mImpedanceTxt.setVisibility(View.GONE);
        mVisuChan.setVisibility(View.GONE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            //setContentView(R.layout.gatt_services_characteristics);
            showGraph();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            //setContentView(R.layout.gatt_services_characteristics);
            hideGraph();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        setContentView(R.layout.gatt_services_characteristics);
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mGraphDialog = new GraphDialog(this);

        int orientation = this.getResources().getConfiguration().orientation;
        /*
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            //takeDownGraph();
        } else {
            showGraph();
        }*/
        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mStreamingBtn = (Button) findViewById(R.id.startStrmBtn);
        mConfigBtn = (Button) findViewById(R.id.ConfigBtn);
        mRegBtn = (Button) findViewById(R.id.registerBtn);
        mBtnImpedance = (Button) findViewById(R.id.btnImpedance);
        mChanSpin = (Spinner) findViewById(R.id.chanSelectSpin);
        mImpedanceTxt = (TextView) findViewById(R.id.txtImpedance);
        mVisuChan = (TextView) findViewById(R.id.textVisiChan);

        setupUICallbacks();
        setupListeners();
        mBtnImpedance.setText(R.string.stream_imp_start);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        startService(gattServiceIntent);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void showGraph() {
        if (mGraphDialog == null) {
            mGraphDialog = new GraphDialog(this);
        }
        mGraphDialog.show();
    }

    private void hideGraph() {
        if ((mGraphDialog != null) && mGraphDialog.isShowing()) {
            mGraphDialog.resetData();
            mGraphDialog.dismiss();
        }
    }

    private void setChildViewStatus(View view, boolean visible) {
        if (view instanceof ViewGroup) {
            if (visible) {
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setChildViewStatus(child, visible);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLocalBroadcastManager.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLocalBroadcastManager.unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        mBluetoothLeService.disconnect();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupUICallbacks() {

    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }
    private void updateImpedance(final double Impedance) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mImpedanceTxt.setText(Integer.toString((int)Math.round(Impedance/1000)) + "k");
            }
        });
    }


    private void setupListeners(){
        mStreamingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                readBLEStream();
            }
        });
        mRegBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openRegActivity();
            }
        });
        mBtnImpedance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO: Change channel before!
                readBLEImpedance();
            }
        });
        mConfigBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openConfigActivity();
            }
        });
        mChanSpin.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String chanStr = parent.getItemAtPosition(position).toString();
                mImpedanceChan = Integer.valueOf(chanStr);
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }

        });
    }

    public void openRegActivity(){
        Intent intent = new Intent(this, RegActivity.class);
        startActivity(intent);
        // readRegisters();
    }
    public void openConfigActivity() {
        Intent intent = new Intent(this, ConfigActivity.class);
        startActivity(intent);
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}