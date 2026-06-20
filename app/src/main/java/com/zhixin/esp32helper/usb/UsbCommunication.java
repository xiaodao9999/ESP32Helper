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
    private static final int VID_CH340 = 0x1A86;

    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint endpointIn;
    private UsbEndpoint endpointOut;

    public boolean openDevice(UsbManager manager, UsbDevice device) {
        this.usbManager = manager;
        this.usbDevice = device;

        if (device == null) {
            Log.e(TAG, "Device is null");
            return false;
        }

        Log.d(TAG, "Opening device VID=" + Integer.toHexString(device.getVendorId()) 
            + " PID=" + Integer.toHexString(device.getProductId()));

        int interfaceCount = device.getInterfaceCount();
        Log.d(TAG, "Interface count: " + interfaceCount);

        if (device.getVendorId() == VID_CH340) {
            for (int i = 0; i < interfaceCount; i++) {
                UsbInterface intf = device.getInterface(i);
                Log.d(TAG, "CH340 Interface " + i + " class=" + intf.getInterfaceClass());
                int intfClass = intf.getInterfaceClass();
                if (intfClass == 0x0A || intfClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                    if (tryInterface(intf)) {
                        return true;
                    }
                }
            }
        }

        for (int i = 0; i < interfaceCount; i++) {
            UsbInterface intf = device.getInterface(i);
            int intfClass = intf.getInterfaceClass();
            Log.d(TAG, "Interface " + i + " class=" + intfClass);

            if (intfClass == UsbConstants.USB_CLASS_VENDOR_SPEC || 
                intfClass == UsbConstants.USB_CLASS_COMM ||
                intfClass == UsbConstants.USB_CLASS_CDC_DATA ||
                intfClass == 0x0A) {
                if (tryInterface(intf)) {
                    return true;
                }
            }
        }

        if (interfaceCount > 0) {
            UsbInterface intf = device.getInterface(0);
            if (tryInterface(intf)) {
                return true;
            }
        }

        Log.e(TAG, "No suitable interface found");
        return false;
    }

    private boolean tryInterface(UsbInterface intf) {
        try {
            int endpointCount = intf.getEndpointCount();
            Log.d(TAG, "Endpoint count: " + endpointCount);

            endpointIn = null;
            endpointOut = null;

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

            if (endpointIn == null || endpointOut == null) {
                Log.e(TAG, "Required endpoints not found");
                return false;
            }

            usbConnection = usbManager.openDevice(usbDevice);
            if (usbConnection == null) {
                Log.e(TAG, "Cannot open device");
                return false;
            }

            if (!usbConnection.claimInterface(intf, true)) {
                Log.e(TAG, "Cannot claim interface");
                usbConnection.close();
                usbConnection = null;
                return false;
            }

            usbInterface = intf;
            Log.d(TAG, "Device opened successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error trying interface", e);
            return false;
        }
    }

    public void closeDevice() {
        try {
            if (usbConnection != null) {
                if (usbInterface != null) {
                    usbConnection.releaseInterface(usbInterface);
                }
                usbConnection.close();
                usbConnection = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing device", e);
        }
        usbInterface = null;
        endpointIn = null;
        endpointOut = null;
    }

    public boolean writeFile(String fileName, String content) {
        if (endpointOut == null || usbConnection == null) {
            Log.e(TAG, "Connection not ready");
            return false;
        }

        try {
            Log.d(TAG, "Writing file: " + fileName);

            sendData(new byte[]{0x03});
            Thread.sleep(200);
            readResponse();

            byte[] nl = new byte[]{'\r', '\n'};
            sendData(nl);
            Thread.sleep(100);

            String removeCmd = "import os\r\ntry: os.remove('" + fileName + "')\nexcept: pass\r\n";
            sendData(removeCmd.getBytes("UTF-8"));
            Thread.sleep(200);
            readResponse();

            String[] lines = content.split("\n");
            String openCmd = "f=open('" + fileName + "','w')\r\n";
            sendData(openCmd.getBytes("UTF-8"));
            Thread.sleep(100);

            for (String line : lines) {
                String escapedLine = line.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n");
                String writeCmd = "f.write('" + escapedLine + "')+\"\\n\"\r\n";
                sendData(writeCmd.getBytes("UTF-8"));
                Thread.sleep(50);
            }

            sendData("f.close()\r\n".getBytes("UTF-8"));
            Thread.sleep(200);

            String verifyCmd = "import os\r\nprint('SIZE:', os.path.getsize('" + fileName + "'))\r\n";
            sendData(verifyCmd.getBytes("UTF-8"));
            Thread.sleep(300);
            byte[] response = readResponse();
            if (response != null) {
                String resp = new String(response, "UTF-8");
                Log.d(TAG, "Response: " + resp);
                if (resp.contains("SIZE:")) {
                    Log.d(TAG, "File written successfully");
                    return true;
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Write error", e);
            return false;
        }
    }

    private void sendData(byte[] data) {
        if (endpointOut == null || usbConnection == null) {
            return;
        }

        try {
            int written = usbConnection.bulkTransfer(endpointOut, data, data.length, TIMEOUT);
            if (written < 0) {
                Log.e(TAG, "Bulk write failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Send data error", e);
        }
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
            Log.e(TAG, "Read error", e);
        }
        return null;
    }

    public boolean isConnected() {
        return usbConnection != null;
    }

    public UsbDevice getUsbDevice() {
        return usbDevice;
    }
}
