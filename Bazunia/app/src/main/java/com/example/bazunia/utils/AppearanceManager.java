package com.example.bazunia.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.bazunia.R;
import com.google.android.material.button.MaterialButton;

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
    private final Context context;

    public AppearanceManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.context = context;
    }

    public void saveTheme(int themeMode) {
        sharedPreferences.edit().putInt(KEY_THEME, themeMode).apply();
        AppCompatDelegate.setDefaultNightMode(themeMode);
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

    public void applyAppearance(Activity activity) {
        AppCompatDelegate.setDefaultNightMode(getTheme());
        activity.setTheme(R.style.Theme_Bazunia);

        String textScale = getTextScale();
        switch (textScale) {
            case SCALE_SMALL:
                activity.getTheme().applyStyle(R.style.TextScale_Small, true);
                break;
            case SCALE_LARGE:
                activity.getTheme().applyStyle(R.style.TextScale_Large, true);
                break;
            default:
                activity.getTheme().applyStyle(R.style.TextScale_Medium, true);
                break;
        }

        String buttonScale = getButtonScale();
        switch (buttonScale) {
            case SCALE_SMALL:
                activity.getTheme().applyStyle(R.style.ButtonScale_Small, true);
                break;
            case SCALE_LARGE:
                activity.getTheme().applyStyle(R.style.ButtonScale_Large, true);
                break;
            default:
                activity.getTheme().applyStyle(R.style.ButtonScale_Medium, true);
                break;
        }
    }

    public void applyIconScale(MaterialButton button) {
        Resources res = context.getResources();
        int sizeInPixels;

        switch (getButtonScale()) {
            case SCALE_SMALL:
                sizeInPixels = res.getDimensionPixelSize(R.dimen.icon_size_small);
                break;
            case SCALE_LARGE:
                sizeInPixels = res.getDimensionPixelSize(R.dimen.icon_size_large);
                break;
            default:
                sizeInPixels = res.getDimensionPixelSize(R.dimen.icon_size_medium);
                break;
        }
        button.setIconSize(sizeInPixels);
    }
}
