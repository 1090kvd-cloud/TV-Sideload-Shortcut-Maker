# ---------------------------------------------------------------------------
#  Release shrinking rules
# ---------------------------------------------------------------------------
# R8 keeps only what it can see being called. Everything below is reached
# indirectly, so without these rules the release build compiles cleanly and then
# fails at runtime — the worst kind of breakage to discover after shipping.

# Started by the launcher through a pinned shortcut, never from our own code.
-keep class com.tvshortcut.maker.launch.LaunchProxyActivity { *; }

# Invoked by the system via a PendingIntent when an install session finishes.
-keep class com.tvshortcut.maker.data.apk.InstallResultReceiver { *; }

# apksig resolves parts of itself reflectively while signing generated APKs.
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

# ZXing looks up format-specific writers by name when encoding the donation QR.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Compose ships its own consumer rules; nothing extra is needed for it.
