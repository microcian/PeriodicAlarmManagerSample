package com.zebra.jamesswinton.periodicalarmmanager;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.zebra.jamesswinton.periodicalarmmanager.utilities.AlarmHelper;
import com.zebra.jamesswinton.periodicalarmmanager.utilities.NotificationHelper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SendHeartbeatService extends Service {

    // Debugging
    private static final String TAG = "SendHeartbeatService";

    // WakeLock Holder
    private PowerManager.WakeLock mWakeLock = null;

    // Log File
    private final File mLogFile = new File(Environment.getExternalStorageDirectory()
            + File.separator + "PeriodicAlarmManagerTest" + File.separator + "log.txt");

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service Created");

        // Acquire WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "PeriodAlarmService::HeartbeatWakeLock");
        mWakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);

        // Log Launch
        logToFile();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service Started");

        // StartUp
        startForeground(NotificationHelper.NOTIFICATION_ID,
                NotificationHelper.createNotification(this));

        // Start Heart Beat
        sendHeartbeat();

        // Return NOT_STICKY - I.e. when service is killed, leave it dead, don't restart
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Release WakeLock
        mWakeLock.release();
    }

    @SuppressLint("MissingPermission")
    private void sendHeartbeat() {
        Log.i(TAG, "Preparing Heartbeat...");

        // Acquire LocationManager
        Log.i(TAG, "Acquiring LocationManager");
        LocationManager locationManager = (LocationManager) getApplicationContext()
                .getSystemService(LOCATION_SERVICE);

        // Get Best Provider from Max Criteria
        Log.i(TAG, "Getting best provider");
        Criteria maxCriteria = new Criteria();
        maxCriteria.setAccuracy(Criteria.ACCURACY_FINE);
        maxCriteria.setPowerRequirement(Criteria.POWER_HIGH);
        String bestLocationProvider = locationManager.getBestProvider(maxCriteria, true);

        // Get Location
        Log.i(TAG, "Best provider found: " + bestLocationProvider + " - Acquiring location");
        locationManager.requestSingleUpdate(bestLocationProvider, new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                // Log Location
                Log.i(TAG, "Location obtained, logging location");
                logLocationToFile(location);

                // Reset Alarm
                Log.i(TAG, "Location logged, re-scheduling alarm");
                AlarmHelper.setAlarm(SendHeartbeatService.this);

                // Close Service
                Log.i(TAG, "Alarm rescheduled, quitting service");
                stopSelf();
            }
        }, null);
    }

    private void logToFile() {
        try {
            String logText = String.format(getString(R.string.log_alarm),
                    getCurrentTimeHumanReadable());
            FileUtils.writeStringToFile(mLogFile, logText, Charset.defaultCharset(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logLocationToFile(Location location) {
        try {
            double lat = location == null ? -1 : location.getLatitude();
            double lng = location == null ? -1 : location.getLongitude();
            String logText = String.format(getString(R.string.log_location), lat, lng);
            FileUtils.writeStringToFile(mLogFile, logText, Charset.defaultCharset(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentTimeHumanReadable() {
        return new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault())
                .format(new Date(System.currentTimeMillis()));
    }
}