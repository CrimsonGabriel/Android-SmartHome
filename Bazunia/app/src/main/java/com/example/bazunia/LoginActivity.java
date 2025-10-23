package com.example.bazunia;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText editTextLogin;
    private EditText editTextPassword;
    private Button buttonLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new AppearanceManager(this).applyAppearance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        editTextLogin = findViewById(R.id.editTextLogin);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);

        buttonLogin.setOnClickListener(v -> {
            String login = editTextLogin.getText().toString();
            String password = editTextPassword.getText().toString();

            if (login.equals("admin") && password.equals("admin")) {

                // URUCHOMIENIE TYLKO SERWISU KLIENTA VPS (REST)
                startVpsClientService();

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Nieprawidłowy login lub hasło", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // NOWA METODA URUCHAMIAJĄCA TYLKO VpsClientService
    private void startVpsClientService() {
        Intent serviceIntent = new Intent(this, VpsClientService.class);

        // Uruchomienie jako foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // USUNIĘTO starą metodę startMonitoringService(), która uruchamiała Socket.IO
}