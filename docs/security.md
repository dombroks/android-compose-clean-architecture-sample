# 🔐 Security Best Practices

## Overview

BakingApp implements enterprise-grade security measures:

- **Encrypted storage** for sensitive data
- **Certificate pinning** for network security
- **Secure token management**
- **No sensitive data logging**

## Encrypted Storage

### EncryptedSharedPreferences

```kotlin
@Singleton
class EncryptedPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
```

### What to Store Encrypted

| Data Type | Store Encrypted? | Example |
|-----------|-----------------|---------|
| Auth Tokens | ✅ Yes | Access token, refresh token |
| User Credentials | ✅ Yes | Session data |
| API Keys | ✅ Yes | Third-party service keys |
| User Preferences | ❌ No | Theme, language |
| Cached Data | ❌ No | Recipe data |

## Token Management

```kotlin
@Singleton
class SecureTokenManager @Inject constructor(
    private val encryptedPrefs: EncryptedPreferencesManager
) : TokenProvider {
    
    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)
        encryptedPrefs.putString(KEY_ACCESS_TOKEN, accessToken)
        encryptedPrefs.putString(KEY_REFRESH_TOKEN, refreshToken)
        encryptedPrefs.putLong(KEY_TOKEN_EXPIRY, expiryTime)
    }
    
    fun isTokenExpired(): Boolean {
        val expiryTime = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        return System.currentTimeMillis() >= expiryTime
    }
    
    fun clearAll() {
        encryptedPrefs.remove(KEY_ACCESS_TOKEN)
        encryptedPrefs.remove(KEY_REFRESH_TOKEN)
        encryptedPrefs.remove(KEY_TOKEN_EXPIRY)
    }
}
```

## Network Security

### Certificate Pinning

```kotlin
val certificatePinner = CertificatePinner.Builder()
    .add("api.bakingapp.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

val okHttpClient = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

### Network Security Config

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.bakingapp.com</domain>
        <pin-set expiration="2025-12-31">
            <pin digest="SHA-256">base64-encoded-pin</pin>
            <pin digest="SHA-256">backup-pin</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

### AndroidManifest Configuration

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    android:usesCleartextTraffic="false">
```

## Logging Security

### Debug-Only Logging

```kotlin
@Provides
fun provideLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
}
```

### Redacting Sensitive Headers

```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    redactHeader("Authorization")
    redactHeader("Cookie")
}
```

## Security Checklist

- [ ] Use EncryptedSharedPreferences for tokens
- [ ] Implement certificate pinning
- [ ] Disable cleartext traffic
- [ ] No sensitive data in logs
- [ ] Clear tokens on logout
- [ ] Implement token refresh
- [ ] Use ProGuard/R8 for release builds
- [ ] Validate SSL certificates
- [ ] Implement biometric authentication (optional)
- [ ] Detect rooted devices (optional)

## Anti-Patterns to Avoid

```kotlin
// ❌ DON'T: Log sensitive data
Log.d("Auth", "Token: $accessToken")

// ✅ DO: Log only non-sensitive info
Log.d("Auth", "Token received, length: ${accessToken.length}")

// ❌ DON'T: Store tokens in regular SharedPreferences
val prefs = context.getSharedPreferences("prefs", MODE_PRIVATE)
prefs.edit().putString("token", token).apply()

// ✅ DO: Use encrypted storage
encryptedPrefs.putString("token", token)

// ❌ DON'T: Hardcode API keys
const val API_KEY = "abc123secret"

// ✅ DO: Use BuildConfig or encrypted storage
val apiKey = BuildConfig.API_KEY
```
