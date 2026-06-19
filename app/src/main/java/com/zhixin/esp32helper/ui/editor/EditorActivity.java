package com.zhixin.esp32helper.ui.editor;

import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zhixin.esp32helper.R;
import com.zhixin.esp32helper.usb.UsbCommunication;

import java.util.HashMap;

public class EditorActivity extends AppCompatActivity {
    private static final String TAG = "EditorActivity";

    private static final int VID_ESPRESSIF = 0x303A;
    private static final int VID_ST = 0x0483;
    private static final int VID_SILICON = 0x10C4;
    private static final int VID_FTDI = 0x0403;
    private static final int VID_CH340 = 0x1A86;

    private EditText etCode;
    private Button btnCopy;
    private Button btnPaste;
    private Button btnClear;
    private Button btnWrite;
    private Button btnBack;
    private TextView tvStatus;

    private UsbManager usbManager;
    private UsbCommunication usbComm;
    private UsbDevice esp32Device;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        etCode = findViewById(R.id.etCode);
        btnCopy = findViewById(R.id.btnCopy);
        btnPaste = findViewById(R.id.btnPaste);
        btnClear = findViewById(R.id.btnClear);
        btnWrite = findViewById(R.id.btnWrite);
        btnBack = findViewById(R.id.btnBack);
        tvStatus = findViewById(R.id.tvStatus);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbComm = new UsbCommunication();
        handler = new Handler(Looper.getMainLooper());

        btnCopy.setOnClickListener(v -> copyCode());
        btnPaste.setOnClickListener(v -> pasteCode());
        btnClear.setOnClickListener(v -> clearCode());
        btnWrite.setOnClickListener(v -> writeToDevice());
        btnBack.setOnClickListener(v -> finish());

        findDevice();
    }

    @Override
    protected void onResume() {
        super.onResume();
        findDevice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        usbComm.closeDevice();
    }

    private void findDevice() {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        esp32Device = null;

        for (UsbDevice dev : devices.values()) {
            if (isSupportedDevice(dev.getVendorId())) {
                esp32Device = dev;
                if (usbManager.hasPermission(esp32Device)) {
                    usbComm.openDevice(usbManager, esp32Device);
                    tvStatus.setText("设备已连接");
                    tvStatus.setTextColor(getResources().getColor(R.color.success, null));
                    btnWrite.setEnabled(true);
                } else {
                    tvStatus.setText("需要USB权限");
                    tvStatus.setTextColor(getResources().getColor(R.color.warning, null));
                    btnWrite.setEnabled(false);
                }
                return;
            }
        }

        tvStatus.setText("未检测到设备");
        tvStatus.setTextColor(getResources().getColor(R.color.gray, null));
        btnWrite.setEnabled(false);
    }

    private boolean isSupportedDevice(int vid) {
        return vid == VID_ESPRESSIF || vid == VID_ST || vid == VID_SILICON || 
               vid == VID_FTDI || vid == VID_CH340;
    }

    private void copyCode() {
        String code = etCode.getText().toString();
        if (!code.isEmpty()) {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("code", code);
            cb.setPrimaryClip(clip);
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "代码为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteCode() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb.hasPrimaryClip()) {
            String text = cb.getPrimaryClip().getItemAt(0).getText().toString();
            etCode.setText(text);
            Toast.makeText(this, "已粘贴", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearCode() {
        new AlertDialog.Builder(this)
            .setTitle("确认清空")
            .setMessage("确定清空代码？")
            .setPositiveButton("确定", (dialog, which) -> etCode.setText(""))
            .setNegativeButton("取消", null)
            .show();
    }

    private void writeToDevice() {
        String content = etCode.getText().toString();
        if (content.isEmpty()) {
            Toast.makeText(this, "代码为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (esp32Device == null) {
            Toast.makeText(this, "设备未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("正在写入...");
        btnWrite.setEnabled(false);

        new Thread(() -> {
            boolean success = false;
            try {
                if (!usbComm.isConnected()) {
                    usbComm.openDevice(usbManager, esp32Device);
                }
                success = usbComm.writeFile("main.py", content);
            } catch (Exception e) {
                Log.e(TAG, "write error", e);
            }

            handler.post(() -> {
                btnWrite.setEnabled(true);
                if (success) {
                    tvStatus.setText("写入成功");
                    tvStatus.setTextColor(getResources().getColor(R.color.success, null));
                    Toast.makeText(EditorActivity.this, "写入成功", Toast.LENGTH_SHORT).show();
                } else {
                    tvStatus.setText("写入失败");
                    tvStatus.setTextColor(getResources().getColor(R.color.error, null));
                    Toast.makeText(EditorActivity.this, "写入失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}
