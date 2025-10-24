package com.example.bazunia;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        new AppearanceManager(this).applyAppearance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText editTextLogin = findViewById(R.id.editTextLogin);
        EditText editTextPassword = findViewById(R.id.editTextPassword);
        Button buttonLogin = findViewById(R.id.buttonLogin);

        buttonLogin.setOnClickListener(v -> {
            String login = editTextLogin.getText().toString();
            String password = editTextPassword.getText().toString();

            if (login.equals(getString(R.string.login_admin_username)) && password.equals(getString(R.string.login_admin_password))) {

                // URUCHOMIENIE TYLKO SERWISU KLIENTA VPS (REST)
                startVpsClientService();

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, getString(R.string.login_invalid_credentials), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // NOWA METODA URUCHAMIAJĄCA TYLKO VpsClientService
    private void startVpsClientService() {
        Intent serviceIntent = new Intent(this, VpsClientService.class);

        // Uruchomienie jako foreground service
        startForegroundService(serviceIntent);
    }

    // USUNIĘTO starą metodę startMonitoringService(), która uruchamiała Socket.IO
}
