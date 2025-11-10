package com.example.bazunia.utils;

public class Constants {
    // Definiuje unikalną nazwę dla sygnału (broadcast)
    public static final String ACTION_DATA_UPDATED = "com.example.bazunia.DATA_UPDATED";


    public static final String VPS_SERVER_IP = "https://testserwera.pl";

    public static final int ANDROID_LISTEN_PORT = 3000;
    public static final String SECRET_PASSWORD = "ZMIEN_TO_HASLO_XD";

    // --- KLUCZOWE STAŁE URL ---
    // Endpoint do rejestracji
    public static final String REGISTRATION_ENDPOINT = VPS_SERVER_IP + "/register/android";

    // Endpoint do pobierania danych (Polling)
    public static final String SENSOR_DATA_ENDPOINT = VPS_SERVER_IP + "/data/android";

    public static final String GATEWAYS_ENDPOINT = VPS_SERVER_IP + "/api/gateways";
    // --- NOWY ENDPOINT DLA GOOGLE AUTH (POPRAWIONO ŚCIEŻKĘ) ---
    public static final String SENSORS_ENDPOINT = VPS_SERVER_IP + "/api/sensors";
    public static final String GOOGLE_AUTH_ENDPOINT = VPS_SERVER_IP + "/api/auth/google";

    // --- NOWE ENDPOINTY 2FA (POPRAWIONO ŚCIEŻKI) ---
    public static final String LOGIN_2FA_VERIFY_ENDPOINT = VPS_SERVER_IP + "/api/auth/2fa/login-verify";
    public static final String CHECK_2FA_STATUS_ENDPOINT = VPS_SERVER_IP + "/api/auth/2fa/status";
    public static final String SETUP_2FA_ENDPOINT = VPS_SERVER_IP + "/api/auth/2fa/setup";
    public static final String VERIFY_2FA_ENDPOINT = VPS_SERVER_IP + "/api/auth/2fa/verify";
    public static final String DISABLE_2FA_ENDPOINT = VPS_SERVER_IP + "/api/auth/2fa/disable";




    public static final String UPDATE_STATUS_ENDPOINT = VPS_SERVER_IP + "/api/update/status";


    public static final String UPDATE_DECISION_ENDPOINT = VPS_SERVER_IP + "/api/update/decision";
    public static final String REGISTER_ANDROID_ENDPOINT = VPS_SERVER_IP + "/api/auth/android/register";
    public static final String LOGIN_EMAIL_ENDPOINT = VPS_SERVER_IP + "/api/auth/login";

    public static final String LOGIN_EMAIL_2FA_VERIFY_ENDPOINT = VPS_SERVER_IP + "/api/auth/2fa/email-verify";
    public static final String SET_PASSWORD_ENDPOINT = VPS_SERVER_IP + "/api/user/set-password";

    public static final String CHANGE_PASSWORD_ENDPOINT = VPS_SERVER_IP + "/api/user/change-password";
    public static final String REQUEST_PASSWORD_RESET_ENDPOINT = VPS_SERVER_IP + "/api/auth/request-password-reset";
}
