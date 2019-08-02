package com.nesl.main;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.nesl.ntpclasses.Dsense;
import com.nesl.ntpclasses.SntpClient;
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

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    /*
     * Sensor Stuff
     */
    private SensorManager sensorManager;
    private Sensor mAccel;
    private Sensor mGyro;
    private Sensor mLight;
    private Sensor mAudio;

    // NTP Stuff
    long ntp_sleep_time = 30000; // in milliseconds

    long ntp_offset_global=0;

    SntpClient SNTP_CLIENT = null;
    float _rootDelayMax = 100;
    float _rootDispersionMax = 100;
    int _serverResponseDelayMax = 750;
    int _udpSocketTimeoutInMillis = 5_000;

    //String _ntpHost = "1.us.pool.ntp.org";
    String _ntpHost = "17.253.26.253";//"time.apple.com";
    TextView text_ntp_diff;
    Button bt_enable_ntp;
    boolean ntp_thread_running = false;
    String filename_ntp = "ntpTime.txt";
    public Dsense library_dsense;
    Handler mHandler;

    //End NTP Time Stuff

    private final int RECORD_AUDIO_PERMISSION_CODE = 1;

    private Handler mainHandler;
    private RecordSoundRunnable rsRunnable;
    
    // Audio Stuff
    private boolean isAudioRecording = false;

    // Resources
    private ProgressBar pb_Record;
    private Button bt_Record;
    private TextView tv_recordUpdate;
    private TextView tv_recordCancel;
    private TextView tv_accel;
    private TextView tv_ntpTime;
    private String m_fileName;
    private AudioRecord recorder;
    private CheckBox cb_audio;
    private CheckBox cb_imu;
    private CheckBox cb_ambient;

    //File stuff
    private  File accelPath;
    private File accelFile;
    private  String accelPathFileName = "accelData";
    private  OutputStream accelOSStream;
    private OutputStreamWriter accelOS;
    private  String currAccelFileName = "";

    private volatile boolean isRecording = false;
    private int m_numOfSamples;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainHandler = new Handler();
        bt_Record = findViewById(R.id.buttonRecord);
        pb_Record = findViewById(R.id.progressBar);
        pb_Record.setVisibility(View.INVISIBLE);
        tv_recordUpdate = findViewById(R.id.textViewRecordUpdate);
        tv_recordUpdate.setVisibility(View.INVISIBLE);
        cb_audio = findViewById(R.id.checkBoxAudio);
        cb_audio.setEnabled(false);
        cb_imu = findViewById(R.id.checkBoxIMU);
        cb_ambient = findViewById(R.id.checkBoxAmbient);
        cb_ambient.setEnabled(false);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //tv_accel = findViewById(R.id.accelerometerData);
        //tv_ntpTime = findViewById(R.id.NTPTime);

        final Resources res = this.getResources();
        int id = Resources.getSystem().getIdentifier(
                "config_ntpServer", "string","android");
        String defaultServer = res.getString(id);



        int id2=Resources.getSystem().getIdentifier(
                "config_ntpPollingInterval", "integer","android");
        //getApplicationContext().getResources().getInteger(com.android.internal.R.integer.config_ntpPollingInterval);

        int mPollingIntervalMs = res.getInteger(id2);
        //System.out.println(":NTP UPDATE Server:"+defaultServer+" : Timeout:"+mPollingIntervalMs);

        //End NTP System Details

        // Record to the external cache directory for visibility
        //mFileName = getExternalCacheDir().getAbsolutePath();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        //initializing SNTP client
        SNTP_CLIENT = new SntpClient();



        try{
            library_dsense = new Dsense();
            library_dsense.start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
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
                if(cb_imu.isChecked()) {
                    // In this example, alpha is calculated as t / (t + dT),
                    // where t is the low-pass filter's time-constant and
                    // dT is the event delivery rate
                    String accel = event.values[0] + ", " + event.values[1] + "," + event.values[2];
                    Long now = library_dsense.currentTimeMillis();
                    Date date = new Date(now);
                    SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");
                    formatter.setTimeZone(TimeZone.getTimeZone("PST"));
                    String dateFormatted = formatter.format(date);
                    String dName = dateFormatted.replace(':', '-');
                    //System.out.println("ACCEL DATA: " + accel);
                    try {
                        accelOS.append(dName + ", " + accel + "\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case Sensor.TYPE_LIGHT:
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                break;
        }

        }

        //float lux = event.values[0];
        // Do something with this sensor value.
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);

        //Close the files once we are done recording
        if(isRecording)
        {
            recordClick(findViewById(R.id.buttonRecord));
        }

    }

    //Function for button record click
    public void recordClick(View v) {

        // check if permissions are already granted
        if (checkPermissionFromDevice(RECORD_AUDIO_PERMISSION_CODE)) {
            //openConfirmDialog();
        }
        // if permissions aren't granted, request them
        else {
            requestPermissions(RECORD_AUDIO_PERMISSION_CODE);
        }

        if(!isRecording) {
            if(cb_imu.isChecked() || cb_audio.isChecked() || cb_ambient.isChecked()) {

                //startRecording();
                Long now = library_dsense.currentTimeMillis();
                Date date = new Date(now);
                SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");
                formatter.setTimeZone(TimeZone.getTimeZone("PST"));
                String dateFormatted = formatter.format(date);
                String dName = dateFormatted.replace(':', '-');
                dName = dName.replace('.', '-');

                if (cb_imu.isChecked()) {
                    try {

                        /** creates file path */
                        String fileName = "accelData-" + dName;
                        /*File pathParent =  Environment.getDataDirectory();
                        if (!pathParent.exists())
                            pathParent.mkdir();

                        File pathChild = new File(pathParent + "/accelData/");
                        if (!pathChild.exists())
                            pathChild.mkdir();*/

                        /** creates new folders in storage if they do not exist */
                        File pathParent = new File( Environment.getExternalStoragePublicDirectory("NTPSense") + "/");
                        if (!pathParent.exists())
                            pathParent.mkdir();
                        File pathChild = new File(pathParent + "/accelData/");
                        if (!pathChild.exists())
                            pathChild.mkdir();

                        /** creates file path */
                        String filePath = pathChild + "/" + fileName;
                        accelOSStream = new FileOutputStream(filePath + ".csv");
                        /*accelFile = new File(Environment.getDataDirectory(), filePath);
                        if (!accelFile.mkdirs()) {
                            Log.e("NTPSENSE", "Directory not created");
                        }*/
                        //accelFile = new File(this.getFilesDir(), filePath);
                        //accelFile.getParentFile().mkdirs();
                        //accelFile.createNewFile();
                       // accelOSStream = new FileOutputStream(accelFile,true);
                        accelOS = new OutputStreamWriter(accelOSStream);

                        bt_Record.setText("Stop Recording");
                        bt_Record.setBackgroundColor(Color.RED);
                        cb_imu.setEnabled(false);
                        cb_ambient.setEnabled(false);
                        cb_audio.setEnabled(false);
                        pb_Record.setVisibility(View.VISIBLE);
                        tv_recordUpdate.setVisibility(View.VISIBLE);
                        isRecording = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
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
            cb_audio.setEnabled(true);

            if(cb_imu.isChecked()){
                try {
                    accelOS.flush();
                    accelOS.close();
                }catch(IOException e)
                {
                    e.printStackTrace();
                }
            }
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
            case RECORD_AUDIO_PERMISSION_CODE: {
                if (grantResults.length > 0) {
                    for (int i : grantResults) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                            permissionGranted = true;
                    }

                    if (permissionGranted) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                        openConfirmDialog();
                    } else
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                } else
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();

                break;
            }
        }
    }

    /**
     * Confirm to start recording and name files.
     */
    public void openConfirmDialog() {
        AlertDialog.Builder popUp = new AlertDialog.Builder(this);
        popUp.setTitle("Start Recording?");
        popUp.setMessage("Enter the file name(s)");

        final EditText input = new EditText(this);
        popUp.setView(input);

        popUp.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
               // rsRunnable = new RecordSoundRunnable(input.getText().toString());
                new Thread(rsRunnable).start();
                // disable all buttons
            }
        });

        popUp.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            }
        });

        popUp.show();
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
        private AudioRecord recorder;
        private volatile boolean isRecording;

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

            /** show progress bar and cancel button */
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                   // pb_record.setVisibility(View.VISIBLE);
                   // b_recordCancel.setVisibility(View.VISIBLE);
                }
            });

            /** initial buffer of 5 secs */
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            /** take specified number of samples */
            for (int i = 0; i < m_numOfSamples; i++) {

                /** check to see if cancel button has been used */
                if (!isRecording) {
                    /** hide progress bar and cancel button */
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            //pb_record.setVisibility(View.INVISIBLE);
                            //pb_recordCancel.setVisibility(View.INVISIBLE);
                            tv_recordCancel.setVisibility(View.INVISIBLE);
                        }
                    });
                    return;
                }

                try {
                    /** creates new folders in storage if they do not exist */
                    File pathParent = new File(Environment.getExternalStoragePublicDirectory("Sound Bytes") + "/");
                    if (!pathParent.exists())
                        pathParent.mkdir();
                    File pathChild = new File(pathParent + "/" + m_fileName + "/");
                    if (!pathChild.exists())
                        pathChild.mkdir();

                    /** creates file path */
                    String fileName = getFileName();
                    String filePath = pathChild + "/" + fileName;
                    FileOutputStream os = new FileOutputStream(filePath + ".pcm");

                    /** unknown */
                    short soundData[] = new short[BufferElementsToRec];

                    /** starts recording for 3 secs */
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tv_recordUpdate.setVisibility(View.VISIBLE);
                        }
                    });
                    recorder.startRecording();
                    long a = System.currentTimeMillis();    // start time
                    while (isRecording) {
                        recorder.read(soundData, 0, BufferElementsToRec);
                        // writes the data to file from buffer
                        byte bufferData[] = shortToByte(soundData);
                        // stores the voice buffer
                        os.write(bufferData, 0, BufferElementsToRec * BytesPerElement);

                        long b = System.currentTimeMillis();   // end time
                        if (b - a >= 3000) // 3 secs have passed
                            break;
                    }

                    /** stops recording */
                    recorder.stop();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tv_recordUpdate.setVisibility(View.INVISIBLE);
                        }
                    });
                    os.close();

                    /** buffer of 1 sec in between taking samples */
                    Thread.sleep(1000);

                    /** converts pcm file to wav */
                    File f1 = new File(filePath + ".pcm"); // The location of your PCM file
                    File f2 = new File(filePath + ".wav"); // The location where you want your WAV file
                    try {
                        rawToWave(f1, f2);
                        f1.delete();
                    } catch (IOException e) {
                        e.printStackTrace();
                        f1.delete();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            /** hide progress bar and cancel button */
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    //pb_record.setVisibility(View.INVISIBLE);
                    //b_recordCancel.setVisibility(View.INVISIBLE);
                }
            });

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
         * setter method to change isRecording value to false
         */
        private void stopRecordingSound() {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
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
