// 💾 NotificationHelper.java (PEŁNA, POPRAWIONA WERSJA)
package com.example.bazunia.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent; // <-- DODANY IMPORT
import android.content.Context;
import android.content.Intent; // <-- DODANY IMPORT
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.example.bazunia.R;
import com.example.bazunia.ui.AcceptUpdateActivity; // <-- DODANY IMPORT
import com.example.bazunia.ui.DeferUpdateActivity; // <-- DODANY IMPORT

public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "AlertChannel";
    private static final String CHANNEL_NAME = "Alerty Czujników";

    private final Context context;
    private final NotificationManager notificationManager;

    public NotificationHelper(Context context) {
        this.context = context;
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

    // WYMAGANIE 5.2: Wyświetla powiadomienie z akcjami (Zainstaluj/Odłóż).
    public void showNotificationWithAction(String title, String message, int notificationId, String updateKey) {

        // Intent dla akcji AKCEPTACJI
        Intent acceptIntent = new Intent(context, AcceptUpdateActivity.class);
        acceptIntent.putExtra("UPDATE_KEY", updateKey);
        acceptIntent.putExtra("NOTIFICATION_ID", notificationId);
        // Używamy flagi FLAG_IMMUTABLE
        PendingIntent acceptPendingIntent = PendingIntent.getActivity(context, notificationId * 2, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent dla akcji ODROCZENIA
        Intent deferIntent = new Intent(context, DeferUpdateActivity.class);
        deferIntent.putExtra("UPDATE_KEY", updateKey);
        deferIntent.putExtra("NOTIFICATION_ID", notificationId);
        // Używamy flagi FLAG_IMMUTABLE
        PendingIntent deferPendingIntent = PendingIntent.getActivity(context, notificationId * 2 + 1, deferIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);


        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                // Zakładamy, że masz ikonę ic_notification_update
                .setSmallIcon(R.drawable.ic_notification_update)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

                // DODANIE AKCJI (PRZYCISKÓW)
                // Zakładamy, że masz ikony ic_check i ic_close
                .addAction(R.drawable.ic_check, context.getString(R.string.action_accept_update), acceptPendingIntent)
                .addAction(R.drawable.ic_close, context.getString(R.string.action_defer_update), deferPendingIntent);

        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
            Log.i(TAG, "Wyslano powiadomienie o aktualizacji: " + updateKey);
        }
    }
}