# Car Launcher as a privileged system app (Magisk module).
# Nothing on the system partition is changed: Magisk mounts these files at boot, and removing
# the module in the Magisk app undoes it.

ui_print "- Car Launcher -> /system/priv-app/TheCarLauncher"
if [ ! -f "$MODPATH/system/priv-app/TheCarLauncher/TheCarLauncher.apk" ]; then
  abort "! TheCarLauncher.apk is missing from the module"
fi

# The privileged permissions the launcher asks for must all be listed, or Android stops at boot
# on firmwares that enforce the list: system/etc/permissions/privapp-permissions-com.thecarlauncher.xml
ui_print "- Privileged permissions whitelisted"

set_perm_recursive "$MODPATH" 0 0 0755 0644
ui_print "- Done. Reboot, then keep updating the launcher from Setup -> Software update."
