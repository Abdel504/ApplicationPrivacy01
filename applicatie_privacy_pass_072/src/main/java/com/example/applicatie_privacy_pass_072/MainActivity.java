package com.example.applicatie_privacy_pass_072;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // SensorManager regelt toegang tot alle sensoren in het systeem
    private SensorManager sm;
    // Twee sensortypen voor stappenteller-functionaliteit
    private Sensor stepCounter, stepDetector;
    // Tekstveld en knop in de UI
    private TextView tv;
    private Button btnStart;

    // Waarde bij opstart (baseline) voor TYPE_STEP_COUNTER
    private Float baseline = null;
    // Houdt bij of er momenteel naar sensor-events geluisterd wordt
    private boolean listening = false;

    // Launcher om permissies aan te vragen via het Android-permissiesysteem
    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), res -> {
                // Controleer of beide permissies verleend zijn
                boolean arOk = !requiresAR() || Boolean.TRUE.equals(res.get(Manifest.permission.ACTIVITY_RECOGNITION));
                if (arOk) startListening();
                // Geen pop-up of melding bij weigering, blijft stil
            });

    // Controleert of het apparaat Android 10 (API 29) of hoger gebruikt.
    private static boolean requiresAR() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q; // vanaf Android 10 verplicht
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI-elementen koppelen
        tv = findViewById(R.id.tvSensor);
        btnStart = findViewById(R.id.btn_start);

        // SensorManager initialiseren
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) {
            // Sensoren opvragen: stap-teller en stap-detector
            stepCounter  = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            stepDetector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        }

        // Startknop koppelen aan permissie-check en sensor-start
        btnStart.setOnClickListener(v -> requestPermsThenStart());
    }

    /**
     * Controleert of de permissies verleend zijn, en vraagt ze indien nodig aan.
     */
    private void requestPermsThenStart() {
        boolean arOk = !requiresAR() || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;

        if (arOk) {
            // Beide permissies zijn aanwezig: start de sensor
            startListening();
        } else {
            // Vraag permissies aan (beide vanaf Android 10, anders alleen BODY_SENSORS)
            if (requiresAR()) {
                permLauncher.launch(new String[]{
                        Manifest.permission.ACTIVITY_RECOGNITION
                });
            } else {
                // Pre-Android 10: geen runtime permissie nodig
                startListening();
            }
        }
    }

    /**
     * Registreert de listener zodat sensor-events ontvangen worden.
     */
    private void startListening() {
        if (sm == null) return;
        if (listening) return;

        baseline = null;
        boolean registered = false;

        // Registreer beide sensoren (indien aanwezig)
        if (stepCounter != null) {
            registered |= sm.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (stepDetector != null) {
            registered |= sm.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL);
        }

        listening = registered;

        // Toon melding als geen sensor beschikbaar is
        if (!registered) {
            tv.setText("Geen geschikte sensor gevonden.");
        } else {
            tv.setText(""); // Leeg laten tot eerste event
        }
    }

    /**
     * Stopt met luisteren naar sensor-events.
     */
    private void stopListening() {
        if (sm != null && listening) {
            sm.unregisterListener(this);
            listening = false;
        }
    }

    /**
     * Wordt aangeroepen bij iedere sensor-update (stap gedetecteerd).
     */
    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            float totalSinceBoot = e.values[0];
            if (baseline == null) baseline = totalSinceBoot;
            int delta = Math.max(0, Math.round(totalSinceBoot - baseline));
            tv.setText("Stappen: " + delta);
        } else if (e.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            tv.setText("Stap gedetecteerd ✔︎");
        }
    }

    // Niet gebruikt, maar verplicht door SensorEventListener-interface
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { /* n.v.t. */ }

    // Stop de sensor bij het pauzeren van de app
    @Override protected void onPause() {
        super.onPause();
        stopListening();
    }
}
