package com.example.bazunia;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;

// Usunieto importy Socket.IO
// Usunieto importy DatabaseHelper, ThresholdManager, NotificationHelper

public class SensorMonitoringService extends Service {

    private static final String TAG = "SensorService (Legacy)";
    // Usunieto: private Socket socket;
    // Usunieto inne zmienne pomocnicze

    @Override
    public void onCreate() {
        super.onCreate();
        // Cala logika laczenia Socket.IO zostala usunieta
        Log.d(TAG, "SensorMonitoringService (legacy) jest uruchomiony, ale nie laczy sie przez Socket.IO. Jest nieaktywny.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // Usunieto wszystkie prywatne metody zwiazane z Socket.IO i obsluga danych
    // handleSensorData(), setupSocket(), sendDataUpdateBroadcast()

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "SensorMonitoringService (legacy) zakonczyl dzialanie.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}