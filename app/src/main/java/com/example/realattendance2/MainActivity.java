package com.example.realattendance2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private String SERVER_URL;
    private String DEVICE_NAME;
    private static final String TOKEN = "ANDROID_SECRET";

    TextView statusText;

    private ConfigServer server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        loadConfig();

        statusText = findViewById(R.id.statusText);
        Button scanBtn = findViewById(R.id.scanBtn);

        scanBtn.setOnClickListener(v -> startScanner());

        server = new ConfigServer(8080, (deviceName, apiEndpoint) -> {

            // Save config locally
            saveConfig(deviceName, apiEndpoint);

            // Update UI (must be on UI thread)
//            runOnUiThread(() -> deviceNameText.setText(deviceName));
        });

        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            sendAttendance(result.getContents());
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void saveConfig(String deviceName, String apiEndpoint) {
        SharedPreferences prefs =
                getSharedPreferences("device_config", MODE_PRIVATE);

        prefs.edit()
                .putString("device_name", deviceName)
                .putString("api_endpoint", apiEndpoint)
                .apply();
    }

    private void loadConfig() {
        SharedPreferences prefs =
                getSharedPreferences("device_config", MODE_PRIVATE);

        DEVICE_NAME = prefs.getString("device_name", "Unconfigured Device");
        SERVER_URL  = prefs.getString("api_endpoint", null);
    }


    private void sendAttendance(String studentId) {
        statusText.setText("Sending...");

        new Thread(() -> {
            try {
                Log.d("SERVER_URL",SERVER_URL);
                Log.d("DEVICE_NAME",DEVICE_NAME);
                URL url = new URL(SERVER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("barcode", studentId);
                json.put("device", DEVICE_NAME);
                json.put("token", TOKEN);
                json.put("event", "");

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                InputStream is = code >= 200 && code < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }

                JSONObject res = new JSONObject(response.toString());
                String message = res.getString("message");

                runOnUiThread(() ->
                        statusText.setText(message)
                );

            } catch (Exception e) {
                runOnUiThread(() ->
                        statusText.setText("Network Error")
                );
            }
        }).start();
    }

    private void startScanner() {
        loadConfig();
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan Student ID");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false); // IMPORTANT
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }
}