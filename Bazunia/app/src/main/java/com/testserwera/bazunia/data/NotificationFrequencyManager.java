package com.testserwera.bazunia.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Zarządza lokalnymi, specyficznymi dla czujnika ustawieniami częstotliwości powiadomień.
 * Działa analogicznie do ThresholdManager, zapisując dane w SharedPreferences.
 */
public class NotificationFrequencyManager {

    private static final String PREFS_NAME = "SensorNotificationFrequencies";
    private static final String KEY_PREFIX_INTERVAL = "notification_interval_";

    private final SharedPreferences sharedPreferences;

    public NotificationFrequencyManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String getSensorKey(long sensorId) {
        return KEY_PREFIX_INTERVAL + sensorId;
    }

    /**
     * Zapisuje częstotliwość (w minutach) dla konkretnego czujnika.
     * @param sensorId ID czujnika
     * @param intervalInMinutes Ilość minut. Podanie wartości <= 0 usuwa ustawienie (użyje globalnego).
     */
    public void saveFrequency(long sensorId, int intervalInMinutes) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String key = getSensorKey(sensorId);

        if (intervalInMinutes <= 0) {
            editor.remove(key);
        } else {
            editor.putInt(key, intervalInMinutes);
        }
        editor.apply();
    }

    /**
     * Odczytuje zapisaną częstotliwość powiadomień.
     * @param sensorId ID czujnika
     * @param defaultValue Wartość zwracana, jeśli dla czujnika nic nie ustawiono (np. -1)
     */
    public int getFrequency(long sensorId, int defaultValue) {
        return sharedPreferences.getInt(getSensorKey(sensorId), defaultValue);
    }
}