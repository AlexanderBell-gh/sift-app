# Sift app release ProGuard rules. Minification is currently off;
# keep this file so enabling isMinifyEnabled later is a one-line change.
# Retrofit / kotlinx.serialization: keep generic signatures for converters.
-keepattributes Signature, InnerClasses, EnclosingMethod
# DataStore + coroutines are reflection-free; no extra rules needed.
