package com.example.pohachalo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
    private TextView tvStatusBadge, tvStatusText;
    private MaterialButton btnToggle;

    // Presets overview box items
    private TextView tvSlot1Name, tvSlot1Time, tvSlot1Badge;
    private LinearLayout itemSlot2, itemSlot3;
    private View divSlot2, divSlot3;
    private TextView tvSlot2Name, tvSlot2Time, tvSlot2Badge;
    private TextView tvSlot3Name, tvSlot3Time, tvSlot3Badge;

    private SharedPreferences prefs;
    private boolean isArmed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("PohachaloPrefs", MODE_PRIVATE);

        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvStatusText = findViewById(R.id.tvStatusText);
        btnToggle = findViewById(R.id.btnToggle);
        MaterialCardView cardStatusFloating = findViewById(R.id.cardStatusFloating);

        tvSlot1Name = findViewById(R.id.tvSlot1Name);
        tvSlot1Time = findViewById(R.id.tvSlot1Time);
        tvSlot1Badge = findViewById(R.id.tvSlot1Badge);

        itemSlot2 = findViewById(R.id.itemSlot2);
        divSlot2 = findViewById(R.id.divSlot2);
        tvSlot2Name = findViewById(R.id.tvSlot2Name);
        tvSlot2Time = findViewById(R.id.tvSlot2Time);
        tvSlot2Badge = findViewById(R.id.tvSlot2Badge);

        itemSlot3 = findViewById(R.id.itemSlot3);
        divSlot3 = findViewById(R.id.divSlot3);
        tvSlot3Name = findViewById(R.id.tvSlot3Name);
        tvSlot3Time = findViewById(R.id.tvSlot3Time);
        tvSlot3Badge = findViewById(R.id.tvSlot3Badge);

        MaterialButton btnOpenSettings = findViewById(R.id.btnOpenSettings);
        ImageButton btnAppGuide = findViewById(R.id.btnAppGuide);

        geofencingClient = LocationServices.getGeofencingClient(this);

        Animation floatAnimation = AnimationUtils.loadAnimation(this, R.anim.float_and_pulse);
        cardStatusFloating.startAnimation(floatAnimation);

        // Schedule all configured presets independently
        int visibleCount = prefs.getInt("visible_slots_count", 1);
        for (int i = 1; i <= visibleCount; i++) {
            String prefix = "slot_" + i + "_";
            if (prefs.getBoolean(prefix + "is_configured", false)) {
                int h = prefs.getInt(prefix + "hour", 12);
                int m = prefs.getInt(prefix + "minute", 0);
                SettingsActivity.scheduleSlotAlarm(this, i, h, m);
            }
        }

        requestInitialPermissions();
        checkExactAlarmPermission();
        checkBatteryOptimization();

        btnToggle.setOnClickListener(v -> {
            if (isArmed) {
                disarmActivePreset();
            } else {
                startArmingFlow();
            }
        });

        btnOpenSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        btnAppGuide.setOnClickListener(v -> showAppGuideDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPresetsBox();
    }

    private void refreshPresetsBox() {
        int activeSlot = prefs.getInt("active_slot_id", 1);
        int visibleCount = prefs.getInt("visible_slots_count", 1);

        // Slot 1
        bindSlotRow(1, tvSlot1Name, tvSlot1Time, tvSlot1Badge, activeSlot);

        // Slot 2
        if (visibleCount >= 2) {
            itemSlot2.setVisibility(View.VISIBLE);
            divSlot2.setVisibility(View.VISIBLE);
            bindSlotRow(2, tvSlot2Name, tvSlot2Time, tvSlot2Badge, activeSlot);
        } else {
            itemSlot2.setVisibility(View.GONE);
            divSlot2.setVisibility(View.GONE);
        }

        // Slot 3
        if (visibleCount >= 3) {
            itemSlot3.setVisibility(View.VISIBLE);
            divSlot3.setVisibility(View.VISIBLE);
            bindSlotRow(3, tvSlot3Name, tvSlot3Time, tvSlot3Badge, activeSlot);
        } else {
            itemSlot3.setVisibility(View.GONE);
            divSlot3.setVisibility(View.GONE);
        }

        isArmed = prefs.getBoolean("slot_" + activeSlot + "_is_armed", false);
        updateUIState(isArmed, activeSlot);
    }

    private void bindSlotRow(int slotId, TextView tvName, TextView tvTime, TextView tvBadge, int currentActiveSlot) {
        String prefix = "slot_" + slotId + "_";
        String label = prefs.getString(prefix + "label", "Preset " + slotId);
        int hour = prefs.getInt(prefix + "hour", 12);
        int minute = prefs.getInt(prefix + "minute", 0);
        boolean isConfigured = prefs.getBoolean(prefix + "is_configured", false);

        String amPm = (hour >= 12) ? "PM" : "AM";
        int displayHour = (hour > 12) ? hour - 12 : (hour == 0 ? 12 : hour);

        tvName.setText(label);
        tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d %s DAILY", displayHour, minute, amPm));

        if (slotId == currentActiveSlot) {
            tvBadge.setText("PRIMARY");
            tvBadge.setTextColor(Color.parseColor("#2EE59D"));
            tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#064E3B")));
        } else if (isConfigured) {
            tvBadge.setText("READY");
            tvBadge.setTextColor(Color.parseColor("#94A3B8"));
            tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#16222F")));
        } else {
            tvBadge.setText("EMPTY");
            tvBadge.setTextColor(Color.parseColor("#64748B"));
            tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0F172A")));
        }
    }

    private void updateUIState(boolean active, int activeSlot) {
        isArmed = active;
        prefs.edit().putBoolean("slot_" + activeSlot + "_is_armed", active).apply();

        String slotName = prefs.getString("slot_" + activeSlot + "_label", "Preset " + activeSlot);

        if (active) {
            tvStatusBadge.setText("ACTIVE");
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
            tvStatusText.setText("Armed: " + slotName);
            btnToggle.setText("DISARM MONITORING");
            btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#7A1520")));
            showOngoingNotification(slotName);
        } else {
            tvStatusBadge.setText("OFFLINE");
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
            tvStatusText.setText("Monitoring Inactive");
            btnToggle.setText("ARM MONITORING");
            btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3B82F6")));
            removeOngoingNotification();
        }
    }

    private void startArmingFlow() {
        int activeSlot = prefs.getInt("active_slot_id", 1);
        String prefix = "slot_" + activeSlot + "_";
        float lat = prefs.getFloat(prefix + "lat", Float.NaN);
        float lng = prefs.getFloat(prefix + "lng", Float.NaN);

        if (Float.isNaN(lat) || Float.isNaN(lng)) {
            new AlertDialog.Builder(this)
                    .setTitle("Preset Incomplete")
                    .setMessage("Active Preset coordinates are missing. Please go to Settings and set them first.")
                    .setPositiveButton("Go to Settings", (d, w) -> startActivity(new Intent(this, SettingsActivity.class)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        boolean fineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean sms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;

        if (!fineLoc || !sms) {
            requestInitialPermissions();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle("Background Location Required")
                        .setMessage("Geofencing requires location access in the background. Tap 'Allow all the time' in the next screen.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            ActivityCompat.requestPermissions(
                                    this,
                                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                    REQ_BACKGROUND
                            );
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }

        armActivePreset(activeSlot);
    }

    @SuppressLint("MissingPermission")
    private void armActivePreset(int activeSlot) {
        String prefix = "slot_" + activeSlot + "_";
        float lat = prefs.getFloat(prefix + "lat", Float.NaN);
        float lng = prefs.getFloat(prefix + "lng", Float.NaN);
        String slotLabel = prefs.getString(prefix + "label", "Preset " + activeSlot);

        String p1 = prefs.getString(prefix + "phone1", "");
        String p2 = prefs.getString(prefix + "phone2", "");
        String p3 = prefs.getString(prefix + "phone3", "");
        String msg = prefs.getString(prefix + "msg", "Reached safely.");
        String combinedPhones = p1 + (p2.isEmpty() ? "" : "," + p2) + (p3.isEmpty() ? "" : "," + p3);

        String geofenceRequestId = "GEOFENCE_SLOT_" + activeSlot;

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

        Intent intent = new Intent(this, GeofenceReceiver.class);
        intent.putExtra("slot_id", activeSlot);
        intent.putExtra("target_phones", combinedPhones);
        intent.putExtra("target_message", msg);
        intent.putExtra("slot_label", slotLabel);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 3000 + activeSlot, intent, flags);

        geofencingClient.addGeofences(request, pendingIntent)
                .addOnSuccessListener(aVoid -> {
                    prefs.edit().putLong("last_sms_sent_timestamp", 0).apply();
                    updateUIState(true, activeSlot);
                    Toast.makeText(MainActivity.this, "Armed: " + slotLabel, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Geofence Error")
                            .setMessage("Failed to arm: " + e.getMessage() + "\n\nEnsure Location (GPS) is turned ON on your phone.")
                            .setPositiveButton("OK", null)
                            .show();
                });
    }

    private void disarmActivePreset() {
        int activeSlot = prefs.getInt("active_slot_id", 1);
        Intent intent = new Intent(this, GeofenceReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 3000 + activeSlot, intent, flags);
        geofencingClient.removeGeofences(pendingIntent)
                .addOnSuccessListener(aVoid -> {
                    updateUIState(false, activeSlot);
                    Toast.makeText(this, "Disarmed successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    updateUIState(false, activeSlot);
                    Toast.makeText(this, "Disarmed", Toast.LENGTH_SHORT).show();
                });
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                        .setTitle("Reliable Auto-Arm Setup")
                        .setMessage("To ensure your daily auto-arm triggers accurately when your phone is locked or asleep, please allow Reached to run unrestricted in the background.")
                        .setPositiveButton("Configure", (dialog, which) -> {
                            try {
                                @SuppressLint("BatteryLife")
                                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        }
    }

    private void showAppGuideDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_app_guide, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        MaterialButton btnGithub = dialogView.findViewById(R.id.btnDialogGithub);
        MaterialButton btnLinkedin = dialogView.findViewById(R.id.btnDialogLinkedin);
        MaterialButton btnDismiss = dialogView.findViewById(R.id.btnDialogDismiss);

        btnGithub.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Rudra1725"));
            startActivity(intent);
        });

        btnLinkedin.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/rudra-wagh"));
            startActivity(intent);
        });

        btnDismiss.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                }
            }
        }
    }

    private void requestInitialPermissions() {
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
        }
    }

    private void showOngoingNotification(String slotName) {
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
                .setContentTitle("Reached 📍 is Armed (" + slotName + ")")
                .setContentText("Monitoring configured destination boundary.")
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
    }
}