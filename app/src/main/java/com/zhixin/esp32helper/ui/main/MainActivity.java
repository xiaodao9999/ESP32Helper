package com.zhixin.esp32helper.ui.main;

import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zhixin.esp32helper.R;
import com.zhixin.esp32helper.ui.connect.ConnectActivity;
import com.zhixin.esp32helper.usb.UsbCommunication;

public class MainActivity extends AppCompatActivity {
    private Button btnConnect;
    private Button btnEditCode;
    private TextView tvStatus;
    private UsbCommunication usbCommunication;

    public static final int VENDOR_ID_ESPRESSIF_MICROPYTHON = 0x303A;
    public static final int VENDOR_ID_ST = 0x0483;
    public static final int VENDOR_ID_SILICON = 0x10C4;
    public static final int VENDOR_ID_FTDI = 0x0403;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnConnect = findViewById(R.id.btnConnect);
        btnEditCode = findViewById(R.id.btnEditCode);
        tvStatus = findViewById(R.id.tvStatus);

        usbCommunication = new UsbCommunication();

        btnConnect.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ConnectActivity.class);
            startActivity(intent);
        });

        btnEditCode.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.zhixin.esp32helper.ui.editor.EditorActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkDeviceStatus();
    }

    private void checkDeviceStatus() {
        UsbManager usbManager = (UsbManager) getSystemService(USB_SERVICE);
        if (usbManager != null) {
            boolean deviceFound = false;
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                if (device.getVendorId() == VENDOR_ID_ESPRESSIF_MICROPYTHON || 
                    device.getVendorId() == VENDOR_ID_ST || 
                    device.getVendorId() == VENDOR_ID_SILICON || 
                    device.getVendorId() == VENDOR_ID_FTDI) {
                    deviceFound = true;
                    break;
                }
            }
            if (deviceFound) {
                tvStatus.setText(R.string.device_connected);
                tvStatus.setTextColor(getResources().getColor(R.color.success, null));
            } else {
                tvStatus.setText(R.string.device_disconnected);
                tvStatus.setTextColor(getResources().getColor(R.color.gray, null));
            }
        }
    }
}
