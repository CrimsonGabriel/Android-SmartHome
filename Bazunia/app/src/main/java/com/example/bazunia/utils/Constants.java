package com.example.bazunia.utils;

public class Constants {
    // Definiuje unikalną nazwę dla sygnału (broadcast)
    public static final String ACTION_DATA_UPDATED = "com.example.bazunia.DATA_UPDATED";

    // ZMIENIONO: Używamy HTTPS i pełnej nazwy domenowej
    public static final String VPS_SERVER_IP = "https://testserwera.pl";

    public static final int ANDROID_LISTEN_PORT = 3000;
    public static final String SECRET_PASSWORD = "ZMIEN_TO_HASLO_XD";

    // --- KLUCZOWE STAŁE URL ---
    // Endpoint do rejestracji
    public static final String REGISTRATION_ENDPOINT = VPS_SERVER_IP + "/register/android";

    // Endpoint do pobierania danych (Polling)
    public static final String SENSOR_DATA_ENDPOINT = VPS_SERVER_IP + "/data/android";

    // --- NOWY ENDPOINT DLA GOOGLE AUTH ---
    public static final String GOOGLE_AUTH_ENDPOINT = VPS_SERVER_IP + "/auth/google";

    // --- NOWY ENDPOINT DLA WERYFIKACJI 2FA PRZY LOGOWANIU ---
    public static final String LOGIN_2FA_VERIFY_ENDPOINT = VPS_SERVER_IP + "/auth/2fa/login-verify";

    public static final String CHECK_2FA_STATUS_ENDPOINT = VPS_SERVER_IP + "/auth/2fa/status";
}

