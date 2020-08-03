package com.nesl.main;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.nesl.ntpclasses.GoodClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

public class SensorRecordService extends Service  implements SensorEventListener {

    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    /*
     * Sensor Stuff
     */
    private SensorManager sensorManager;
    private Sensor mAccel;
    private Sensor mGyro;
    private Sensor mLight;
    private Sensor mMagnet;
    private Sensor mPressure;

    //GPS Stuff
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private int intervalTime = 0;
    private int maxWaitTime = 1500;
    private float smallestDisplacement = 3.0f;
    private OutputStream gpsOSStream;
    private OutputStreamWriter gpsOS;
    private final int FINE_LOCATION_PERMISSION_CODE = 2;
    private final int COARSE_LOCATION_PERMISSION_CODE = 2;

    // NTP Stuff

    /*
    Used to get the offset and the correct current time
     */
    protected GoodClock goodClock;
    private String timeZone = "America/Los_Angeles";

    // Indicator if GoodClock has received an initial fix. Because this was designed for a 15-20 minute trial, we don't care about too much drift.
    private boolean goodClockInitialFix = false;

    //End NTP Time Stuff



    //IMU File stuff
    private  OutputStream accelOSStream;
    private OutputStreamWriter accelOS;
    private  OutputStream gyroOSStream;
    private OutputStreamWriter gyroOS;
    private  OutputStream magnetOSStream;
    private OutputStreamWriter magnetOS;

    //Ambient Light File Stuff
    private OutputStream ambientLightOSStream;
    private OutputStreamWriter ambientLightOS;

    //Ambient Pressure File Stuff
    private OutputStream ambientPressureOSStream;
    private OutputStreamWriter ambientPressureOS;

    // Indicator for whether app is recording all checked modalities or not
    private volatile boolean isRecording = false;


    // Checkbox status
    private Boolean cb_pressureIsChecked = false;
    private Boolean cb_imuIsChecked = false;
    private Boolean cb_AmbientLightIsChecked = false;
    private Boolean cb_gpsIsChecked = false;
    private Boolean cb_timeDriftIsChecked = false;



    //ZeroMQ Stuff
    private ZMQ.Context context_=null;

    private ZMQ.Socket socket_ = null;


    public SensorRecordService() {
    }


    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String input = intent.getStringExtra("inputExtra");
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText(input)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);
        //cb_ambient.setEnabled(false);
        cb_AmbientLightIsChecked = intent.getExtras().getBoolean("cb_ambientLight");
        cb_imuIsChecked = intent.getExtras().getBoolean("cb_imu");
        cb_pressureIsChecked= intent.getExtras().getBoolean("cb_pressure");
        cb_gpsIsChecked = intent.getExtras().getBoolean("cb_gps");
        cb_timeDriftIsChecked = intent.getExtras().getBoolean("cb_timeDrift");

        // Get default sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mMagnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mPressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if(cb_imuIsChecked){
            sensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(this, mGyro, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(this, mMagnet, SensorManager.SENSOR_DELAY_GAME);

        }
        if(cb_AmbientLightIsChecked){

            sensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_GAME);
        }
        if(cb_pressureIsChecked){
            sensorManager.registerListener(this, mPressure, SensorManager.SENSOR_DELAY_GAME);

        }

        //ZeroMQ stuff:
        /*
        try  {
            ZContext context = new ZContext()
            // Socket to talk to clients
            ZMQ.Socket socket = context.createSocket(ZMQ.REP);
            socket.bind("tcp://*:5555");

            while (!Thread.currentThread().isInterrupted()) {
                // Block until a message is received
                byte[] reply = socket.recv(0);

                // Print the message
                System.out.println(
                        "Received: [" + new String(reply, ZMQ.CHARSET) + "]"
                );

                // Send a response
                String response = "Hello, world!";
                socket.send(response.getBytes(ZMQ.CHARSET), 0);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }*/


        //NTP Stuff
        //Starting the GoodClock library
        try{
            goodClock = new GoodClock(cb_timeDriftIsChecked);
            goodClock.start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }


        startRecording();
        //stopSelf();

        return START_STICKY;
    }

    private void createSensorFiles(){
        //startRecording();
        Long now = goodClock.Now();
        Date date = new Date(now);


        while(date.getYear() < 100)//Make sure we get a year greater than 1970
        {
            try{

                Thread.sleep(500);
            }

            catch (Exception e)
            {
                e.printStackTrace();
            }
            now = goodClock.Now();
            date = new Date(now);
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
        formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
        String dateFormatted = formatter.format(date);
        if (cb_imuIsChecked) {
            try {

                /** creates file path */
                String accelFileName = "accelData-" + dateFormatted;
                String gyroFileName = "gyroData-" + dateFormatted;
                String magnetFileName = "magnetData-" + dateFormatted;

                /** creates new folders in storage if they do not exist */
                File pathParent = new File( Environment.getExternalStoragePublicDirectory("NTPSense") + "/");
                if (!pathParent.exists())
                    pathParent.mkdir();
                File pathChild = new File(pathParent + "/accelData/");
                if (!pathChild.exists())
                    pathChild.mkdir();

                /** creates file paths */
                String accelFilePath = pathChild + "/" + accelFileName;
                accelOSStream = new FileOutputStream(accelFilePath + ".csv");
                accelOS = new OutputStreamWriter(accelOSStream);
                //Log.i("DEBUG Stuff","Accel file created****");
                String gyroFilePath = pathChild + "/" + gyroFileName;
                gyroOSStream = new FileOutputStream(gyroFilePath + ".csv");
                gyroOS = new OutputStreamWriter(gyroOSStream);
                String magnetFilePath = pathChild + "/" + magnetFileName;
                magnetOSStream = new FileOutputStream(magnetFilePath + ".csv");
                magnetOS = new OutputStreamWriter(magnetOSStream);



            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        if(cb_pressureIsChecked) {
            try {

                /** creates file path */
                String ambientPressureFileName = "ambientPressureData-" + dateFormatted;

                /** creates new folders in storage if they do not exist */
                File pathParent = new File( Environment.getExternalStoragePublicDirectory("NTPSense") + "/");
                if (!pathParent.exists())
                    pathParent.mkdir();
                File pathChild = new File(pathParent + "/ambientPressureData/");
                if (!pathChild.exists())
                    pathChild.mkdir();

                /** creates file paths */
                String ambientPressureFilePath = pathChild + "/" + ambientPressureFileName;
                ambientPressureOSStream = new FileOutputStream(ambientPressureFilePath + ".csv");
                ambientPressureOS = new OutputStreamWriter(ambientPressureOSStream);


            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        if(cb_AmbientLightIsChecked){
            try {

                /** creates file path */
                String ambientLightFileName = "ambientLightData-" + dateFormatted;

                /** creates new folders in storage if they do not exist */
                File pathParent = new File( Environment.getExternalStoragePublicDirectory("NTPSense") + "/");
                if (!pathParent.exists())
                    pathParent.mkdir();
                File pathChild = new File(pathParent + "/ambientLightData/");
                if (!pathChild.exists())
                    pathChild.mkdir();

                /** creates file paths */
                String ambientLightFilePath = pathChild + "/" + ambientLightFileName;
                ambientLightOSStream = new FileOutputStream(ambientLightFilePath + ".csv");
                ambientLightOS = new OutputStreamWriter(ambientLightOSStream);


            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        if(cb_gpsIsChecked)
        {
            try {
                //GPS Stuff:
                /** creates file path */
                String gpsFileName = "gpsData-" + dateFormatted;

                /** creates new folders in storage if they do not exist */
                File pathParent = new File(Environment.getExternalStoragePublicDirectory("NTPSense") + "/");
                if (!pathParent.exists())
                    pathParent.mkdir();
                File pathChild = new File(pathParent + "/gpsData/");
                if (!pathChild.exists())
                    pathChild.mkdir();
                locationRequest = new LocationRequest();
                locationRequest.setInterval(intervalTime);
                locationRequest.setMaxWaitTime(maxWaitTime);
                locationRequest.setSmallestDisplacement(smallestDisplacement);
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                String gpsFilePath = pathChild + "/" + gpsFileName;
                gpsOSStream = new FileOutputStream(gpsFilePath + ".csv");
                gpsOS = new OutputStreamWriter(gpsOSStream);
                locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        if (locationResult == null) {
                            return;
                        }
                        for (Location location : locationResult.getLocations()) {
                            // In this example, alpha is calculated as t / (t + dT),
                            // where t is the low-pass filter's time-constant and
                            // dT is the event delivery rate
                            String locationStr = "Lat: "+ location.getLatitude()+", Long: " + location.getLongitude();
                            Long now = goodClock.Now();
                            Date date = new Date(now);
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                            formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
                            String dateFormatted = formatter.format(date);
                            try {
                                gpsOS.append(dateFormatted + ", " + locationStr + "\n");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    ;
                };
                startLocationUpdates();
            }catch(IOException e)
            {
                e.printStackTrace();
            }
        }

    }


    private void startRecording()
    {
        createSensorFiles();
        isRecording= true;
    }


    private void startLocationUpdates() {

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                null /* Looper */);
    }

    private void stopLocationUpdates() {

        fusedLocationClient.removeLocationUpdates(locationCallback);
        try {
            gpsOS.flush();
            gpsOS.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void closeSensorFiles(){
        if(cb_imuIsChecked){
            try {
                accelOS.flush();
                accelOS.close();
                gyroOS.flush();
                gyroOS.close();
                magnetOS.flush();
                magnetOS.close();
            }catch(IOException e)
            {
                e.printStackTrace();
            }
        }
        if(cb_pressureIsChecked){
            try{
                ambientPressureOS.flush();
                ambientPressureOS.close();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        if(cb_AmbientLightIsChecked) {
            try{
                ambientLightOS.flush();
                ambientLightOS.close();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "recording done", Toast.LENGTH_SHORT).show();
        closeSensorFiles();
        if(cb_gpsIsChecked)
        {
            stopLocationUpdates();
        }
        isRecording = false;
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.

        // This is only to raise a flag if we got our initial GoodClock fix.
        if(goodClock.SntpSuceeded){
            goodClockInitialFix = true;
        }

        Log.i("OnSensorChanged", "Got value...");

        if(isRecording) {
            Log.i("OnSensorChanged", "Still recording...");
            switch(event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                   // Log.i("OnSensorChanged", "Got Accel...");
                    if(cb_imuIsChecked && goodClockInitialFix) {
                        // In this example, alpha is calculated as t / (t + dT),
                        // where t is the low-pass filter's time-constant and
                        // dT is the event delivery rate
                        String accel = event.values[0] + ", " + event.values[1] + "," + event.values[2];
                        Long now = goodClock.Now();
                        Date date = new Date(now);
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                        formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
                        String dateFormatted = formatter.format(date);
                        try {
                            Log.i("OnSensorChanged", "Accel: "+ dateFormatted + ", " + accel );
                            accelOS.append(dateFormatted + ", " + accel + "\n");
                        } catch (IOException e) {
                            Log.i("OnSensorChanged", "There was an error writing accel");
                            e.printStackTrace();
                            closeSensorFiles();
                            createSensorFiles();
                        }
                    }
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:

                    Log.i("OnSensorChanged", "Got Mag...");
                    if(cb_imuIsChecked && goodClockInitialFix) {
                        // In this example, alpha is calculated as t / (t + dT),
                        // where t is the low-pass filter's time-constant and
                        // dT is the event delivery rate
                        String magnet = event.values[0] + ", " + event.values[1] + "," + event.values[2];
                        Long now = goodClock.Now();
                        Date date = new Date(now);
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                        formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
                        String dateFormatted = formatter.format(date);
                        try {
                            Log.i("OnSensorChanged", "Mag: "+ dateFormatted + ", " + magnet );
                            magnetOS.append(dateFormatted + ", " + magnet + "\n");
                        } catch (IOException e) {
                            Log.i("OnSensorChanged", "There was an error writing magnet");
                            e.printStackTrace();
                            closeSensorFiles();
                            createSensorFiles();
                        }
                    }
                    break;
                case Sensor.TYPE_GYROSCOPE:

                    Log.i("OnSensorChanged", "Got Gyro...");
                    if(cb_imuIsChecked && goodClockInitialFix) {

                        // In this example, alpha is calculated as t / (t + dT),
                        // where t is the low-pass filter's time-constant and
                        // dT is the event delivery rate
                        String gyro = event.values[0] + ", " + event.values[1] + "," + event.values[2];
                        Long now = goodClock.Now();
                        Date date = new Date(now);
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                        formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
                        String dateFormatted = formatter.format(date);
                        try {
                            Log.i("OnSensorChanged", "Gyro: "+ dateFormatted + ", " + gyro );
                            gyroOS.append(dateFormatted + ", " + gyro + "\n");

                        } catch (IOException e) {

                            Log.i("OnSensorChanged", "There was an error writing gyro");
                            e.printStackTrace();
                            closeSensorFiles();
                            createSensorFiles();
                        }
                    }
                    break;
                case Sensor.TYPE_LIGHT:

                    Log.i("OnSensorChanged", "Got Ambient Light...");
                    if(cb_AmbientLightIsChecked && goodClockInitialFix){
                        // In this example, alpha is calculated as t / (t + dT),
                        // where t is the low-pass filter's time-constant and
                        // dT is the event delivery rate
                        String ambientLight = String.valueOf(event.values[0]);
                        Long now = goodClock.Now();
                        Date date = new Date(now);
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                        formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
                        String dateFormatted = formatter.format(date);
                        try {
                            Log.i("OnSensorChanged", "ambient: "+ dateFormatted + ", " + ambientLight );
                            ambientLightOS.append(dateFormatted + ", " + ambientLight + "\n");
                        } catch (IOException e) {
                            Log.i("OnSensorChanged", "There was an error writing ambient");
                            e.printStackTrace();
                            closeSensorFiles();
                            createSensorFiles();
                        }
                    }

                    break;
                case Sensor.TYPE_PRESSURE:

                    Log.i("OnSensorChanged", "Got Ambient Pressure...");
                    if(cb_pressureIsChecked && goodClockInitialFix){
                        // In this example, alpha is calculated as t / (t + dT),
                        // where t is the low-pass filter's time-constant and
                        // dT is the event delivery rate
                        String pressure = String.valueOf(event.values[0]);
                        Long now = goodClock.Now();
                        Date date = new Date(now);
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                        formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
                        String dateFormatted = formatter.format(date);
                        try {
                            Log.i("OnSensorChanged", "ambient pressure: "+ dateFormatted + ", " + pressure );
                            ambientPressureOS.append(dateFormatted + ", " + pressure + "\n");
                        } catch (IOException e) {
                            Log.i("OnSensorChanged", "There was an error writing ambient");
                            e.printStackTrace();
                            closeSensorFiles();
                            createSensorFiles();
                        }
                    }

                    break;
                default:

                    Log.i("OnSensorChanged", "Got Unknown Sensor!...");
                    break;
            }

        }

        //float lux = event.values[0];
        // Do something with this sensor value.
    }


        /* Checks if external storage is available for read and write */
        public boolean isExternalStorageWritable() {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                return true;
            }
            return false;
        }

        /* Checks if external storage is available to at least read */
        public boolean isExternalStorageReadable() {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state) ||
                    Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                return true;
            }
            return false;
        }



    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}
