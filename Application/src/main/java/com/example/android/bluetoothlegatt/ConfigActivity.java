package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import java.util.UUID;
public class ConfigActivity extends Activity {

    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    private Button mGetConfig;
    private Button mSetConfig;

    private CheckBox mChan1Check;
    private CheckBox mChan2Check;
    private CheckBox mChan3Check;
    private CheckBox mChan4Check;
    private CheckBox mChan5Check;
    private CheckBox mChan6Check;
    private CheckBox mChan7Check;
    private CheckBox mChan8Check;
    private EditText mImpedanceInterval;
    private TextView mImpedanceName;
    private Switch mImpedanceSwitch;
    private Spinner mChanGainSpin;
    private Spinner mChanRateSpin;
    private Spinner mRateStreamSpin;
    private int mRate = 250;
    private int mRateStream = 250;
    private int mChannelGain = 1;
    private byte mChanMask = 0;
    private int mImpedanceIntervalVal = 0;
    private boolean mImpedance = false;
    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;
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
    private final String CONFIG_UUID ="5c3a659e-897e-45e1-b016-007107c96df4";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        mGetConfig = (Button) findViewById(R.id.buttonGetConfig);
        mSetConfig = (Button) findViewById(R.id.buttonSetConfig);

        mChan1Check = (CheckBox) findViewById(R.id.checkBoxChan1);
        mChan2Check = (CheckBox) findViewById(R.id.checkBoxChan2);
        mChan3Check = (CheckBox) findViewById(R.id.checkBoxChan3);
        mChan4Check = (CheckBox) findViewById(R.id.checkBoxChan4);
        mChan5Check = (CheckBox) findViewById(R.id.checkBoxChan5);
        mChan6Check = (CheckBox) findViewById(R.id.checkBoxChan6);
        mChan7Check = (CheckBox) findViewById(R.id.checkBoxChan7);
        mChan8Check = (CheckBox) findViewById(R.id.checkBoxChan8);

        mChanGainSpin = (Spinner) findViewById(R.id.spinnerGain1);
        mChanRateSpin = (Spinner) findViewById(R.id.spinnerRate);
        mRateStreamSpin = (Spinner) findViewById(R.id.spinnerRateStream);
        mImpedanceSwitch = (Switch) findViewById(R.id.switchImpedance);

        mImpedanceInterval = (EditText) findViewById(R.id.impedanceIntervalText);
        mImpedanceName = (TextView) findViewById(R.id.textViewImpedance);
        setupListeners();

        // Code to manage Service lifecycle.
//        getConfig();

    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }
    private void setConfig() {
        new Thread() {
            public void run() {
                BluetoothGattCharacteristic characteristic;
                do {
                    characteristic = mBluetoothLeService.getCharacteristic(UUID.fromString(CONFIG_UUID));
                    if (characteristic != null) {
                        byte[] cmd = new byte[8];
                        cmd[0] = (byte) mChannelGain;
                        cmd[1] = mChanMask;
                        cmd[2] = (byte) mImpedanceIntervalVal;
                        if (mImpedance == true){
                            cmd[3] = (byte) 0xff;
                        } else {
                            cmd[3] = (byte) 0;
                        }

                        cmd[4] = (byte) (mRate >> 8);
                        cmd[5] = (byte) (mRate & 0xff);
                        cmd[6] = (byte) (mRateStream >> 8);
                        cmd[7] = (byte) (mRateStream & 0xff);
                        mBluetoothLeService.writeBytes(characteristic, cmd);
                    }
                }while(characteristic == null);
            }
        }.start();
    }
    private void getConfig() {
        new Thread() {
            public void run() {
                while (mBluetoothLeService == null) ;
                BluetoothGattCharacteristic readChar =
                        mBluetoothLeService.getCharacteristic((UUID.fromString(CONFIG_UUID)));
                if (readChar != null) {
                    if (mBluetoothLeService.readBytes(readChar)) {
                        byte[] cmd = readChar.getValue();
                        // update all values
                        mChannelGain = cmd[0] & 0xff;
                        mChanMask = (byte) (cmd[1] & 0xff);
                        mImpedanceIntervalVal = cmd[2] & 0xff;
                        if (cmd[3] != 0) {
                            mImpedance = true;
                        } else {
                            mImpedance = false;
                        }
                        mRate = (cmd[4] << 8);
                        mRate = mRate + (cmd[5] & 0xff);
                        mRateStream = (cmd[6] << 8);
                        mRateStream = mRateStream + (cmd[7] & 0xff);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateConfig();
                            }
                        });
                    }
                }
            }
        }.start();
    }
    private int getIndex(Spinner spinner, String myString){
        for (int i=0;i<spinner.getCount();i++){
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(myString)){
                return i;
            }
        }

        return 0;
    }
    private void updateConfig() {

        if (mImpedance == false) {
            mImpedanceSwitch.setChecked(false);
        } else {
            mImpedanceSwitch.setChecked(true);
        }
        if ((mChanMask & 0x01) == 0x01) {
            mChan1Check.setChecked(true);
        } else {
            mChan1Check.setChecked(false);
        }
        if ((mChanMask & 0x02) == 0x02) {
            mChan2Check.setChecked(true);
        }else {
            mChan2Check.setChecked(false);
        }
        if ((mChanMask & 0x04) == 0x04) {
            mChan3Check.setChecked(true);
        }else {
            mChan3Check.setChecked(false);
        }
        if ((mChanMask & 0x08) == 0x08) {
            mChan4Check.setChecked(true);
        }else {
            mChan4Check.setChecked(false);
        }
        if ((mChanMask & 0x10) == 0x10) {
            mChan5Check.setChecked(true);
        }else {
            mChan5Check.setChecked(false);
        }
        if ((mChanMask & 0x20) == 0x20) {
            mChan6Check.setChecked(true);
        }else {
            mChan6Check.setChecked(false);
        }
        if ((mChanMask & 0x40) == 0x40) {
            mChan7Check.setChecked(true);
        }else {
            mChan7Check.setChecked(false);
        }
        if ((mChanMask & 0x80) == 0x80) {
            mChan8Check.setChecked(true);
        }else {
            mChan8Check.setChecked(false);
        }
        mChanGainSpin.setSelection(getIndex(mChanGainSpin, Integer.toString(mChannelGain)));
        mImpedanceInterval.setText(Integer.toString(mImpedanceIntervalVal));
        mChanRateSpin.setSelection(getIndex(mChanRateSpin, Integer.toString(mRate)));
        mRateStreamSpin.setSelection(getIndex(mRateStreamSpin, Integer.toString((mRateStream))));

    }
    private void setupListeners(){
        mGetConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getConfig();
                mSetConfig.setEnabled(true);
            }
        });

        mSetConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setConfig();
            }
        });

        mChan1Check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked == true){
                    mChanMask |= (1 << 0);
                } else {
                    mChanMask &= ~(1 << 0);
                }
            }
        });
        mChan2Check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked == true){
                    mChanMask |= (1 << 1);
                } else {
                    mChanMask &= ~(1 << 1);
                }
            }
        });
        mChan3Check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked == true){
                    mChanMask |= (1 << 2);
                } else {
                    mChanMask &= ~(1 << 2);
                }
            }
        });
        mChan4Check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked == true){
                    mChanMask |= (1 << 3);
                } else {
                    mChanMask &= ~(1 << 3);
                }
            }
        });
        mChan5Check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked == true){
                    mChanMask |= (1 << 4);
                } else {
                    mChanMask &= ~(1 << 4);
                }
            }
        });
        mChan6Check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked == true){
                    mChanMask |= (1 << 5);
                } else {
                    mChanMask &= ~(1 << 5);
                }
            }
        });
        mChan7Check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked == true){
                    mChanMask |= (1 << 6);
                } else {
                    mChanMask &= ~(1 << 6);
                }
            }
        });
        mChan8Check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked == true){
                    mChanMask |= (1 << 7);
                } else {
                    mChanMask &= ~(1 << 7);
                }
            }
        });

        mChanGainSpin.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String gainStr = parent.getItemAtPosition(position).toString();
                mChannelGain = Integer.valueOf(gainStr);
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }

        });

        mChanRateSpin.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String rateStr = parent.getItemAtPosition(position).toString();
                mRate = Integer.valueOf(rateStr);
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }

        });
        mRateStreamSpin.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String rateStr = parent.getItemAtPosition(position).toString();
                mRateStream = Integer.valueOf(rateStr);
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }

        });

        mImpedanceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == true) {
                    mImpedance = true;
                    mImpedanceInterval.setVisibility(View.VISIBLE);
                    mImpedanceName.setVisibility(View.VISIBLE);
                } else {
                    mImpedance = false;
                    mImpedanceInterval.setVisibility(View.GONE);
                    mImpedanceName.setVisibility(View.GONE);
                }
            }
        });
        mImpedanceInterval.addTextChangedListener( new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence c, int start, int before, int count) {
                mImpedanceIntervalVal = Integer.valueOf(c.toString());
            }

            public void beforeTextChanged(CharSequence c, int start, int count, int after) {
                // this space intentionally left blank
            }

            public void afterTextChanged(Editable c) {
                // this one too
            }
        });
    }
}
