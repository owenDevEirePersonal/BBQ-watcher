package com.deveire.dev.bbq_watcher;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/*
    ++++++++General Explanation of the code++++++++

    How the app basically works is this.

    1. On startup, the app gets the phone's bluetooth adapter(btAdapter).
    2. When the user clicks the app's Scan Button it starts an LEScan and begins scanning for BLE(Bluetooth Low Energy) Devices.
    3. Continues to scan until it finds a device that matches the hard-coded Address(I've used the address of the range dial I was given)
        This check is performed in the leScanCallback listener.
    4. Once it finds the corresponding device it opens a gattConnection to the device( btGatt).
    5. Once the connection is established, it triggers onConnectionStateChanged in the BluetoothGattCallback(btGattCallback), which will stop the ble scanning and
        start the service discovery process.
    6. Once the services are discovered, it triggers onServicesDiscovered inside btGattCallback, which calls displayGattServices(). Which in turn
        writes all the connected gatt's services, charactistices and descriptors to Android Stuido's Logcat, for debugging purposes.
    7. displayGattServices() also finds the characteristic that contains the temperature data(btCharacteristic) by matching its UUID,
        and writes the enable notifications value to that characteristic's description.
    8. Now, whenever the value stored in the temperature charactistic changes, it will trigger onCharacteristicChanged() inside btGattCallback.
    9. The code in onCharacteristicChanged() then simply retrieves the array of bytes stored in the characteristic's value and decodes them into the temperatures for
        the 2 probes before displaying them in both Logcat and on the app's textView.

    ++++++++End of General Explanation of the code++++++++


*/

public class MainActivity extends AppCompatActivity
{
    private Button scanButton;
    private TextView temperText;

    private BluetoothAdapter btAdapter;
    private BluetoothGatt btGatt;
    private BluetoothGattCharacteristic btCharacteristic;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //Setup the bluetooth adapter
        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        btAdapter = btManager.getAdapter();

        //if bluetooth is not enabled, open dialog asking the user to enable it
        final int REQUEST_ENABLE_BT = 99;
        if (btAdapter != null && !btAdapter.isEnabled())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }


        //Setup the textView for later when we need to display the temperatures
        temperText = (TextView) findViewById(R.id.temperText);


        //Setup the listener for when the scan button is clicked
        scanButton = (Button) findViewById(R.id.scanButton);
        scanButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Log.i("BBQ_bt", "Button Clicked");
                btAdapter.startLeScan(leScanCallback);
            }
        });


    }

    @Override
    protected void onDestroy()
    {
        //When the app is shutdown by the android OS, stop any ongoing scans and close any open Gatt connections.
        btAdapter.stopLeScan(leScanCallback);
        btGatt.close();
        super.onDestroy();
    }



    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback()
    {
        //onLeScan triggers whenever an leScan detects a BLE device
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord)
        {
            Log.i("BBQ bt", "onLeScan occured: Device Address: " + device.getAddress());

            // your implementation here
            if(device.getAddress().matches("E4:81:3F:31:D9:6E"))
            {
                Log.i("BBQ bt", "onLeScan found device: " + device.getAddress());
                btGatt = device.connectGatt(getApplicationContext(), false, btleGattCallback);
                ;
            }

        }
    };



    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback()
    {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic)
        {
            // this will get called anytime you perform a read or write characteristic operation

            Log.i("BBQ bt", "CHARACTERISTIC CHANGED: UUID:" + characteristic.getUuid() + " VALUE: " + characteristic.getValue());
            for (Byte aByte: characteristic.getValue())
            {
                try
                {
                    Log.i("BBQ bt", "String Value: " + aByte.toString() + " Int Value: " + aByte.intValue() + " Char Value: " + (char)(aByte.intValue()));
                }
                catch (IllegalArgumentException e)
                {
                    Log.i("BBQ bt", "String Value: " + aByte.toString() + " Int Value: " + aByte.intValue());
                }
            }
            byte[] bytes = characteristic.getValue();
            if((char)(bytes[0]) == 'T')
            {
                final double temp1 = bytesToTemperatures(bytes, 1, 2);
                final double temp2 = bytesToTemperatures(bytes, 3, 4);
                Log.i("BBQ bt", "Translating Byte Character: " + (char) (bytes[0]) + " Probe 1 Temp: " + temp1 + " Probe 2 Temp: " + temp2);
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if(temp1 == -325.0 && temp2 == -325.0)
                        {
                            temperText.setText("Current Temperature:\n Counter-Clockwise-most Probe 1:\t Disconnected\n Clockwise-most Probe 2:     Disconnected");
                        }
                        else if(temp1 == -325.0)
                        {
                            temperText.setText("Current Temperature:\n Counter-Clockwise-most Probe 1:\t Disconnected\n Clockwise-most Probe 2:\t                " + temp2 + "^C");
                        }
                        else if(temp2 == -325.0)
                        {
                            temperText.setText("Current Temperature:\n Counter-Clockwise-most Probe 1:\t " + temp1 + "^C" + "\n Clockwise-most Probe 2:       Disconnected");
                        }
                        else
                        {
                            temperText.setText("Current Temperature:\n Counter-Clockwise-most Probe 1:\t " + temp1 + "^C" + "\n Clockwise-most Probe 2:\t                " + temp2 + "^C");
                        }
                    }
                });
            }
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState)
        {
            // this will get called when a device connects or disconnects
            Log.i("BBQ bt", "onConnectionStateChange occured: " + status + ", " + newState);
            if(newState == BluetoothGatt.STATE_CONNECTED)
            {
                Log.i("BBQ bt", "onConnectionStateChanged: connected, stopping scan.");
                btAdapter.stopLeScan(leScanCallback);
                Log.i("BBQ bt", "Attempting to start service discovery:");
                btGatt.discoverServices();
            }

        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status)
        {
            displayGattServices(gatt.getServices());
        }
    };

    //Write to log, any services, characteristics or descriptors found.
    //Also writes the enableNotifications value to the descriptor for the temperature storing characteristic
    private void displayGattServices(List<BluetoothGattService> gattServices)
    {
        if (gattServices == null) return;

        // Loops through available GATT Services.
        for (BluetoothGattService aGattService : gattServices)
        {
            Log.i("BBQ bt", "BT Service Discovered for gatt:" + aGattService + "\n with UUID: " + aGattService.getUuid());
            for (BluetoothGattCharacteristic aCharacteristic : aGattService.getCharacteristics())
            {
                Log.i("BBQ bt", "Found Characteristic for Service: " + aGattService.toString() + " Characteristic: " + aCharacteristic.toString() + " and UUID: "
                        + " Characteristic UUID: " + aCharacteristic.getUuid().toString() + " Characteristic Value: " + aCharacteristic.getValue());

                Log.i("BBQ bt", "Searching Descripors for Characteristics");
                for (BluetoothGattDescriptor aDescriptor: aCharacteristic.getDescriptors())
                {
                     Log.i("BBQ bt", "Found Descriptor for UUID: " + aCharacteristic.getUuid() + " Descriptor: "
                              + aDescriptor.toString() + " with Value: " + aDescriptor.getValue() + " UUID: " + aDescriptor.getUuid());
                }
                Log.i("BBQ bt", "Done Searching Descripors for Characteristics");


                //If aCharacteristic is the Temperature Storing Characteristic, writes the enableNotifications value to its descriptor
                if(aCharacteristic.getUuid().toString().matches("6e400003-b5a3-f393-e0a9-e50e24dcca9e"))
                {
                    btCharacteristic = aCharacteristic;
                    btGatt.setCharacteristicNotification(btCharacteristic, true);
                    BluetoothGattDescriptor descriptor = btCharacteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    btGatt.writeDescriptor(descriptor);
                    Log.i("BBQ bt", "Decriptor written: " + descriptor.toString() + " UUID: " + descriptor.getUuid() +  " B VALUE: " + descriptor.getValue().toString());
                }
            }
        }
    }


    //Converts a 2-byte code to an a temperature in degree's Celsius
    private double bytesToTemperatures(byte[] data, int firstByteIndex, int secondByteIndex)
    {
        int tempdata = data[firstByteIndex] * 256 + data[secondByteIndex];

        if (tempdata != 0x810C)
        {
            double tempout = tempdata / 100.0;
            return tempout;
        }
        return 2222.22;
    }

}














/*





 */
