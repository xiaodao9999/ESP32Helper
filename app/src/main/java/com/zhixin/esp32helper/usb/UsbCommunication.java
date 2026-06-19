package com.zhixin.esp32helper.usb;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class UsbCommunication {
    private static final String TAG = "UsbCommunication";
    private static final int TIMEOUT = 1000;

    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private UsbInterface usbInterface;
    private UsbEndpoint endpointIn;
    private UsbEndpoint endpointOut;
    private UsbRequest request;

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
            Log.d(TAG, "Interface " + i + " - " + intf.getInterfaceClass() + " " + intf.getInterfaceSubclass());

            if (intf.getInterfaceClass() == 255 || intf.getInterfaceClass() == 10) {
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

        if (!usbManager.claimInterface(usbInterface, true)) {
            Log.e(TAG, "Cannot claim interface");
            return false;
        }

        Log.d(TAG, "Device opened successfully");
        return true;
    }

    public void closeDevice() {
        if (usbInterface != null && usbDevice != null) {
            try {
                usbManager.releaseInterface(usbInterface);
            } catch (Exception e) {
                Log.e(TAG, "Error releasing interface", e);
            }
        }
        usbInterface = null;
        endpointIn = null;
        endpointOut = null;
        usbDevice = null;
    }

    public boolean writeFile(String fileName, String content) {
        if (endpointOut == null) {
            Log.e(TAG, "Output endpoint is null");
            return false;
        }

        try {
            byte[] fileNameBytes = fileName.getBytes("UTF-8");
            byte[] contentBytes = content.getBytes("UTF-8");

            byte[] command = ("wfile:" + new String(fileNameBytes) + ":" + new String(contentBytes)).getBytes("UTF-8");
            byte[] buffer = new byte[endpointOut.getMaxPacketSize()];

            int offset = 0;
            while (offset < command.length) {
                int length = Math.min(buffer.length, command.length - offset);
                System.arraycopy(command, offset, buffer, 0, length);

                int written = usbDevice.getConnection().bulkTransfer(endpointOut, buffer, length, TIMEOUT);
                if (written < 0) {
                    Log.e(TAG, "Write failed");
                    return false;
                }
                offset += written;
            }

            Log.d(TAG, "File written successfully: " + fileName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error writing file", e);
            return false;
        }
    }

    public boolean writeRawData(byte[] data) {
        if (endpointOut == null) {
            Log.e(TAG, "Output endpoint is null");
            return false;
        }

        try {
            int offset = 0;
            byte[] buffer = new byte[endpointOut.getMaxPacketSize()];

            while (offset < data.length) {
                int length = Math.min(buffer.length, data.length - offset);
                System.arraycopy(data, offset, buffer, 0, length);

                int written = usbDevice.getConnection().bulkTransfer(endpointOut, buffer, length, TIMEOUT);
                if (written < 0) {
                    return false;
                }
                offset += written;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error writing raw data", e);
            return false;
        }
    }

    public byte[] readResponse() {
        if (endpointIn == null) {
            return null;
        }

        try {
            byte[] buffer = new byte[endpointIn.getMaxPacketSize()];
            int read = usbDevice.getConnection().bulkTransfer(endpointIn, buffer, buffer.length, TIMEOUT);

            if (read > 0) {
                return Arrays.copyOf(buffer, read);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading response", e);
        }
        return null;
    }

    public boolean isConnected() {
        return usbDevice != null && usbInterface != null;
    }
}
