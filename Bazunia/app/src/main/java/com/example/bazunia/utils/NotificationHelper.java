package com.example.bazunia.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.bazunia.R;
import com.example.bazunia.data.SensorModel;
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
        LOW, HIGH, DOOR
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
     * <<< NOWA WERSJA UŻYWAJĄCA .replace() >>>
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
            default:
                title = context.getString(R.string.alert_title_temp_humidity)
                        .replace("[TYP]", sensor.type)
                        .replace("[ID]", sensor.sensorId);

                message = context.getString(R.string.alert_msg_too_high)
                        .replace("[TYP]", sensor.type)
                        .replace("[WARTOŚĆ]", valStr)
                        .replace("[PROG]", thrStr);
                break;
        }

        // Używamy standardowej metody showNotification do wyświetlenia
        showNotification(title, message, notificationId);
    }
}