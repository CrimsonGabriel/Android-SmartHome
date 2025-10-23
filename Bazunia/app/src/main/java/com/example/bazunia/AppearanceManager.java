package com.example.bazunia;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class AppearanceManager {

    private static final String PREFS_NAME = "AppearancePrefs";
    private static final String KEY_THEME = "selected_theme";
    // NOWE, ODDZIELNE KLUCZE
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

    // --- Zarządzanie Motywem (Jasny/Ciemny) ---
    public void saveTheme(int themeMode) {
        sharedPreferences.edit().putInt(KEY_THEME, themeMode).apply();
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }

    public int getTheme() {
        return sharedPreferences.getInt(KEY_THEME, THEME_LIGHT);
    }

    // --- Zarządzanie Rozmiarem TEKSTU ---
    public void saveTextScale(String scale) {
        sharedPreferences.edit().putString(KEY_SCALE_TEXT, scale).apply();
    }

    public String getTextScale() {
        return sharedPreferences.getString(KEY_SCALE_TEXT, SCALE_MEDIUM);
    }

    // --- Zarządzanie Rozmiarem PRZYCISKÓW ---
    public void saveButtonScale(String scale) {
        sharedPreferences.edit().putString(KEY_SCALE_BUTTON, scale).apply();
    }

    public String getButtonScale() {
        return sharedPreferences.getString(KEY_SCALE_BUTTON, SCALE_MEDIUM);
    }


    /**
     * Stosuje wszystkie zapisane ustawienia (Motyw, Skala Tekstu, Skala Przycisków).
     * Musi być wywołana w każdej Aktywności PRZED super.onCreate() i setContentView().
     */
    public void applyAppearance(Activity activity) {
        // 1. Zastosuj motyw Jasny/Ciemny
        AppCompatDelegate.setDefaultNightMode(getTheme());

        // 2. Ustaw motyw bazowy (Theme.Bazunia)
        activity.setTheme(R.style.Theme_Bazunia);

        // 3. Zastosuj motyw Rozmiaru TEKSTU (nakładkowo)
        String textScale = getTextScale();
        switch (textScale) {
            case SCALE_SMALL:
                activity.getTheme().applyStyle(R.style.TextScale_Small, true);
                break;
            case SCALE_LARGE:
                activity.getTheme().applyStyle(R.style.TextScale_Large, true);
                break;
            case SCALE_MEDIUM:
            default:
                activity.getTheme().applyStyle(R.style.TextScale_Medium, true);
                break;
        }

        // 4. Zastosuj motyw Rozmiaru PRZYCISKÓW (nakładkowo)
        String buttonScale = getButtonScale();
        switch (buttonScale) {
            case SCALE_SMALL:
                activity.getTheme().applyStyle(R.style.ButtonScale_Small, true);
                break;
            case SCALE_LARGE:
                activity.getTheme().applyStyle(R.style.ButtonScale_Large, true);
                break;
            case SCALE_MEDIUM:
            default:
                activity.getTheme().applyStyle(R.style.ButtonScale_Medium, true);
                break;
        }
    }
}