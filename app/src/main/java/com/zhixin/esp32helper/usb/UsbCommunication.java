package com.zhixin.esp32helper.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.Arrays;

public class UsbCommunication {
    private static final String TAG = "UsbCommunication";
    private static final int TIMEOUT = 3000;

    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint endpointIn;
    private UsbEndpoint endpointOut;

    public interface OnDataReceivedListener {
        void onDataReceived(byte[] data);
        void onError(String message);
    }

    public boolean openDevice(UsbManager manager, UsbDevice device) {
        this.usbManager = manager;
        this.usbDevice = device;

        if (device == null) {
            Log.e(TAG, "Device is null");
            return false;
        }

        int interfaceCount = device.getInterfaceCount();
        Log.d(TAG, "Device interface count: " + interfaceCount);

        for (int i = 0; i < interfaceCount; i++) {
            UsbInterface intf = device.getInterface(i);
            Log.d(TAG, "Interface " + i + " - class: " + intf.getInterfaceClass());

            int intfClass = intf.getInterfaceClass();
            if (intfClass == UsbConstants.USB_CLASS_VENDOR_SPEC || 
                intfClass == UsbConstants.USB_CLASS_COMM ||
                intfClass == 0x0A) {  // CDC_DATA class
                usbInterface = intf;
                int endpointCount = intf.getEndpointCount();
                Log.d(TAG, "Endpoint count: " + endpointCount);

                for (int j = 0; j < endpointCount; j++) {
                    UsbEndpoint endpoint = intf.getEndpoint(j);
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                        endpointIn = endpoint;
                        Log.d(TAG, "Found IN endpoint");
                    } else {
                        endpointOut = endpoint;
                        Log.d(TAG, "Found OUT endpoint");
                    }
                }
                break;
            }
        }

        if (endpointIn == null || endpointOut == null) {
            Log.e(TAG, "Endpoints not found");
            return false;
        }

        usbConnection = usbManager.openDevice(usbDevice);
        if (usbConnection == null) {
            Log.e(TAG, "Cannot open device connection");
            return false;
        }

        if (!usbConnection.claimInterface(usbInterface, true)) {
            Log.e(TAG, "Cannot claim interface");
            usbConnection.close();
            usbConnection = null;
            return false;
        }

        Log.d(TAG, "Device opened successfully");
        return true;
    }

    public void closeDevice() {
        if (usbConnection != null) {
            try {
                if (usbInterface != null) {
                    usbConnection.releaseInterface(usbInterface);
                }
                usbConnection.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing connection", e);
            }
            usbConnection = null;
        }
        usbInterface = null;
        endpointIn = null;
        endpointOut = null;
        usbDevice = null;
    }

    public boolean writeFile(String fileName, String content) {
        if (endpointOut == null || usbConnection == null) {
            Log.e(TAG, "Endpoint or connection is null");
            return false;
        }

        try {
            String command = "import os\nos.remove('main.py')\n";
            byte[] removeCmd = command.getBytes("UTF-8");
            sendRawData(removeCmd);

            String writeCommand = "f = open('main.py', 'w')\nf.write('''" + content.replace("'", "\\'").replace("\\", "\\\\") + "''')\nf.close()\n";
            byte[] writeData = writeCommand.getBytes("UTF-8");
            sendRawData(writeData);

            Log.d(TAG, "File written successfully: " + fileName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error writing file", e);
            return false;
        }
    }

    private boolean sendRawData(byte[] data) {
        if (endpointOut == null || usbConnection == null) {
            return false;
        }

        int offset = 0;
        byte[] buffer = new byte[endpointOut.getMaxPacketSize()];

        while (offset < data.length) {
            int length = Math.min(buffer.length, data.length - offset);
            System.arraycopy(data, offset, buffer, 0, length);

            int written = usbConnection.bulkTransfer(endpointOut, buffer, length, TIMEOUT);
            if (written < 0) {
                Log.e(TAG, "Write failed at offset " + offset);
                return false;
            }
            offset += written;
        }
        return true;
    }

    public byte[] readResponse() {
        if (endpointIn == null || usbConnection == null) {
            return null;
        }

        try {
            byte[] buffer = new byte[endpointIn.getMaxPacketSize()];
            int read = usbConnection.bulkTransfer(endpointIn, buffer, buffer.length, TIMEOUT);

            if (read > 0) {
                return Arrays.copyOf(buffer, read);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading response", e);
        }
        return null;
    }

    public boolean isConnected() {
        return usbConnection != null && usbInterface != null;
    }

    public UsbDevice getUsbDevice() {
        return usbDevice;
    }
}
