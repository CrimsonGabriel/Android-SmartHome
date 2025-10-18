package com.example.bazunia;

import android.content.Context;
import android.content.SharedPreferences;

public class ThresholdManager {

    private static final String PREFS_NAME = "SensorThresholds";
    private static final String KEY_PREFIX_MIN = "threshold_min_";
    private static final String KEY_PREFIX_MAX = "threshold_max_";

    private final SharedPreferences sharedPreferences;

    public ThresholdManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String getMinKey(String gatewayId, String sensorId) {
        return KEY_PREFIX_MIN + gatewayId + "_" + sensorId;
    }

    private String getMaxKey(String gatewayId, String sensorId) {
        return KEY_PREFIX_MAX + gatewayId + "_" + sensorId;
    }

    /**
     * Zapisuje obie wartości progu (min i max) dla konkretnego czujnika.
     */
    public void saveThresholds(String gatewayId, String sensorId, float min, float max) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(getMinKey(gatewayId, sensorId), min);
        editor.putFloat(getMaxKey(gatewayId, sensorId), max);
        editor.apply();
    }

    /**
     * Odczytuje zapisany próg minimalny.
     */
    public float getMinThreshold(String gatewayId, String sensorId, float defaultValue) {
        // Domyślnie zwraca najmniejszą możliwą wartość, co oznacza brak progu.
        return sharedPreferences.getFloat(getMinKey(gatewayId, sensorId), defaultValue);
    }

    /**
     * Odczytuje zapisany próg maksymalny.
     */
    public float getMaxThreshold(String gatewayId, String sensorId, float defaultValue) {
        // Domyślnie zwraca największą możliwą wartość, co oznacza brak progu.
        return sharedPreferences.getFloat(getMaxKey(gatewayId, sensorId), defaultValue);
    }

    // Dodatkowo: Metody usuwania lub pobierania aktualnego stanu, jeśli są potrzebne w GUI.
}