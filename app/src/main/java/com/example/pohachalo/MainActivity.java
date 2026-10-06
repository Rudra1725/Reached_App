package com.example.pohachalo;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // PUT YOUR SELECTED CAMPUS COORDINATES HERE
    private static final double TARGET_LAT = 18.5204;
    private static final double TARGET_LNG = 73.9782;
    private static final float GEOFENCE_RADIUS_METERS = 500.0f;

    private static final int REQ_FOREGROUND = 101;
    private static final int REQ_BACKGROUND = 102;
    private static final String ONGOING_CHANNEL_ID = "pohachalo_ongoing";
    private static final int ONGOING_NOTIF_ID = 9001;

    private GeofencingClient geofencingClient;
    private TextView tvStatusBadge, tvStatusText;
    private MaterialButton btnToggle;
    private boolean isArmed = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("PohachaloPrefs", MODE_PRIVATE);
        isArmed = prefs.getBoolean("is_armed", false);

        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvStatusText = findViewById(R.id.tvStatusText);
        btnToggle = findViewById(R.id.btnToggle);

        geofencingClient = LocationServices.getGeofencingClient(this);

        updateUIState(isArmed);

        btnToggle.setOnClickListener(v -> {
            if (isArmed) {
                disarmGeofence();
            } else {
                checkAndRequestPermissions();
            }
        });
    }

    private void updateUIState(boolean active) {
        isArmed = active;
        prefs.edit().putBoolean("is_armed", active).apply();

        if (active) {
            tvStatusBadge.setText("ACTIVE");
            tvStatusBadge.setBackgroundColor(Color.parseColor("#10B981")); // Green
            tvStatusText.setText("Armed & Monitoring Campus");
            btnToggle.setText("DISARM MONITORING");
            btnToggle.setBackgroundColor(Color.parseColor("#EF4444")); // Red
            showOngoingNotification();
        } else {
            tvStatusBadge.setText("OFFLINE");
            tvStatusBadge.setBackgroundColor(Color.parseColor("#EF4444")); // Red
            tvStatusText.setText("Monitoring Inactive");
            btnToggle.setText("ARM MONITORING");
            btnToggle.setBackgroundColor(Color.parseColor("#3B82F6")); // Blue
            removeOngoingNotification();
        }
    }

    private void checkAndRequestPermissions() {
        boolean fineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean sms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean notif = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        if (!fineLoc || !sms || !notif) {
            List<String> permissions = new ArrayList<>();
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.SEND_SMS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), REQ_FOREGROUND);
        } else {
            checkBackgroundLocation();
        }
    }

    private void checkBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Select 'Allow all the time' on next prompt", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        REQ_BACKGROUND
                );
                return;
            }
        }
        armGeofence();
    }

    private void armGeofence() {
        Geofence geofence = new Geofence.Builder()
                .setRequestId("CAMPUS_GEOFENCE")
                .setCircularRegion(TARGET_LAT, TARGET_LNG, GEOFENCE_RADIUS_METERS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        Intent intent = new Intent(this, GeofenceReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(request, pendingIntent)
                    .addOnSuccessListener(aVoid -> {
                        updateUIState(true);
                        Toast.makeText(MainActivity.this, "Armed successfully!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void disarmGeofence() {
        Intent intent = new Intent(this, GeofenceReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        geofencingClient.removeGeofences(pendingIntent)
                .addOnSuccessListener(aVoid -> {
                    updateUIState(false);
                    Toast.makeText(this, "Disarmed successfully", Toast.LENGTH_SHORT).show();
                });
    }

    private void showOngoingNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ONGOING_CHANNEL_ID, "Monitoring Status", NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Reached is Armed 📍")
                .setContentText("Monitoring campus boundary. Auto-SMS enabled.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                manager.notify(ONGOING_NOTIF_ID, builder.build());
            }
        } else {
            manager.notify(ONGOING_NOTIF_ID, builder.build());
        }
    }

    private void removeOngoingNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(ONGOING_NOTIF_ID);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_FOREGROUND) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkBackgroundLocation();
            } else {
                Toast.makeText(this, "Permissions are required", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_BACKGROUND) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                armGeofence();
            } else {
                Toast.makeText(this, "Must choose 'Allow all the time'", Toast.LENGTH_LONG).show();
            }
        }
    }
}