package com.deveire.dev.bbq_watcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
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
    private BluetoothSocket btSocket;

    private ConnectSocketRunnable connectToRunnable;
    private ConnectedRunnable btInOutThread;

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
                btAdapter.cancelDiscovery();
                btAdapter.startDiscovery();
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

        registerReceiver(btReceiver, filter);



        //btAdapter.startLeScan(leScanCallback);



    }

    @Override
    protected void onDestroy()
    {
        unregisterReceiver(btReceiver);
        try
        {
            btSocket.close();
            Log.i("BBQ_bt", "Successfully Closed Socket");
        }
        catch (IOException e)
        {
            Log.e("BBQ_bt", "IOException Error while closing socket: " + e);
            e.printStackTrace();
        }

        try
        {
            btSocket.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    /*private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback()
    {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord)
        {
            Log.i("BBQ Watcher", "onLeScan occured: Device Address: " + device.getAddress());
            // your implementation here
            if(device.getAddress().matches("50:F1:4A:50:BD:6D"))
            {
                Log.i("BBQ Watcher", "onLeScan found device: " + device.getAddress());
                BluetoothGatt bluetoothGatt = device.connectGatt(getApplicationContext(), false, btleGattCallback);
            }

        }
    };*/

    /*private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // this will get called when a device connects or disconnects
            Log.i("BBQ Watcher", "onConnectionStateChange occured: " + status + ", " + newState);
            if(newState == BluetoothGatt.STATE_CONNECTED)
            {
                Log.i("BBQ Watcher", "onConnectionStateChanged: connected, stopping scan.");
                btAdapter.stopLeScan(leScanCallback);
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            // this will get called after the client initiates a 			BluetoothGatt.discoverServices() call
        }
    };*/



    private final BroadcastReceiver btReceiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                //discovery starts, we can show progress dialog or perform other tasks
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //discovery finishes, dismis progress dialog
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //bluetooth device found
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                Log.i("BBQ_bt", "Found device " + device.getName());
                Log.i("BBQ_bt", "\tAddress: " + device.getAddress() + " Class:" + device.getBluetoothClass());

                if(device.getName() != null && device.getName().matches("RangeBlack6E"))
                {
                    Log.i("BBQ_bt", "Range Timer Found");
                    btDevice = device;
                    btAdapter.cancelDiscovery();
                    try
                    {

                        btSocket = device.createRfcommSocketToServiceRecord(range_Socket_UUID);
                        Log.i("BBQ_bt", "Socket Successfully established: " + btSocket.toString());
                        connectToRunnable = new ConnectSocketRunnable();
                        connectToRunnable.run();

                    }
                    catch (IOException e)
                    {
                        Log.e("BBQ_bt", "IOException Error while creating socket: " + e);
                        e.printStackTrace();
                    }
                }
            }
        }
    };




//+++++++++[Bluetooth stream reading thread]+++++++++
    private class ConnectSocketRunnable implements Runnable
    {
        @Override
        public void run()
        {
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                btSocket.connect();
                LogI("Connected btSocket");
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    btSocket.close();
                    LogE("closed the client socket: " + connectException);
                } catch (IOException closeException) {
                    LogE("Could not close the client socket: " + closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            btInOutThread = new ConnectedRunnable(btSocket);
            btInOutThread.run();
        }

        private void LogI(final String aString)
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    ThreadLog += "\nI:" + aString;
                    temperText.setText(ThreadLog);
                }
            });
        }

        private void LogE(final String aString)
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    ThreadLog += "\nE:" + aString;
                    temperText.setText(ThreadLog);
                }
            });
        }
    }



    private class ConnectedRunnable implements Runnable
    {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        private static final int MESSAGE_READ = 0;
        private static final int MESSAGE_WRITE = 1;
        private static final int MESSAGE_TOAST = 2;

        public ConnectedRunnable(BluetoothSocket socket) {
            LogI( "Creating Thread");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
                LogI( "Established inputStream: " + tmpIn.toString());
            } catch (IOException e) {
                Log.e("BBQ bt", "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
                LogI( "Established OutpuStream: " + tmpOut.toString());
            } catch (IOException e) {
                Log.e("BBQ bt", "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            LogI( "Begining Read Cycle");
            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    LogI( "Read Stream: " + new String(mmBuffer, 0, numBytes));
                    // Send the obtained bytes to the UI activity.
                    /*Message readMsg = btReadWriteHandler.obtainMessage(
                            MESSAGE_READ, numBytes, -1,
                            mmBuffer);
                    readMsg.sendToTarget();*/
                } catch (IOException e) {
                    LogE("Input stream was disconnected: " + e);
                    break;
                }
            }
        }

        private void LogI(final String aString)
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    ThreadLog += "\nI:" + aString;
                    temperText.setText(ThreadLog);
                }
            });
        }

        private void LogE(final String aString)
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    ThreadLog += "\nE:" + aString;
                    temperText.setText(ThreadLog);
                }
            });
        }
    }
//+++++++++[/Bluetooth stream reading thread]+++++++++

}














/*




 */
