package com.example.bazunia;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {

    private static final String PREFS_NAME = "ThemePrefs";
    private static final String KEY_THEME = "selected_theme";
    public static final int THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    public static final int THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES;

    private final SharedPreferences sharedPreferences;

    public ThemeManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Zapisuje wybrany motyw (jasny lub ciemny).
     */
    public void setTheme(int themeMode) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_THEME, themeMode);
        editor.apply();
        // Natychmiast zastosuj motyw w całej aplikacji
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }

    /**
     * Odczytuje zapisany motyw i stosuje go.
     * Należy ją wywołać w każdej aktywności PRZED `setContentView()`.
     */
    public void applyTheme() {
        int selectedTheme = sharedPreferences.getInt(KEY_THEME, THEME_LIGHT); // Domyślnie jasny
        AppCompatDelegate.setDefaultNightMode(selectedTheme);
    }

    /**
     * Zwraca aktualnie zapisany motyw.
     */
    public int getCurrentTheme() {
        return sharedPreferences.getInt(KEY_THEME, THEME_LIGHT);
    }
}