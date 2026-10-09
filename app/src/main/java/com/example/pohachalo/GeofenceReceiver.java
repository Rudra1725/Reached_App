package com.example.pohachalo;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.telephony.SmsManager;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.LocationServices;

public class GeofenceReceiver extends BroadcastReceiver {

    private static final String ALERT_CHANNEL_ID = "pohachalo_alerts";
    private static final int ONGOING_NOTIF_ID = 9001;

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Reached:SMSWakeLock");
            wakeLock.acquire(15000);
        }

        try {
            GeofencingEvent event = GeofencingEvent.fromIntent(intent);
            if (event == null || event.hasError()) {
                return;
            }

            int transition = event.getGeofenceTransition();
            if (transition == Geofence.GEOFENCE_TRANSITION_ENTER || transition == Geofence.GEOFENCE_TRANSITION_DWELL) {
                SharedPreferences prefs = context.getSharedPreferences("PohachaloPrefs", Context.MODE_PRIVATE);

                // Read comma-separated contacts from the active slot
                String allPhones = prefs.getString("target_phone_all", prefs.getString("target_phone", "+919322161563"));
                String messageBody = prefs.getString("target_message", "P");

                SmsManager smsManager;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    smsManager = context.getSystemService(SmsManager.class);
                } else {
                    smsManager = SmsManager.getDefault();
                }

                String[] phoneList = allPhones.split(",");
                int sentCount = 0;
                for (String phone : phoneList) {
                    String cleanPhone = phone.trim();
                    if (!cleanPhone.isEmpty()) {
                        try {
                            smsManager.sendTextMessage(cleanPhone, null, messageBody, null, null);
                            sentCount++;
                        } catch (Exception ignored) {}
                    }
                }

                sendArrivalNotification(context, "Destination Reached 📍", "Sent: \"" + messageBody + "\" to " + sentCount + " contact(s).");
                disarmSelf(context);
            }
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private void disarmSelf(Context context) {
        GeofencingClient client = LocationServices.getGeofencingClient(context);
        Intent intent = new Intent(context, GeofenceReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        client.removeGeofences(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(ONGOING_NOTIF_ID);
        }

        SharedPreferences prefs = context.getSharedPreferences("PohachaloPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("is_armed", false).apply();
    }

    private void sendArrivalNotification(Context context, String title, String msg) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ALERT_CHANNEL_ID, "Arrival Alerts", NotificationManager.IMPORTANCE_HIGH
            );
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (manager != null) {
            manager.notify(1002, builder.build());
        }
    }
}