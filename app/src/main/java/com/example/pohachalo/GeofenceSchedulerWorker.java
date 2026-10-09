package com.example.pohachalo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

public class GeofenceSchedulerWorker extends Worker {

    private static final float GEOFENCE_RADIUS_METERS = 750.0f;
    private static final String ONGOING_CHANNEL_ID = "pohachalo_ongoing";
    private static final int ONGOING_NOTIF_ID = 9001;

    public GeofenceSchedulerWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    @SuppressLint("MissingPermission")
    public Result doWork() {
        Context context = getApplicationContext();

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure();
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

        Intent intent = new Intent(context, GeofenceReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        client.addGeofences(request, pendingIntent);

        prefs.edit().putBoolean("is_armed", true).apply();
        showNotification(context);

        return Result.success();
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
                .setContentTitle("Reached is Armed (Daily Auto) 📍")
                .setContentText("Monitoring destination boundary. Auto-SMS active.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (manager != null) {
            manager.notify(ONGOING_NOTIF_ID, builder.build());
        }
    }
}