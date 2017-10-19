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
            Log.i("BBQ bt", "BT Service Discovered for gatt:" + gatt + "\n with status: " + status);
            displayGattServices(gatt.getServices());
        }
    };

    private void displayGattServices(List<BluetoothGattService> gattServices)
    {
        if (gattServices == null) return;

        // Loops through available GATT Services.
        for (BluetoothGattService aGattService : gattServices)
        {
            for (BluetoothGattCharacteristic aCharacteristic : aGattService.getCharacteristics())
            {
                Log.i("BBQ bt", "Found Characteristic for Service: " + aGattService.toString() + " Characteristic: " + aCharacteristic.toString()
                        + " Characteristic UUID: " + aCharacteristic.getUuid().toString() + " Characteristic Value: " + aCharacteristic.getValue());
                if(aCharacteristic.getUuid().toString().matches("6e400003-b5a3-f393-e0a9-e50e24dcca9e"))
                {
                    Log.i("BBQ bt", "Searching Descripors for Characteristics");
                    btCharacteristic = aCharacteristic;
                    for (BluetoothGattDescriptor aDescriptor: btCharacteristic.getDescriptors())
                    {
                        Log.i("BBQ bt", "Found Descriptor for UUID: " + aCharacteristic.getUuid() + " Descriptor: "
                                + aDescriptor.toString() + " with Value: " + aDescriptor.getValue() + " UUID: " + aDescriptor.getUuid());
                    }
                }
            }
        }
    }
}














/*
Timeline

Inn - another party shows up
(An elven rogue(slightly on fire) with a terrified halfling wizard wrapped around his head,riding on a shield being dragged behind a horse ridden by
a dwarven fighter and elf ranger(unconscious).)

Rubble-filled road - Traveral Challenge
(The road is blocked by rubble, requiring the party to find a way of bypassing it if they want to take the wagon with them)

Merge{
Burnt Out Alchemists Shop - blighter fight
(Overrun by blighters. Contains Blighter Trogs, a pseudoDragon joins the party and tells them were Wilard's gold is)

Farm - Wilards gold
(Overrun by blighters Has a blighter farmer approaching a cow, brandishing its fork at the party and then fleeing, as other blighters ambush the party.
Introduction of the first blighter Warlock)
}

Dwarven Dragon Funeral - World Building
(A group of dwarven air clerices transporting a wing of Bishop Horn, Dragon Fulcrum of the Dwarven Priesthood, transporting the part of his body he instructed to
be sent to the bottom of a lake where he had his air-cleric epiphany. The group gives the party a vision gave the dragon had)

Fork in the Road - Traversal Challenge
A fork in road that the party must figure out whether or not their lost. Getting lost will lead to a road blocked by a rockslide, which they must navigate only to
realize they've gone the wrong way once they get to the other side.

The Paired Knights - Negociation/Combat Encounter
The 2 chared new blood knights return, ambushing the party on the

The Walk of Boards - Traversal Challenge
(The Party must find a way through the Oakland's swamp to the monastry, options include the walk of boards, a series of rotting planks/rails the wagon can try
to meander along. Another option is the ferry raft(the landing place is abandoned). Another option is to suck it and see and try to brute force their way
through the swamp)

Rotted Ambush - Combat encounter
(The party is jumped by hunters from the Rotted Township. Who carry off John, Bill will then walk off into the swamp after him)

Rotted Township Outskirts - Stealth/Combat Encounter
(The party must sneak past a series of wandering mobs lead by A hate seat)

Rotted Township - Dungeon-esque Encounter
Party reached the rotted township. Where the town spring is guarded by 3 choirmen and a number of lynchers. A rock inside the spring is a sloth demon that is
invulnerable and must be banished with the fuck off stick held in the town chapel. The mob of lyncher will return if not slain earlier. john is rescued from a
net. A small cute bookish librarian girl(or she-rat) is also rescued(secretly a sucubus going for Willard's/Rayn's Character).

The Monastary of Ooh-Whatsit - Story Area/Murder Mystery
(The party arrives at the Knowledge Gnome Monastery)




 */
