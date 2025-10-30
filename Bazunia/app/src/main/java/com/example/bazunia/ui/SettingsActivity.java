package com.example.bazunia.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bazunia.utils.AppearanceManager;
import com.example.bazunia.utils.LocaleManager;
import com.example.bazunia.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.appbar.MaterialToolbar; // Jeśli używasz Toolbara, ale tutaj go nie ma.

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    // Zmienne Wyglądu
    private AppearanceManager appearanceManager;
    private LocaleManager localeManager;
    private MaterialSwitch switchTheme;
    private RadioGroup radioGroupTextScale;
    private RadioGroup radioGroupButtonScale;

    // ⭐️ NOWA ZMIENNA DLA WYBORU JĘZYKA ⭐️
    private RadioGroup radioGroupLanguage;

    /**
     * KLUCZOWA METODA DLA ZMIANY JĘZYKA
     * Musi być nadpisana, aby zastosować LocaleManager.
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleManager tempLocaleManager = new LocaleManager(newBase);
        super.attachBaseContext(tempLocaleManager.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appearanceManager = new AppearanceManager(this);
        appearanceManager.applyAppearance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        localeManager = new LocaleManager(this);

        // Inicjalizacja komponentów
        setupViews();
        loadCurrentAppearanceSettings();
        setupAppearanceListeners();

        // --- LOGIKA OBSŁUGI PRZYCISKÓW ---

        // Ustawienie listenerek dla przycisku konta
        MaterialButton btnAccountSettings = findViewById(R.id.btnAccountSettings);
        btnAccountSettings.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, AccountSettingsActivity.class);
            startActivity(intent);
        });

        // Obsługa przycisku Wróć
        MaterialButton btnBackSettings = findViewById(R.id.btnBackSettings);
        btnBackSettings.setOnClickListener(v -> finish());
    }

    private void setupViews() {
        // Znajdowanie widoków i przypisywanie do zmiennych składowych
        switchTheme = findViewById(R.id.switchThemeSettings);
        radioGroupTextScale = findViewById(R.id.radioGroupTextScale);
        radioGroupButtonScale = findViewById(R.id.radioGroupButtonScale);
        radioGroupLanguage = findViewById(R.id.radioGroupLanguage);
    }

    private void loadCurrentAppearanceSettings() {
        // --- Tryb Ciemny ---
        int currentTheme = appearanceManager.getTheme();
        switchTheme.setChecked(currentTheme == AppearanceManager.THEME_DARK);

        // --- Rozmiar Tekstu ---
        String currentTextScale = appearanceManager.getTextScale();
        if (AppearanceManager.SCALE_SMALL.equals(currentTextScale)) {
            radioGroupTextScale.check(R.id.radioTextSmall);
        } else if (AppearanceManager.SCALE_LARGE.equals(currentTextScale)) {
            radioGroupTextScale.check(R.id.radioTextLarge);
        } else {
            radioGroupTextScale.check(R.id.radioTextMedium);
        }

        // --- Rozmiar Przycisków ---
        String currentButtonScale = appearanceManager.getButtonScale();
        if (AppearanceManager.SCALE_SMALL.equals(currentButtonScale)) {
            radioGroupButtonScale.check(R.id.radioButtonSmall);
        } else if (AppearanceManager.SCALE_LARGE.equals(currentButtonScale)) {
            radioGroupButtonScale.check(R.id.radioButtonLarge);
        } else {
            radioGroupButtonScale.check(R.id.radioButtonMedium);
        }

        // ⭐️ POPRAWIONA LOGIKA DLA WYBORU JĘZYKA ⭐️
        RadioGroup radioGroupLanguage = findViewById(R.id.radioGroupLanguage);
        String currentLanguage = localeManager.getLanguage();

        // Używamy logicznego sprawdzenia: PL jest domyślny, więc sprawdzamy najpierw jego
        if (LocaleManager.LANGUAGE_POLISH.equals(currentLanguage)) {
            radioGroupLanguage.check(R.id.radioLanguagePolish);
        } else {
            // Jeśli nie jest PL, zaznacz EN
            radioGroupLanguage.check(R.id.radioLanguageEnglish);
        }
    }


    private void setupAppearanceListeners() {
        // Listener dla switchTheme (Poprawka pętli)
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int newTheme = isChecked ? AppearanceManager.THEME_DARK : AppearanceManager.THEME_LIGHT;
            if (appearanceManager.getTheme() != newTheme) {
                appearanceManager.saveTheme(newTheme);
                recreate();
            }
        });

        // Listener dla radioGroupTextScale (Poprawka pętli)
        radioGroupTextScale.setOnCheckedChangeListener((group, checkedId) -> {
            String newScale;
            if (checkedId == R.id.radioTextSmall) newScale = AppearanceManager.SCALE_SMALL;
            else if (checkedId == R.id.radioTextLarge) newScale = AppearanceManager.SCALE_LARGE;
            else newScale = AppearanceManager.SCALE_MEDIUM;
            if (!appearanceManager.getTextScale().equals(newScale)) {
                appearanceManager.saveTextScale(newScale);
                recreate();
            }
        });

        // Listener dla radioGroupButtonScale (Poprawka pętli)
        radioGroupButtonScale.setOnCheckedChangeListener((group, checkedId) -> {
            String newScale;
            if (checkedId == R.id.radioButtonSmall) newScale = AppearanceManager.SCALE_SMALL;
            else if (checkedId == R.id.radioButtonLarge) newScale = AppearanceManager.SCALE_LARGE;
            else newScale = AppearanceManager.SCALE_MEDIUM;
            if (!appearanceManager.getButtonScale().equals(newScale)) {
                appearanceManager.saveButtonScale(newScale);
                recreate();
            }
        });

        // ⭐️ NOWY LISTENER DLA WYBORU JĘZYKA ⭐️
        radioGroupLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            String newLanguageCode;
            if (checkedId == R.id.radioLanguageEnglish) {
                newLanguageCode = LocaleManager.LANGUAGE_ENGLISH;
            } else {
                newLanguageCode = LocaleManager.LANGUAGE_POLISH;
            }

            if (!localeManager.getLanguage().equals(newLanguageCode)) {
                localeManager.saveLanguage(newLanguageCode);
                // ⭐️ KLUCZOWA ZMIANA: Ustawienie flagi na true ⭐️
                LocaleManager.languageChanged = true;
                // Ponowne utworzenie aktywności załaduje nowy Context z attachBaseContext
                recreate();
            }
        });
    }
}