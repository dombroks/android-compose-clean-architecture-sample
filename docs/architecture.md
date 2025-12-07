# 🏗️ Architecture Overview

## Introduction

BakingApp follows **Clean Architecture** principles combined with **MVI (Model-View-Intent)** pattern for the presentation layer. This architecture ensures:

- **Separation of concerns**
- **Testability**
- **Maintainability**
- **Scalability**

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                       │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │   Screens   │  │  ViewModels  │  │      UI State       │ │
│  │  (Compose)  │  │    (MVI)     │  │   (Immutable)       │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                           │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │  Use Cases  │  │   Entities   │  │  Repository         │ │
│  │             │  │   (Models)   │  │  Interfaces         │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       DATA LAYER                            │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │ Repository  │  │  Data Sources│  │     Mappers         │ │
│  │   Impl      │  │ (API/Room)   │  │  (DTO ↔ Domain)     │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Layer Details

### Presentation Layer

The UI layer built with **Jetpack Compose**:

```kotlin
// UI State - Immutable data class
data class HomeUiState(
    val recipes: List<Recipe> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

// ViewModel - State holder
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getRecipesUseCase: GetRecipesUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}

// Composable Screen
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // UI implementation
}
```

### Domain Layer

Pure Kotlin layer with no Android dependencies:

```kotlin
// Use Case
class GetRecipesUseCase @Inject constructor(
    private val repository: RecipeRepository
) {
    operator fun invoke(): Flow<Result<List<Recipe>>> {
        return repository.getRecipes()
    }
}

// Domain Model
data class Recipe(
    val id: String,
    val name: String,
    val description: String,
    // ... other fields
)

// Repository Interface
interface RecipeRepository {
    fun getRecipes(): Flow<Result<List<Recipe>>>
}
```

### Data Layer

Implements repository interfaces and handles data operations:

```kotlin
@Singleton
class RecipeRepositoryImpl @Inject constructor(
    private val recipeDao: RecipeDao,
    private val recipesApi: RecipesApi
) : RecipeRepository {
    
    override fun getRecipes(): Flow<Result<List<Recipe>>> = flow {
        emit(Result.Loading)
        // Offline-first: emit from database first
        recipeDao.getAllRecipes().collect { entities ->
            emit(Result.Success(entities.map { it.toDomain() }))
        }
    }
}
```

## Data Flow

```
User Action → ViewModel → UseCase → Repository → DataSource
     ↓            ↓           ↓          ↓            ↓
   Event     Updates     Business    Data         Network/
             State       Logic       Operations   Database
```

## SOLID Principles Applied

### Single Responsibility
- Each class has one reason to change
- ViewModels handle only UI state
- UseCases contain only business logic

### Open/Closed
- Repository interfaces allow extension without modification
- New features added through new modules

### Liskov Substitution
- Implementations can replace interfaces
- FakeDataSource can replace real API for testing

### Interface Segregation
- Small, focused interfaces
- TokenProvider vs full AuthManager

### Dependency Inversion
- High-level modules don't depend on low-level modules
- Both depend on abstractions (interfaces)
