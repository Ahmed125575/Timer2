package com.stoptime.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_BT_PERMS = 500;
    private static final int REQ_ENABLE_BT = 501;
    private static final int REQ_DISCOVERABLE = 502;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private WebView webView;
    private BluetoothAdapter bluetoothAdapter;
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket socket;
    private PrintWriter writer;
    private Thread readThread;
    private String role = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        setupWebView();
        requestBluetoothPermissions();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDefaultTextEncodingName("utf-8");
        webView.setBackgroundColor(0xFF000000);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<String> perms = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (!perms.isEmpty()) requestPermissions(perms.toArray(new String[0]), REQ_BT_PERMS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_BT_PERMS);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private boolean ensureBluetoothEnabled() {
        if (bluetoothAdapter == null) {
            sendEvent("error", json("message", "This Android device does not support Bluetooth."));
            return false;
        }
        boolean enabled;
        try {
            enabled = bluetoothAdapter.isEnabled();
        } catch (SecurityException e) {
            sendEvent("error", json("message", "Bluetooth permission is not granted yet."));
            return false;
        }
        if (!enabled) {
            try {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQ_ENABLE_BT);
            } catch (Exception e) {
                sendEvent("error", json("message", "Please enable Bluetooth in system settings."));
            }
            return false;
        }
        return true;
    }

    private JSONObject json(String key, Object value) {
        JSONObject o = new JSONObject();
        try { o.put(key, value); } catch (JSONException ignored) {}
        return o;
    }

    private JSONObject json(String k1, Object v1, String k2, Object v2) {
        JSONObject o = json(k1, v1);
        try { o.put(k2, v2); } catch (JSONException ignored) {}
        return o;
    }

    @SuppressLint("MissingPermission")
    private String pairedDevicesJson() {
        JSONArray arr = new JSONArray();
        if (bluetoothAdapter == null) return arr.toString();
        try {
            Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice d : devices) {
                JSONObject item = new JSONObject();
                item.put("name", d.getName() == null ? "Unnamed device" : d.getName());
                item.put("address", d.getAddress());
                arr.put(item);
            }
        } catch (SecurityException | JSONException ignored) {}
        return arr.toString();
    }

    @SuppressLint("MissingPermission")
    private void startHost() {
        if (!ensureBluetoothEnabled()) return;
        closeConnection();
        role = "controller";
        ioExecutor.execute(() -> {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("StopTime", SPP_UUID);
                mainHandler.post(() -> sendEvent("host_started", null));
                BluetoothSocket client = serverSocket.accept();
                synchronized (MainActivity.this) {
                    socket = client;
                }
                closeServerSocket();
                setupSocketStreams(client);
                sendEvent("connected", json("role", role));
            } catch (IOException | SecurityException e) {
                sendEvent("error", json("message", "Could not start the Bluetooth host: " + safeMessage(e)));
                closeServerSocket();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void connectTo(String address) {
        if (!ensureBluetoothEnabled()) return;
        closeConnection();
        role = "display";
        ioExecutor.execute(() -> {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                BluetoothSocket candidate = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothAdapter.cancelDiscovery();
                candidate.connect();
                synchronized (MainActivity.this) {
                    socket = candidate;
                }
                setupSocketStreams(candidate);
                sendEvent("connected", json("role", role, "deviceName", device.getName()));
                sendTimeRequest();
            } catch (IOException | SecurityException e) {
                sendEvent("error", json("message", "Connection failed: " + safeMessage(e)));
                closeConnection();
            }
        });
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isEmpty()) ? e.getClass().getSimpleName() : m;
    }

    private void setupSocketStreams(BluetoothSocket newSocket) throws IOException {
        PrintWriter newWriter = new PrintWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        writer = newWriter;
        BufferedReader reader = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
        readThread = new Thread(() -> readLoop(reader), "StopTime-BT-Reader");
        readThread.start();
    }

    private void readLoop(BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) handleIncoming(line);
            }
        } catch (IOException ignored) {
            // Connection closed.
        } finally {
            sendEvent("disconnected", null);
            synchronized (this) {
                socket = null;
                writer = null;
            }
        }
    }

    private void handleIncoming(String line) {
        try {
            JSONObject obj = new JSONObject(line);
            String type = obj.optString("type", "");
            if ("TIME_REQ".equals(type)) {
                long t1 = obj.optLong("t1");
                long t2 = System.currentTimeMillis();
                long t3 = System.currentTimeMillis();
                sendRaw(json("type", "TIME_RES", "t1", t1, "t2", t2, "t3", t3).toString());
                return;
            }
            JSONObject payload = new JSONObject();
            payload.put("message", obj);
            payload.put("receivedAt", System.currentTimeMillis());
            sendEvent("bt_message", payload);
        } catch (JSONException ignored) {
            sendEvent("error", json("message", "Received malformed Bluetooth data."));
        }
    }

    private void sendTimeRequest() {
        long t1 = System.currentTimeMillis();
        sendRaw(json("type", "TIME_REQ", "t1", t1).toString());
    }

    private void sendRaw(String message) {
        PrintWriter w = writer;
        if (w != null) {
            ioExecutor.execute(() -> {
                try {
                    w.println(message);
                    w.flush();
                } catch (Exception e) {
                    sendEvent("error", json("message", "Bluetooth send failed: " + safeMessage(e)));
                }
            });
        }
    }

    private void sendEvent(String event, JSONObject payload) {
        if (webView == null) return;
        JSONObject wrapper = new JSONObject();
        try {
            wrapper.put("event", event);
            if (payload != null) wrapper.put("data", payload);
        } catch (JSONException ignored) {}
        String js = "window.__nativeEvent(" + JSONObject.quote(wrapper.toString()) + ")";
        mainHandler.post(() -> webView.evaluateJavascript(js, null));
    }

    @SuppressLint("MissingPermission")
    private void makeDiscoverable() {
        if (!ensureBluetoothEnabled()) return;
        try {
            Intent discoverable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(discoverable, REQ_DISCOVERABLE);
        } catch (Exception e) {
            sendEvent("error", json("message", "Could not open Bluetooth discoverability: " + safeMessage(e)));
        }
    }

    private void openBluetoothSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private synchronized void closeServerSocket() {
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
        }
    }

    private synchronized void closeConnection() {
        closeServerSocket();
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
            socket = null;
        }
        writer = null;
        role = "";
    }

    @Override
    protected void onDestroy() {
        closeConnection();
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void setRole(String selectedRole) {
            role = selectedRole == null ? "" : selectedRole;
        }

        @JavascriptInterface
        public boolean isBluetoothSupported() {
            return bluetoothAdapter != null;
        }

        @JavascriptInterface
        public boolean isBluetoothEnabled() {
            if (bluetoothAdapter == null) return false;
            try { return bluetoothAdapter.isEnabled(); }
            catch (SecurityException e) { return false; }
        }

        @JavascriptInterface
        public void requestEnableBluetooth() {
            ensureBluetoothEnabled();
        }

        @JavascriptInterface
        public void host() {
            startHost();
        }

        @JavascriptInterface
        public void connect(String address) {
            if (address != null && !address.isEmpty()) connectTo(address);
        }

        @JavascriptInterface
        public void send(String message) {
            if (message != null) sendRaw(message);
        }

        @JavascriptInterface
        public String getPairedDevices() {
            return pairedDevicesJson();
        }

        @JavascriptInterface
        public void makeDiscoverable() {
            MainActivity.this.makeDiscoverable();
        }

        @JavascriptInterface
        public void openBluetoothSettings() {
            MainActivity.this.openBluetoothSettings();
        }

        @JavascriptInterface
        public void disconnect() {
            closeConnection();
            sendEvent("disconnected", null);
        }
    }
}
