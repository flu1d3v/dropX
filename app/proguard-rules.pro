# ─── CORE SERIALIZATION METADATA CONTRACTS ────────────────────────────
# Preserves the annotations, type graphs, and closures used by Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Stops R8 from renaming or stripping fields out of JSON data models
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─── APP DATA MODELS ─────────────────────────────────────────────
# Guarantees that the exact string names match what index.html expects
-keep class com.fluid.dropx.model.** { *; }

# ─── KTOR CORE LIFECYCLE ──────────────────────────────────────────────
# Protects Ktor's dynamic engine reflection lookups from breaking
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }

# Tell R8 to safely ignore missing optional dependencies inside Ktor's server layers
-dontwarn io.ktor.**
-dontwarn org.slf4j.**