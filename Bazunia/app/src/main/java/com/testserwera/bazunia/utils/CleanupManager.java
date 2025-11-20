package com.testserwera.bazunia.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class CleanupManager {

    private static final String PREFS_NAME = "CleanupPrefs";
    private static final String KEY_CLEANUP_DAYS = "cleanup_days";
    // Używamy klucza z Constants lub definiujemy lokalnie, jeśli wolisz
    private static final String KEY_CLEANUP_SIZE = "cleanup_size_records";

    public static final int DEFAULT_CLEANUP_DAYS = 30;
    public static final int DEFAULT_CLEANUP_SIZE = 0; // 0 = wyłączone

    private final SharedPreferences sharedPreferences;

    public CleanupManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveCleanupDays(int days) {
        if (days < 0) days = 0;
        sharedPreferences.edit().putInt(KEY_CLEANUP_DAYS, days).apply();
    }

    public int getCleanupDays() {
        return sharedPreferences.getInt(KEY_CLEANUP_DAYS, DEFAULT_CLEANUP_DAYS);
    }

    // ⭐️ NOWE METODY DO OBSŁUGI ROZMIARU ⭐️

    /**
     * Zapisuje limit rekordów. 0 oznacza brak limitu.
     */
    public void saveCleanupSize(int records) {
        if (records < 0) records = 0;
        sharedPreferences.edit().putInt(KEY_CLEANUP_SIZE, records).apply();
    }

    /**
     * Pobiera limit rekordów. Zwraca 0, jeśli limit jest wyłączony.
     */
    public int getCleanupSize() {
        return sharedPreferences.getInt(KEY_CLEANUP_SIZE, DEFAULT_CLEANUP_SIZE);
    }
}