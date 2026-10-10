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
    private static final long DEBOUNCE_COOLDOWN_MS = 15000;

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
            if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                SharedPreferences prefs = context.getSharedPreferences("PohachaloPrefs", Context.MODE_PRIVATE);

                long lastSentTime = prefs.getLong("last_sms_sent_timestamp", 0);
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastSentTime < DEBOUNCE_COOLDOWN_MS) {
                    return;
                }
                prefs.edit().putLong("last_sms_sent_timestamp", currentTime).apply();

                // 1. Identify which slot triggered
                int slotId = intent.getIntExtra("slot_id", 1);
                String prefix = "slot_" + slotId + "_";

                // 2. Read parameters directly from this slot
                String p1 = prefs.getString(prefix + "phone1", "");
                String p2 = prefs.getString(prefix + "phone2", "");
                String p3 = prefs.getString(prefix + "phone3", "");
                String messageBody = prefs.getString(prefix + "msg", "Reached safely.");
                String slotLabel = prefs.getString(prefix + "label", "Preset " + slotId);

                String allPhones = p1 + (p2.isEmpty() ? "" : "," + p2) + (p3.isEmpty() ? "" : "," + p3);

                if (!allPhones.isEmpty() && !messageBody.isEmpty()) {
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

                    sendArrivalNotification(context, slotLabel + " Reached", "Sent: \"" + messageBody + "\" to " + sentCount + " contact(s).");
                }

                disarmSlot(context, slotId);
            }
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private void disarmSlot(Context context, int slotId) {
        GeofencingClient client = LocationServices.getGeofencingClient(context);
        Intent intent = new Intent(context, GeofenceReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 3000 + slotId, intent, flags);
        client.removeGeofences(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(ONGOING_NOTIF_ID);
        }

        SharedPreferences prefs = context.getSharedPreferences("PohachaloPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("slot_" + slotId + "_is_armed", false).apply();
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