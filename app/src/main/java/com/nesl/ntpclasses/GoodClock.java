/*
Author: Sandeep Singh Sandha
Email: sandha.iitr@gmail.com
*/


package com.nesl.ntpclasses;

import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


public class GoodClock {


    long period = 1 * 30 * 1000;//Default update every 15  minutes


    SntpDsense client = null;
    String ntpHost = "time1.ucla.edu";//17.253.26.253
    private String timeZone = "America/Los_Angeles";
    int timeout = 3000;


    public boolean SntpSuceeded;
    Thread NTP_update;
    boolean NTP_thread_running = false;

    boolean use_drift_correction = false;

    //stores the drift in the ntp_clockoffset
    double drift = 0.0;

    //these are affected when system time jumps
    double first_ntp_offset = 0;
    long first_ntp_monotonic_time = 0;
    long first_ntp_sys_time = 0;

    long curr_ntp_offset = 0;
    long curr_ntp_monotonic_time = 0;
    long curr_ntp_sys_time = 0;

    //below numbers are used in the drift computation
    double total_ntp_offset_run = 0.0;
    double total_ntp_monotonic_time_run = 0.0;
    double total_ntp_offset_change = 0.0;
    double total_ntp_monotonic_time_passed = 0.0;

    //Ambient Light File Stuff
    private OutputStream driftRecordOSStream;
    private OutputStreamWriter driftRecordOS;

    boolean is_first = true; //stores whether it is a first NTP update

    //boolean to record to file
    boolean recordDriftToFile;
    String experimentDirectory;

    /*
    1) Initializing SntpDsense client
    2) Initialize SNTP (NTP) has not been done
     */
    public GoodClock(Boolean recordDrift) {

        try {
            client = new SntpDsense();
            SntpSuceeded = false;
            NTP_update = null;
            this.recordDriftToFile = recordDrift;
            // period = 10000;//how often to do the NTP update

        } catch (Exception e) {
            e.printStackTrace();
        }

    }//end GoodClock()

    //start the dsense library
    /*
    Create a thread to periodically update the SntpDsense client ntp update function
     */
    public void start() {
        try {
            //start thread only if not running
            if (NTP_update == null) {
                NTP_thread_running = true;
                NTP_update = new Thread(thread_periodic_update_NTP);
                NTP_update.start();

            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }//end start

    public void stop() {
        try {
            NTP_thread_running = false;
            NTP_update.stop();
            if (recordDriftToFile) {
                try {
                    driftRecordOS.flush();
                    driftRecordOS.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }//end stop

    //return currentTimeMillis based on the below logic:
    /*
    1) Stores the NTP offset of the system time and updates the offset periodically
     */

    public void appendDriftToFile() {
        long elapsed_time_since_last_ntp = SystemClock.elapsedRealtime() - curr_ntp_monotonic_time;
        long drift_correction = (long)((drift)*(double)(elapsed_time_since_last_ntp));
        long utc0Time = System.currentTimeMillis();
        try {
            if(is_first) {
                long now = Now();
                Date date = new Date(now);
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
                formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
                String dateFormatted = formatter.format(date);
                /** creates file path */
                String driftRecordFileName = "timeDriftRecord-" + dateFormatted;

                /** creates new folders in storage if they do not exist */
                File pathParent = new File(Environment.getExternalStoragePublicDirectory("NTPSense") + "/");
                if (!pathParent.exists())
                    pathParent.mkdir();
                experimentDirectory = "/exp-" + dateFormatted + "/";
                File pathChild = new File(pathParent + experimentDirectory);
                if (!pathChild.exists())
                    pathChild.mkdir();

                /** creates file paths */
                String driftRecordFilePath = pathChild + "/" + driftRecordFileName;
                driftRecordOSStream = new FileOutputStream(driftRecordFilePath + ".csv");
                driftRecordOS = new OutputStreamWriter(driftRecordOSStream);
                driftRecordOS.append("CurrentTimeStamp, CurrNTPOffset, DriftCorrection, CurrNTPSysTime, CurrNTPMonotonicTime, ElapsedTimeSinceLastNTP, UTC-0 WallClockTime");
                driftRecordOS.flush();
            }
            driftRecordOS.append( "" + Now()+","+curr_ntp_offset + ", " + drift_correction +  ", " + curr_ntp_sys_time + ", " +curr_ntp_monotonic_time+", "+ elapsed_time_since_last_ntp + ","+ utc0Time+"\n");
            driftRecordOS.flush();
            Log.i("AppendingDriftToFile", "" + curr_ntp_offset + ", " + drift_correction  + ", " + curr_ntp_sys_time + ", " +curr_ntp_monotonic_time+", "+ elapsed_time_since_last_ntp + ", "+utc0Time);

        }
        catch(IOException e)
        {
            Log.e("AppendingDriftToFile", "Couldn't do it!");

            e.printStackTrace();
        }
    }


    public long currentTimeMillis()
    {

    /*
    We have to make sure client is not null
     */
        if(client==null)
            return -1;//client not initialized

        long now = -1;
        try{

            //client.get_ntp_update_sys_time(): last sys time NTP was updated.
            //SystemClock.elapsedRealtime(): monotonic system elaspsed time since boot.
            //client.get_ntp_update_monotonic_time(): monotonic system elasped time at instant of NTP offset calcuation.
            //client.getNtp_clockoffset(): offset of system time with NTP server at time of NTP update
            long elapsed_time_since_last_ntp = SystemClock.elapsedRealtime() - curr_ntp_monotonic_time;
            long drift_correction = (long)((drift)*(double)(elapsed_time_since_last_ntp));
            now = drift_correction+curr_ntp_offset+curr_ntp_sys_time+elapsed_time_since_last_ntp;

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return now;
    }//end long currentTimeMillis

    public String experimentDirectoryName()
    {
        return experimentDirectory;
    }
    /*
   Gives the time now in milliseconds based on corrections
     */
    public long Now()
    {

    /*
    We have to make sure client is not null
     */
        if(client==null)
            return -1;//client not initialized

        long now = -1;
        try{

            long elapsed_time_since_last_ntp = SystemClock.elapsedRealtime() - curr_ntp_monotonic_time;
            long drift_correction = (long)((drift)*(double)(elapsed_time_since_last_ntp));
            now = drift_correction+curr_ntp_offset+curr_ntp_sys_time+elapsed_time_since_last_ntp;

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return now;
    }//end long Now

    public long getNtp_clockoffset()
    {
        try{
            return client.getNtp_clockoffset();
        }
        catch (Exception e)
        {
            return Integer.MAX_VALUE;
        }

    }

    public double getDrift()
    {
        return drift;

    }//end getDrift()

    Runnable thread_periodic_update_NTP = new Runnable() {
        @Override
        public void run() {
            while(true) {
                try {

                    System.out.println("GoodClock Thread is running");
                    SntpSuceeded = client.requestTime(ntpHost, timeout);

                    if(SntpSuceeded)
                    {
                        //is this the first update
                        if(is_first==true)
                        {




                            curr_ntp_offset = client.getNtp_clockoffset();
                            curr_ntp_monotonic_time = client.get_ntp_update_monotonic_time();
                            curr_ntp_sys_time=client.get_ntp_update_sys_time();

                            //these are set during the first time
                            first_ntp_monotonic_time = curr_ntp_monotonic_time;
                            first_ntp_offset= curr_ntp_offset;
                            first_ntp_sys_time=curr_ntp_sys_time;
                            if(recordDriftToFile) {

                                appendDriftToFile();
                            }
                            is_first=false;

                        }

                        else
                        {


                            curr_ntp_offset = client.getNtp_clockoffset();
                            curr_ntp_monotonic_time = client.get_ntp_update_monotonic_time();
                            curr_ntp_sys_time=client.get_ntp_update_sys_time();

                            //if there was jump in the system time, then the previous
                            //difference in monotonic time will not match with the difference in the system time

                            long diff_monotonic = curr_ntp_monotonic_time- first_ntp_monotonic_time;
                            long diff_system = curr_ntp_sys_time - first_ntp_sys_time;

                            long jump_system_time = diff_monotonic-diff_system;

                            //note this clock difference will be due to the jump in system time due to correction (NTP or Nitz at the system level)
                            //we need to check there was no jump in the system time

                            if(Math.abs(jump_system_time)<10)//there is an insignificant jump of 10 ms or less.

                            {

                                total_ntp_offset_run = (curr_ntp_offset - first_ntp_offset);
                                total_ntp_monotonic_time_run = (curr_ntp_monotonic_time - first_ntp_monotonic_time);

                                double curr_drift=0.0;

                                if(total_ntp_monotonic_time_run>(10.0*60.0*1000.0))//if current run > 10 minutes
                                {

                                    curr_drift = (total_ntp_offset_change+total_ntp_offset_run)/(total_ntp_monotonic_time_passed+total_ntp_monotonic_time_run);

                                    //System.out.println("Sandeep: curr run"+total_ntp_offset_run+":"+total_ntp_monotonic_time_run);
                                }

                                else//we only use the value from the previous runs, in case system time is changed
                                {
                                    curr_drift = (total_ntp_offset_change)/(total_ntp_monotonic_time_passed);
                                }


                                if(curr_drift*(1000.0*60.0*60.0*24.0)>40)
                                {
                                    drift = curr_drift;
                                }

                                else
                                    drift=0.0;

                            }

                            //we are starting a new offset calcuation after the jump
                            else
                            {

                                total_ntp_offset_change = total_ntp_offset_change+total_ntp_offset_run;
                                total_ntp_monotonic_time_passed = total_ntp_monotonic_time_passed+total_ntp_monotonic_time_run;




                                is_first=true;

                                curr_ntp_offset = client.getNtp_clockoffset();
                                curr_ntp_monotonic_time = client.get_ntp_update_monotonic_time();
                                curr_ntp_sys_time=client.get_ntp_update_sys_time();

                                //these are set during the first time
                                first_ntp_monotonic_time = curr_ntp_monotonic_time;
                                first_ntp_offset= curr_ntp_offset;
                                first_ntp_sys_time=curr_ntp_sys_time;

                            }


                        }//end else

                    }
                    if(recordDriftToFile){
                        appendDriftToFile();
                    }
                    Thread.sleep(period);

                } catch (Exception e) {

                    e.printStackTrace();
                }

                if(!NTP_thread_running)
                {
                    break;
                }

            }//end  while (true)
        }//end run
    };

}//end GoodClock