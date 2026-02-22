# Add project specific ProGuard rules here.
-keepattributes *Annotation*

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep data classes
-keep class com.esde.emulatormanager.data.model.** { *; }
