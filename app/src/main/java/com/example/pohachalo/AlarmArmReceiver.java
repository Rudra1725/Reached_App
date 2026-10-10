package com.example.pohachalo;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

public class AlarmArmReceiver extends BroadcastReceiver {

    private static final String ONGOING_CHANNEL_ID = "pohachalo_ongoing";
    private static final int ONGOING_NOTIF_ID = 9001;
    private static final float GEOFENCE_RADIUS_METERS = 750.0f;

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Reached:AlarmWakeLock");
            wakeLock.acquire(30000);
        }

        final PowerManager.WakeLock finalWakeLock = wakeLock;
        final PendingResult pendingResult = goAsync();

        // Retrieve the triggering slot ID
        int slotId = intent.getIntExtra("slot_id", 1);

        SharedPreferences prefs = context.getSharedPreferences("PohachaloPrefs", Context.MODE_PRIVATE);
        String prefix = "slot_" + slotId + "_";

        float lat = prefs.getFloat(prefix + "lat", Float.NaN);
        float lng = prefs.getFloat(prefix + "lng", Float.NaN);
        int hour = prefs.getInt(prefix + "hour", 12);
        int minute = prefs.getInt(prefix + "minute", 0);
        String slotLabel = prefs.getString(prefix + "label", "Preset " + slotId);

        String p1 = prefs.getString(prefix + "phone1", "");
        String p2 = prefs.getString(prefix + "phone2", "");
        String p3 = prefs.getString(prefix + "phone3", "");
        String msg = prefs.getString(prefix + "msg", "Reached safely.");
        String combinedPhones = p1 + (p2.isEmpty() ? "" : "," + p2) + (p3.isEmpty() ? "" : "," + p3);

        // Reschedule this specific slot's alarm for tomorrow
        SettingsActivity.scheduleSlotAlarm(context, slotId, hour, minute);

        if (Float.isNaN(lat) || Float.isNaN(lng) || combinedPhones.isEmpty()) {
            releaseResources(finalWakeLock, pendingResult);
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            releaseResources(finalWakeLock, pendingResult);
            return;
        }

        GeofencingClient geofencingClient = LocationServices.getGeofencingClient(context);

        // Unique geofence request ID per slot (GEOFENCE_SLOT_1, GEOFENCE_SLOT_2, etc.)
        String geofenceRequestId = "GEOFENCE_SLOT_" + slotId;

        Geofence geofence = new Geofence.Builder()
                .setRequestId(geofenceRequestId)
                .setCircularRegion((double) lat, (double) lng, GEOFENCE_RADIUS_METERS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setNotificationResponsiveness(5000)
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        // Pass this slot's payload to the GeofenceReceiver
        Intent geofenceIntent = new Intent(context, GeofenceReceiver.class);
        geofenceIntent.putExtra("slot_id", slotId);
        geofenceIntent.putExtra("target_phones", combinedPhones);
        geofenceIntent.putExtra("target_message", msg);
        geofenceIntent.putExtra("slot_label", slotLabel);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent pi = PendingIntent.getBroadcast(context, 3000 + slotId, geofenceIntent, flags);

        geofencingClient.addGeofences(request, pi)
                .addOnSuccessListener(aVoid -> {
                    prefs.edit()
                            .putBoolean("is_armed", true)
                            .putLong("last_sms_sent_timestamp", 0)
                            .apply();
                    showOngoingNotification(context, slotLabel);
                    releaseResources(finalWakeLock, pendingResult);
                })
                .addOnFailureListener(e -> {
                    releaseResources(finalWakeLock, pendingResult);
                });
    }

    private void releaseResources(PowerManager.WakeLock wakeLock, PendingResult pendingResult) {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {}
        try {
            if (pendingResult != null) {
                pendingResult.finish();
            }
        } catch (Exception ignored) {}
    }

    private void showOngoingNotification(Context context, String label) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ONGOING_CHANNEL_ID, "Monitoring Status", NotificationManager.IMPORTANCE_LOW
            );
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Reached 📍 is Armed (" + label + ")")
                .setContentText("Monitoring configured destination boundary.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (manager != null) {
            manager.notify(ONGOING_NOTIF_ID, builder.build());
        }
    }
}