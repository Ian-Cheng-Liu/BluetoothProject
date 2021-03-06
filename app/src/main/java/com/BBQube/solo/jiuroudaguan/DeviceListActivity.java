package com.BBQube.solo.jiuroudaguan;

/**
 * Created by solo on 15/12/6.
 */
/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Set;

/*import com.example.android.common.logger.Log;*/


/**
 * This Activity appears as a dialog. It lists any paired devices and
 * devices detected in the area after discovery. When a device is chosen
 * by the user, the MAC address of the device is sent back to the parent
 * Activity in the result Intent.
 */
public class DeviceListActivity extends Activity {

    /**
     * Tag for Log
     */
    private static final String TAG = "DeviceListActivity";

    /**
     * Return Intent extra
     */
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    /**
     * Member fields
     */
    private BluetoothAdapter mBtAdapter;

    /**
     * Newly discovered devices
     */
    private ArrayAdapter<String> mNewDevicesArrayAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "DeviceListActivity onCreate");

        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_device_list);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        // Initialize the button to perform device discovery
        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
            }
        });

        // Initialize array adapters. One for already paired devices and
        // one for newly discovered devices
        ArrayAdapter<String> pairedDevicesArrayAdapter =
                new ArrayAdapter<String>(this, R.layout.device_name);
        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        // Find and set up the ListView for paired devices
        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(pairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mPairedDeviceClickListener);

        // Find and set up the ListView for newly discovered devices
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        // [Ian] changed the click listener for unpaired devices
        //newDevicesListView.setOnItemClickListener(mDeviceClickListener);
        newDevicesListView.setOnItemClickListener(mNewUnPairedDeviceClickListener);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);


        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get a set of currently paired devices
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                // [Ian] add the device to the list for display only if the device is a BBQube device
                String currentDeviceName = device.getName();
                if ( (currentDeviceName != null) && (currentDeviceName.contains("BBQube")) ){
                    pairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            }
        } else {
            String noDevices = getResources().getText(R.string.none_paired).toString();
            pairedDevicesArrayAdapter.add(noDevices);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "DeviceListActivity onDestroy");

        // Make sure we're not doing discovery anymore
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        this.unregisterReceiver(mReceiver);
    }

    /**
     * Start device discover with the BluetoothAdapter
     */
    private void doDiscovery() {
        Log.d(TAG, "doDiscovery()");

        // Indicate scanning in the title
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        // Turn on sub-title for new devices
        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        // If we're already discovering, stop it
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        mBtAdapter.startDiscovery();
    }

    /**
     * The on-click listener for paired devices in the ListViews
     */
    private AdapterView.OnItemClickListener mPairedDeviceClickListener
            = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            mBtAdapter.cancelDiscovery();

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            // Create the result Intent and include the MAC address
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            Log.d(TAG, "!!!!!!!!!!!! Old Paired Device !!!!!!!!!!!!!!!!!!");
            Log.d(TAG, address);
            Log.d(TAG, EXTRA_DEVICE_ADDRESS);
            Log.d(TAG, "!!!!!!!!!!!!   !!!!!!!!!!!!!!!!!!");

            // Set result and finish this Activity
            setResult(Activity.RESULT_OK, intent); // will invoke MainActivityFragment onActivityResult()

            // [Ian] changed the toast to happen in MainActivityFragment connectDevice()
            //Toast.makeText(getBaseContext(), "Connecting BBQube Device...", Toast.LENGTH_LONG).show();
            finish();
        }
    };

    /** [Ian] added a new listener for un-paired devices
     * The on-click listener for new un-paired devices in the ListViews
     */
    private AdapterView.OnItemClickListener mNewUnPairedDeviceClickListener
            = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            mBtAdapter.cancelDiscovery();

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            // Create the result Intent and include the MAC address
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            Log.d(TAG, "!!!!!!!!!!!! New Un-Paired Device  !!!!!!!!!!!!!!!!!!");
            Log.d(TAG, address);
            Log.d(TAG, EXTRA_DEVICE_ADDRESS);
            Log.d(TAG, "!!!!!!!!!!!! Now trying to pair for the first time  !!!!!!!!!!!!!!!!!!");

            // Get the BluetoothDevice object
            BluetoothDevice device = mBtAdapter.getRemoteDevice(address);

            int state = device.getBondState();
            Log.d(TAG, "current device name is: " + device.getName());
            Log.d(TAG, "current device bond state is: " + Integer.toString(state));
            // now call pairDevice to pair the device
            pairDevice(device);

            // Set result and finish this Activity,
            //setResult(Activity.RESULT_OK, intent); // will invoke MainActivityFragment onActivityResult()

            // [Ian] changed the toast to happen in MainActivityFragment connectDevice()
            Toast.makeText(getBaseContext(), "Please Wait Patiently for Pairing Taking Place Automatically (No Manual PIN enter Needed)", Toast.LENGTH_LONG).show();
            finish();
        }
    };



    /**
     * The BroadcastReceiver that listens for discovered devices and changes the title when
     * discovery is finished
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    //mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    //[Ian] add the device to the list for display only if the device is a BBQube device
                    String currentDeviceName = device.getName();
                    if ( (currentDeviceName != null) && (currentDeviceName.contains("BBQube")) ){
                        mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    }
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            }

        }
    };


    //[Ian] after createBond() is called, system will invoke a pairing request dialog and a ACTION_PAIRING_REQUEST, which is trapped by our broadcast listener (mPairingRequestReceiver) in MainActivityFragment
    private void pairDevice(BluetoothDevice device) {
        try {
            Log.d(TAG, "Start Pairing... with: " + device.getName() + " createBond() is called");
            device.createBond();
        } catch (Exception e) {
            // TODO: sometimes createBond() is not invoked successfully, cannt get system dialog pop up, so that cannot intercept it. Need to capture that
            Log.d(TAG, "Error happened when calling createBond()");
            Toast.makeText(getBaseContext(), "Could not pair device from the APP, please try pairing from system bluetooth setting", Toast.LENGTH_LONG).show();
            Log.e(TAG, e.getMessage());
        }
    }


}
