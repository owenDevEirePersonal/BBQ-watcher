package com.deveire.dev.bbq_watcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;

public class LogActivity extends AppCompatActivity
{

    //[Saved Preferences Variables]
    private SharedPreferences savedData;
    private SharedPreferences.Editor edit;
    private int savedTaskCount;
    private ArrayList<String> savedTaskName;
    private ArrayList<String> savedTaskTimestamp;
    private ArrayList<Double> savedTaskPeak;
    //[/Saved Preferences Variables]

    private TextView logText;
    private Button clearButton;

    private String logString;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        //[SharedPreferences Setup]
        savedData = getApplicationContext().getSharedPreferences("BBQWatchLogsSavedData", Context.MODE_PRIVATE);
        savedTaskName = new ArrayList<String>();
        savedTaskTimestamp = new ArrayList<String>();
        savedTaskPeak = new ArrayList<Double>();
        savedTaskCount = savedData.getInt("TaskCount", 0);
        logString = "";
        NumberFormat degreeFormat = new DecimalFormat("#0.00");

        for(int i = 1; i <= savedTaskCount; i++)
        {
            savedTaskName.add(savedData.getString("TaskName" + i, "-TaskName" + i + " not found-"));
            savedTaskTimestamp.add(savedData.getString("TaskTimestamp" + i, "-TaskTimestamp" + i + " not found-"));
            savedTaskPeak.add((double) savedData.getFloat("TaskPeak" + i, 0));
            logString += savedTaskName.get(i - 1) + ": " + degreeFormat.format(savedTaskPeak.get(i - 1)) + "^C : " + savedTaskTimestamp.get(i - 1) + "\n\n";
        }
        //[/SharedPreferences Setup]

        logText = (TextView) findViewById(R.id.logText);
        logText.setText(logString);

        clearButton = (Button) findViewById(R.id.clearButton);
        clearButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                logString = "";
                logText.setText(logString);
                SharedPreferences.Editor edit = savedData.edit();
                edit.putInt("TaskCount", 0);
                edit.commit();
            }
        });
    }
}
