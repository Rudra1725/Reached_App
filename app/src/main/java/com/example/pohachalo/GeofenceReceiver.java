package com.example.pohachalo;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.SmsManager;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceReceiver extends BroadcastReceiver {

    // REPLACE WITH BABA'S REAL NUMBER (include +91)
    private static final String TARGET_PHONE = "+919322161563";
    private static final String SMS_BODY = "P";
    private static final String ALERT_CHANNEL_ID = "pohachalo_alerts";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) {
            return;
        }

        if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(TARGET_PHONE, null, SMS_BODY, null, null);
                sendArrivalNotification(context, "Campus Arrived 📍", "Sent 'P' to Baba successfully!");
            } catch (Exception e) {
                sendArrivalNotification(context, "Pohachalo Alert", "Geofence reached, but SMS failed: " + e.getMessage());
            }
        }
    }

    private void sendArrivalNotification(Context context, String title, String msg) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ALERT_CHANNEL_ID, "Arrival Alerts", NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        manager.notify(1002, builder.build());
    }
}