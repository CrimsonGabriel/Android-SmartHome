package com.testserwera.bazunia.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

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

    public void saveThresholds(String gatewayId, String sensorId, float min, float max) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(getMinKey(gatewayId, sensorId), min);
        editor.putFloat(getMaxKey(gatewayId, sensorId), max);
        editor.apply();
    }

    public float getMinThreshold(String gatewayId, String sensorId, float defaultValue) {
        return sharedPreferences.getFloat(getMinKey(gatewayId, sensorId), defaultValue);
    }

    public float getMaxThreshold(String gatewayId, String sensorId, float defaultValue) {
        return sharedPreferences.getFloat(getMaxKey(gatewayId, sensorId), defaultValue);
    }

    /**
     * Zwraca domyślne zakresy dla suwaków (tylko dla typów ANALOGOWYCH).
     */
    public Pair<Float, Float> getDefaultRangeForType(String type) {
        if (type == null) return new Pair<>(0f, 100f);

        switch (type.toLowerCase()) {
            case "temperature":
                return new Pair<>(-10f, 40f);
            case "humidity":
                return new Pair<>(0f, 100f);
            case "power":
                return new Pair<>(0f, 2500f);
            case "voltage":
                return new Pair<>(180f, 260f);
            case "smoke":
                return new Pair<>(0f, 500f);
            case "level":
                return new Pair<>(0f, 100f);
            case "sunlight":
                return new Pair<>(0f, 10000f);

            default:
                return new Pair<>(0f, 100f);
        }
    }

    /**
     * TRUE -> Typy Analogowe (Mają suwaki: Temp, Wilgotność, Moc) -> Widoczne ustawienia progów
     * FALSE -> Typy Binarne (ON/OFF: Światło, Drzwi, Woda, Przepływ) -> Ukryte ustawienia progów
     */
    public boolean isThresholdSupported(String type) {
        if (type == null) return false;
        switch (type.toLowerCase()) {
            case "contact":
            case "motion":
            case "button":
            case "button_press":
            case "valve":
            case "water_leak":
            case "light":
            case "socket":
            case "switch":
            case "lock":
            case "flow":
                return false;

            default:
                return true;
        }
    }
}