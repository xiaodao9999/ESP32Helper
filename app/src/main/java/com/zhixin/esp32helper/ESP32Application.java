package com.zhixin.esp32helper.usb;

import android.app.Application;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.content.Context;

public class ESP32Application extends Application {
    public static final int VENDOR_ID_ESPRESSIF = 0x0483;
    public static final int PRODUCT_ID_STM32 = 0x5740;

    private UsbManager usbManager;
    private UsbDevice currentDevice;

    @Override
    public void onCreate() {
        super.onCreate();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    }

    public UsbManager getUsbManager() {
        return usbManager;
    }

    public UsbDevice getCurrentDevice() {
        return currentDevice;
    }

    public void setCurrentDevice(UsbDevice device) {
        this.currentDevice = device;
    }

    public boolean isESP32Connected() {
        if (usbManager == null) return false;
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == VENDOR_ID_ESPRESSIF || 
                device.getVendorId() == 0x10C4) {
                return true;
            }
        }
        return currentDevice != null;
    }
}
