# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Viiibe App ProGuard Rules
# These rules enable code obfuscation while preserving necessary functionality

# ==================== Keep Data Models ====================
# Keep Room entities and data classes for JSON serialization
-keep class com.viiibe.app.data.model.** { *; }
-keep class com.viiibe.app.data.database.** { *; }
-keep class com.viiibe.app.arcade.p2p.P2PModels** { *; }
-keep class com.viiibe.app.arcade.p2p.*State { *; }
-keep class com.viiibe.app.arcade.p2p.*Info { *; }
-keep class com.viiibe.app.arcade.p2p.*Payload { *; }

# ==================== Keep Bluetooth ====================
-keep class com.viiibe.app.bluetooth.** { *; }

# ==================== Keep Blockchain ====================
# Keep Web3j classes for blockchain operations
-keep class org.web3j.** { *; }
-dontwarn org.web3j.**
-keep class com.viiibe.app.blockchain.** { *; }

# ==================== Keep BouncyCastle ====================
# Required for Web3j crypto operations and BKS keystore
-keep class org.bouncycastle.** { *; }
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keepclassmembers class org.bouncycastle.jce.provider.** { *; }

# ==================== Keep Streaming ====================
-keep class com.viiibe.app.streaming.** { *; }

# ==================== Keep Auth ====================
-keep class com.viiibe.app.auth.** { *; }
-keep class net.openid.appauth.** { *; }

# ==================== Media/ExoPlayer ====================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ==================== OkHttp/Retrofit ====================
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ==================== Gson ====================
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ==================== Compose ====================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ==================== Kotlin ====================
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ==================== Obfuscation Settings ====================
# Enable aggressive obfuscation
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Don't warn about missing classes
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Keep line numbers for crash reporting (optional - remove for maximum obfuscation)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
