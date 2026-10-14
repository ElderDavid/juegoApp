#!/usr/bin/env bash
# Se ejecuta dentro del emulador: instala la release, la abre y guarda el registro del sistema.
adb install -r app/build/outputs/apk/release/app-release.apk || exit 1
adb logcat -c
adb shell monkey -p com.elderdavid.quientienelarazon -c android.intent.category.LAUNCHER 1
sleep 25
adb logcat -d > logcat.txt
adb shell pidof com.elderdavid.quientienelarazon > pid.txt || echo "sin proceso" > pid.txt
adb exec-out screencap -p > pantalla.png
exit 0
