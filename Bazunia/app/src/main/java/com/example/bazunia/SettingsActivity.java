package com.example.bazunia;

import android.os.Bundle;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {

    private AppearanceManager appearanceManager;
    private MaterialSwitch switchTheme;
    private RadioGroup radioGroupTextScale;
    private RadioGroup radioGroupButtonScale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appearanceManager = new AppearanceManager(this);
        appearanceManager.applyAppearance(this); // Zastosuj motyw PRZED resztą

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Znajdź widoki
        switchTheme = findViewById(R.id.switchThemeSettings);
        radioGroupTextScale = findViewById(R.id.radioGroupTextScale);
        radioGroupButtonScale = findViewById(R.id.radioGroupButtonScale);
        MaterialButton btnBack = findViewById(R.id.btnBackSettings);

        // Zastosuj skalowanie ikony
        appearanceManager.applyIconScale(btnBack);

        // Podłącz przycisk powrotu
        btnBack.setOnClickListener(v -> finish());

        loadCurrentSettings();
        setupListeners();
    }

    private void loadCurrentSettings() {
        // 1. Ustaw przełącznik motywu
        switchTheme.setChecked(appearanceManager.getTheme() == AppearanceManager.THEME_DARK);

        // 2. Ustaw przełącznik rozmiaru TEKSTU
        String textScale = appearanceManager.getTextScale();
        if (AppearanceManager.SCALE_SMALL.equals(textScale)) {
            radioGroupTextScale.check(R.id.radioTextSmall);
        } else if (AppearanceManager.SCALE_LARGE.equals(textScale)) {
            radioGroupTextScale.check(R.id.radioTextLarge);
        } else {
            radioGroupTextScale.check(R.id.radioTextMedium);
        }

        // 3. Ustaw przełącznik rozmiaru PRZYCISKÓW
        String buttonScale = appearanceManager.getButtonScale();
        if (AppearanceManager.SCALE_SMALL.equals(buttonScale)) {
            radioGroupButtonScale.check(R.id.radioButtonSmall);
        } else if (AppearanceManager.SCALE_LARGE.equals(buttonScale)) {
            radioGroupButtonScale.check(R.id.radioButtonLarge);
        } else {
            radioGroupButtonScale.check(R.id.radioButtonMedium);
        }
    }

    private void setupListeners() {
        // 1. Listener motywu
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appearanceManager.saveTheme(isChecked ? AppearanceManager.THEME_DARK : AppearanceManager.THEME_LIGHT);
            // Zmiana motywu nie wymaga recreate(), jest natychmiastowa
        });

        // 2. Listener rozmiaru TEKSTU
        radioGroupTextScale.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioTextSmall) {
                appearanceManager.saveTextScale(AppearanceManager.SCALE_SMALL);
            } else if (checkedId == R.id.radioTextLarge) {
                appearanceManager.saveTextScale(AppearanceManager.SCALE_LARGE);
            } else {
                appearanceManager.saveTextScale(AppearanceManager.SCALE_MEDIUM);
            }
            recreate(); // Zmiana rozmiaru wymaga odtworzenia aktywności
        });

        // 3. Listener rozmiaru PRZYCISKÓW
        radioGroupButtonScale.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioButtonSmall) {
                appearanceManager.saveButtonScale(AppearanceManager.SCALE_SMALL);
            } else if (checkedId == R.id.radioButtonLarge) {
                appearanceManager.saveButtonScale(AppearanceManager.SCALE_LARGE);
            } else {
                appearanceManager.saveButtonScale(AppearanceManager.SCALE_MEDIUM);
            }
            recreate(); // Zmiana rozmiaru wymaga odtworzenia aktywności
        });
    }
}
