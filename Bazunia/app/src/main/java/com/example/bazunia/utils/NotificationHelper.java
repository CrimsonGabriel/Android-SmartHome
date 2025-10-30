package com.example.bazunia.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.example.bazunia.R;

public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "AlertChannel";
    private static final String CHANNEL_NAME = "Alerty Czujników";

    private final Context context;
    private final NotificationManager notificationManager;

    public NotificationHelper(Context context) {
        this.context = context;
        // Wymaga getSystemService()
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Powiadomienia o przekroczeniu progów czujników.");

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Wyświetla powiadomienie.
     * @param title Tytuł powiadomienia
     * @param message Treść powiadomienia
     * @param notificationId Unikalny ID powiadomienia (np. ID czujnika)
     */
    public void showNotification(String title, String message, int notificationId) {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
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
}
