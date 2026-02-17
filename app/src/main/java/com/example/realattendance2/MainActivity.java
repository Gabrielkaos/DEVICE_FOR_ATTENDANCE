package com.example.realattendance2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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
import java.net.Inet4Address;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private String SERVER_URL;
    private String DEVICE_NAME;
    private static final String TOKEN = "ANDROID_SECRET";
    private TextView displayNameText, apiUrlText, ipAddressText, connectionStatus;

    TextView statusText;

    private ConfigServer server;

    private final Handler handler = new Handler();
    private final Runnable connectionChecker = new Runnable() {
        @Override
        public void run() {
            checkConnection();
            handler.postDelayed(this, 5000);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        loadConfig();

        statusText = findViewById(R.id.statusText);
        connectionStatus = findViewById(R.id.connectionStatusText);
        Button scanBtn = findViewById(R.id.scanBtn);
        displayNameText = findViewById(R.id.deviceName);
        apiUrlText = findViewById(R.id.apiUrl);
        ipAddressText = findViewById(R.id.ip_address);

        ipAddressText.setText(getDeviceIpAddress());

        if(!DEVICE_NAME.isEmpty()){
            displayNameText.setText(DEVICE_NAME);
        }
        if(!SERVER_URL.isEmpty()){
            apiUrlText.setText(SERVER_URL);
        }

        scanBtn.setOnClickListener(v -> startScanner());

        server = new ConfigServer(8080, DEVICE_NAME , (deviceName, apiEndpoint) -> {

            // Save config locally
            saveConfig(deviceName, apiEndpoint);

            server.setStoredDeviceName(deviceName);

            // Update UI (must be on UI thread)
            runOnUiThread(() -> displayNameText.setText(deviceName));
            runOnUiThread(() -> apiUrlText.setText(apiEndpoint));
            checkConnection();
        });


        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        checkConnection();
        handler.post(connectionChecker);


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up any resources here
        // For example:
        // - stop background threads
        // - unregister broadcast receivers
        // - close database connections
        // - stop sensors, etc.
        Log.d("MainActivity", "Activity is being destroyed");
        handler.removeCallbacks(connectionChecker);

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

        integrator.setOrientationLocked(true);

        // Force portrait by setting camera orientation hint (ZXing supports it)
        integrator.setCaptureActivity(PortraitCaptureActivity.class);
        integrator.initiateScan();
    }

    private String getDeviceIpAddress() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        if (cm == null) return "No network";

        Network network = cm.getActiveNetwork();
        if (network == null) return "No connection";

        LinkProperties linkProperties = cm.getLinkProperties(network);
        if (linkProperties == null) return "No IP";

        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            if (linkAddress.getAddress() instanceof Inet4Address) {
                return "Device IP:"+linkAddress.getAddress().getHostAddress();
            }
        }
        return "IP not found";
    }

    private void checkConnection() {

        if (SERVER_URL == null || SERVER_URL.isEmpty()) {
            connectionStatus.setText("Not Configured");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("device_config", MODE_PRIVATE);
        String deviceName = prefs.getString("device_name", "Unconfigured Device");

        new Thread(() -> {
            try {

                URL url = new URL(SERVER_URL.concat("ping/"));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                // Create JSON body
                JSONObject json = new JSONObject();
                json.put("device_name", deviceName);

                // Send JSON
                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();

                if (code >= 200 && code < 300) {

                    // Read response body
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream())
                    );

                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject responseJson = new JSONObject(response.toString());
                    String status = responseJson.optString("status");

                    runOnUiThread(() -> {
                        if ("valid".equals(status)) {
                            connectionStatus.setText("Connected");
                        } else {
                            connectionStatus.setText("Invalid Device");
                        }
                    });

                } else {
                    runOnUiThread(() ->
                            connectionStatus.setText("Server Error")
                    );
                }

            } catch (Exception e) {
                runOnUiThread(() ->
                        connectionStatus.setText("Disconnected")
                );
            }
        }).start();
    }


}