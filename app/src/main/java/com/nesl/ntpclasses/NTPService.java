package com.nesl.ntpclasses;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class NTPService extends Service {

    SntpClient SNTP_CLIENT = null;
    float _rootDelayMax = 100;
    float _rootDispersionMax = 100;
    int _serverResponseDelayMax = 750;
    int _udpSocketTimeoutInMillis = 5_000;

    //String _ntpHost = "1.us.pool.ntp.org";
    String _ntpHost = "time.apple.com";
    String text_id_global;
    String audio_id_filename = "timesync_id.txt";

    String host = "128.97.92.44";


    public NTPService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        //throw new UnsupportedOperationException("Not yet implemented");
        return null;

    }

    //running system time clock
    Runnable thread_send_data_ping = new Runnable() {
        @Override
        public void run() {

            while (true) {
                try {

                    long different = SNTP_CLIENT.requestTime(_ntpHost,
                            _rootDelayMax,
                            _rootDispersionMax,
                            _serverResponseDelayMax,
                            _udpSocketTimeoutInMillis);

                    //update only if we receive time from devices
                    if (different != Long.MAX_VALUE) {

                        send_data_ping(0, different);

                    }

                    Thread.sleep(10000);


                } catch (Exception e) {

                    e.printStackTrace();

                }

            }//end  while (run_sys_time)
        }

    };



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // do your jobs here

        Toast.makeText(this,"Starting Service",Toast.LENGTH_LONG).show();

        try {
            //a separate thread to count the delay
            new Thread(thread_send_data_ping).start();


        } catch (Exception e) {
            e.printStackTrace();
        }



        return super.onStartCommand(intent, flags, startId);
    }

    void send_data_ping(long event_time, long ntp_offset_global)
    {
        try {
            String event_time_string = Long.toString(event_time);
            String ntp_offset_global_string = Long.toString(ntp_offset_global);
            if(text_id_global==null)
                text_id_global= read_id();

            if(text_id_global!=null) {


                Socket socket = null;
                OutputStreamWriter osw;
                String str = text_id_global+"\t"+event_time_string+"\t"+ntp_offset_global_string;
                //System.out.println("Sending Data Sandeep:"+str);
                try {
                    socket = new Socket(host, 4450);
                    PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);

                    pw.println(str);

                    socket.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

        }//end try

        catch (Exception e)
        {
            e.printStackTrace();
        }


    }//end  send_data_ping()

    //read id to a file
    String read_id()
    {
        String st=null;

        try {

            File path = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);

            File myFile = new File(path, audio_id_filename);

            if(myFile.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(myFile));
                while ((st = br.readLine()) != null)
                    return st;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return st;
        }

        return st;
    }//end String read_id()

}//end service
