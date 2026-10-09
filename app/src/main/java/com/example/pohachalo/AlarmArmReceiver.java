package com.example.pohachalo;

import android.Manifest;
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

    private static final float GEOFENCE_RADIUS_METERS = 750.0f;
    private static final String ONGOING_CHANNEL_ID = "pohachalo_ongoing";
    private static final int ONGOING_NOTIF_ID = 9001;

    @Override
    @SuppressLint("MissingPermission")
    public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Reached:ArmWakeLock");
            wakeLock.acquire(10000); // 10 seconds
        }

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            SharedPreferences prefs = context.getSharedPreferences("PohachaloPrefs", Context.MODE_PRIVATE);
            double lat = prefs.getFloat("target_lat", 18.5204f);
            double lng = prefs.getFloat("target_lng", 73.9782f);

            GeofencingClient client = LocationServices.getGeofencingClient(context);

            Geofence geofence = new Geofence.Builder()
                    .setRequestId("CAMPUS_GEOFENCE")
                    .setCircularRegion(lat, lng, GEOFENCE_RADIUS_METERS)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_DWELL)
                    .setLoiteringDelay(10000)
                    .setNotificationResponsiveness(5000)
                    .build();

            GeofencingRequest request = new GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER | GeofencingRequest.INITIAL_TRIGGER_DWELL)
                    .addGeofence(geofence)
                    .build();

            Intent geofenceIntent = new Intent(context, GeofenceReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, 0, geofenceIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );

            client.addGeofences(request, pendingIntent);

            prefs.edit().putBoolean("is_armed", true).apply();
            showNotification(context);

            // Re-arm for tomorrow at the same scheduled time
            int hour = prefs.getInt("timer_hour", 8);
            int minute = prefs.getInt("timer_minute", 30);
            SettingsActivity.scheduleExactAlarm(context, hour, minute);

        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private void showNotification(Context context) {
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
                .setContentTitle("Reached is Armed (Auto-Alarm) 📍")
                .setContentText("Monitoring campus boundary. Auto-SMS active.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (manager != null) {
            manager.notify(ONGOING_NOTIF_ID, builder.build());
        }
    }
}