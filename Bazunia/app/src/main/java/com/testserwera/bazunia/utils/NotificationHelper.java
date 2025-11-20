package com.testserwera.bazunia.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.SensorModel; // Ten import jest OK dla starej metody
import com.testserwera.bazunia.ui.AcceptUpdateActivity;
import com.testserwera.bazunia.ui.DeferUpdateActivity;

import java.util.Locale;

public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID_ALERTS = "AlertChannel";
    private static final String CHANNEL_NAME_ALERTS = "Alerty Czujników";
    private static final String CHANNEL_ID_UPDATES = "UpdateChannel";
    private static final String CHANNEL_NAME_UPDATES = "Aktualizacje Systemu";

    private final Context context;
    private final NotificationManager notificationManager;

    public enum ThresholdType {
        LOW, HIGH, DOOR,
        BATTERY // <<< 1. DODANY NOWY TYP
    }

    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        NotificationChannel alertChannel = new NotificationChannel(
                CHANNEL_ID_ALERTS,
                CHANNEL_NAME_ALERTS,
                NotificationManager.IMPORTANCE_HIGH
        );
        alertChannel.setDescription("Powiadomienia o przekroczeniu progów czujników.");

        NotificationChannel updateChannel = new NotificationChannel(
                CHANNEL_ID_UPDATES,
                CHANNEL_NAME_UPDATES,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        updateChannel.setDescription("Powiadomienia o dostępnych aktualizacjach.");

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(alertChannel);
            notificationManager.createNotificationChannel(updateChannel);
        }
    }

    // Ta metoda jest ogólna i używana przez inne
    public void showNotification(String title, String message, int notificationId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
            Log.i(TAG, "Wyslano powiadomienie: " + title + " - " + message);
        }
    }

    /**
     * NOWA METODA DLA AKTUALIZACJI SYSTEMOWYCH
     */
    public void showUpdateNotification(long assignmentId, String title, String version, String urgency) {
        String notifTitle = "Aktualizacja: " + title + " (" + version + ")";
        String notifBody = "Dostępna nowa aktualizacja. Status: " + urgency;

        if ("REQUIRED".equals(urgency)) {
            notifBody += " (Wymagana!)";
        }

        // --- NOWA LOGIKA: Sprawdzamy licznik odroczeń ---
        // Musimy odczytać ten sam plik SharedPreferences co w DeferUpdateActivity ("UpdatePrefs")
        android.content.SharedPreferences prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE);
        int deferCount = prefs.getInt("defer_count_" + assignmentId, 0);

        // Decyzja: Czy pokazać przycisk "Odłóż"?
        // Pokaż JEŚLI: (To NIE jest REQUIRED) LUB (To jest REQUIRED, ale licznik jest 0)
        boolean showDeferButton = !"REQUIRED".equals(urgency) || deferCount == 0;
        // -----------------------------------------------

        int notificationId = (int) assignmentId;

        // Intent dla Akceptacji (Zainstaluj)
        Intent acceptIntent = new Intent(context, AcceptUpdateActivity.class);
        acceptIntent.putExtra("ASSIGNMENT_ID", assignmentId);
        acceptIntent.putExtra("URGENCY", urgency);
        acceptIntent.putExtra("NOTIFICATION_ID", notificationId);
        PendingIntent acceptPendingIntent = PendingIntent.getActivity(
                context,
                notificationId * 10,
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Budowanie powiadomienia
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_update)
                .setContentTitle(notifTitle)
                .setContentText(notifBody)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notifBody))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false) // Wymaga akcji użytkownika
                .setOngoing("REQUIRED".equals(urgency)) // Nie da się usunąć palcem jeśli wymagana

                // Guzik "Zainstaluj" - ZAWSZE widoczny
                .addAction(R.drawable.ic_check, context.getString(R.string.action_accept_update), acceptPendingIntent);

        // Guzik "Odłóż" - WARUNKOWO widoczny
        if (showDeferButton) {
            // Intent dla Odroczenia (tworzymy go tylko jeśli potrzebny)
            Intent deferIntent = new Intent(context, DeferUpdateActivity.class);
            deferIntent.putExtra("ASSIGNMENT_ID", assignmentId);
            deferIntent.putExtra("URGENCY", urgency);
            deferIntent.putExtra("NOTIFICATION_ID", notificationId);
            PendingIntent deferPendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId * 10 + 1,
                    deferIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            builder.addAction(R.drawable.ic_close, context.getString(R.string.action_defer_update), deferPendingIntent);
        } else if ("REQUIRED".equals(urgency)) {
            // Opcjonalnie: Zmień tekst, żeby użytkownik wiedział, że to ostatnia szansa
            builder.setContentText(notifBody + "\nOstatnie ostrzeżenie: Instalacja wymagana.");
        }

        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
        }
    }

    /**
     * <<< 2. NOWA METODA DLA POWIADOMIEŃ O BATERII >>>
     * Używa nowych stringów i formatowania.
     */
    public void showBatteryAlert(String sensorName, String gatewayName, int batteryLevel) {
        String title = context.getString(R.string.alert_title_battery_low);
        String message;

        // Używamy ID powiadomienia na podstawie nazwy, aby nadpisać poprzednie powiadomienie
        // o baterii dla tego samego czujnika.
        int notificationId = (gatewayName + "_" + sensorName + "_battery").hashCode();

        // Wybierz odpowiedni komunikat na podstawie poziomu baterii
        if (batteryLevel <= 1) {
            message = context.getString(R.string.alert_msg_battery_1, sensorName, gatewayName, batteryLevel);
        } else if (batteryLevel <= 10) {
            message = context.getString(R.string.alert_msg_battery_10, sensorName, gatewayName, batteryLevel);
        } else {
            // Domyślnie dla progu 20% (lub innego między 11 a 20)
            message = context.getString(R.string.alert_msg_battery_20, sensorName, gatewayName, batteryLevel);
        }

        // Użyj ogólnej metody do zbudowania i wysłania
        showNotification(title, message, notificationId);
    }


    /**
     * Stara metoda dla progów (temp, drzwi)
     */
    public void showThresholdAlert(SensorModel sensor, float currentValue, float threshold, ThresholdType type) {
        String title, message;

        int notificationId = (sensor.gatewayId + "_" + sensor.sensorId).hashCode();

        // Konwertuj float na String (bezpiecznie, z kropką)
        String valStr = String.format(Locale.US, "%.1f", currentValue);
        String thrStr = String.format(Locale.US, "%.1f", threshold);


        switch (type) {
            case DOOR:
                title = context.getString(R.string.alert_title_door_window);
                message = context.getString(R.string.alert_msg_door_open)
                        .replace("[ID]", sensor.sensorId)
                        .replace("[GATEWAY]", sensor.gatewayId);
                break;


            case LOW:
                title = context.getString(R.string.alert_title_temp_humidity)
                        .replace("[TYP]", sensor.type)
                        .replace("[ID]", sensor.sensorId);

                message = context.getString(R.string.alert_msg_too_low)
                        .replace("[TYP]", sensor.type)
                        .replace("[WARTOŚĆ]", valStr)
                        .replace("[PROG]", thrStr);
                break;


            case HIGH:
                title = context.getString(R.string.alert_title_temp_humidity)
                        .replace("[TYP]", sensor.type)
                        .replace("[ID]", sensor.sensorId);

                message = context.getString(R.string.alert_msg_too_high)
                        .replace("[TYP]", sensor.type)
                        .replace("[WARTOŚĆ]", valStr)
                        .replace("[PROG]", thrStr);
                break;

            // <<< 3. DODANA OBSŁUGA NOWEGO TYPU ENUM (aby uniknąć błędu) >>>
            case BATTERY:
            default:
                // Ta metoda nie powinna być nigdy wywołana dla 'BATTERY'
                // Używamy 'showBatteryAlert'
                Log.e(TAG, "showThresholdAlert został błędnie wywołany dla typu BATTERY.");
                return; // Nie wysyłaj powiadomienia
        }

        // Używamy standardowej metody showNotification do wyświetlenia
        showNotification(title, message, notificationId);
    }
    public void showSensorCommsError(String entityName, String errorMessage) {
        String title = context.getString(R.string.alert_title_sensor_comms_error); // Nowy string
        int notificationId = (entityName + "_comms_error").hashCode(); // Unikalne ID
        showNotification(title, errorMessage, notificationId); // Użyj generycznej metody
    }


}