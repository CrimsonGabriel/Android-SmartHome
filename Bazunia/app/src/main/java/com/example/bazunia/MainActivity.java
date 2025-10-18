package com.example.bazunia;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
public class MainActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private ThemeManager themeManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        themeManager = new ThemeManager(this);
        themeManager.applyTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        dbHelper = new DatabaseHelper(this);

        Button btnView = findViewById(R.id.btnView);
        MaterialSwitch switchTheme = findViewById(R.id.switchTheme);

        // Ustaw stan przełącznika na podstawie zapisanego motywu
        switchTheme.setChecked(themeManager.getCurrentTheme() == ThemeManager.THEME_DARK);
        btnView.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DataActivity.class);
            startActivity(intent);
        });

        // Logika przełączania motywu
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                themeManager.setTheme(ThemeManager.THEME_DARK);
            } else {
                themeManager.setTheme(ThemeManager.THEME_LIGHT);
            }
        });
    }
}
