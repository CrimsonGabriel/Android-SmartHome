package com.testserwera.bazunia.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class CleanupManager {

    private static final String PREFS_NAME = "CleanupPrefs";
    private static final String KEY_CLEANUP_DAYS = "cleanup_days";
    public static final int DEFAULT_CLEANUP_DAYS = 30; // Domyślnie usuwamy dane starsze niż 30 dni

    private final SharedPreferences sharedPreferences;

    public CleanupManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Zapisuje interwał automatycznego czyszczenia lokalnej bazy danych w dniach.
     */
    public void saveCleanupDays(int days) {
        if (days < 0) days = 0; // Ustaw 0, aby wyłączyć czyszczenie
        sharedPreferences.edit().putInt(KEY_CLEANUP_DAYS, days).apply();
    }

    /**
     * Pobiera zapisany interwał czyszczenia. Zwraca 0, jeśli czyszczenie jest wyłączone.
     */
    public int getCleanupDays() {
        return sharedPreferences.getInt(KEY_CLEANUP_DAYS, DEFAULT_CLEANUP_DAYS);
    }
}