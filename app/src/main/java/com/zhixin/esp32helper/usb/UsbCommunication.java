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
            if (tryInterface(device.getInterface(0))) {
                return true;
            }
        }

        return false;
    }

    private boolean tryInterface(UsbInterface intf) {
        try {
            endpointIn = null;
            endpointOut = null;

            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint ep = intf.getEndpoint(j);
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    endpointIn = ep;
                } else {
                    endpointOut = ep;
                }
            }

            if (endpointIn == null || endpointOut == null) {
                return false;
            }

            usbConnection = usbManager.openDevice(usbDevice);
            if (usbConnection == null) {
                return false;
            }

            if (!usbConnection.claimInterface(intf, true)) {
                usbConnection.close();
                usbConnection = null;
                return false;
            }

            usbInterface = intf;
            return true;

        } catch (Exception e) {
            Log.e(TAG, "tryInterface error", e);
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
            Log.e(TAG, "closeDevice error", e);
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

            // 中断当前程序
            sendByte((byte) 0x03); // Ctrl+C
            Thread.sleep(100);
            sendByte((byte) 0x03);
            Thread.sleep(200);
            drainInput();

            // 进入Raw REPL模式
            sendByte((byte) 0x01); // Ctrl+A
            Thread.sleep(200);
            drainInput();

            // 构建Python代码
            StringBuilder code = new StringBuilder();
            code.append("import os\n");
            code.append("try:\n");
            code.append("    os.remove('").append(fileName).append("')\n");
            code.append("except:\n");
            code.append("    pass\n");
            code.append("\n");
            code.append("with open('").append(fileName).append("', 'w') as f:\n");
            
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String escaped = escapeForPython(line);
                if (i < lines.length - 1) {
                    code.append("    f.write(").append(escaped).append(" + '\\n')\n");
                } else if (!line.isEmpty()) {
                    code.append("    f.write(").append(escaped).append(")\n");
                }
            }

            Log.d(TAG, "Code to execute:\n" + code.toString());

            // 发送代码
            sendData(code.toString().getBytes("UTF-8"));
            Thread.sleep(100);

            // 执行代码 (Ctrl+D)
            sendByte((byte) 0x04);
            Thread.sleep(500);

            // 读取响应
            byte[] response = readAllResponse(2000);
            if (response != null) {
                String resp = new String(response, "UTF-8");
                Log.d(TAG, "Raw REPL response: " + resp);
            }

            // 退出Raw REPL
            sendByte((byte) 0x02); // Ctrl+B
            Thread.sleep(100);

            // 验证文件
            Thread.sleep(200);
            sendByte((byte) 0x03);
            Thread.sleep(100);
            
            sendData("import os; print('SIZE:', os.path.getsize('main.py'))\r\n".getBytes("UTF-8"));
            Thread.sleep(500);
            
            byte[] verify = readAllResponse(1000);
            if (verify != null) {
                String v = new String(verify, "UTF-8");
                Log.d(TAG, "Verify: " + v);
                if (v.contains("SIZE:")) {
                    Log.d(TAG, "File verified!");
                    return true;
                }
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "writeFile error", e);
            return false;
        }
    }

    private String escapeForPython(String s) {
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\r': sb.append("\\r"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c >= 32 && c < 127) {
                        sb.append(c);
                    } else {
                        sb.append(String.format("\\x%02x", (int)c));
                    }
            }
        }
        sb.append("'");
        return sb.toString();
    }

    private void sendByte(byte b) {
        byte[] buf = new byte[]{b};
        usbConnection.bulkTransfer(endpointOut, buf, 1, TIMEOUT);
    }

    private void sendData(byte[] data) {
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(64, data.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(data, offset, chunk, 0, len);
            int written = usbConnection.bulkTransfer(endpointOut, chunk, len, TIMEOUT);
            if (written < 0) break;
            offset += written;
        }
    }

    private void drainInput() {
        byte[] buf = new byte[64];
        while (usbConnection.bulkTransfer(endpointIn, buf, buf.length, 100) > 0) {
            // drain
        }
    }

    private byte[] readAllResponse(int timeoutMs) {
        byte[] result = new byte[4096];
        int total = 0;
        long endTime = System.currentTimeMillis() + timeoutMs;
        
        while (System.currentTimeMillis() < endTime && total < result.length) {
            byte[] buf = new byte[64];
            int read = usbConnection.bulkTransfer(endpointIn, buf, buf.length, 200);
            if (read > 0) {
                if (total + read > result.length) {
                    read = result.length - total;
                }
                System.arraycopy(buf, 0, result, total, read);
                total += read;
            }
        }
        
        if (total > 0) {
            return Arrays.copyOf(result, total);
        }
        return null;
    }

    public byte[] readResponse() {
        return readAllResponse(500);
    }

    public boolean isConnected() {
        return usbConnection != null;
    }

    public UsbDevice getUsbDevice() {
        return usbDevice;
    }
}
