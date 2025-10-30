package com.example.bazunia;

import android.content.Intent; // ⭐️ NOWY IMPORT
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast; // Zachowany na wszelki wypadek

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

// ⭐️ TO JEST STARA KLASA, MOCNO "ODCHUDZONA" ⭐️
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    // Zmienne Wyglądu (zostają)
    private AppearanceManager appearanceManager;
    private MaterialSwitch switchTheme;
    private RadioGroup radioGroupTextScale;
    private RadioGroup radioGroupButtonScale;

    // --- WSZYSTKIE ZMIENNE 2FA ZOSTAŁY USUNIĘTE ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appearanceManager = new AppearanceManager(this);
        appearanceManager.applyAppearance(this);
        super.onCreate(savedInstanceState);
        // Upewnij się, że używasz layoutu, który wkleiłem w poprzedniej wiadomości
        // (tego bez 2FA, a z nowym przyciskiem)
        setContentView(R.layout.activity_settings);

        // --- INICJALIZACJA 2FA USUNIĘTA ---

        // Znajdź widoki (Wygląd)
        switchTheme = findViewById(R.id.switchThemeSettings);
        radioGroupTextScale = findViewById(R.id.radioGroupTextScale);
        radioGroupButtonScale = findViewById(R.id.radioGroupButtonScale);
        MaterialButton btnBack = findViewById(R.id.btnBackSettings);

        // ⭐️ NOWY PRZYCISK DO USTAWIEŃ KONTA ⭐️
        // Ten ID musi istnieć w Twoim activity_settings.xml
        MaterialButton btnAccountSettings = findViewById(R.id.btnAccountSettings);

        // --- ZNAJDOWANIE WIDOKÓW 2FA USUNIĘTE ---

        // Ustaw słuchaczy
        appearanceManager.applyIconScale(btnBack);
        btnBack.setOnClickListener(v -> finish());
        setupAppearanceListeners(); // Zostaje

        // ⭐️ LISTENER DLA NOWEGO PRZYCISKU ⭐️
        btnAccountSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, AccountSettingsActivity.class));
        });

        // --- setup2FAListeners() USUNIĘTE ---

        // Załaduj ustawienia
        loadCurrentAppearanceSettings(); // Zostaje

        // --- retrieveIdToken() i fetch2FAStatusFromServer() USUNIĘTE ---
    }

    // --- LOGIKA WYGLĄDU (ZOSTAJE, Z POPRAWKĄ PĘTLI) ---
    private void loadCurrentAppearanceSettings() {
        switchTheme.setChecked(appearanceManager.getTheme() == AppearanceManager.THEME_DARK);
        String textScale = appearanceManager.getTextScale();
        if (AppearanceManager.SCALE_SMALL.equals(textScale)) radioGroupTextScale.check(R.id.radioTextSmall);
        else if (AppearanceManager.SCALE_LARGE.equals(textScale)) radioGroupTextScale.check(R.id.radioTextLarge);
        else radioGroupTextScale.check(R.id.radioTextMedium);
        String buttonScale = appearanceManager.getButtonScale();
        if (AppearanceManager.SCALE_SMALL.equals(buttonScale)) radioGroupButtonScale.check(R.id.radioButtonSmall);
        else if (AppearanceManager.SCALE_LARGE.equals(buttonScale)) radioGroupButtonScale.check(R.id.radioButtonLarge);
        else radioGroupButtonScale.check(R.id.radioButtonMedium);
    }

    private void setupAppearanceListeners() {
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // POPRAWKA PĘTLI (z poprzedniej rozmowy)
            int newTheme = isChecked ? AppearanceManager.THEME_DARK : AppearanceManager.THEME_LIGHT;
            if (appearanceManager.getTheme() != newTheme) {
                appearanceManager.saveTheme(newTheme);
                recreate();
            }
        });
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
    }

    // --- CAŁA LOGIKA 2FA (WSZYSTKIE METODY) ZOSTAŁA STĄD USUNIĘTA ---
}