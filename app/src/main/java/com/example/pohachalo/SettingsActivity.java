package com.example.pohachalo;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Calendar;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText etSlotLabel, etPhone1, etPhone2, etPhone3, etTargetMessage, etTargetLat, etTargetLng;
    private TextInputLayout tilPhone2, tilPhone3;
    private TextView tvSelectedTime;
    private MaterialButton btnSlot1, btnSlot2, btnSlot3, btnAddSlot, btnAddPhoneField;
    private ImageButton btnDeleteSlot;
    private SharedPreferences prefs;

    private int activeSlot = 1;
    private int visibleSlotsCount = 1;
    private int scheduledHour = 12;
    private int scheduledMinute = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("PohachaloPrefs", MODE_PRIVATE);

        etSlotLabel = findViewById(R.id.etSlotLabel);
        etPhone1 = findViewById(R.id.etPhone1);
        etPhone2 = findViewById(R.id.etPhone2);
        etPhone3 = findViewById(R.id.etPhone3);
        tilPhone2 = findViewById(R.id.tilPhone2);
        tilPhone3 = findViewById(R.id.tilPhone3);
        etTargetMessage = findViewById(R.id.etTargetMessage);
        etTargetLat = findViewById(R.id.etTargetLat);
        etTargetLng = findViewById(R.id.etTargetLng);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);

        btnSlot1 = findViewById(R.id.btnSlot1);
        btnSlot2 = findViewById(R.id.btnSlot2);
        btnSlot3 = findViewById(R.id.btnSlot3);
        btnAddSlot = findViewById(R.id.btnAddSlot);
        btnAddPhoneField = findViewById(R.id.btnAddPhoneField);
        btnDeleteSlot = findViewById(R.id.btnDeleteSlot);
        MaterialButton btnPickTime = findViewById(R.id.btnPickTime);
        MaterialButton btnPickOnMap = findViewById(R.id.btnPickOnMap);
        MaterialButton btnSaveSettings = findViewById(R.id.btnSaveSettings);

        visibleSlotsCount = prefs.getInt("visible_slots_count", 1);
        activeSlot = prefs.getInt("active_slot_id", 1);
        if (activeSlot > visibleSlotsCount) {
            visibleSlotsCount = activeSlot;
        }

        refreshSlotsVisibility();
        loadSlotData(activeSlot);
        updateSlotButtonsUI();

        btnSlot1.setOnClickListener(v -> switchSlot(1));
        btnSlot2.setOnClickListener(v -> switchSlot(2));
        btnSlot3.setOnClickListener(v -> switchSlot(3));

        btnAddSlot.setOnClickListener(v -> {
            if (visibleSlotsCount == 1) {
                visibleSlotsCount = 2;
                switchSlot(2);
            } else if (visibleSlotsCount == 2) {
                visibleSlotsCount = 3;
                switchSlot(3);
            }
            prefs.edit().putInt("visible_slots_count", visibleSlotsCount).apply();
            refreshSlotsVisibility();
        });

        btnDeleteSlot.setOnClickListener(v -> confirmDeleteSlot());

        btnAddPhoneField.setOnClickListener(v -> {
            if (tilPhone2.getVisibility() == View.GONE) {
                tilPhone2.setVisibility(View.VISIBLE);
            } else if (tilPhone3.getVisibility() == View.GONE) {
                tilPhone3.setVisibility(View.VISIBLE);
                btnAddPhoneField.setVisibility(View.GONE);
            }
        });

        btnPickTime.setOnClickListener(v -> {
            TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                scheduledHour = hourOfDay;
                scheduledMinute = minute;
                updateTimerLabel(hourOfDay, minute);
            }, scheduledHour, scheduledMinute, false);
            dialog.show();
        });

        btnPickOnMap.setOnClickListener(v -> {
            String currentLat = etTargetLat.getText().toString().trim();
            String currentLng = etTargetLng.getText().toString().trim();
            String query = (!currentLat.isEmpty() && !currentLng.isEmpty()) ? currentLat + "," + currentLng : "0,0";

            String uri = "geo:" + query + "?q=" + query + "(Destination)";
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            mapIntent.setPackage("com.google.android.apps.maps");

            try {
                startActivity(mapIntent);
                Toast.makeText(this, "Drop a pin, copy coordinates, and paste here", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Intent webMapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + query));
                startActivity(webMapIntent);
            }
        });

        btnSaveSettings.setOnClickListener(v -> saveCurrentSlot());
    }

    private void confirmDeleteSlot() {
        if (activeSlot == 1) {
            new AlertDialog.Builder(this)
                    .setTitle("Reset Slot 1?")
                    .setMessage("Slot 1 is your primary preset and cannot be deleted, but all its fields will be cleared back to empty defaults.")
                    .setPositiveButton("Reset", (dialog, which) -> resetSlotData(1))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Slot " + activeSlot + "?")
                .setMessage("Are you sure you want to permanently remove this preset?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    cancelSlotAlarm(this, activeSlot);
                    clearSlotData(activeSlot);
                    if (visibleSlotsCount > 1) {
                        visibleSlotsCount--;
                    }
                    prefs.edit().putInt("visible_slots_count", visibleSlotsCount).apply();
                    refreshSlotsVisibility();
                    switchSlot(1);
                    Toast.makeText(this, "Slot deleted successfully", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearSlotData(int slot) {
        String prefix = "slot_" + slot + "_";
        prefs.edit()
                .remove(prefix + "label")
                .remove(prefix + "hour")
                .remove(prefix + "minute")
                .remove(prefix + "lat")
                .remove(prefix + "lng")
                .remove(prefix + "phone1")
                .remove(prefix + "phone2")
                .remove(prefix + "phone3")
                .remove(prefix + "msg")
                .remove(prefix + "is_configured")
                .apply();
    }

    private void resetSlotData(int slot) {
        cancelSlotAlarm(this, slot);
        clearSlotData(slot);
        loadSlotData(slot);
        Toast.makeText(this, "Slot " + slot + " reset to defaults", Toast.LENGTH_SHORT).show();
    }

    private void refreshSlotsVisibility() {
        btnSlot2.setVisibility(visibleSlotsCount >= 2 ? View.VISIBLE : View.GONE);
        btnSlot3.setVisibility(visibleSlotsCount >= 3 ? View.VISIBLE : View.GONE);
        btnAddSlot.setVisibility(visibleSlotsCount >= 3 ? View.GONE : View.VISIBLE);
    }

    private void switchSlot(int newSlot) {
        activeSlot = newSlot;
        loadSlotData(activeSlot);
        updateSlotButtonsUI();
    }

    private void updateSlotButtonsUI() {
        btnSlot1.setBackgroundTintList(ColorStateList.valueOf(activeSlot == 1 ? Color.parseColor("#2EE59D") : Color.parseColor("#16222F")));
        btnSlot1.setTextColor(activeSlot == 1 ? Color.parseColor("#0A0E17") : Color.parseColor("#94A3B8"));

        btnSlot2.setBackgroundTintList(ColorStateList.valueOf(activeSlot == 2 ? Color.parseColor("#2EE59D") : Color.parseColor("#16222F")));
        btnSlot2.setTextColor(activeSlot == 2 ? Color.parseColor("#0A0E17") : Color.parseColor("#94A3B8"));

        btnSlot3.setBackgroundTintList(ColorStateList.valueOf(activeSlot == 3 ? Color.parseColor("#2EE59D") : Color.parseColor("#16222F")));
        btnSlot3.setTextColor(activeSlot == 3 ? Color.parseColor("#0A0E17") : Color.parseColor("#94A3B8"));
    }

    private void loadSlotData(int slot) {
        String prefix = "slot_" + slot + "_";
        etSlotLabel.setText(prefs.getString(prefix + "label", ""));
        scheduledHour = prefs.getInt(prefix + "hour", 12);
        scheduledMinute = prefs.getInt(prefix + "minute", 0);
        updateTimerLabel(scheduledHour, scheduledMinute);

        float lat = prefs.getFloat(prefix + "lat", Float.NaN);
        float lng = prefs.getFloat(prefix + "lng", Float.NaN);
        etTargetLat.setText(Float.isNaN(lat) ? "" : String.valueOf(lat));
        etTargetLng.setText(Float.isNaN(lng) ? "" : String.valueOf(lng));

        etPhone1.setText(prefs.getString(prefix + "phone1", ""));

        String p2 = prefs.getString(prefix + "phone2", "");
        etPhone2.setText(p2);
        tilPhone2.setVisibility(p2.isEmpty() ? View.GONE : View.VISIBLE);

        String p3 = prefs.getString(prefix + "phone3", "");
        etPhone3.setText(p3);
        tilPhone3.setVisibility(p3.isEmpty() ? View.GONE : View.VISIBLE);

        btnAddPhoneField.setVisibility((!p2.isEmpty() && !p3.isEmpty()) ? View.GONE : View.VISIBLE);
        etTargetMessage.setText(prefs.getString(prefix + "msg", ""));
    }

    private void updateTimerLabel(int hour, int minute) {
        String amPm = (hour >= 12) ? "PM" : "AM";
        int displayHour = (hour > 12) ? hour - 12 : (hour == 0 ? 12 : hour);
        tvSelectedTime.setText(String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, amPm));
    }

    private void saveCurrentSlot() {
        String label = etSlotLabel.getText().toString().trim();
        String latStr = etTargetLat.getText().toString().trim();
        String lngStr = etTargetLng.getText().toString().trim();
        String p1 = etPhone1.getText().toString().trim();
        String p2 = (tilPhone2.getVisibility() == View.VISIBLE) ? etPhone2.getText().toString().trim() : "";
        String p3 = (tilPhone3.getVisibility() == View.VISIBLE) ? etPhone3.getText().toString().trim() : "";
        String msg = etTargetMessage.getText().toString().trim();

        if (p1.isEmpty() || msg.isEmpty() || latStr.isEmpty() || lngStr.isEmpty()) {
            Toast.makeText(this, "Please enter phone number, message, and coordinates", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            float lat = Float.parseFloat(latStr);
            float lng = Float.parseFloat(lngStr);
            String prefix = "slot_" + activeSlot + "_";
            String finalLabel = label.isEmpty() ? "Preset " + activeSlot : label;

            prefs.edit()
                    .putInt("active_slot_id", activeSlot)
                    .putString("active_slot_name", finalLabel)
                    .putInt("visible_slots_count", visibleSlotsCount)
                    .putString(prefix + "label", finalLabel)
                    .putInt(prefix + "hour", scheduledHour)
                    .putInt(prefix + "minute", scheduledMinute)
                    .putFloat(prefix + "lat", lat)
                    .putFloat(prefix + "lng", lng)
                    .putString(prefix + "phone1", p1)
                    .putString(prefix + "phone2", p2)
                    .putString(prefix + "phone3", p3)
                    .putString(prefix + "msg", msg)
                    .putBoolean(prefix + "is_configured", true)
                    .putLong("last_sms_sent_timestamp", 0)
                    .apply();

            // Schedules this specific slot with its own unique request code
            scheduleSlotAlarm(this, activeSlot, scheduledHour, scheduledMinute);

            Toast.makeText(this, "\"" + finalLabel + "\" Activated & Saved", Toast.LENGTH_SHORT).show();
            finish();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Coordinates must be valid numbers", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    public static void scheduleSlotAlarm(Context context, int slotId, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, AlarmArmReceiver.class);
        intent.putExtra("slot_id", slotId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        // Unique request code per slot (Slot 1 -> 1001, Slot 2 -> 1002, Slot 3 -> 1003)
        int requestCode = 1000 + slotId;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags);

        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.getTimeInMillis() <= now.getTimeInMillis()) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }

        Intent showIntent = new Intent(context, MainActivity.class);
        PendingIntent showPendingIntent = PendingIntent.getActivity(context, 2000 + slotId, showIntent, flags);

        AlarmManager.AlarmClockInfo alarmClockInfo =
                new AlarmManager.AlarmClockInfo(target.getTimeInMillis(), showPendingIntent);

        try {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pendingIntent);
            }
        }
    }

    public static void cancelSlotAlarm(Context context, int slotId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, AlarmArmReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1000 + slotId, intent, flags);
        alarmManager.cancel(pendingIntent);
    }
}