package com.testserwera.bazunia.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import java.util.Locale;

public class LocaleManager {
    public static boolean languageChanged = false;
    private static final String PREFS_NAME = "LocalePrefs";
    private static final String KEY_LANGUAGE = "selected_language";
    public static final String LANGUAGE_POLISH = "pl";
    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_DEFAULT = LANGUAGE_POLISH;

    private final SharedPreferences sharedPreferences;

    public LocaleManager(Context context) {
        // Używamy getApplicationContext, aby uniknąć potencjalnych wycieków pamięci
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveLanguage(String languageCode) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    public String getLanguage() {
        return sharedPreferences.getString(KEY_LANGUAGE, LANGUAGE_DEFAULT);
    }

    /**
     * Stosuje zapisany język do podanego kontekstu.
     * Wywoływana w attachBaseContext() każdej Activity.
     *
     * @param context bazowy kontekst aktywności.
     * @return nowy kontekst z zaaplikowanymi zasobami językowymi.
     */
    public Context setLocale(Context context) {
        String languageCode = getLanguage();
        return updateResources(context, languageCode);
    }

    /**
     * Właściwa logika przełączania zasobów językowych.
     */
    private Context updateResources(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = res.getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            // Tworzenie nowego kontekstu konfiguracji jest preferowanym sposobem od Androida 7.0
            return context.createConfigurationContext(config);
        } else {
            // Starsze wersje
            config.locale = locale;
            // Wymaga deprecated metody dla starszych API
            res.updateConfiguration(config, res.getDisplayMetrics());
            return context;
        }
    }
}