# 📦 Module Structure

## Overview

BakingApp uses a multi-module architecture to achieve:

- **Faster build times** (parallel compilation)
- **Better separation of concerns**
- **Reusability of modules**
- **Clear dependency boundaries**

## Module Diagram

```
                        ┌─────────────┐
                        │     app     │
                        └──────┬──────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────────┐
│features:login │    │ features:home │    │features:recipe-   │
│               │    │               │    │     details       │
└───────┬───────┘    └───────┬───────┘    └─────────┬─────────┘
        │                    │                      │
        └────────────────────┼──────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │          │         │         │          │
        ▼          ▼         ▼         ▼          ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│  core:   │ │  core:   │ │  core:   │ │  core:   │ │  core:   │
│ network  │ │ database │ │ security │ │   ui     │ │  common  │
└──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘
```

## Module Responsibilities

### 🔹 app

**Entry point module**

- Application class with Hilt
- MainActivity
- Navigation graph
- App-level configuration
- Theme providers

```kotlin
dependencies {
    implementation(project(":features:login"))
    implementation(project(":features:home"))
    implementation(project(":features:recipe-details"))
    implementation(project(":core:ui"))
    // ... other core modules
}
```

### 🔹 core:common

**Shared utilities and base classes**

- Result sealed class
- Base UseCase classes
- Coroutine dispatchers
- Flow extensions
- Common utilities

```kotlin
// No Android dependencies - pure Kotlin
dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
}
```

### 🔹 core:network

**Networking layer**

- Retrofit setup
- OkHttp configuration
- API interfaces
- Network models (DTOs)
- Interceptors
- NetworkResponse handling

```kotlin
dependencies {
    api(libs.retrofit)
    api(libs.okhttp)
    api(libs.moshi.kotlin)
    implementation(project(":core:common"))
}
```

### 🔹 core:database

**Local persistence**

- Room database
- DAOs
- Entity classes
- Database migrations
- Paging support

```kotlin
dependencies {
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.paging.runtime)
    ksp(libs.room.compiler)
}
```

### 🔹 core:security

**Security implementations**

- EncryptedSharedPreferences
- Token management
- Secure storage
- Certificate pinning config

```kotlin
dependencies {
    api(libs.androidx.security.crypto)
    implementation(project(":core:network"))
}
```

### 🔹 core:ui

**Reusable UI components**

- Theme (colors, typography)
- Common composables
- Loading indicators
- Error views
- Text fields
- Buttons

```kotlin
dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.coil.compose)
}
```

### 🔹 features:login

**Authentication feature**

- Login screen
- LoginViewModel
- Login use cases
- Validation logic
- Auth repository

### 🔹 features:home

**Home/recipes list feature**

- Home screen
- HomeViewModel
- Recipe models
- Recipe repository
- Search functionality

### 🔹 features:recipe-details

**Recipe details feature**

- Detail screen
- DetailViewModel
- Ingredients list
- Steps list
- Favorite toggle

## Dependency Rules

1. **Feature modules** can depend on **core modules**
2. **Feature modules** should NOT depend on other feature modules (except shared data models)
3. **Core modules** can depend on other core modules
4. **app module** depends on all feature modules and core:ui
5. **Domain layer** has NO Android dependencies
