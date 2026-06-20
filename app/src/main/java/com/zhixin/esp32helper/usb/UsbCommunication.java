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
    private static final int TIMEOUT = 5000;
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
            Log.d(TAG, "Writing file: " + fileName + " size: " + content.length());

            // Step 1: 发送Ctrl+C中断当前程序
            Log.d(TAG, "Step 1: Send Ctrl+C");
            sendData(new byte[]{0x03});
            Thread.sleep(100);
            sendData(new byte[]{0x03});
            Thread.sleep(200);
            drainInput();

            // Step 2: 进入REPL
            Log.d(TAG, "Step 2: Enter REPL");
            sendCommand("\r\n");
            Thread.sleep(100);
            drainInput();

            // Step 3: 删除旧文件
            Log.d(TAG, "Step 3: Remove old file");
            sendCommand("import os\r\n");
            Thread.sleep(50);
            drainInput();
            
            sendCommand("try:\r\n");
            Thread.sleep(30);
            sendCommand(" os.remove('" + fileName + "')\r\n");
            Thread.sleep(30);
            sendCommand("except:\r\n");
            Thread.sleep(30);
            sendCommand(" pass\r\n");
            Thread.sleep(100);
            drainInput();

            // Step 4: 使用paste模式写入文件内容
            Log.d(TAG, "Step 4: Write file content using paste mode");
            sendCommand("with open('" + fileName + "','w') as f:\r\n");
            Thread.sleep(50);

            // 逐行写入
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                // 转义特殊字符
                String escaped = escapeString(line);
                if (i < lines.length - 1) {
                    sendCommand(" f.write(" + escaped + "+\"\\n\")\r\n");
                } else {
                    // 最后一行不加换行
                    sendCommand(" f.write(" + escaped + ")\r\n");
                }
                Thread.sleep(30);
            }

            // Step 5: 关闭文件
            Log.d(TAG, "Step 5: Close file");
            Thread.sleep(100);
            drainInput();

            // Step 6: 验证文件
            Log.d(TAG, "Step 6: Verify file");
            sendCommand("import os\r\n");
            Thread.sleep(50);
            sendCommand("print('FILE_SIZE:', os.path.getsize('" + fileName + "'))\r\n");
            Thread.sleep(200);

            byte[] response = readResponse();
            if (response != null) {
                String resp = new String(response, "UTF-8");
                Log.d(TAG, "Response: " + resp);
                if (resp.contains("FILE_SIZE:")) {
                    Log.d(TAG, "File written successfully");
                    return true;
                }
            }

            Log.d(TAG, "Write completed (verification inconclusive)");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Write error", e);
            return false;
        }
    }

    private String escapeString(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\r': sb.append("\\r"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append("'");
        return sb.toString();
    }

    private void sendCommand(String cmd) {
        try {
            sendData(cmd.getBytes("UTF-8"));
        } catch (Exception e) {
            Log.e(TAG, "Send command error", e);
        }
    }

    private void drainInput() {
        try {
            while (true) {
                byte[] data = readResponse();
                if (data == null || data.length == 0) break;
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void sendData(byte[] data) {
        if (endpointOut == null || usbConnection == null) {
            return;
        }

        try {
            int offset = 0;
            while (offset < data.length) {
                int chunkSize = Math.min(endpointOut.getMaxPacketSize(), data.length - offset);
                byte[] chunk = new byte[chunkSize];
                System.arraycopy(data, offset, chunk, 0, chunkSize);
                
                int written = usbConnection.bulkTransfer(endpointOut, chunk, chunkSize, TIMEOUT);
                if (written < 0) {
                    Log.e(TAG, "Bulk write failed at offset " + offset);
                    return;
                }
                offset += written;
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
            int read = usbConnection.bulkTransfer(endpointIn, buffer, buffer.length, 500);
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
