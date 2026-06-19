package com.zhixin.esp32helper.ui.connect;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zhixin.esp32helper.R;
import com.zhixin.esp32helper.usb.UsbCommunication;

import java.util.HashMap;

public class ConnectActivity extends AppCompatActivity {
    private static final String TAG = "ConnectActivity";
    private static final int VENDOR_ID_ESPRESSIF_MICROPYTHON = 0x303A;
    private static final int VENDOR_ID_ST = 0x0483;
    private static final int VENDOR_ID_SILICON = 0x10C4;
    private static final int VENDOR_ID_FTDI = 0x0403;

    private TextView tvDeviceInfo;
    private Button btnRefresh;
    private Button btnStartConnection;
    private Button btnDisconnect;
    private Button btnBack;
    private ProgressBar progressBar;
    private TextView tvLog;

    private static final String ACTION_USB_PERMISSION = "com.zhixin.esp32helper.USB_PERMISSION";

    private UsbManager usbManager;
    private UsbDevice selectedDevice;
    private UsbCommunication usbCommunication;
    private Handler handler;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            appendLog("USB权限已获取");
                            openDeviceConnection();
                        }
                    } else {
                        appendLog("USB权限被拒绝");
                        btnStartConnection.setEnabled(true);
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                appendLog("USB设备已连接");
                refreshDeviceList();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                appendLog("USB设备已断开");
                selectedDevice = null;
                updateDeviceInfo();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnStartConnection = findViewById(R.id.btnStartConnection);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnBack = findViewById(R.id.btnBack);
        progressBar = findViewById(R.id.progressBar);
        tvLog = findViewById(R.id.tvLog);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbCommunication = new UsbCommunication();
        handler = new Handler(Looper.getMainLooper());

        btnRefresh.setOnClickListener(v -> refreshDeviceList());
        btnStartConnection.setOnClickListener(v -> connectToDevice());
        btnDisconnect.setOnClickListener(v -> disconnectDevice());
        btnBack.setOnClickListener(v -> finish());

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        refreshDeviceList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        if (usbCommunication != null) {
            usbCommunication.closeDevice();
        }
    }

    private void refreshDeviceList() {
        if (usbManager == null) {
            appendLog("USB服务不可用");
            return;
        }

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            tvDeviceInfo.setText(R.string.no_device);
            selectedDevice = null;
            btnStartConnection.setEnabled(false);
        } else {
            StringBuilder info = new StringBuilder();
            for (UsbDevice device : deviceList.values()) {
                int vendorId = device.getVendorId();
                int productId = device.getProductId();
                String deviceName = device.getDeviceName();

                info.append("设备: ").append(deviceName).append("\n");
                info.append("Vendor ID: ").append(String.format("0x%04X", vendorId)).append("\n");
                info.append("Product ID: ").append(String.format("0x%04X", productId)).append("\n");

                if (vendorId == VENDOR_ID_ESPRESSIF_MICROPYTHON || vendorId == VENDOR_ID_ST || vendorId == VENDOR_ID_SILICON || vendorId == VENDOR_ID_FTDI) {
                    selectedDevice = device;
                    info.append("状态: 已找到ESP32设备\n\n");
                    btnStartConnection.setEnabled(true);
                } else {
                    info.append("状态: 未知设备\n\n");
                }
            }
            if (selectedDevice == null) {
                btnStartConnection.setEnabled(false);
            }
            tvDeviceInfo.setText(info.toString());
        }
        updateButtons();
    }

    private void connectToDevice() {
        if (selectedDevice == null) {
            appendLog("没有选择设备");
            return;
        }

        if (usbManager.hasPermission(selectedDevice)) {
            openDeviceConnection();
        } else {
            appendLog("请求USB权限...");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(selectedDevice, pendingIntent);
        }
    }

    private void openDeviceConnection() {
        progressBar.setVisibility(View.VISIBLE);
        btnStartConnection.setEnabled(false);
        appendLog("正在连接...");

        new Thread(() -> {
            boolean success = usbCommunication.openDevice(usbManager, selectedDevice);
            handler.post(() -> {
                progressBar.setVisibility(View.GONE);
                if (success) {
                    appendLog("连接成功！");
                    btnStartConnection.setVisibility(View.GONE);
                    btnDisconnect.setVisibility(View.VISIBLE);
                } else {
                    appendLog("连接失败");
                    btnStartConnection.setEnabled(true);
                }
                updateButtons();
            });
        }).start();
    }

    private void disconnectDevice() {
        usbCommunication.closeDevice();
        appendLog("已断开连接");
        btnDisconnect.setVisibility(View.GONE);
        btnStartConnection.setVisibility(View.VISIBLE);
        updateButtons();
        refreshDeviceList();
    }

    private void updateButtons() {
        boolean isConnected = usbCommunication.isConnected();
        btnStartConnection.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        btnDisconnect.setVisibility(isConnected ? View.VISIBLE : View.GONE);
        btnStartConnection.setEnabled(selectedDevice != null);
    }

    private void updateDeviceInfo() {
        if (selectedDevice != null) {
            int vendorId = selectedDevice.getVendorId();
            int productId = selectedDevice.getProductId();
            String info = "设备: " + selectedDevice.getDeviceName() + "\n" +
                         "Vendor ID: " + String.format("0x%04X", vendorId) + "\n" +
                         "Product ID: " + String.format("0x%04X", productId);
            tvDeviceInfo.setText(info);
        } else {
            tvDeviceInfo.setText(R.string.no_device);
        }
    }

    private void appendLog(String message) {
        String current = tvLog.getText().toString();
        String timestamp = java.text.SimpleDateFormat.getTimeInstance().format(new java.util.Date());
        String newLog = current + "[" + timestamp + "] " + message + "\n";
        tvLog.setText(newLog);
    }
}
