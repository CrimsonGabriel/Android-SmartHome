package com.testserwera.bazunia.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class AppearanceManager {

    private static final String PREFS_NAME = "AppearancePrefs";
    private static final String KEY_THEME = "selected_theme";
    private static final String KEY_SCALE_TEXT = "selected_scale_text";
    private static final String KEY_SCALE_BUTTON = "selected_scale_button";
    public static final int THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    public static final int THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES;
    public static final String SCALE_SMALL = "Small";
    public static final String SCALE_MEDIUM = "Medium";
    public static final String SCALE_LARGE = "Large";
    private final SharedPreferences sharedPreferences;

    public AppearanceManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveTheme(int themeMode) {
        sharedPreferences.edit().putInt(KEY_THEME, themeMode).apply();
        applyThemeMode();
    }

    public void applyThemeMode() {
        AppCompatDelegate.setDefaultNightMode(getTheme());
    }

    public int getTheme() {
        return sharedPreferences.getInt(KEY_THEME, THEME_LIGHT);
    }

    public void saveTextScale(String scale) {
        sharedPreferences.edit().putString(KEY_SCALE_TEXT, scale).apply();
    }

    public String getTextScale() {
        return sharedPreferences.getString(KEY_SCALE_TEXT, SCALE_MEDIUM);
    }

    public void saveButtonScale(String scale) {
        sharedPreferences.edit().putString(KEY_SCALE_BUTTON, scale).apply();
    }

    public String getButtonScale() {
        return sharedPreferences.getString(KEY_SCALE_BUTTON, SCALE_MEDIUM);
    }
}