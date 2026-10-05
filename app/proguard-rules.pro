# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\AndroidSDK/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# OrmLite uses reflection over annotated model classes
-keep class io.github.atmstudent.kakukaku.Database.** { *; }
-keepclassmembers class * { @com.j256.ormlite.field.DatabaseField *; }
-dontwarn javax.persistence.**
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn com.j256.ormlite.**

# ML Kit finds its components by reflection; R8 removes the registrars' constructors otherwise and OCR fails to start
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class * implements com.google.firebase.components.ComponentRegistrar
