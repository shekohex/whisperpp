# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Gson ---
# Gson (de)serialises these model classes reflectively, so R8 must keep their
# fields, constructors and names intact.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

-keep class com.github.shekohex.whisperpp.data.** { *; }
-keep class com.github.shekohex.whisperpp.updater.** { *; }
-keep class com.github.shekohex.whisperpp.analytics.** { *; }
# Model classes (de)serialised by Gson outside the data package: discovery cache,
# modes, auto-learn queue/proposals, dictionary replacements, editor drafts.
-keep class com.github.shekohex.whisperpp.domain.** { *; }
-keep class com.github.shekohex.whisperpp.core.text.** { *; }
-keep class com.github.shekohex.whisperpp.ui.providers.ProviderDraft { *; }
-keep class com.github.shekohex.whisperpp.ui.providers.ProviderDraft$* { *; }

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep generic signatures of TypeToken subclasses (object : TypeToken<List<T>>() {}).
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
