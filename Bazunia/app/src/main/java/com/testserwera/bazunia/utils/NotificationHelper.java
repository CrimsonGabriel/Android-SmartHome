package com.testserwera.bazunia.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.SensorModel;
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
        LOW,            // Za nisko (np. zimno)
        HIGH,           // Za wysoko (np. gorąco)
        BINARY_ACTIVE,  // ⭐️ NOWOŚĆ: Stan aktywny (Otwarte, Ruch, Światło, Wyciek)
        BATTERY         // Bateria
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
        alertChannel.setDescription("Powiadomienia o alarmach i progach.");

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
            Log.i(TAG, "Wyslano powiadomienie: " + title);
        }
    }

    /**
     * Główna metoda wyświetlania alarmów czujników.
     * Obsługuje logikę tekstów dla różnych typów (Ruch, Drzwi, Temp).
     */
    /**
     * Główna metoda wyświetlania alarmów czujników.
     * NAPRAWIONE: Używa poprawnego formatowania stringów (%s) zamiast .replace
     */
    public void showThresholdAlert(SensorModel sensor, float currentValue, float threshold, ThresholdType type) {
        String title;
        String message;
        int notificationId = (sensor.gatewayId + "_" + sensor.sensorId).hashCode();

        // Formatowanie wartości liczbowych (dla analogowych)
        String valStr = String.format(Locale.US, "%.1f", currentValue);
        String thrStr = String.format(Locale.US, "%.1f", threshold);

        switch (type) {
            case BINARY_ACTIVE:
                String sensorType = sensor.type != null ? sensor.type.toLowerCase() : "";

                if (sensorType.contains("motion")) {
                    // RUCH
                    title = context.getString(R.string.alert_title_motion);
                    // Używamy argumentów (sensorId, gatewayId) pod %1$s i %2$s
                    message = context.getString(R.string.alert_msg_motion, sensor.sensorId, sensor.gatewayId);
                }
                else if (sensorType.contains("light") || sensorType.contains("socket") || sensorType.contains("switch")) {
                    // ŚWIATŁO
                    title = context.getString(R.string.alert_title_light);
                    message = context.getString(R.string.alert_msg_light, sensor.sensorId, sensor.gatewayId);
                }
                else if (sensorType.contains("flow") || sensorType.contains("leak") || sensorType.contains("valve")) {
                    // WYCIEK
                    title = context.getString(R.string.alert_title_leak);
                    message = context.getString(R.string.alert_msg_leak, sensor.sensorId, sensor.gatewayId);
                }
                else {
                    // DRZWI (Domyślne)
                    title = context.getString(R.string.alert_title_door_window);
                    message = context.getString(R.string.alert_msg_door_open, sensor.sensorId, sensor.gatewayId);
                }
                break;

            case LOW:
                // ANALOGOWE - ZA NISKO
                title = context.getString(R.string.alert_title_temp_humidity)
                        .replace("[TYP]", sensor.type) // Tu w tytule nie dawaliśmy %s, więc replace jest OK (chyba że w XML zmieniłeś)
                        .replace("[ID]", sensor.sensorId);

                // W treści używamy argumentów: TYP, WARTOŚĆ, PRÓG
                message = context.getString(R.string.alert_msg_too_low, sensor.type, valStr, thrStr);
                break;

            case HIGH:
                // ANALOGOWE - ZA WYSOKO
                title = context.getString(R.string.alert_title_temp_humidity)
                        .replace("[TYP]", sensor.type)
                        .replace("[ID]", sensor.sensorId);

                message = context.getString(R.string.alert_msg_too_high, sensor.type, valStr, thrStr);
                break;

            default:
                return;
        }

        showNotification(title, message, notificationId);
    }

    // --- Metody Aktualizacji (System) ---
    public void showUpdateNotification(long assignmentId, String title, String version, String urgency) {
        String notifTitle = "Aktualizacja: " + title + " (" + version + ")";
        String notifBody = "Dostępna nowa aktualizacja. Status: " + urgency;
        boolean isRequired = "REQUIRED".equals(urgency);
        if (isRequired) notifBody += " (Wymagana!)";

        android.content.SharedPreferences prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE);
        int deferCount = prefs.getInt("defer_count_" + assignmentId, 0);
        boolean showDeferButton = !isRequired || deferCount == 0;

        int notificationId = (int) assignmentId;
        Intent acceptIntent = new Intent(context, AcceptUpdateActivity.class);
        acceptIntent.putExtra("ASSIGNMENT_ID", assignmentId);
        acceptIntent.putExtra("URGENCY", urgency);
        acceptIntent.putExtra("NOTIFICATION_ID", notificationId);
        PendingIntent acceptPendingIntent = PendingIntent.getActivity(context, notificationId * 10, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_update)
                .setContentTitle(notifTitle)
                .setContentText(notifBody)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notifBody))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setOngoing(isRequired)
                .addAction(R.drawable.ic_check, context.getString(R.string.action_accept_update), acceptPendingIntent);

        if (showDeferButton) {
            Intent deferIntent = new Intent(context, DeferUpdateActivity.class);
            deferIntent.putExtra("ASSIGNMENT_ID", assignmentId);
            deferIntent.putExtra("URGENCY", urgency);
            deferIntent.putExtra("NOTIFICATION_ID", notificationId);
            PendingIntent deferPendingIntent = PendingIntent.getActivity(context, notificationId * 10 + 1, deferIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_close, context.getString(R.string.action_defer_update), deferPendingIntent);
        } else {
            builder.setContentText(notifBody + "\nOstatnie ostrzeżenie: Instalacja wymagana.");
        }
        if (notificationManager != null) notificationManager.notify(notificationId, builder.build());
    }

    // --- Metody Baterii i Błędów ---
    public void showBatteryAlert(String sensorName, String gatewayName, int batteryLevel) {
        String title = context.getString(R.string.alert_title_battery_low);
        String message;
        int notificationId = (gatewayName + "_" + sensorName + "_battery").hashCode();

        if (batteryLevel <= 1) message = context.getString(R.string.alert_msg_battery_1, sensorName, gatewayName, batteryLevel);
        else if (batteryLevel <= 10) message = context.getString(R.string.alert_msg_battery_10, sensorName, gatewayName, batteryLevel);
        else message = context.getString(R.string.alert_msg_battery_20, sensorName, gatewayName, batteryLevel);

        showNotification(title, message, notificationId);
    }

    public void showSensorCommsError(String entityName, String errorMessage) {
        String title = context.getString(R.string.alert_title_sensor_comms_error);
        int notificationId = (entityName + "_comms_error").hashCode();
        showNotification(title, errorMessage, notificationId);
    }

    public void showRetentionStatusNotification(boolean accepted) {
        String title = context.getString(R.string.notif_retention_title);
        String message = accepted ? context.getString(R.string.notif_retention_accepted) : context.getString(R.string.notif_retention_rejected);
        int notificationId = 999;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
                .setSmallIcon(accepted ? R.drawable.ic_check : R.drawable.ic_close)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        if (notificationManager != null) notificationManager.notify(notificationId, builder.build());
    }
}