# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.tegami.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.tegami.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tegami.**$$serializer { *; }

# BouncyCastle (via web3j): R8 may warn about unused JCA/LDAP parts.
-dontwarn org.bouncycastle.**
# web3j: ABI types are looked up reflectively; wallet-file JSON (Jackson) is unused here.
-keep class org.web3j.abi.datatypes.** { *; }
-dontwarn org.web3j.**
-dontwarn com.fasterxml.jackson.**
-dontwarn javax.annotation.**
# Ktor references JVM-only classes that are not on Android.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
# A transitive dependency of web3j references an optional Groovy script engine that is never used.
-dontwarn groovy.lang.**
# web3j reads each TypeReference's generic type at runtime ("Missing type parameter" without it): keep the
# Signature attribute and every TypeReference subclass (the anonymous ones in SealContract).
-keepattributes Signature, EnclosingMethod
-keep class org.web3j.abi.TypeReference { *; }
-keep class * extends org.web3j.abi.TypeReference
