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
                return new Pair<>(-10f, 40f); // Typowa temperatura otoczenia
            case "humidity":
                return new Pair<>(0f, 100f);  // Wilgotność %
            case "power":
                return new Pair<>(0f, 2500f); // Moc do 2.5kW (czajnik itp.)
            case "voltage":
                return new Pair<>(180f, 260f); // Napięcie sieciowe
            case "smoke":
                return new Pair<>(0f, 500f);   // PPM dymu
            case "level":
                return new Pair<>(0f, 100f);   // Poziom %
            case "sunlight":
                return new Pair<>(0f, 10000f); // Luxy

            // Flow usunięty stąd, bo ma nie mieć suwaków

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
            // GRUPA BEZ SUWAKÓW (Alarm gdy > 0 lub > 0.5)
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
            case "flow": // Flow też tu jest -> zwróci FALSE -> wejdzie w GONE w Activity
                return false;

            // GRUPA Z SUWAKAMI (Temp, Power, Smoke itp.)
            default:
                return true;
        }
    }
}