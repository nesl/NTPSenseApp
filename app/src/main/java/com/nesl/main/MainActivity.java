package com.nesl.main;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.nesl.ntpsense.R;

import java.io.OutputStream;
import java.io.OutputStreamWriter;


public class MainActivity extends AppCompatActivity  {

    /*
     * Sensor Stuff
     */
    private SensorManager sensorManager;
    private Sensor mAccel;
    private Sensor mGyro;
    private Sensor mLight;
    private Sensor mMagnet;

    //GPS Stuff
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private int intervalTime = 0;
    private int maxWaitTime = 1500;
    private float smallestDisplacement = 3.0f;
    private  OutputStream gpsOSStream;
    private OutputStreamWriter gpsOS;
    private final int FINE_LOCATION_PERMISSION_CODE = 2;





    // Audio Stuff
    protected final int RECORD_AUDIO_PERMISSION_CODE = 1;



    // Resources
    private ProgressBar pb_Record;
    private Button bt_Record;
    private TextView tv_recordUpdate;
    private CheckBox cb_pressure;
    private CheckBox cb_imu;
    private CheckBox cb_ambient;
    private CheckBox cb_gps;



    // Indicator for whether app is recording all checked modalities or not
    private volatile boolean isRecording = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bt_Record = findViewById(R.id.buttonRecord);
        pb_Record = findViewById(R.id.progressBar);
        pb_Record.setVisibility(View.INVISIBLE);
        tv_recordUpdate = findViewById(R.id.textViewRecordUpdate);
        tv_recordUpdate.setVisibility(View.INVISIBLE);
        cb_pressure = findViewById(R.id.checkBoxPressure);
        //cb_audio.setEnabled(false);
        cb_imu = findViewById(R.id.checkBoxIMU);
        cb_ambient = findViewById(R.id.checkBoxAmbient);
        cb_gps = findViewById(R.id.checkBoxGPS);

    }


    @Override
    protected void onResume() {
        super.onResume();

        if(isRecording) {
            bt_Record.setText("Stop Recording");
            //bt_Record.setText(dateFormatted);
            bt_Record.setBackgroundColor(Color.RED);
            cb_imu.setEnabled(false);
            cb_ambient.setEnabled(false);
            cb_pressure.setEnabled(false);
            cb_gps.setEnabled(false);
            pb_Record.setVisibility(View.VISIBLE);
            tv_recordUpdate.setVisibility(View.VISIBLE);
        }

    }


    @Override
    protected void onPause() {
        super.onPause();


    }


    //Function for button record click
    public void recordClick(View v) {
        // check if permissions are already granted
        if (ContextCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED )
        {
            ActivityCompat.requestPermissions(
                    this,
                    new String [] { Manifest.permission.ACCESS_FINE_LOCATION },
                    FINE_LOCATION_PERMISSION_CODE
            );
        }

        // check if permissions are already granted
        if (checkPermissionFromDevice(RECORD_AUDIO_PERMISSION_CODE)) {
            //openConfirmDialog();
        }
        // if permissions aren't granted, request them
        else {
            requestPermissions(RECORD_AUDIO_PERMISSION_CODE);
        }

        // check if permissions are already granted
        if (ContextCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED )
        {
            ActivityCompat.requestPermissions(
                    this,
                    new String [] { Manifest.permission.ACCESS_FINE_LOCATION },
                    FINE_LOCATION_PERMISSION_CODE
            );
        }

        if(!isRecording) {
            if(cb_imu.isChecked() || cb_pressure.isChecked() || cb_ambient.isChecked() || cb_gps.isChecked()) {

                //startService
                bt_Record.setText("Stop Recording");
                //bt_Record.setText(dateFormatted);
                bt_Record.setBackgroundColor(Color.RED);
                cb_imu.setEnabled(false);
                cb_ambient.setEnabled(false);
                cb_pressure.setEnabled(false);
                cb_gps.setEnabled(false);
                pb_Record.setVisibility(View.VISIBLE);
                tv_recordUpdate.setVisibility(View.VISIBLE);
                isRecording = true;
                Intent intent = new Intent(MainActivity.this, SensorRecordService.class);
                intent.putExtra("cb_ambientLight", cb_ambient.isChecked());
                intent.putExtra("cb_imu", cb_imu.isChecked());
                intent.putExtra("cb_pressure", cb_pressure.isChecked());
                intent.putExtra("cb_gps", cb_gps.isChecked());
                ContextCompat.startForegroundService(this, intent);
                //startService(intent);
            }
        }else
        {
            bt_Record.setText("Start Recording");
            bt_Record.setBackgroundColor(Color.GREEN);
            isRecording = false;

            pb_Record.setVisibility(View.INVISIBLE);
            tv_recordUpdate.setVisibility(View.INVISIBLE);
            cb_imu.setEnabled(true);
            cb_ambient.setEnabled(true);
            cb_pressure.setEnabled(true);
            cb_gps.setEnabled(true);
            Intent intent = new Intent(MainActivity.this, SensorRecordService.class);
            stopService(intent);
        }




    }




    public void stopRecording(View v) {

    }

    /**
     * List of Permission methods
     */
    private boolean checkPermissionFromDevice(int permissions) {

        switch (permissions) {
            case RECORD_AUDIO_PERMISSION_CODE: {
                // int variables will be 0 if permissions are not granted already
                int write_external_storage_result =
                        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                int record_audio_result =
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);

                // returns true if both permissions are already granted
                return write_external_storage_result == PackageManager.PERMISSION_GRANTED &&
                        record_audio_result == PackageManager.PERMISSION_GRANTED;
            }
            default:
                return false;
        }
    }

    private void requestPermissions(int permissions) {

        switch (permissions) {
            case RECORD_AUDIO_PERMISSION_CODE: {
                // used to pass what permissions were requested
                String[] permissionsRequested = {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO};

                // requests all necessary permissions
                ActivityCompat.requestPermissions(this, permissionsRequested, permissions);
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean permissionGranted = false;

        switch (requestCode) {
            /*
            case RECORD_AUDIO_PERMISSION_CODE: {
                if (grantResults.length > 0) {
                    for (int i : grantResults) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                            permissionGranted = true;
                    }

                    if (permissionGranted) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                    } else
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                } else
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();

                break;
            }

             */

            default:
                break;
        }
    }


















}
