package com.deveire.dev.bbq_watcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.ResultReceiver;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import com.android.volley.AuthFailureError;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

/*import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;*/

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

public class MainActivity extends AppCompatActivity implements RecognitionListener, DownloadCallback<String>
{
    private Button scanButton;
    private TextView temperText;
    //private EditText foodEditText;
    private TextView peakText;
    private TextView foodText;
    //private Button foodButton;
    private Button foodVoiceButton;
    private Button logButton;
    private Button scriptedButton;

    private BluetoothAdapter btAdapter;
    private BluetoothGatt btGatt;
    private BluetoothGattCharacteristic btCharacteristic;
    private boolean rangeDialIsConnected;

    private int scriptLine;

    //[Saved Preferences Variables]
    private SharedPreferences savedData;
    private SharedPreferences.Editor edit;
    private int savedTaskCount;
    private ArrayList<String> savedTaskName;
    private ArrayList<String> savedTaskTimestamp;
    private ArrayList<Double> savedTaskPeak;
    //[/Saved Preferences Variables]

    //reading from probe 1
    private double latestPeak;
    private int checksSinceLastIncrease;
    private Calendar aCalendar;
    private Date latestPeakTimestamp;
    private SimpleDateFormat timestampFormat;

    //[Text To Speech Variables]
    private TextToSpeech toSpeech;
    private String speechInText;
    private HashMap<String, String> endOfSpeakIndentifier;

    private final String textToSpeechID_Clarification = "Clarification";
    //[/Text To Speech Variables]

    private SpeechRecognizer recog;
    private Intent recogIntent;
    private int pingingRecogFor;
    private int previousPingingRecogFor;
    private final int pingingRecogFor_FoodName = 1;
    private final int pingingRecogFor_Confirmation = 2;
    private final int pingingRecogFor_Clarification = 3;
    private final int pingingRecogFor_ScriptedExchange = 4;
    private final int pingingRecogFor_Nothing = -1;

    private String[] currentPossiblePhrasesNeedingClarification;

    //[Brightspot Network Variables]
    //private OkHttpClient networkClient;
    private final String serverIP = "http://34.251.66.61:9080/food_safety_server";
    //private WebSocket wsSocket;

    private JSONObject jsonToPass;
    //[/Brightspot Network Variables]

    //[Network and periodic location update, Variables]


    private boolean pingingServer;
    //private final String serverIPAddress = "http://192.168.1.188:8080/TruckyTrackServlet/TTServlet";
    //private final String serverIPAddress = "http://api.eirpin.com/api/TTServlet";
    //private final String serverIPAddress = "http://eirpin.com/kegbots360/TTServlet";
    //private String serverIPAddress;
    private String serverURL;
    private NetworkFragment aNetworkFragment;
    //[/Network and periodic location update, Variables]


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //Setup the bluetooth adapter
        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        btAdapter = btManager.getAdapter();
        rangeDialIsConnected = false;

        //if bluetooth is not enabled, open dialog asking the user to enable it
        final int REQUEST_ENABLE_BT = 99;
        if (btAdapter != null && !btAdapter.isEnabled())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }


        //Setup the textView for later when we need to display the temperatures
        temperText = (TextView) findViewById(R.id.temperText);
        foodText = (TextView) findViewById(R.id.foodTextView);
        peakText = (TextView) findViewById(R.id.peakTextView);
        //foodEditText = (EditText) findViewById(R.id.foodEditText);


        //Setup the listener for when the scan button is clicked
        scanButton = (Button) findViewById(R.id.scanButton);
        scanButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                    Log.i("BBQ_bt", "Button Clicked");
                if(!rangeDialIsConnected)
                {
                    btAdapter.startLeScan(leScanCallback);
                }
            }
        });

        /*foodButton = (Button) findViewById(R.id.foodButton);
        foodButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                foodText.setText("Current Food: " + foodEditText.getText().toString());
                checksSinceLastIncrease = 0;
                latestPeak = 0.00;
            }
        });*/

        foodVoiceButton = (Button) findViewById(R.id.foodVoiceButton);
        foodVoiceButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                {
                    toSpeech.speak("What is the name of the current task?", TextToSpeech.QUEUE_FLUSH, null, "StartReading");
                }
            }
        });

        logButton = (Button) findViewById(R.id.logButton);
        logButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startActivity(new Intent(getApplicationContext(), LogActivity.class));
            }
        });

        scriptedButton = (Button) findViewById(R.id.scriptedButton);
        scriptedButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                {
                    toSpeech.speak("Sausage Rolls 30 degrees celsius at 30 minutes.", TextToSpeech.QUEUE_FLUSH, null, "Scripted");
                }
            }
        });

        aCalendar = Calendar.getInstance();
        latestPeak = 0.00;
        latestPeakTimestamp = new Date();
        timestampFormat = new SimpleDateFormat("hh:mm:ss dd-MM-yyyy");
        checksSinceLastIncrease = 11;//11 is the number at which it stops checking for increases in temperature.
        // (by initializing at 11, the app will not check for peak temperature on startup)

        recog = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        recog.setRecognitionListener(this);
        recogIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recogIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"en");
        recogIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getApplicationContext().getPackageName());
        recogIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recogIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        currentPossiblePhrasesNeedingClarification = new String[]{};


        //[SharedPreferences Setup]
        savedData = getApplicationContext().getSharedPreferences("BBQWatchLogsSavedData", Context.MODE_PRIVATE);
        savedTaskName = new ArrayList<String>();
        savedTaskTimestamp = new ArrayList<String>();
        savedTaskPeak = new ArrayList<Double>();
        savedTaskCount = savedData.getInt("TaskCount", 0);

        for(int i = 1; i <= savedTaskCount; i++)
        {
            savedTaskName.add(savedData.getString("TaskName" + i, "-TaskName" + i + " not found-"));
            savedTaskTimestamp.add(savedData.getString("TaskTimestamp" + i, "-TaskTimestamp" + i + " not found-"));
            savedTaskPeak.add((double) savedData.getFloat("TaskPeak" + i, 0));
        }
        //[/SharedPreferences Setup]


        setupTextToSpeech();

        //setupSocket();

        scriptLine = 0;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        //[SharedPreferences Setup]
        savedData = getApplicationContext().getSharedPreferences("BBQWatchLogsSavedData", Context.MODE_PRIVATE);
        savedTaskName = new ArrayList<String>();
        savedTaskTimestamp = new ArrayList<String>();
        savedTaskPeak = new ArrayList<Double>();
        savedTaskCount = savedData.getInt("TaskCount", 0);

        for (int i = 1; i <= savedTaskCount; i++)
        {
            savedTaskName.add(savedData.getString("TaskName" + i, "-TaskName" + i + " not found-"));
            savedTaskTimestamp.add(savedData.getString("TaskTimestamp" + i, "-TaskTimestamp" + i + " not found-"));
            savedTaskPeak.add((double) savedData.getFloat("TaskPeak" + i, 0));
        }
        //[/SharedPreferences Setup]
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        edit = savedData.edit();

        for(int i = 1; i <= savedTaskCount; i++)
        {
            edit.putString("TaskName" + i, savedTaskName.get(i - 1));
            edit.putString("TaskTimestamp" + i, savedTaskTimestamp.get(i - 1));
            edit.putFloat("TaskPeak" + i, savedTaskPeak.get(i - 1).floatValue());
        }

        edit.putInt("TaskCount", savedTaskCount);
        edit.commit();
    }

    @Override
    protected void onDestroy()
    {
        //When the app is shutdown by the android OS, stop any ongoing scans and close any open Gatt connections.

        btAdapter.stopLeScan(leScanCallback);
        if(btGatt != null)
        {
            btGatt.close();
        }
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

                if(checksSinceLastIncrease < 10)
                {
                    if(temp1 > latestPeak)
                    {
                        checksSinceLastIncrease = 0;

                        latestPeak = temp1;
                        aCalendar = Calendar.getInstance();
                        latestPeakTimestamp = aCalendar.getTime();

                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                peakText.setText("Peak Temperature: " + latestPeak + "^C" + "\nTimestamp: " + timestampFormat.format(latestPeakTimestamp));
                                Log.i("BBQData", "Peak Temperature: " + latestPeak + "^C" + "\nTimestamp: " + timestampFormat.format(latestPeakTimestamp));
                            }
                        });


                    }
                    else
                    {
                        checksSinceLastIncrease++;
                    }
                }
                else if(checksSinceLastIncrease == 10)
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    {
                        toSpeech.speak("Reading Complete for " + foodText.getText() + ". . Peak Temperature was " + latestPeak + " degrees Celsius. ", TextToSpeech.QUEUE_FLUSH, null, "PeakPlayback");
                        Log.e("Mock Output", "{command:store, data:{foodtext: " + foodText.getText() + ", peak: " + latestPeak + ", time: " + timestampFormat.format(latestPeakTimestamp) + " }}");

                    }
                    Log.i("BBQData", "Reading Complete for " + foodText.getText().subSequence(18, foodText.length() - 1) + ". Peak Temperature was " + latestPeak + ".");
                    createJson(latestPeakTimestamp, foodText.getText().toString(), latestPeak);
                    //startSocketConnection();
                    postDataToBrightspot();

                    savedTaskName.add(foodText.getText().toString());
                    savedTaskTimestamp.add(timestampFormat.format(latestPeakTimestamp));
                    savedTaskPeak.add(latestPeak);
                    savedTaskCount++;

                    checksSinceLastIncrease++; //advances checksSinceLastIncrease to 11, so the result outputting code here doesn't run a second time.
                }
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
                rangeDialIsConnected = true;
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



//++++++++[Text To Speech Code]
    private void setupTextToSpeech()
    {
        toSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status)
            {
                Log.i("Text To Speech Update", "onInit Complete");
                toSpeech.setLanguage(Locale.ENGLISH);
                endOfSpeakIndentifier = new HashMap();
                endOfSpeakIndentifier.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "endOfSpeech");
                toSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener()
                {
                    @Override
                    public void onStart(String utteranceId)
                    {
                        Log.i("Text To Speech Update", "onStart called");
                    }

                    @Override
                    public void onDone(String utteranceId)
                    {
                        switch (utteranceId)
                        {
                            case "PeakPlayback": break;
                            case "StartReading":
                                pingingRecogFor = pingingRecogFor_FoodName;
                                runOnUiThread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        recog.startListening(recogIntent);
                                    }
                                });
                                break;
                            case "Scripted":
                                pingingRecogFor = pingingRecogFor_ScriptedExchange;
                                runOnUiThread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        recog.startListening(recogIntent);
                                    }
                                });
                            break;
                        }
                        /*if(utteranceId.matches("QualityAsk") || utteranceId.matches("QuantityAsk"))
                        {
                            pingingRecogFor = pingingRecogFor_Quality
                            recognizer.startListening(recogIntent);
                        }
                        else
                        {
                            recognizer.startListening(recogIntent);
                        }*/
                        //toSpeech.shutdown();
                    }

                    @Override
                    public void onError(String utteranceId)
                    {
                        Log.i("Text To Speech Update", "ERROR DETECTED");
                    }
                });
            }
        });
    }
//++++++++[/Text To Speech Code]

    //++++++++[Recognition Listener Code]
    @Override
    public void onReadyForSpeech(Bundle bundle)
    {
        Log.e("Recog", "ReadyForSpeech");
    }

    @Override
    public void onBeginningOfSpeech()
    {
        Log.e("Recog", "BeginningOfSpeech");
    }

    @Override
    public void onRmsChanged(float v)
    {
        Log.e("Recog", "onRmsChanged");
    }

    @Override
    public void onBufferReceived(byte[] bytes)
    {
        Log.e("Recog", "onBufferReceived");
    }

    @Override
    public void onEndOfSpeech()
    {
        Log.e("Recog", "End ofSpeech");
        recog.stopListening();
    }

    @Override
    public void onError(int i)
    {
        switch (i)
        {
            //case RecognizerIntent.RESULT_AUDIO_ERROR: Log.e("Recog", "RESULT AUDIO ERROR"); break;
            //case RecognizerIntent.RESULT_CLIENT_ERROR: Log.e("Recog", "RESULT CLIENT ERROR"); break;
            //case RecognizerIntent.RESULT_NETWORK_ERROR: Log.e("Recog", "RESULT NETWORK ERROR"); break;
            //case RecognizerIntent.RESULT_SERVER_ERROR: Log.e("Recog", "RESULT SERVER ERROR"); break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: Log.e("Recog", "SPEECH TIMEOUT ERROR"); break;
            case SpeechRecognizer.ERROR_SERVER: Log.e("Recog", "SERVER ERROR"); break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: Log.e("Recog", "BUSY ERROR"); break;
            case SpeechRecognizer.ERROR_NO_MATCH: Log.e("Recog", "NO MATCH ERROR");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                {
                    toSpeech.speak("No Response Detected, aborting.", TextToSpeech.QUEUE_FLUSH, null, null);
                }
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: Log.e("Recog", "NETWORK TIMEOUT ERROR"); break;
            case SpeechRecognizer.ERROR_NETWORK: Log.e("Recog", "TIMEOUT ERROR"); break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: Log.e("Recog", "INSUFFICENT PERMISSIONS ERROR"); break;
            case SpeechRecognizer.ERROR_CLIENT: Log.e("Recog", "CLIENT ERROR"); break;
            case SpeechRecognizer.ERROR_AUDIO: Log.e("Recog", "AUDIO ERROR"); break;
            default: Log.e("Recog", "UNKNOWN ERROR: " + i); break;
        }
    }



    @Override
    public void onResults(Bundle bundle)
    {
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        recogResultLogic(matches);

    }

    @Override
    public void onPartialResults(Bundle bundle)
    {
        Log.e("Recog", "Partial Result");
    }

    @Override
    public void onEvent(int i, Bundle bundle)
    {
        Log.e("Recog", "onEvent");
    }
//++++++++[/Recognition Listener Code]

    //++++++++[Recognition Other Code]
    private String sortThroughRecognizerResults(ArrayList<String> results, String[] matchablePhrases)
    {
        for (String aResult: results)
        {
            Log.i("Recog", "Sorting results for result: " + aResult);
            for (String aPhrase: matchablePhrases)
            {
                Log.i("Recog", "Sorting results for result: " + aResult.toLowerCase().replace("-", " ") + " and Phrase: " + aPhrase.toLowerCase());
                if((aResult.toLowerCase().replace("-"," ")).contains(aPhrase.toLowerCase()))
                {
                    Log.i("Recog", "Match Found");
                    return aPhrase;
                }
            }
        }
        Log.i("Recog", "No matches found, returning empty string \"\" .");
        return "";
    }



    private void sortThroughRecognizerResultsForAllPossiblities(ArrayList<String> results, String[] matchablePhrases)
    {
        ArrayList<String> possibleResults = new ArrayList<String>();
        for (String aResult: results)
        {
            Log.i("Recog", "All Possiblities, Sorting results for result: " + aResult);
            for (String aPhrase: matchablePhrases)
            {
                Boolean isDuplicate = false;
                Log.i("Recog", "All Possiblities, Sorting results for result: " + aResult.toLowerCase().replace("-", " ") + " and Phrase: " + aPhrase.toLowerCase());
                for (String b: possibleResults)
                {
                    if(b.matches(aPhrase)){isDuplicate = true; break;}
                }

                if((aResult.toLowerCase().replace("-"," ")).contains(aPhrase.toLowerCase()) && !isDuplicate)
                {
                    Log.i("Recog", "All Possiblities, Match Found");
                    possibleResults.add(aPhrase);
                }
            }
        }

        currentPossiblePhrasesNeedingClarification = possibleResults.toArray(new String[possibleResults.size()]);
        //if there is more than 1 keyword in the passed phrase, the method will list those keywords back to the user and ask them to repeat  the correct 1.
        //This in turn will call recogResult from the utterance listener and trigger the pinging for Clarification case where the repeated word will then be used
        //to resolve the logic of the previous call to recogResult.
        if(possibleResults.size() > 1)
        {
            String clarificationString = "I'm sorry but did you mean.";

            for (String a: possibleResults)
            {
                clarificationString += (". " + a);
                if(!possibleResults.get(possibleResults.size() - 1).matches(a))
                {
                    clarificationString += ". or";
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                pingingRecogFor = pingingRecogFor_Clarification;
                toSpeech.speak(clarificationString, TextToSpeech.QUEUE_FLUSH, null, textToSpeechID_Clarification);
            }
        }
        //if there is only 1 keyword in the passed phrase, the method skips speech confirmation and immediately calls it's own listener in recogResults,
        // which(given that there is only 1 possible match, will skip to resolving the previous call to recogResult's logic)
        else if (possibleResults.size() == 1)
        {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                pingingRecogFor = pingingRecogFor_Clarification;
                recogResultLogic(possibleResults);
                //toSpeech.speak("h", TextToSpeech.QUEUE_FLUSH, null, textToSpeechID_Clarification);
            }
        }
        else
        {
            Log.i("Recog", "No matches found, Requesting Repetition .");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                toSpeech.speak("Can you please repeat that?", TextToSpeech.QUEUE_FLUSH, null, textToSpeechID_Clarification);
            }
        }
    }

    private String sortThroughRecognizerResults(ArrayList<String> results, String matchablePhrase)
    {
        for (String aResult: results)
        {
            Log.i("Recog", "Sorting results for result: " + aResult.replace("-", " ") + " and Phrase: " + matchablePhrase.toLowerCase());
            if((aResult.replace("-", " ")).contains(matchablePhrase.toLowerCase()))
            {
                Log.i("Recog", "Match Found");
                return matchablePhrase;
            }
        }
        Log.i("Recog", "No matches found, returning empty string \"\" .");
        return "";
    }


    //CALLED FROM: RecogListener onResults()
    private void recogResultLogic(ArrayList<String> matches)
    {
        String[] phrases;
        Log.i("Recog", "Results recieved: " + matches);
        String response = "-Null-";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            Log.i("Recog", "Pinging For: " + pingingRecogFor);
            switch (pingingRecogFor)
            {
                case pingingRecogFor_Clarification:

                    Log.i("Recog", "onResult for Clarification");
                    phrases = currentPossiblePhrasesNeedingClarification;
                    response = sortThroughRecognizerResults(matches, phrases);
                    Log.i("Recog", "onClarification: Response= " + response);
                    if(response.matches(""))
                    {
                        Log.i("Recog", "Unrecongised response: " + response);
                        pingingRecogFor = pingingRecogFor_Clarification;
                        ArrayList<String> copyOfCurrentPossiblePhrases = new ArrayList<String>(Arrays.asList(currentPossiblePhrasesNeedingClarification));
                        sortThroughRecognizerResultsForAllPossiblities(copyOfCurrentPossiblePhrases, phrases);
                    }
                    else
                    {
                        Log.i("Recog", "Clarification Returned: " + response);
                    }
                break;

                case pingingRecogFor_FoodName:

                    if(sortThroughRecognizerResults(matches, "next").matches("next"))
                    {
                        String text = foodText.getText().toString();
                        int lastNum = 1;
                        try
                        {
                            lastNum = Integer.parseInt(text.substring(text.length() - 1, text.length() - 1));
                        }
                        catch (NumberFormatException e)
                        {
                            Log.e("BBQData", "NumberFormatException in pingRecogFor_FoodName: " + e.toString());
                            lastNum = 1;
                        }
                        lastNum++;
                        foodText.setText(text.substring(0, text.length() - 1) + lastNum);
                        toSpeech.speak("starting next scan", TextToSpeech.QUEUE_FLUSH, null, null);
                        checksSinceLastIncrease = 0;
                        latestPeak = 0.00;
                        Log.e("Mock Output", "next");
                    }
                    else if (sortThroughRecognizerResults(matches, "dump").matches("dump"))
                    {
                        Log.e("Mock Output", "dump");
                    }
                    else if (sortThroughRecognizerResults(matches, "ok").matches("ok"))
                    {
                        Log.e("Mock Output", "ok");
                    }
                    else if (sortThroughRecognizerResults(matches, "raise trouble ticket").matches("raise trouble ticket"))
                    {
                        Log.e("Mock Output", "raise trouble ticket");
                    }
                    else
                    {
                        if(!sortThroughRecognizerResults(matches, new String[]{"sausage rolls", "rashers", "pudding", "puddings", "fried eggs", "scrambled", "scrambled eggs", "beans"}).matches(""))
                        {
                            foodText.setText("Current Food: " + matches.get(0));
                            checksSinceLastIncrease = 0;
                            latestPeak = 0.00;
                        }
                    }


                break;

                case pingingRecogFor_ScriptedExchange:

                    scriptLine++;

                    switch (scriptLine)
                    {
                        case 1:
                            if(!sortThroughRecognizerResults(matches, "ticket").matches(""))
                            {
                                toSpeech.speak("Dan. What is the issue?", TextToSpeech.QUEUE_FLUSH, null, "Scripted");
                            }
                            else
                            {
                                toSpeech.speak("Unacceptable Response: Aborting Dialog", TextToSpeech.QUEUE_FLUSH, null, "");
                                scriptLine = 0;
                            }
                            break;

                        case 2:
                            if(!sortThroughRecognizerResults(matches, "element").matches(""))
                            {
                                toSpeech.speak("Do you mean that the heating element in the cooker is not working?", TextToSpeech.QUEUE_FLUSH, null, "Scripted");
                            }
                            else
                            {
                                toSpeech.speak("Unacceptable Response: Aborting Dialog", TextToSpeech.QUEUE_FLUSH, null, "");
                                scriptLine = 0;
                            }
                            break;

                        case 3:
                            if(!sortThroughRecognizerResults(matches, "element").matches(""))
                            {
                                toSpeech.speak("OK Trouble Ticket S U 16 24 has been raised for Asset number A 13 45 as a Priority 2 Ticket. . This Job has been allocated to Sean at . 087 28 41 23. . , . , You will receive a text message with an expected resolution day and time. . Please say okay or Not okay after the Tone.", TextToSpeech.QUEUE_FLUSH, null, "Scripted");
                            }
                            else
                            {
                                toSpeech.speak("Unacceptable Response: Aborting Dialog", TextToSpeech.QUEUE_FLUSH, null, "");
                                scriptLine = 0;
                            }
                            break;

                        case 4:
                            if(!sortThroughRecognizerResults(matches, "not").matches(""))
                            {
                                toSpeech.speak("Please advise, why is it not okay?", TextToSpeech.QUEUE_FLUSH, null, "Scripted");
                            }
                            else
                            {
                                toSpeech.speak("Unacceptable Response: Aborting Dialog", TextToSpeech.QUEUE_FLUSH, null, "");
                                scriptLine = 0;
                            }
                            break;

                        case 5:
                            if(!sortThroughRecognizerResults(matches, "priority 1").matches(""))
                            {
                                toSpeech.speak("Trouble Ticket S U 16 24 has been changed to priority 3, Please say okay or not okay after the tone.", TextToSpeech.QUEUE_FLUSH, null, "Scripted");
                            }
                            else
                            {
                                toSpeech.speak("Unacceptable Response: Aborting Dialog", TextToSpeech.QUEUE_FLUSH, null, "");
                                scriptLine = 0;
                            }
                            break;

                        case 6:
                            if(!sortThroughRecognizerResults(matches, "ok").matches(""))
                            {
                                toSpeech.speak("Thank you for using this service and on behalf of the SUDEXO hard services department, we apologise for the inconvenience caused as we rush to fix this issue ASAP", TextToSpeech.QUEUE_FLUSH, null, "");
                                scriptLine = 0;
                                createTroubleJson();
                                postDataToBrightspot();
                            }
                            else
                            {
                                toSpeech.speak("Unacceptable Response: Aborting Dialog", TextToSpeech.QUEUE_FLUSH, null, "");
                                scriptLine = 0;
                            }

                            //startSocketConnection();
                            break;
                    }


                    break;
            }
        }
    }
//++++++++[/Recognition Other Code]

//++++++++++[Brightspot Server Code]
    /*private void setupSocket()
    {
        networkClient = new OkHttpClient();
    }

    private void startSocketConnection() {
        Request request = new Request.Builder().url(serverURL).build();
        BBQWebSocketListener listener = new BBQWebSocketListener();
        wsSocket = networkClient.newWebSocket(request, listener);
        wsSocket.send(jsonToPass.toString());
        networkClient.dispatcher().executorService().shutdown();
    }

    private class BBQWebSocketListener extends WebSocketListener
    {
        private static final int NORMAL_CLOSURE_STATUS = 1000;

        @Override
        public void onOpen(WebSocket webSocket, Response response)
        {
            Log.i("WebSocket", " Socket onOpen. Response: " + response.toString());
            webSocket.close(NORMAL_CLOSURE_STATUS, "Goodbye !");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text)
        {
            Log.i("WebSocket", "Socket onMessage. Receiving : " + text);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes)
        {
            Log.i("WebSocket", "Socket onMessage. Receiving bytes : " + bytes.hex());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason)
        {
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
            Log.i("WebSocket", "Socket onMessage. Closing : " + code + " / " + reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response)
        {
            Log.i("WebSocket", "Socket onFailure. Error : " + t.getMessage());
        }
    }*/

    private void createJson(Date inDate, String inNameOfFood, double inTemperature)
    {
        JSONObject temper = new JSONObject();
        try
        {
            temper.put("value", inTemperature);
            temper.put("unit", "C");
        }
        catch (JSONException e)
        {
            Log.e("WebSocket", "Json Error while creating temper Json: " + e.toString());
        }

        JSONObject paras = new JSONObject();
        try
        {
            paras.put("date_milliseconds", inDate.getTime());
            paras.put("name", inNameOfFood);
            paras.put("temperature", temper);
        }
        catch (JSONException e)
        {
            Log.e("WebSocket", "Json Error while creating paras Json: " + e.toString());
        }

        jsonToPass = new JSONObject();
        try
        {
            jsonToPass.put("sender", "android_client");
            jsonToPass.put("method_name", "add_measurement");
            jsonToPass.put("params", paras);
        }
        catch (JSONException e)
        {
            Log.e("WebSocket", "Json Error while creating jsonToPass Json: " + e.toString());
        }

        Log.i("WebSocket", "Json Creation Complete: " + jsonToPass.toString());
    }

    private void createTroubleJson()
    {
        JSONObject paras = new JSONObject();

        jsonToPass = new JSONObject();
        try
        {
            jsonToPass.put("sender", "android_client");
            jsonToPass.put("method_name", "probe_trouble_ticket");
            jsonToPass.put("params", paras);
        }
        catch (JSONException e)
        {
            Log.e("WebSocket", "Json Error while creating jsonToPass Json: " + e.toString());
        }

        Log.i("WebSocket", "Json Creation Complete: " + jsonToPass.toString());
    }


    //**********[Location Update and server pinging Code]
    private void postDataToBrightspot()
    {
        try
        {

            serverURL = serverIP + "?data=" + URLEncoder.encode(jsonToPass.toString(), "UTF-8");
            //lat and long are doubles, will cause issue? nope
            Log.i("Network Update", "Attempting to start download from scanKeg. " + serverURL);
            aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);
        }
        catch (Exception e)
        {
            Log.e("Network Update", "Error: " + e.toString());
        }

    }


    //Update activity based on the results sent back by the servlet.
    @Override
    public void updateFromDownload(String result) {
        //intervalTextView.setText("Interval: " + result);

        if(result != null)
        {

        }
        else
        {
            Log.e("Network UPDATE", "Error: network unavaiable");
        }

        Log.e("Download Output", "" + result);
    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo;
    }

    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        switch(progressCode) {
            // You can add UI behavior for progress updates here.
            case Progress.ERROR:
                Log.e("Network Update", "Progress Error: there was an error during a progress report at: " + percentComplete + "%");
                break;
            case Progress.CONNECT_SUCCESS:
                Log.i("Network Update ", "connection successful during a progress report at: " + percentComplete + "%");
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                Log.i("Network Update ", "input stream acquired during a progress report at: " + percentComplete + "%");
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                Log.i("Network Update ", "input stream in progress during a progress report at: " + percentComplete + "%");
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                Log.i("Network Update ", "input stream processing successful during a progress report at: " + percentComplete + "%");
                break;
        }
    }

    @Override
    public void finishDownloading() {
        pingingServer = false;
        Log.i("Network Update", "finished Downloading");
        if (aNetworkFragment != null) {
            Log.e("Network Update", "network fragment found, canceling download");
            aNetworkFragment.cancelDownload();
        }
    }

    class AddressResultReceiver extends ResultReceiver
    {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            // Display the address string
            // or an error message sent from the intent service.
            resultData.getString(Constants.RESULT_DATA_KEY);


            // Show a toast message if an address was found.
            if (resultCode == Constants.SUCCESS_RESULT)
            {
                Log.i("Success", "Address found");
            }
            else
            {
                Log.e("Network Error:", "in OnReceiveResult in AddressResultReceiver: " +  resultData.getString(Constants.RESULT_DATA_KEY));
            }

        }
    }
//**********[/Location Update and server pinging Code]

//++++++++++[/Brightspot Server Code]
}




/*

private void postDataToBrightspot() throws IOException
    {
        serverURL = serverIP + "?data=" + jsonToPass.toString();

        /*URL url = new URL(serverURL);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        int response = urlConnection.getResponseCode();

        Log.i("Network Update", "Attempting to start download from postDataToBrightSpot: " + serverURL);
        aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);*

    URL url = new URL(serverIP);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);

                List<AbstractMap.SimpleEntry<String, String>> params = new ArrayList<AbstractMap.SimpleEntry<String, String>>();
        params.add(new AbstractMap.SimpleEntry<String, String>("data", jsonToPass.toString()));


        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(os, "UTF-8"));
        writer.write(getQuery(params));
        writer.flush();
        writer.close();
        os.close();

        conn.connect();
        Log.e("Network", "Response: " + conn.getResponseMessage());
        }

private String getQuery(List<AbstractMap.SimpleEntry<String, String>> params) throws UnsupportedEncodingException
        {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (AbstractMap.SimpleEntry<String, String> pair : params)
        {
        if (first)
        first = false;
        else
        result.append("&");

        result.append(URLEncoder.encode(pair.getKey(), "UTF-8"));
        result.append("=");
        result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
        }

        return result.toString();
        }
 */




















/*


*/
