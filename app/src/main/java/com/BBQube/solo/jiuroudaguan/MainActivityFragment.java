package com.BBQube.solo.jiuroudaguan;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.view.WindowManager;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.Legend.LegendForm;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.YAxis.AxisDependency;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.mikephil.charting.highlight.Highlight;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * A multi-thread-safe produce-consumer byte array.
 * Only allows one producer and one consumer.
 */

class ByteQueue {
    public ByteQueue(int size) {
        mBuffer = new byte[size];
    }

    public int getBytesAvailable() {
        synchronized(this) {
            return mStoredBytes;
        }
    }

    public int read(byte[] buffer, int offset, int length)
            throws InterruptedException {
        if (length + offset > buffer.length) {
            throw
                    new IllegalArgumentException("length + offset > buffer.length");
        }
        if (length < 0) {
            throw
                    new IllegalArgumentException("length < 0");

        }
        if (length == 0) {
            return 0;
        }
        synchronized(this) {
            while (mStoredBytes == 0) {
                wait();
            }
            int totalRead = 0;
            int bufferLength = mBuffer.length;
            boolean wasFull = bufferLength == mStoredBytes;
            while (length > 0 && mStoredBytes > 0) {
                int oneRun = Math.min(bufferLength - mHead, mStoredBytes);
                int bytesToCopy = Math.min(length, oneRun);
                System.arraycopy(mBuffer, mHead, buffer, offset, bytesToCopy);
                mHead += bytesToCopy;
                if (mHead >= bufferLength) {
                    mHead = 0;
                }
                mStoredBytes -= bytesToCopy;
                length -= bytesToCopy;
                offset += bytesToCopy;
                totalRead += bytesToCopy;
            }
            if (wasFull) {
                notify();
            }
            return totalRead;
        }
    }

    public void write(byte[] buffer, int offset, int length)
            throws InterruptedException {
        if (length + offset > buffer.length) {
            throw
                    new IllegalArgumentException("length + offset > buffer.length");
        }
        if (length < 0) {
            throw
                    new IllegalArgumentException("length < 0");

        }
        if (length == 0) {
            return;
        }
        synchronized(this) {
            int bufferLength = mBuffer.length;
            boolean wasEmpty = mStoredBytes == 0;
            while (length > 0) {
                while(bufferLength == mStoredBytes) {
                    wait();
                }
                int tail = mHead + mStoredBytes;
                int oneRun;
                if (tail >= bufferLength) {
                    tail = tail - bufferLength;
                    oneRun = mHead - tail;
                } else {
                    oneRun = bufferLength - tail;
                }
                int bytesToCopy = Math.min(oneRun, length);
                System.arraycopy(buffer, offset, mBuffer, tail, bytesToCopy);
                offset += bytesToCopy;
                mStoredBytes += bytesToCopy;
                length -= bytesToCopy;
            }
            if (wasEmpty) {
                notify();
            }
        }
    }

    private byte[] mBuffer;
    private int mHead;
    private int mStoredBytes;
}




/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment implements OnChartValueSelectedListener, TemperatureDialog.TemperatureDialogListener, TimerDialog.TimerDialogListener{


    // [Ian] add a flag to check if a connection is already there
    public static boolean isConnectionExist = false;

    // [Ian] add a flag to check if an Alarm is being set, and also a public long integer to store the value of target Alarm time
    public static boolean isAlarmExist = false;
    public long AlarmTargetTimeMilliSec;

    // [Ian] add a new receiver when new device is successfully paired(reference code: http://www.londatiga.net/it/programming/android/how-to-programmatically-pair-or-unpair-android-bluetooth-device/)
    private final BroadcastReceiver mPairingReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // [Ian] if the action is related to Pairing State Change
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state        = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState    = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (state == BluetoothDevice.BOND_BONDING && prevState == BluetoothDevice.BOND_NONE){
                    // [Ian] since we automatically pass PIN without use manually enter the PIN, we want to dismiss the pairing request system dialog
                    // [Ian] check this one for modifying SDK to dismiss pairing request dialog window: http://stackoverflow.com/questions/17971834/android-prevent-bluetooth-pairing-dialog
                    Log.d(TAG, "Pairing in process, need to dismiss the dialog ");
                    Toast.makeText(getActivity(), "Pairing in process, please ignore system PIN request dialog", Toast.LENGTH_SHORT).show();


                }else if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    Toast.makeText(getActivity(), "Just Successfully Paired " + device.getName() + ", Now Click It To Connect", Toast.LENGTH_LONG).show();
                    Log.d(TAG, "New Device Just Paired ");

                    //pick one way from below:

                    ///*
                    // [Ian] Way 1: now jump to DeviceListActivity to display device list for user to select to connect
                    Log.d(TAG, "Now directly go to DeviceListActivity to Request_CONNECY_DEVICE_SECURE");
                    Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                    //*/
                    /*
                    // [Ian] Way 2: Alternatively we can directly start connect process without going back to REQUEST_CONNECT_DEVICE_SECURE list
                    Log.d(TAG, "Now directly start try to connectDevice in secure mode");
                    Intent data = new Intent();
                    intent.putExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS, device.getAddress());
                    connectDevice(data, true);
                    */

                } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED){
                    Toast.makeText(getActivity(), "Bluetooth Device Un-Paired", Toast.LENGTH_LONG).show();
                    Log.d(TAG, "New Device Not Paired ");
                }
            }

            // [Ian] add an action listener if the action is pairing request, then intercept it and pass the PIN through this following program without user enter the PIN manually
            if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
                try {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // assuming pin = 1234
                    int pin=intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", 1234);
                    //the pin in case you need to accept for an specific pin
                    Log.d(TAG, "ACTION_PAIRING_REQUEST trapped. Start Auto Pairing. PIN = " + Integer.toString(pin));
                    byte[] pinBytes;
                    pinBytes = (""+pin).getBytes("UTF-8");
                    device.setPin(pinBytes);
                    //setPairing confirmation if neeeded
                    device.setPairingConfirmation(true);
                    //device.getClass().getMethod("cancelPairingUserInput").invoke(device);
                } catch (Exception e) {
                    Log.e(TAG, "Error occurs when trying to auto pair");
                    e.printStackTrace();
                }
            }

        }
    };


    // chart things
    private LineChart mChart;
    protected String[] mMonths = new String[] {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dec"
    };

    // [Ian] lower half buttons and textviews
    private Button timerSetButton;
    private Button targetTempSetButton;

    private TextView currentStatusTextView;
    private TextView currentGrillTempTextView;
    private TextView currentFood1TempTextView;
    private TextView currentFood2TempTextView;
    private TextView targetGrillTempTextView;


    //bluetooth things
    private static final String TAG = "MainFragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;


    private ByteQueue mByteQueue;

    /**
     * Used to temporarily hold data received from the remote process. Allocated
     * once and used permanently to minimize heap thrashing.
     */
    private byte[] mReceiveBuffer;
    private String stringRead;
    private Boolean start = Boolean.FALSE;


    //private EditText mOutEditText;

    // Layout Views
    //private ListView mConversationView;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;



    /**
     * Member object for the chat services
     */
    private BluetoothService mBluetoothService = null;


    /*
    private AlertDialog.Builder dialogBuilder = null;
    private String temperatureInput = "";

    private void showDialog() {
        dialogBuilder = new AlertDialog.Builder(this);
    }*/


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }

        getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);


        // [Ian] add a bluetooth pairing request listener
        // see rodolfo's response and code here: http://stackoverflow.com/questions/17168263/how-to-pair-bluetooth-device-programmatically-android?lq=1
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        getActivity().registerReceiver(mPairingReceiver, filter);
        //Log.d(TAG, "Pairing Request Receiver Registered");

        // [Ian] register for broadcast when a new device finished pairing, since its Bond State Changed
        filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        getActivity().registerReceiver(mPairingReceiver, filter);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG, "MainActivityFragment onStart()");
        // If BT is not on, request that it be enabled.
        // setupBluetooth() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mBluetoothService == null) {
            setupBluetooth();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBluetoothService != null) {
            mBluetoothService.stop();
        }
        //[Ian] unregister the two Pairing related Receivers that I added into this Fragment
        getActivity().unregisterReceiver(mPairingReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "MainActivityFragment resumed");
        //[Ian] I disallowed the following .Start() service, because when we returned from deviceListActivity after clicking the device we want to pair, the .Start() will delay the process.
        /*
        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mBluetoothService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mBluetoothService.getState() == BluetoothService.STATE_NONE) {
                // Start the Bluetooth chat services
                mBluetoothService.start();
            }
        }
        */
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "MainFragment onViewCreated");
        // [Ian] add several textview displays and buttons at the bottom half
        timerSetButton = (Button) view.findViewById(R.id.button_setTimer);
        targetTempSetButton = (Button) view.findViewById(R.id.button_setTemp);

        currentFood1TempTextView = (TextView) view.findViewById(R.id.textView_food1temp);
        currentFood2TempTextView = (TextView) view.findViewById(R.id.textView_food2temp);
        currentGrillTempTextView = (TextView) view.findViewById(R.id.textView_currentGrillTemp);
        currentStatusTextView = (TextView) view.findViewById(R.id.textView_currentStatus);
        targetGrillTempTextView = (TextView) view.findViewById(R.id.textView_targetTemp);

        timerSetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimerDialog();
            }
        });

        //add the chart at the top half
        mChart = (LineChart) view.findViewById(R.id.chart1);
        mChart.setOnChartValueSelectedListener(this);

        // no description text
        mChart.setDescription("");
        mChart.setNoDataTextDescription("You need to provide data for the chart.");

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // set an alternative background color
        mChart.setBackgroundColor(Color.LTGRAY);

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart.setData(data);

        //Typeface tf = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        // l.setPosition(LegendPosition.LEFT_OF_CHART);
        l.setForm(LegendForm.LINE);
        //l.setTypeface(tf);
        l.setTextColor(Color.WHITE);

        XAxis xl = mChart.getXAxis();
        //xl.setTypeface(tf);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setSpaceBetweenLabels(5);
        xl.setEnabled(true);

        YAxis leftAxis = mChart.getAxisLeft();
        //leftAxis.setTypeface(tf);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaxValue(500f);
        leftAxis.setAxisMinValue(-500f);
        leftAxis.setStartAtZero(false);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

    }

    /**
     * Set up the UI and background operations.
     */
    private void setupBluetooth() {
        Log.d(TAG, "setupBluetooth()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(
                getActivity(), R.layout.message
        );

        //mConversationView.setAdapter(
        //        mConversationArrayAdapter
        //);
        // Initialize the compose field with a listener for the return key
        //mOutEditText = (EditText) getView().findViewById(R.id.edit_text_out);
        //mOutEditText.setOnEditorActionListener(mWriteListener);


        // Initialize the BluetoothService to perform bluetooth connections
        mBluetoothService = new BluetoothService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");


        mReceiveBuffer = new byte[4 * 1024];
        mByteQueue = new ByteQueue(4 * 1024);
    }
    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }


    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mBluetoothService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            //mOutEditText.setText(mOutStringBuffer);
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
                    Log.i(TAG, "END onEditorAction");
                    return true;
                }
            };


    /**
     * The Handler that gets information back from the BluetoothService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    Log.d(TAG, String.format("arg1 = %d", msg.arg1));
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));

                            // [Ian] set the flag so the user won't be able to connect again while there is an existing connection
                            isConnectionExist = true;

                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            setStatus(R.string.title_not_connected);

                            // [Ian] reset the flag so the user can connect again
                            isConnectionExist = false;

                            break;
                    }
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;

                    // version 2
                    try {
                        mByteQueue.write(readBuf, 0, msg.arg1);
                        int bytesAvailable = mByteQueue.getBytesAvailable();
                        int bytesToRead = Math.min(bytesAvailable, mReceiveBuffer.length);
                        int bytesRead = mByteQueue.read(mReceiveBuffer, 0, bytesToRead);
                        String tmpStringRead = new String(mReceiveBuffer, 0, bytesRead);

                        if (tmpStringRead.contains("\r\n")){
                            Log.i(TAG, stringRead);
                            stringRead = "";

                        } else {
                            stringRead += tmpStringRead;
                            // TODO 改这个适应接口数据
                            /*
                            String[] arr = stringRead.split(" ", 4);
                            if (arr.length == 4) {
                                Log.i(TAG, stringRead);
                                Log.e(TAG, arr[0]);
                                Log.e(TAG, arr[1]);
                                Log.e(TAG, arr[2]);
                                Log.e(TAG, arr[3]);
                                if (start) {
                                    addEntry(arr);
                                }
                            }
                            */
                            String[] arr = stringRead.split(":::", 5);
                            if (arr.length == 5) {
                                Log.i(TAG, stringRead);
                                Log.e(TAG, arr[0]);
                                Log.e(TAG, arr[1]);
                                Log.e(TAG, arr[2]);
                                Log.e(TAG, arr[3]);
                                Log.e(TAG, arr[4]);
                                if (start) {
                                    addEntry(arr);
                                }
                            }
                        }

                    } catch (InterruptedException e) {
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add(writeMessage);
                    break;

                //[Ian] add connection message to let user know, also updated the current status Textview on UI
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to " + mConnectedDeviceName + " Successfully", Toast.LENGTH_LONG).show();
                        currentStatusTextView.setText(mConnectedDeviceName + "\n" + "Connected");
                        currentStatusTextView.setTextColor(Color.GREEN);
                    }
                    break;


                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "start try to connectDevice in secure mode");
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "start try to connectDevice in insecure mode");
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a session
                    Log.d(TAG, "BT enabled!!!!!");
                    setupBluetooth();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }


    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        // Attempt to connect to the device
        FragmentActivity activity = getActivity();
        Toast.makeText(activity, "Connecting BBQube device...", Toast.LENGTH_SHORT).show();
        mBluetoothService.connect(device, secure);

    }

    //[Ian] here is where all the menu items are
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                //[Ian] need to determine whether the device has already been connected, if so, do not connect again
                if (isConnectionExist == false) {

                    // launch enable bluetooth & setup bluetooth in onActivityResult()
                    Log.d(TAG, "Option menu selected, first to enable bluetooth");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);

                    // Launch the DeviceListActivity to see devices and do scan
                    Log.d(TAG, "after enable bluetooth, call ConnectDevice() to setup connection");
                    Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);

                    Log.d(TAG, "after startActivityForResult (REQUEST_CONNECT_DEVICE_SECURE)");

                } else{
                    Log.d(TAG, "try to connect while there is already a connection");
                    FragmentActivity activity = getActivity();
                    Toast.makeText(activity, "There is an existing connection, you need to disconnect it first", Toast.LENGTH_SHORT).show();
                }

                return true;
            }
            // [Ian] deleted insecure option in menu_main.xml
            /*case R.id.insecure_connect_scan: {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);

                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);

                return true;
            }*/

            // [Ian] added an option to disconnect current connection
            case R.id.disconnect_current:{

                if (mBluetoothService != null) {
                    // Only if the state is STATE_NONE, do we know that we haven't started already
                    if (mBluetoothService.getState() == BluetoothService.STATE_CONNECTED) {
                        // Stop the BluetoothService
                        mBluetoothService.stop();
                        // set the flag and update status textview in UI
                        isConnectionExist = false;
                        mBluetoothService = null;
                        currentStatusTextView.setText("Device Not Connected");
                        currentStatusTextView.setTextColor(Color.RED);
                        Toast.makeText(getActivity(), "Terminating current connection...", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "stopped current connection");

                    }else{
                        Toast.makeText(getActivity(), "There is no established connection", Toast.LENGTH_SHORT).show();
                    }
                }else {
                    Toast.makeText(getActivity(), "There is no existing connection", Toast.LENGTH_SHORT).show();
                }
                return true;
            }


            // this corresponds to Set Temp menu option being clicked
            case R.id.monitor_start: {

                showTemperatureDialog();
                // start
                //sendMessage("225");
                //start = Boolean.TRUE;
                return true;
            }
            case R.id.monitor_cs: {
                // Cold Smoke
                sendMessage("999");
                start = Boolean.TRUE;
                return true;
            }
            case R.id.monitor_mf: {
                // Max Fan Power
                sendMessage("888");
                start = Boolean.TRUE;
                return true;
            }
            case R.id.monitor_stop: {
                // stop
                sendMessage("777");
                start = Boolean.FALSE;
                return true;
            }
        }
        return false;
    }


//    @Override
//    public void onBackPressed() {
//        super.onBackPressed();
//        overridePendingTransition(R.anim.move_left_in_activity, R.anim.move_right_out_activity);
//    }

    private int year = 2016;

    private void addEntry(String[] arr) {

        LineData data = mChart.getData();
        String time = arr[0];
        String grillTemp = arr[1];
        String meatTemp1 = arr[2];
        String meatTemp2 = arr[3];
        String fanPower = arr[4];

        if (data != null) {

            LineDataSet set = data.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            // add a new x-value first
            data.addXValue(time);
            try {
                data.addEntry(new Entry(Float.parseFloat(grillTemp), set.getEntryCount()), 0);
            }catch (Exception e){
                Log.e(TAG, e.getMessage());
            }


            // let the chart know it's data has changed
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(120);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart.moveViewToX(data.getXValCount() - 121);

            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "Dynamic Data");
        set.setAxisDependency(AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleSize(4f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }


    @Override
    public void onValueSelected(Entry e, int dataSetIndex, Highlight h) {
        Log.i("Entry selected", e.toString());
    }

    @Override
    public void onNothingSelected() {
        Log.i("Nothing selected", "Nothing selected.");
    }

    // will call to show Temperature Dialog
    private void showTemperatureDialog() {
        FragmentManager fm = getActivity().getSupportFragmentManager();
        TemperatureDialog tempSetDialog = TemperatureDialog.newInstance("Set Temperature", this);

        tempSetDialog.show(fm, "fragment_dialog_temperature");
    }

    @Override
    public void onSetTemperatureDialog(String temp) {
        // [Ian] fixed the bug of keep setting 225
        Toast.makeText(getActivity(),"The temperature setting value is: " + temp,Toast.LENGTH_SHORT).show();
        Log.d(TAG, "temp set is: "  + temp);
        sendMessage(temp);
        start = Boolean.TRUE;
    }

    // [Ian] define timer dialog that allows user to set up the Alarm
    private void showTimerDialog(){
        FragmentManager fm = getActivity().getSupportFragmentManager();
        TimerDialog timerSetDialog = TimerDialog.newInstance("Set Timer", this);

        timerSetDialog.show(fm, "fragment_dialog_timer");
    }

    @Override
    public void onSetTimerDialog(Long TimerFinishTime) {
        // [Ian]
        Toast.makeText(getActivity(),"Dialog Returned, Timer is Set",Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Timer Dialog returned "  + Long.toString(TimerFinishTime));

    }

}

