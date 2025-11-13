package com.example.bazunia.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.bazunia.R;
import com.example.bazunia.data.SensorModel; // Ten import jest OK dla starej metody
import com.example.bazunia.ui.AcceptUpdateActivity;
import com.example.bazunia.ui.DeferUpdateActivity;

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

    // Metoda dla aktualizacji (bez zmian)
    public void showUpdateNotification(String updateKey, String status) {
        // Używamy replace() zamiast String.format()
        String title = context.getString(R.string.notification_update_title);
        String message = context.getString(R.string.notification_update_available)
                .replace("[KLUCZ]", updateKey)
                .replace("[STATUS]", status);

        int notificationId = updateKey.hashCode();

        Intent acceptIntent = new Intent(context, AcceptUpdateActivity.class);
        acceptIntent.putExtra("UPDATE_KEY", updateKey);
        acceptIntent.putExtra("NOTIFICATION_ID", notificationId);
        PendingIntent acceptPendingIntent = PendingIntent.getActivity(context, notificationId * 2, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent deferIntent = new Intent(context, DeferUpdateActivity.class);
        deferIntent.putExtra("UPDATE_KEY", updateKey);
        deferIntent.putExtra("NOTIFICATION_ID", notificationId);
        PendingIntent deferPendingIntent = PendingIntent.getActivity(context, notificationId * 2 + 1, deferIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_update)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                // <<< POPRAWKA: Literówka 'NotificationJpcompat' -> 'NotificationCompat'
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_check, context.getString(R.string.action_accept_update), acceptPendingIntent)
                .addAction(R.drawable.ic_close, context.getString(R.string.action_defer_update), deferPendingIntent);

        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
            Log.i(TAG, "Wyslano powiadomienie o aktualizacji: " + updateKey);
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