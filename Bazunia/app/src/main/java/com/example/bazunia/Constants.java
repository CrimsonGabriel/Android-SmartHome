package com.example.bazunia;

public class Constants {
    // Definiuje unikalną nazwę dla sygnału (broadcast)
    public static final String ACTION_DATA_UPDATED = "com.example.bazunia.DATA_UPDATED";

    // ZMIENIONO: Używamy HTTPS i pełnej nazwy domenowej
    public static final String VPS_SERVER_IP = "https://testserwera.pl";

    public static final int ANDROID_LISTEN_PORT = 3000;
    public static final String SECRET_PASSWORD = "ZMIEN_TO_HASLO_XD";

    // --- KLUCZOWE STAŁE URL ---
    // Endpoint do rejestracji (NanoHTTPD/Powiadomienia)
    public static final String REGISTRATION_ENDPOINT = VPS_SERVER_IP + "/register/android";

    // NOWA STAŁA: Endpoint do pobierania danych (Polling)
    // Zmienia starą nazwę DATA_PULL_ENDPOINT na SENSOR_DATA_ENDPOINT
    public static final String SENSOR_DATA_ENDPOINT = VPS_SERVER_IP + "/data/android";

    // Nazwa endpointu nasluchiwanego przez NanoHTTPD (do odbierania POWIADOMIEN)
    public static final String NOTIFICATION_ENDPOINT = "/notify";

    // ... inne stałe ...
}