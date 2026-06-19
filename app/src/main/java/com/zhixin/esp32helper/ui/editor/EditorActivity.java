package com.zhixin.esp32helper.ui.editor;

import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
    private static final int VENDOR_ID_ESPRESSIF_MICROPYTHON = 0x303A;
    private static final int VENDOR_ID_ST = 0x0483;
    private static final int VENDOR_ID_SILICON = 0x10C4;
    private static final int VENDOR_ID_FTDI = 0x0403;

    private EditText etFileName;
    private EditText etCode;
    private Button btnCopy;
    private Button btnPaste;
    private Button btnClear;
    private Button btnWrite;
    private Button btnBack;
    private TextView tvStatus;

    private UsbManager usbManager;
    private UsbCommunication usbCommunication;
    private UsbDevice esp32Device;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        etFileName = findViewById(R.id.etFileName);
        etCode = findViewById(R.id.etCode);
        btnCopy = findViewById(R.id.btnCopy);
        btnPaste = findViewById(R.id.btnPaste);
        btnClear = findViewById(R.id.btnClear);
        btnWrite = findViewById(R.id.btnWrite);
        btnBack = findViewById(R.id.btnBack);
        tvStatus = findViewById(R.id.tvStatus);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbCommunication = new UsbCommunication();
        handler = new Handler(Looper.getMainLooper());

        findDevice();

        btnCopy.setOnClickListener(v -> copyCode());
        btnPaste.setOnClickListener(v -> pasteCode());
        btnClear.setOnClickListener(v -> clearCode());
        btnWrite.setOnClickListener(v -> writeFile());
        btnBack.setOnClickListener(v -> finish());

        etFileName.setText("main.py");
    }

    @Override
    protected void onResume() {
        super.onResume();
        findDevice();
    }

    private void findDevice() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        esp32Device = null;

        for (UsbDevice device : deviceList.values()) {
            int vendorId = device.getVendorId();
            if (vendorId == VENDOR_ID_ESPRESSIF_MICROPYTHON || vendorId == VENDOR_ID_ST || vendorId == VENDOR_ID_SILICON || vendorId == VENDOR_ID_FTDI) {
                esp32Device = device;
                usbCommunication.openDevice(usbManager, esp32Device);
                tvStatus.setText("设备已连接");
                tvStatus.setTextColor(getResources().getColor(R.color.success, null));
                btnWrite.setEnabled(true);
                break;
            }
        }

        if (esp32Device == null) {
            tvStatus.setText("未检测到设备，请先连接");
            tvStatus.setTextColor(getResources().getColor(R.color.gray, null));
            btnWrite.setEnabled(false);
        }
    }

    private void copyCode() {
        String code = etCode.getText().toString();
        if (!code.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("code", code);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "代码已复制", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "没有可复制的代码", Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteCode() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            String text = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
            etCode.setText(text);
            Toast.makeText(this, "代码已粘贴", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearCode() {
        new AlertDialog.Builder(this)
            .setTitle("清空确认")
            .setMessage("确定要清空代码编辑器吗？")
            .setPositiveButton("确定", (dialog, which) -> etCode.setText(""))
            .setNegativeButton("取消", null)
            .show();
    }

    private void writeFile() {
        String fileName = etFileName.getText().toString().trim();
        String content = etCode.getText().toString();

        if (fileName.isEmpty()) {
            Toast.makeText(this, "请输入文件名", Toast.LENGTH_SHORT).show();
            return;
        }

        if (content.isEmpty()) {
            Toast.makeText(this, "代码为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (esp32Device == null) {
            Toast.makeText(this, "设备未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!usbCommunication.isConnected()) {
            if (!usbCommunication.openDevice(usbManager, esp32Device)) {
                Toast.makeText(this, "无法连接设备", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        tvStatus.setText("正在写入...");
        btnWrite.setEnabled(false);

        new Thread(() -> {
            boolean success = writeFileToDevice(fileName, content);
            handler.post(() -> {
                btnWrite.setEnabled(true);
                if (success) {
                    tvStatus.setText("写入成功: " + fileName);
                    tvStatus.setTextColor(getResources().getColor(R.color.success, null));
                    Toast.makeText(EditorActivity.this, R.string.write_success, Toast.LENGTH_SHORT).show();
                } else {
                    tvStatus.setText("写入失败");
                    tvStatus.setTextColor(getResources().getColor(R.color.error, null));
                    Toast.makeText(EditorActivity.this, R.string.write_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private boolean writeFileToDevice(String fileName, String content) {
        try {
            return usbCommunication.writeFile(fileName, content);
        } catch (Exception e) {
            Log.e(TAG, "Error writing file", e);
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbCommunication != null) {
            usbCommunication.closeDevice();
        }
    }
}
