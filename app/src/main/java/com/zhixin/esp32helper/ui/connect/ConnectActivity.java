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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zhixin.esp32helper.R;
import com.zhixin.esp32helper.usb.UsbCommunication;

import java.util.HashMap;

public class ConnectActivity extends AppCompatActivity {
    private static final String TAG = "ConnectActivity";
    private static final String ACTION_USB_PERMISSION = "com.zhixin.esp32helper.USB_PERMISSION";

    private static final int VID_ESPRESSIF = 0x303A;
    private static final int VID_ST = 0x0483;
    private static final int VID_SILICON = 0x10C4;
    private static final int VID_FTDI = 0x0403;
    private static final int VID_CH340 = 0x1A86;

    private TextView tvDeviceInfo;
    private Button btnRefresh;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnBack;
    private ProgressBar progressBar;
    private TextView tvLog;

    private UsbManager usbManager;
    private UsbDevice selectedDevice;
    private UsbCommunication usbComm;
    private Handler handler;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        appendLog("权限已获取");
                        openDevice();
                    }
                } else {
                    appendLog("权限被拒绝");
                    btnConnect.setEnabled(true);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnConnect = findViewById(R.id.btnStartConnection);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnBack = findViewById(R.id.btnBack);
        progressBar = findViewById(R.id.progressBar);
        tvLog = findViewById(R.id.tvLog);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbComm = new UsbCommunication();
        handler = new Handler(Looper.getMainLooper());

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);

        btnRefresh.setOnClickListener(v -> scanDevices());
        btnConnect.setOnClickListener(v -> requestPermission());
        btnDisconnect.setOnClickListener(v -> disconnect());
        btnBack.setOnClickListener(v -> finish());

        scanDevices();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        usbComm.closeDevice();
    }

    private void scanDevices() {
        try {
            tvDeviceInfo.setText("正在扫描设备...");
            HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
            StringBuilder sb = new StringBuilder();
            selectedDevice = null;

            if (devices.isEmpty()) {
                sb.append("未检测到USB设备");
            } else {
                sb.append("发现 ").append(devices.size()).append(" 个设备:\n\n");
                for (UsbDevice dev : devices.values()) {
                    int vid = dev.getVendorId();
                    int pid = dev.getProductId();
                    sb.append("设备: ").append(dev.getDeviceName()).append("\n");
                    sb.append("VID: ").append(String.format("0x%04X", vid)).append("\n");
                    sb.append("PID: ").append(String.format("0x%04X", pid)).append("\n");

                    if (isSupportedDevice(vid)) {
                        sb.append("✓ 支持的设备\n\n");
                        selectedDevice = dev;
                    } else {
                        sb.append("✗ 未知设备\n\n");
                    }
                }
            }

            tvDeviceInfo.setText(sb.toString());
            btnConnect.setEnabled(selectedDevice != null);

            if (selectedDevice != null) {
                appendLog("找到支持的设备");
            } else {
                appendLog("未找到支持的设备");
            }
        } catch (Exception e) {
            Log.e(TAG, "scanDevices error", e);
            tvDeviceInfo.setText("扫描失败: " + e.getMessage());
        }
    }

    private boolean isSupportedDevice(int vid) {
        return vid == VID_ESPRESSIF || vid == VID_ST || vid == VID_SILICON || 
               vid == VID_FTDI || vid == VID_CH340;
    }

    private void requestPermission() {
        if (selectedDevice == null) {
            Toast.makeText(this, "请先扫描选择设备", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (usbManager.hasPermission(selectedDevice)) {
                openDevice();
            } else {
                appendLog("请求USB权限...");
                PendingIntent pi = PendingIntent.getBroadcast(this, 0, 
                    new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                usbManager.requestPermission(selectedDevice, pi);
            }
        } catch (Exception e) {
            Log.e(TAG, "requestPermission error", e);
            Toast.makeText(this, "请求权限失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openDevice() {
        progressBar.setVisibility(View.VISIBLE);
        btnConnect.setEnabled(false);
        appendLog("正在连接设备...");

        final boolean[] result = new boolean[1];
        new Thread(() -> {
            try {
                result[0] = usbComm.openDevice(usbManager, selectedDevice);
            } catch (Exception e) {
                Log.e(TAG, "openDevice error", e);
            }

            handler.post(() -> {
                progressBar.setVisibility(View.GONE);
                if (result[0]) {
                    appendLog("连接成功！");
                    btnConnect.setVisibility(View.GONE);
                    btnDisconnect.setVisibility(View.VISIBLE);
                    Toast.makeText(ConnectActivity.this, "设备连接成功", Toast.LENGTH_SHORT).show();
                } else {
                    appendLog("连接失败");
                    btnConnect.setEnabled(true);
                    Toast.makeText(ConnectActivity.this, "连接失败，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void disconnect() {
        usbComm.closeDevice();
        appendLog("已断开连接");
        btnDisconnect.setVisibility(View.GONE);
        btnConnect.setVisibility(View.VISIBLE);
        btnConnect.setEnabled(true);
        Toast.makeText(this, "已断开连接", Toast.LENGTH_SHORT).show();
    }

    private void appendLog(String msg) {
        String ts = java.text.SimpleDateFormat.getTimeInstance().format(new java.util.Date());
        tvLog.append("[" + ts + "] " + msg + "\n");
    }
}
