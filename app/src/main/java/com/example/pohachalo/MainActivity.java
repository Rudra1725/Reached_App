package com.example.pohachalo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
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
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final float GEOFENCE_RADIUS_METERS = 750.0f;
    private static final int REQ_FOREGROUND = 101;
    private static final int REQ_BACKGROUND = 102;
    private static final String ONGOING_CHANNEL_ID = "pohachalo_ongoing";
    private static final int ONGOING_NOTIF_ID = 9001;

    private GeofencingClient geofencingClient;
    private TextView tvStatusBadge;
    private TextView tvStatusText;
    private TextView tvActiveSlotTitle;
    private TextView tvTimerSummary;
    private MaterialButton btnToggle;
    private MaterialCardView cardStatusFloating;
    private SharedPreferences prefs;
    private boolean isArmed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("PohachaloPrefs", MODE_PRIVATE);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvStatusText = findViewById(R.id.tvStatusText);
        tvActiveSlotTitle = findViewById(R.id.tvActiveSlotTitle);
        tvTimerSummary = findViewById(R.id.tvTimerSummary);
        btnToggle = findViewById(R.id.btnToggle);
        cardStatusFloating = findViewById(R.id.cardStatusFloating);
        MaterialButton btnOpenSettings = findViewById(R.id.btnOpenSettings);
        ImageButton btnAppGuide = findViewById(R.id.btnAppGuide);

        geofencingClient = LocationServices.getGeofencingClient(this);

        // Start smooth vertical floating animation
        Animation floatAnimation = AnimationUtils.loadAnimation(this, R.anim.float_and_pulse);
        cardStatusFloating.startAnimation(floatAnimation);

        // Schedule exact daily alarm using active saved time
        int hour = prefs.getInt("timer_hour", 8);
        int minute = prefs.getInt("timer_minute", 30);
        SettingsActivity.scheduleExactAlarm(this, hour, minute);

        // Arm / Disarm toggle button
        btnToggle.setOnClickListener(v -> {
            if (isArmed) {
                disarmGeofence();
            } else {
                checkAndRequestPermissions();
            }
        });

        // Open Settings
        btnOpenSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        // Corner Guide Dialog
        btnAppGuide.setOnClickListener(v -> showAppGuideDialog());
    }

    private void showAppGuideDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reached 📍 — User Guide")
                .setMessage("⚡ Developed by Rudra Wagh\n" +
                        "Battery-efficient transit automation.\n\n" +
                        "📖 How it works:\n" +
                        "1. Auto-Arm: Wakes up at your set scheduled time and monitors the 750m perimeter.\n" +
                        "2. Arrival Detection: When you enter campus gates, Google Play Services detects the boundary.\n" +
                        "3. Multi-SMS: Dispatches your arrival text to all saved numbers in the active slot.\n" +
                        "4. Self-Disarm: Automatically turns off GPS to preserve battery for the day.\n\n" +
                        "⚙️ Tap 'Configure Settings' to create preset slots, add extra numbers, or change destinations.")
                .setPositiveButton("Got It", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isArmed = prefs.getBoolean("is_armed", false);
        updateUIState(isArmed);

        // Load active preset slot title and scheduled time
        String slotName = prefs.getString("active_slot_name", "College");
        int hour = prefs.getInt("timer_hour", 8);
        int minute = prefs.getInt("timer_minute", 30);
        String amPm = (hour >= 12) ? "PM" : "AM";
        int displayHour = (hour > 12) ? hour - 12 : (hour == 0 ? 12 : hour);

        if (tvActiveSlotTitle != null) {
            tvActiveSlotTitle.setText(slotName);
        }
        tvTimerSummary.setText(String.format(Locale.getDefault(), "%02d:%02d %s DAILY", displayHour, minute, amPm));
    }

    private void updateUIState(boolean active) {
        isArmed = active;
        prefs.edit().putBoolean("is_armed", active).apply();

        if (active) {
            tvStatusBadge.setText("ACTIVE");
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981"))); // Emerald Green
            tvStatusText.setText("Armed & Monitoring");
            btnToggle.setText("DISARM MONITORING");
            btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#7A1520"))); // Deep Crimson
            showOngoingNotification();
        } else {
            tvStatusBadge.setText("OFFLINE");
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444"))); // Red
            tvStatusText.setText("Monitoring Inactive");
            btnToggle.setText("ARM MONITORING");
            btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3B82F6"))); // Blue
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
            List<String> list = new ArrayList<>();
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
            list.add(Manifest.permission.SEND_SMS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            ActivityCompat.requestPermissions(this, list.toArray(new String[0]), REQ_FOREGROUND);
        } else {
            checkBackgroundLocation();
        }
    }

    private void checkBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Select 'Allow all the time'", Toast.LENGTH_LONG).show();
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

    @SuppressLint("MissingPermission")
    private void armGeofence() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission missing", Toast.LENGTH_SHORT).show();
            return;
        }

        double lat = prefs.getFloat("target_lat", 18.5204f);
        double lng = prefs.getFloat("target_lng", 73.9782f);

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

        Intent intent = new Intent(this, GeofenceReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        geofencingClient.addGeofences(request, pendingIntent)
                .addOnSuccessListener(aVoid -> {
                    updateUIState(true);
                    Toast.makeText(MainActivity.this, "Armed successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
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
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Reached is Armed 📍")
                .setContentText("Monitoring configured boundary.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (manager != null) {
            manager.notify(ONGOING_NOTIF_ID, builder.build());
        }
    }

    private void removeOngoingNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(ONGOING_NOTIF_ID);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_FOREGROUND && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkBackgroundLocation();
        } else if (requestCode == REQ_BACKGROUND && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            armGeofence();
        }
    }
}