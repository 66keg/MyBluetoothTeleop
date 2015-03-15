package com.example.keiji_muro.mybluetoothteleop;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity implements SensorEventListener, View.OnClickListener, View.OnTouchListener {

    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_SECURE2 = 4;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_CONNECT_DEVICE = 10;

    // Layout Views
    //private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
    private TextView tvTxL, tvTxR;//, tvRxL, tvRxR;
//    private ImageView iv;
    private Button btn;
    private TextView tv;
    boolean isTouch = false;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services

    private BluetoothChatService mChatService = null;
    private BluetoothChatService mChatService2 = null;

    // add
    private Button btnL, btnR, btnStart;
    private int requested_btn_number = 0;
    private BluetoothChatService[] mChatServiceArr = new BluetoothChatService[] {null, null};
    final int RIGHT_MODULE_ID = 1;
    final int LEFT_MODULE_ID = 0;

    // for sensor
    private SensorManager myManager = null;
    float[] mags = new float[3];
    float[] accels = new float[3];
    float[] mags_filtered = new float[3];
    float[] accels_filtered = new float[3];
    float[] pre_mags_filtered = new float[3];
    float[] pre_accels_filtered = new float[3];
    float[] RotationMat = new float[9];
    float[] InclinationMat = new float[9];
    float[] attitude = new float[3];
    final static double RAD2DEG = 180 / Math.PI;
    final float PARAM_LPF = 0.3F;
    private double azimuth = 0.0, pitch = 0.0, roll = 0.0;
    private boolean broadcast_flag = false;

    // for control
    private long current_time = 0, pre_time = 0;
    private final int CONTROL_INTERVAL = 10;
    private final int PWM_MAX = 99;
    private final float dd = 1;

    private double vel = 0.0, avel = 0.0;
    private double pitch_offset = 0.0, roll_offset = 0.0;
    final double CONTROL_PARAM_LINEAR = 8.0;
    final double CONTROL_PARAM_TURN = 16.0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");
        if(D) Log.e(TAG, "mChatServiceArrSize:" + mChatServiceArr.length);

        setContentView(R.layout.activity_main);

        btnL = (Button) findViewById(R.id.buttonL);
        btnL.setOnClickListener(this);
        btnR = (Button) findViewById(R.id.buttonR);
        btnR.setOnClickListener(this);
        btnStart = (Button) findViewById(R.id.buttonStart);
        btnStart.setOnClickListener(this);

        //tvRxL = (TextView) findViewById(R.id.LeftRx);
        //tvRxR = (TextView) findViewById(R.id.RightRx);
        tvTxL = (TextView) findViewById(R.id.LeftTx);
        tvTxR = (TextView) findViewById(R.id.RightTx);
//        iv = (ImageView) findViewById(R.id.imageView);

        btn = (Button)findViewById(R.id.button_id);
        btn.setOnTouchListener(this);

        tv = (TextView) findViewById(R.id.textView);
        String str = "Azimuth: 0.0\n"
                + "Pitch: 0.0\n"
                + "Roll: 0.0\n\n"
                + "vel:  0.0\n"
                + "avel: 0.0";
        tv.setText(str);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // SensorManager
        myManager = (SensorManager)getSystemService(SENSOR_SERVICE);
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }

        // SensorManager
        myManager.registerListener(this,
                myManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
        myManager.registerListener(this,
                myManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_GAME);
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        //mConversationView = (ListView) findViewById(R.id.in);
        //mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);
        mChatService2 = new BluetoothChatService(this, mHandler);

        for(int i = 0; i < mChatServiceArr.length; i++)
        {
            mChatServiceArr[i] = new BluetoothChatService(this, mHandler);
        }

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");

        // SensorManager
        myManager.unregisterListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if (mChatService2 != null) mChatService2.stop();

        for(int i = 0; i < mChatServiceArr.length; i++)
        {
            if (mChatServiceArr[i] != null) mChatServiceArr[i].stop();
        }

        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        /*
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
        */

        for(int i = 0; i < mChatServiceArr.length; i++)
        {
            if(mChatServiceArr[i] != null) {
                // Check that we're actually connected before trying anything
                if (mChatServiceArr[i].getState() != BluetoothChatService.STATE_CONNECTED) {
                    Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
                    // return;
                }
                // Check that there's actually something to send
                else if (message.length() > 0) {
                    // Get the message bytes and tell the BluetoothChatService to write
                    byte[] send = (message+i).getBytes();
                    mChatServiceArr[i].write(send);

                    // Reset out string buffer to zero and clear the edit text field
                    mOutStringBuffer.setLength(0);
                    mOutEditText.setText(mOutStringBuffer);
                }
            }
        }
    }

    private void sendMessage(String message, int id) {
        if(mChatServiceArr[id] != null) {
            // Check that we're actually connected before trying anything
            if (mChatServiceArr[id].getState() != BluetoothChatService.STATE_CONNECTED) {
                //Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
                // return;
            }
            // Check that there's actually something to send
            else if (message.length() > 0) {
                // Get the message bytes and tell the BluetoothChatService to write
                byte[] send = message.getBytes();
                mChatServiceArr[id].write(send);

                // Reset out string buffer to zero and clear the edit text field
                mOutStringBuffer.setLength(0);
                mOutEditText.setText(mOutStringBuffer);
            }
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
            new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    // If the action is a key-up event on the return key, send the message
                    if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                        String message = view.getText().toString();
                        sendMessage(message);
                    }
                    if(D) Log.i(TAG, "END onEditorAction");
                    return true;
                }
            };

    private final void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        //actionBar.setSubtitle(resId);
    }

    private final void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getActionBar();
        //actionBar.setSubtitle(subTitle);
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_SECURE2:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice2(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true, requested_btn_number);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }
    private void connectDevice2(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService2.connect(device, secure);
    }
    private void connectDevice(Intent data, boolean secure, int _requested_btn_number) {
        // Get the device MAC address
        String address = data.getExtras() .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        Log.v("connectDevice", "address: " + address);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatServiceArr[_requested_btn_number-1].connect(device, secure);
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
            case R.id.secure_connect_scan:
                // Launch the DeviceListActivity to see devices and do scan
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            case R.id.insecure_connect_scan:
                // Launch the DeviceListActivity to see devices and do scan
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            case R.id.discoverable:
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
        }
        return false;
    }
    */

    @Override
    public void onClick(View v) {
        Intent serverIntent = null;
        switch (v.getId()) {
            case R.id.buttonL:
                requested_btn_number = 1;
                /*
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                */
                // Set the device MAC address
                String addressL = "00:1B:DC:0F:60:44";
                // Get the BluetoothDevice object
                BluetoothDevice deviceL = mBluetoothAdapter.getRemoteDevice(addressL);
                // Attempt to connect to the device
                mChatServiceArr[requested_btn_number-1].connect(deviceL, true);
                Log.d(TAG, "ButtonL clicked");
                break;
            case R.id.buttonR:
                requested_btn_number = 2;
                /*
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                */
                // Set the device MAC address
                String addressR = "00:1B:DC:06:D2:E0";
                // Get the BluetoothDevice object
                BluetoothDevice deviceR = mBluetoothAdapter.getRemoteDevice(addressR);
                // Attempt to connect to the device
                mChatServiceArr[requested_btn_number-1].connect(deviceR, true);
                Log.d(TAG, "ButtonR clicked");
                break;
            case R.id.buttonStart:
                if(!broadcast_flag)
                {
                    broadcast_flag = true;
                    btnStart.setText("送信中");
                    Log.v("onClick", "Start");
                }
                else
                {
                    broadcast_flag = false;
                    btnStart.setText("待機中");
                    Log.v("onClick", "Stop");
                }
                break;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            mags = event.values;

            for(int i = 0; i < 3; i++)
            {
                pre_mags_filtered[i] = mags_filtered[i];
                mags_filtered[i] = (1.0F - PARAM_LPF) * pre_mags_filtered[i] + PARAM_LPF * mags[i];
            }

        }
        if (type == Sensor.TYPE_ACCELEROMETER) {
            accels = event.values;

            for(int i = 0; i < 3; i++)
            {
                pre_accels_filtered[i] = accels_filtered[i];
                accels_filtered[i] = (1.0F - PARAM_LPF) * pre_accels_filtered[i] + PARAM_LPF * accels[i];
            }
        }

        if (type == Sensor.TYPE_MAGNETIC_FIELD || type == Sensor.TYPE_ACCELEROMETER) {

            SensorManager.getRotationMatrix(RotationMat, InclinationMat, accels_filtered, mags_filtered);
            SensorManager.getOrientation(RotationMat, attitude);

            azimuth = attitude[0] * RAD2DEG;
            pitch   = attitude[1] * RAD2DEG;
            roll    = attitude[2] * RAD2DEG;

            String str = "Azimuth: " + (float)azimuth + "\n"
                    + "Pitch: " + (float)pitch + "\n"
                    + "Roll: " + (float)roll + "\n\n";

            if(isTouch)
            {
                double pitch_ = pitch - pitch_offset;
                double roll_ = roll - roll_offset;

                // 3度以内の角度変化は無視する
                if(Math.abs(pitch_) < 3) pitch_ = 0;
                if(Math.abs(roll_) < 3) roll_ = 0;

                // 並進速度と回転速度計算
                vel = PWM_MAX*Math.tanh(pitch_/CONTROL_PARAM_LINEAR);
                avel = -PWM_MAX*Math.tanh(roll_/CONTROL_PARAM_TURN);
            }
            else
            {
                vel = 0;
                avel = 0;
            }

            str += "vel:  " + (float)vel + "\n"
                    + "avel: " + (float)avel;

            tv.setText(str);

            current_time = System.currentTimeMillis();

            long time_interval = current_time - pre_time;

            int vR = (int)(vel + avel);
            int vL = -(int)(vel - avel);

            if(vR > PWM_MAX && Math.abs(vL) <= PWM_MAX)
            {
                int tmp = vR - PWM_MAX;
                vR -= tmp;
                vL -= tmp;
            }
            else if(vR < -PWM_MAX && Math.abs(vL) <= PWM_MAX)
            {
                int tmp = -PWM_MAX - vR;
                vR += tmp;
                vL += tmp;
            }
            if(vL > PWM_MAX && Math.abs(vR) <= PWM_MAX)
            {
                int tmp = vL - PWM_MAX;
                vL -= tmp;
                vR -= tmp;
            }
            else if(vL < -PWM_MAX && Math.abs(vR) <= PWM_MAX)
            {
                int tmp = -PWM_MAX - vL;
                vL += tmp;
                vR += tmp;
            }

            String txR, txL;
            if(vR > 0)
            {
                if(vR > PWM_MAX) vR = PWM_MAX;
                txR = "f" + (Math.abs(vR) % 100);
            }
            else if(vR < 0)
            {
                if(vR < -PWM_MAX) vR = -PWM_MAX;
                txR = "b" + (Math.abs(vR) % 100);
            }
            else
            {
                txR = "s00";
            }

            if(vL > 0)
            {
                if(vL > PWM_MAX) vL = PWM_MAX;
                txL = "f" + (Math.abs(vL) % 100);
            }
            else if(vL < 0)
            {
                if(vL < -PWM_MAX) vL = -PWM_MAX;
                txL = "b" + (Math.abs(vL) % 100);
            }
            else
            {
                txL = "s00";
            }

            tvTxR.setText("RightTx: " + txR);
            tvTxL.setText("LeftTx: "  + txL);

            if(broadcast_flag && time_interval > CONTROL_INTERVAL) {

                pre_time = current_time;

                sendMessage(txR, RIGHT_MODULE_ID);
                sendMessage(txL, LEFT_MODULE_ID);

//                iv.setRotation(-(float)azimuth);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        switch(v.getId()) {
            case R.id.button_id:
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    // 指がタッチした時の処理を記述
//                    Log.v("OnTouch", "Touch Down");
                    pitch_offset = pitch;
                    roll_offset = roll;
                    isTouch = true;
                    btn.setText("動作中");
                    Log.v("onTouch", "Button ON");
                }
                else if(event.getAction() == MotionEvent.ACTION_UP) {
                    // タッチした指が離れた時の処理を記述
//                    Log.v("OnTouch", "Touch Up");
                    isTouch = false;
                    btn.setText("停止中");
                    Log.v("onTouch", "Button OFF");
                }
        }
        return false;
    }
}