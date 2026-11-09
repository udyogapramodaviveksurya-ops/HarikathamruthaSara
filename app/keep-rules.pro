# Release-build (R8) rules for classes that libraries create by reflection.

# WorkManager opens its Room database through Class.newInstance(); the library's own rule keeps the class only, so
# without this the app crashes at launch: NoSuchMethodException WorkDatabase_Impl.<init>.
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# Credential Manager finds its Google sign-in provider by class name.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
