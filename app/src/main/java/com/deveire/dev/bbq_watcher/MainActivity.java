package com.deveire.dev.bbq_watcher;

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
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
{
    private Button scanButton;
    private TextView temperText;
    String logcatOutput;
    String allMarkedLines;

    private BluetoothAdapter btAdapter;
    private BluetoothDevice btDevice;
    private BluetoothGatt btGatt;
    private BluetoothGattCharacteristic btCharacteristic;



    private final UUID range_Socket_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    String ThreadLog;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        temperText = (TextView) findViewById(R.id.temperText);
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

        ThreadLog = "Start";

        Log.i("BBQ_bt", "pairedDevices: ");


        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        final int REQUEST_ENABLE_BT = 99;

        btAdapter = btManager.getAdapter();
        if (btAdapter != null && !btAdapter.isEnabled())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        Set<BluetoothDevice> pairedDevices;
        pairedDevices = btAdapter.getBondedDevices();
        Log.i("BBQ_bt", "pairedDevices: " + pairedDevices.toString());

        IntentFilter filter = new IntentFilter();

        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);




        //btAdapter.startLeScan(leScanCallback);



    }

    @Override
    protected void onDestroy()
    {
        btAdapter.stopLeScan(leScanCallback);
        btGatt.close();
        super.onDestroy();
    }

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback()
    {
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


    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation

            Log.i("BBQ bt", "CHARACTERISTIC CHANGED: UUID:" + characteristic.getUuid() + " VALUE: " + characteristic.getValue());
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // this will get called when a device connects or disconnects
            Log.i("BBQ bt", "onConnectionStateChange occured: " + status + ", " + newState);
            if(newState == BluetoothGatt.STATE_CONNECTED)
            {
                Log.i("BBQ bt", "onConnectionStateChanged: connected, stopping scan.");
                btAdapter.stopLeScan(leScanCallback);
                Log.i("BBQ bt", "Attempting to start service discovery:" + btGatt.discoverServices());
            }

        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {


            displayGattServices(gatt.getServices());
        }
    };

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
                if(aCharacteristic.getUuid().toString().matches("6e400003-b5a3-f393-e0a9-e50e24dcca9e"))
                {
                    btCharacteristic = aCharacteristic;
                    btGatt.setCharacteristicNotification(btCharacteristic, true);
                    BluetoothGattDescriptor descriptor = btCharacteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    btGatt.writeDescriptor(descriptor);
                    Log.i("BBQ bt", "Decriptor written: " + descriptor.toString() + " UUID: " + descriptor.getUuid() +  " VALUE: " + descriptor.getValue());
                }
            }
        }
    }
}














/*





 */
