package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
public class RegActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();
	private Button mGetRegBtn;
    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;
    private TextView mRegTxt;
    private String regString = "";
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
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
            mBluetoothLeService = null;
        }
    };


    private final String REGISTER_READ_UUID ="5c3a659e-897e-45e1-b016-007107c96df5";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reg);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        mGetRegBtn = (Button) findViewById(R.id.getRegisterBtn);
        mRegTxt = (TextView) findViewById(R.id.regTxt);
        setupListeners();

        // Code to manage Service lifecycle.
    

    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

	private void setupListeners(){
        mGetRegBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readRegisters();
            }
        });
    }
    public void readRegisters() {
    	new Thread() {
            public void run() {
		        regString = "";
		        if (mBluetoothLeService != null) {
		            BluetoothGattCharacteristic readChar =
		                    mBluetoothLeService.getCharacteristic((UUID.fromString(REGISTER_READ_UUID)));
		            if (readChar != null) {
		                if (mBluetoothLeService.readBytes(readChar)) {
		                    byte[] responseByteArr = readChar.getValue();
		                    int index = 0;
		                    for (byte item : responseByteArr) {
		                        regString = regString + " REG" + Integer.toString(index) + ": 0x" + String.format("%02X", item) + "\r\n";
		//                                Log.i(TAG + regString);
		                        index++;
		                    }
		                    runOnUiThread(new Runnable() {
		                        @Override
		                        public void run() {
		                            updateRegString(regString);
		                        }
		                    });
		                }
		            }
		        }
		    }
		}.start();
    }
    public void updateRegString(String text) {
        mRegTxt.setText(text);
    }
}
