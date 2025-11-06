package com.example.bazunia.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bazunia.utils.AppearanceManager;
import com.example.bazunia.utils.CleanupManager;
import com.example.bazunia.utils.LocaleManager;
import com.example.bazunia.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;


public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    // Zmienne Wyglądu
    private AppearanceManager appearanceManager;
    private LocaleManager localeManager;
    private MaterialSwitch switchTheme;
    private RadioGroup radioGroupTextScale;
    private RadioGroup radioGroupButtonScale;

    private MaterialButton btnGatewayCleanupSettings;

    // ⭐️ NOWA ZMIENNA DLA WYBORU JĘZYKA ⭐️
    private RadioGroup radioGroupLanguage;

    private CleanupManager cleanupManager;
    private com.google.android.material.textfield.TextInputEditText editCleanupDays;

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
        cleanupManager = new CleanupManager(this);
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

        // NOWA OBSŁUGA DLA CZYSZCZENIA BRAMKI
        btnGatewayCleanupSettings.setOnClickListener(v -> showGatewayCleanupDialog());
    }

    private void setupViews() {
        // Znajdowanie widoków i przypisywanie do zmiennych składowych
        switchTheme = findViewById(R.id.switchThemeSettings);
        radioGroupTextScale = findViewById(R.id.radioGroupTextScale);
        radioGroupButtonScale = findViewById(R.id.radioGroupButtonScale);
        radioGroupLanguage = findViewById(R.id.radioGroupLanguage);
        editCleanupDays = findViewById(R.id.editCleanupDays);
        btnGatewayCleanupSettings = findViewById(R.id.btnGatewayCleanupSettings);
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
        // --- Czyszczenie Danych (Wymaganie 6.4) ---
        int cleanupDays = cleanupManager.getCleanupDays();
        // Jeśli 0, zostaw puste (lub użyj hint/placeholder), ale tutaj zostawiamy to tak, żeby
        // wyświetlało wartość domyślną przy pierwszym uruchomieniu, jeśli nie została zmieniona.
        if (cleanupDays > 0) {
            editCleanupDays.setText(String.valueOf(cleanupDays));
        } else {
            editCleanupDays.setText(""); // Opcja wyłączona
            editCleanupDays.setHint(getString(R.string.cleanup_disabled_hint));
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
    @Override
    protected void onPause() {
        super.onPause();
        saveCleanupSettings();
    }

    private void saveCleanupSettings() {
        String input = editCleanupDays.getText() != null ? editCleanupDays.getText().toString() : "";
        int days = 0;
        if (!input.isEmpty()) {
            try {
                days = Integer.parseInt(input);
                if (days < 1) days = 0; // Nie zapisujemy 0, jeśli nie jest to wyraźny wybór wyłączenia
            } catch (NumberFormatException e) {
                Log.e(TAG, "Nieprawidłowa wartość dni czyszczenia.");
                // Opcjonalnie: Toast z błędem dla użytkownika
            }
        }
        cleanupManager.saveCleanupDays(days);
    }

    // Wewnątrz SettingsActivity.java
    private void showGatewayCleanupDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.cleanup_gateway_dialog_title))
                .setMessage(getString(R.string.cleanup_gateway_dialog_message))
                .setPositiveButton(getString(R.string.cleanup_gateway_go_to_web), (dialog, which) -> {
                    // Opcjonalnie: Otwórz przeglądarkę na stronie webowej
                    // Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.VPS_SERVER_IP + "/admin/cleanup"));
                    // startActivity(browserIntent);
                    // Na potrzeby tego zadania po prostu informujemy:
                    Toast.makeText(this, getString(R.string.cleanup_gateway_web_toast), Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(getString(R.string.dialog_cancel_button), null)
                .show();
    }
}