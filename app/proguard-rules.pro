# Proguard rules for ESP32Helper
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# USB Communication
-keep class com.zhixin.esp32helper.usb.** { *; }

# Keep Application class
-keep class com.zhixin.esp32helper.ESP32Application { *; }
