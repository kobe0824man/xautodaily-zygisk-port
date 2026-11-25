#!/system/bin/sh
SKIPUNZIP=1
ui_print "Installing XAutoDaily Zygisk port"
unzip -o "$ZIPFILE" 'module.prop' -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'zygisk/*' -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'payload.apk' -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'native-libs/*' -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'payload.info' -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'uninstall.sh' -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'licenses/*' -d "$MODPATH" >&2
[ -f "$MODPATH/payload.apk" ] || abort "payload.apk missing"
[ -f "$MODPATH/native-libs/arm64-v8a/libxautodaily_hook.so" ] || abort "arm64 native hook support libraries missing"
[ -f "$MODPATH/native-libs/armeabi-v7a/libxautodaily_hook.so" ] || abort "arm32 native hook support libraries missing"
[ -f "$MODPATH/zygisk/arm64-v8a.so" ] || [ -f "$MODPATH/zygisk/armeabi-v7a.so" ] || abort "Zygisk library missing"
set_perm_recursive "$MODPATH" 0 0 0755 0644
