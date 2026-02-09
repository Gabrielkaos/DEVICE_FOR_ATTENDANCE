package com.example.realattendance2;

import static android.content.Context.MODE_PRIVATE;

import android.content.SharedPreferences;

import fi.iki.elonen.NanoHTTPD;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ConfigServer extends NanoHTTPD {

    private final ConfigListener listener;

    public interface ConfigListener {
        void onConfigReceived(String deviceName, String apiEndpoint);
    }

    public ConfigServer(int port, ConfigListener listener) {
        super(port);
        this.listener = listener;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (Method.POST.equals(session.getMethod()) && "/ping".equals(session.getUri())) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String body = files.get("postData");

                if (body == null) {
                    return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            "{\"error\":\"empty body\"}"
                    );
                }

                JSONObject json = new JSONObject(body);
                String deviceName2 = json.getString("device_name");

                return newFixedLengthResponse(
                        Response.Status.OK,
                        "text/plain",
                        "pong"
                );


            } catch (Exception e) {
                return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        "application/json",
                        "{\"error\":\"invalid JSON\"}"
                );
            }
        }
        else if (Method.POST.equals(session.getMethod())
                && "/config".equals(session.getUri())) {

            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);

                String body = files.get("postData");
                if (body == null) {
                    return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            "{\"error\":\"empty body\"}"
                    );
                }

                JSONObject json = new JSONObject(body);

                String deviceName = json.getString("device_name");
                String apiEndpoint = json.getString("apiEndpointUrl");

                listener.onConfigReceived(deviceName, apiEndpoint);

                return newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        "{\"status\":\"configured\"}"
                );

            } catch (Exception e) {
                return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        "application/json",
                        "{\"error\":\"invalid data\"}"
                );
            }
        }

        return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                "{\"error\":\"not found\"}"
        );
    }
}
