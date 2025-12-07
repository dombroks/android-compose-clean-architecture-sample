# 🌐 Networking Guide

## Overview

BakingApp uses a robust networking stack:

- **Retrofit** for API communication
- **OkHttp** for HTTP client
- **Moshi** for JSON parsing
- **Custom NetworkResponse** for type-safe responses

## Network Stack

```
┌─────────────────────────────────────────────────┐
│                  RETROFIT                        │
│     (Type-safe API interface definitions)        │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────┐
│                   OKHTTP                         │
│  ┌──────────────────────────────────────────┐   │
│  │           INTERCEPTOR CHAIN              │   │
│  │  ┌─────────────┐ ┌─────────────────────┐ │   │
│  │  │    Auth     │ │      Logging        │ │   │
│  │  │ Interceptor │ │    Interceptor      │ │   │
│  │  └─────────────┘ └─────────────────────┘ │   │
│  │  ┌─────────────┐ ┌─────────────────────┐ │   │
│  │  │   Delay     │ │       Retry         │ │   │
│  │  │ Interceptor │ │    (Built-in)       │ │   │
│  │  └─────────────┘ └─────────────────────┘ │   │
│  └──────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────┐
│                    MOSHI                         │
│        (JSON serialization/deserialization)      │
└─────────────────────────────────────────────────┘
```

## API Definition

```kotlin
interface RecipesApi {
    @GET("recipes")
    fun getRecipes(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Call<NetworkResponse<RecipeListResponse>>
}
```

## NetworkResponse Sealed Class

```kotlin
sealed class NetworkResponse<out T> {
    data class Success<T>(val data: T) : NetworkResponse<T>()
    data class ApiError(
        val code: Int,
        val message: String?,
        val body: String? = null
    ) : NetworkResponse<Nothing>()
    data class NetworkError(val error: Throwable) : NetworkResponse<Nothing>()
    data class UnknownError(val error: Throwable) : NetworkResponse<Nothing>()
}
```

## Interceptors

### AuthInterceptor

```kotlin
class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.getAccessToken()
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
```

### NetworkDelayInterceptor (Debug Only)

```kotlin
class NetworkDelayInterceptor : Interceptor {
    var delayMs: Long = 1500L
    var isEnabled: Boolean = false
    
    override fun intercept(chain: Interceptor.Chain): Response {
        if (isEnabled) Thread.sleep(delayMs)
        return chain.proceed(chain.request())
    }
}
```

## OkHttp Configuration

```kotlin
@Provides
fun provideOkHttpClient(
    loggingInterceptor: HttpLoggingInterceptor,
    authInterceptor: AuthInterceptor
): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .retryOnConnectionFailure(true)
        .build()
}
```

## Error Handling

```kotlin
// In Repository
fun getRecipes(): Flow<Result<List<Recipe>>> = flow {
    emit(Result.Loading)
    when (val response = api.getRecipes().execute().body()) {
        is NetworkResponse.Success -> emit(Result.Success(response.data))
        is NetworkResponse.ApiError -> emit(Result.Error(ApiException(response.code)))
        is NetworkResponse.NetworkError -> emit(Result.Error(response.error))
        is NetworkResponse.UnknownError -> emit(Result.Error(response.error))
    }
}.catch { emit(Result.Error(it as Exception)) }
```

## Offline-First Strategy

```kotlin
override fun getRecipes(): Flow<Result<List<Recipe>>> = flow {
    emit(Result.Loading)
    
    // 1. Emit cached data first
    recipeDao.getAllRecipes().collect { cached ->
        emit(Result.Success(cached.map { it.toDomain() }))
    }
    
    // 2. Fetch fresh data
    try {
        val fresh = api.getRecipes()
        recipeDao.insertRecipes(fresh.map { it.toEntity() })
    } catch (e: Exception) {
        // Silently fail if offline - cached data already emitted
    }
}
```

## Best Practices

1. **Always use sealed classes** for network responses
2. **Implement retry with exponential backoff** for transient failures
3. **Cache responses locally** for offline support
4. **Log only in debug builds** to avoid leaking sensitive data
5. **Use timeouts** to prevent hung requests
6. **Implement certificate pinning** for production
