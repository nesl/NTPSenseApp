package com.nesl.main;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.nesl.ntpclasses.GoodClock;
import com.nesl.ntpsense.R;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

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
    //End NTP Time Stuff



    // Audio Stuff
    protected boolean isAudioRecording = false;
    protected final int RECORD_AUDIO_PERMISSION_CODE = 1;
    private RecordSoundRunnable rsRunnable;
    protected OutputStream audioOSStream;
    private AudioRecord recorder;


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


    // Indicator for whether app is recording all checked modalities or not
    private volatile boolean isRecording = false;

    // Checkbox status
    private Boolean cb_audioIsChecked;
    private Boolean cb_imuIsChecked;
    private Boolean cb_ambientIsChecked;
    private Boolean cb_gpsIsChecked;

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
        //cb_ambient.setEnabled(false);
        cb_ambientIsChecked = intent.getExtras().getBoolean("cb_ambient");
        cb_imuIsChecked = intent.getExtras().getBoolean("cb_imu");
        cb_audioIsChecked= intent.getExtras().getBoolean("cb_audio");
        cb_gpsIsChecked = intent.getExtras().getBoolean("cb_gps");

        // Get default sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mMagnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        sensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, mGyro, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, mMagnet, SensorManager.SENSOR_DELAY_GAME);

        //do heavy work on a background thread

        //NTP Stuff
        //Starting the GoodClock library
        try{
            goodClock = new GoodClock();
            goodClock.start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        //Let Goodclock warmup
        try{

            Thread.sleep(2000);
        }

        catch (Exception e)
        {
            e.printStackTrace();
        }

        startRecording();
        //stopSelf();

        return START_STICKY;
    }
    private void startRecording()
    {
        //startRecording();
        Long now = goodClock.Now();
        Date date = new Date(now);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
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
        if(cb_audioIsChecked) {
            String fileName = "audioData";
            isAudioRecording = true;
            rsRunnable = new RecordSoundRunnable(fileName);
            new Thread(rsRunnable).start();

        }

        if(cb_ambientIsChecked){
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
                            formatter.setTimeZone(TimeZone.getTimeZone("PST"));
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "recording done", Toast.LENGTH_SHORT).show();
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
        if(cb_audioIsChecked){
            try{
                isAudioRecording = false;
                recorder.stop();
                recorder.release();
                recorder = null;
                audioOSStream.flush();
                audioOSStream.close();
            }catch(IOException e)
            {
                e.printStackTrace();
            }
        }
        if(cb_ambientIsChecked) {
            try{
                ambientLightOS.flush();
                ambientLightOS.close();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        if(cb_gpsIsChecked)
        {
            stopLocationUpdates();
        }
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.

        if(isRecording) {
            switch(event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    if(cb_imuIsChecked && goodClock.SntpSuceeded) {
                        // In this example, alpha is calculated as t / (t + dT),
                        // where t is the low-pass filter's time-constant and
                        // dT is the event delivery rate
                        String accel = event.values[0] + ", " + event.values[1] + "," + event.values[2];
                        Long now = goodClock.Now();
                        Date date = new Date(now);
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
                        String dateFormatted = formatter.format(date);
                        try {
                            accelOS.append(dateFormatted + ", " + accel + "\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    if(cb_imuIsChecked && goodClock.SntpSuceeded) {
                        // In this example, alpha is calculated as t / (t + dT),
                        // where t is the low-pass filter's time-constant and
                        // dT is the event delivery rate
                        String magnet = event.values[0] + ", " + event.values[1] + "," + event.values[2];
                        Long now = goodClock.Now();
                        Date date = new Date(now);
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
                        String dateFormatted = formatter.format(date);
                        try {
                            magnetOS.append(dateFormatted + ", " + magnet + "\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    if(cb_imuIsChecked && goodClock.SntpSuceeded) {
                        // In this example, alpha is calculated as t / (t + dT),
                        // where t is the low-pass filter's time-constant and
                        // dT is the event delivery rate
                        String gyro = event.values[0] + ", " + event.values[1] + "," + event.values[2];
                        Long now = goodClock.Now();
                        Date date = new Date(now);
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
                        String dateFormatted = formatter.format(date);
                        try {
                            gyroOS.append(dateFormatted + ", " + gyro + "\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case Sensor.TYPE_LIGHT:
                    if(cb_ambientIsChecked && goodClock.SntpSuceeded){
                        // In this example, alpha is calculated as t / (t + dT),
                        // where t is the low-pass filter's time-constant and
                        // dT is the event delivery rate
                        String ambientLight = String.valueOf(event.values[0]);
                        Long now = goodClock.Now();
                        Date date = new Date(now);
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
                        String dateFormatted = formatter.format(date);
                        try {
                            ambientLightOS.append(dateFormatted + ", " + ambientLight + "\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    break;
            }

        }

        //float lux = event.values[0];
        // Do something with this sensor value.
    }

    /**
     * CLASS: records audio
     */
    class RecordSoundRunnable implements Runnable {

        /**
         * MEMBER VARIABLES
         */
        private static final int RECORDER_SOURCE = MediaRecorder.AudioSource.UNPROCESSED;
        private static final int RECORDER_SAMPLERATE = 44100;
        private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
        private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        private int BufferElementsToRec = 1024;  // want to play 2048 (2K) since 2 bytes we use only 1024
        private int BytesPerElement = 2;        // 2 bytes in 16bit format

        private String m_fileName;

        /**
         * CONSTRUCTOR
         */
        RecordSoundRunnable(String fileName) {

            this.m_fileName = fileName;
            int bufferSize = AudioRecord.getMinBufferSize(
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
            recorder = new AudioRecord(
                    RECORDER_SOURCE, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);
            isRecording = true;
        }

        /**
         * RUN
         */
        @Override
        public void run() {

            /** initial buffer of 5 secs
             try {
             Thread.sleep(1000);
             } catch (Exception e) {
             e.printStackTrace();
             }*/

            try {
                //startRecording();
                Long now = goodClock.Now();
                Date date = new Date(now);
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                formatter.setTimeZone(TimeZone.getTimeZone("PST"));
                String dateFormatted = formatter.format(date);

                /** creates new folders in storage if they do not exist */
                File pathParent = new File( Environment.getExternalStoragePublicDirectory("NTPSense") + "/");
                if (!pathParent.exists())
                    pathParent.mkdir();
                File pathChild = new File(pathParent + "/audioData/");
                if (!pathChild.exists())
                    pathChild.mkdir();



                /** creates file path */
                String fileName = getFileName();
                String filePath = pathChild + "/" + fileName + "-" + dateFormatted;
                audioOSStream = new FileOutputStream(filePath + ".pcm");

                /** unknown */
                short soundData[] = new short[BufferElementsToRec];

                /** starts recording for 3 secs */

                recorder.startRecording();
                while (isAudioRecording) {
                    recorder.read(soundData, 0, BufferElementsToRec);
                    // writes the data to file from buffer
                    byte bufferData[] = shortToByte(soundData);
                    // stores the voice buffer
                    audioOSStream.write(bufferData, 0, BufferElementsToRec * BytesPerElement);

                }

                /** stops recording */


                /** buffer of 1 sec in between taking samples */
                //Thread.sleep(1000);

                /** converts pcm file to wav
                 File f1 = new File(filePath + ".pcm"); // The location of your PCM file
                 File f2 = new File(filePath + ".wav"); // The location where you want your WAV file
                 try {
                 rawToWave(f1, f2);
                 f1.delete();
                 } catch (IOException e) {
                 e.printStackTrace();
                 f1.delete();
                 }
                 */
            } catch (Exception e) {
                e.printStackTrace();
            }



            /** cleanup */
            recorder.release();
            recorder = null;
        }

        /** MEMBER FUNCTIONS */

        /**
         * names file
         */
        private String getFileName() {
            Date time = new Date(System.currentTimeMillis());
            return (m_fileName + " " + time);
        }


        /**
         * converts short to byte
         */
        private byte[] shortToByte(short[] soundData) {
            int shortArrSize = soundData.length;
            byte[] bytes = new byte[shortArrSize * 2];
            for (int i = 0; i < shortArrSize; i++) {
                bytes[i * 2] = (byte) (soundData[i] & 0x00FF);
                bytes[(i * 2) + 1] = (byte) (soundData[i] >> 8);
                soundData[i] = 0;
            }
            return bytes;
        }

        /**
         * PCM to WAV
         */
        private void rawToWave(final File rawFile, final File waveFile) throws IOException {

            byte[] rawData = new byte[(int) rawFile.length()];
            DataInputStream input = null;
            try {
                input = new DataInputStream(new FileInputStream(rawFile));
                input.read(rawData);
            } finally {
                if (input != null) {
                    input.close();
                }
            }

            DataOutputStream output = null;
            try {
                output = new DataOutputStream(new FileOutputStream(waveFile));
                // WAVE header
                // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
                writeString(output, "RIFF"); // chunk id
                writeInt(output, 36 + rawData.length); // chunk size
                writeString(output, "WAVE"); // format
                writeString(output, "fmt "); // subchunk 1 id
                writeInt(output, 16); // subchunk 1 size
                writeShort(output, (short) 1); // audio format (1 = PCM)
                writeShort(output, (short) 1); // number of channels
                writeInt(output, 44100); // sample rate
                writeInt(output, RECORDER_SAMPLERATE * 2); // byte rate
                writeShort(output, (short) 2); // block align
                writeShort(output, (short) 16); // bits per sample
                writeString(output, "data"); // subchunk 2 id
                writeInt(output, rawData.length); // subchunk 2 size
                // Audio data (conversion big endian -> little endian)
                short[] shorts = new short[rawData.length / 2];
                ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
                for (short s : shorts) {
                    bytes.putShort(s);
                }

                output.write(fullyReadFileToBytes(rawFile));
            } finally {
                if (output != null) {
                    output.close();
                }
            }
        }

        byte[] fullyReadFileToBytes(File f) throws IOException {
            int size = (int) f.length();
            byte bytes[] = new byte[size];
            byte tmpBuff[] = new byte[size];
            FileInputStream fis = new FileInputStream(f);
            try {

                int read = fis.read(bytes, 0, size);
                if (read < size) {
                    int remain = size - read;
                    while (remain > 0) {
                        read = fis.read(tmpBuff, 0, remain);
                        System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
                        remain -= read;
                    }
                }
            } catch (IOException e) {
                throw e;
            } finally {
                fis.close();
            }

            return bytes;
        }

        private void writeInt(final DataOutputStream output, final int value) throws IOException {
            output.write(value >> 0);
            output.write(value >> 8);
            output.write(value >> 16);
            output.write(value >> 24);
        }

        private void writeShort(final DataOutputStream output, final short value) throws IOException {
            output.write(value >> 0);
            output.write(value >> 8);
        }

        private void writeString(final DataOutputStream output, final String value) throws IOException {
            for (int i = 0; i < value.length(); i++) {
                output.write(value.charAt(i));
            }
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

    }
}
