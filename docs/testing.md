# 🧪 Testing Strategy

## Overview

BakingApp follows the **Testing Pyramid**:

```
        ╱╲
       ╱  ╲
      ╱ UI ╲        (Few, Slow, Expensive)
     ╱──────╲
    ╱ Integ- ╲
   ╱  ration  ╲
  ╱────────────╲
 ╱    Unit      ╲   (Many, Fast, Cheap)
╱────────────────╲
```

## Test Types

### Unit Tests

Test individual components in isolation.

**Location:** `module/src/test/`

```kotlin
class ValidateEmailUseCaseTest {
    private lateinit var useCase: ValidateEmailUseCase
    
    @Before
    fun setup() {
        useCase = ValidateEmailUseCase()
    }
    
    @Test
    fun `valid email returns Valid result`() {
        val result = useCase("test@example.com")
        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }
    
    @Test
    fun `empty email returns Invalid result`() {
        val result = useCase("")
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }
}
```

### ViewModel Tests

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var viewModel: LoginViewModel
    private val loginUseCase = mockk<LoginUseCase>()
    
    @Before
    fun setup() {
        viewModel = LoginViewModel(loginUseCase, ValidateEmailUseCase(), ValidatePasswordUseCase())
    }
    
    @Test
    fun `successful login updates state to logged in`() = runTest {
        coEvery { loginUseCase(any(), any()) } returns Result.Success(mockLoginResult)
        
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("Password123")
        viewModel.onLoginClick()
        
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.isLoggedIn).isTrue()
    }
}
```

### Repository Tests

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeRepositoryTest {
    private lateinit var repository: RecipeRepositoryImpl
    private val recipeDao = mockk<RecipeDao>()
    private val fakeDataSource = FakeRecipeDataSource()
    
    @Before
    fun setup() {
        repository = RecipeRepositoryImpl(recipeDao, fakeDataSource)
    }
    
    @Test
    fun `getRecipes returns data from database`() = runTest {
        val recipes = listOf(createRecipeEntity())
        every { recipeDao.getAllRecipes() } returns flowOf(recipes)
        
        repository.getRecipes().test {
            assertThat(awaitItem()).isEqualTo(Result.Loading)
            val success = awaitItem() as Result.Success
            assertThat(success.data).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### Integration Tests

**Location:** `module/src/androidTest/`

```kotlin
@HiltAndroidTest
class RecipeDaoTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var database: BakingDatabase
    
    private lateinit var recipeDao: RecipeDao
    
    @Before
    fun setup() {
        hiltRule.inject()
        recipeDao = database.recipeDao()
    }
    
    @Test
    fun insertAndRetrieveRecipe() = runTest {
        val recipe = createTestRecipeEntity()
        recipeDao.insertRecipe(recipe)
        
        val retrieved = recipeDao.getRecipeById(recipe.id).first()
        assertThat(retrieved?.name).isEqualTo(recipe.name)
    }
}
```

### UI Tests

```kotlin
class LoginScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun loginButton_clickable_when_form_valid() {
        composeTestRule.setContent {
            BakingAppTheme {
                LoginScreen(onNavigateToHome = {})
            }
        }
        
        composeTestRule.onNodeWithText("Email").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("Password123")
        composeTestRule.onNodeWithText("Sign In").assertIsEnabled()
    }
}
```

## Testing Tools

| Tool | Purpose |
|------|---------|
| JUnit | Test framework |
| Truth | Assertions |
| Turbine | Flow testing |
| MockWebServer | API mocking |
| Mockito-Kotlin | Mocking |
| Hilt Testing | DI in tests |
| Compose Testing | UI tests |

## Test Utilities

### MainDispatcherRule

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }
    
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

### Test Doubles

```kotlin
// Fake Repository
class FakeRecipeRepository : RecipeRepository {
    private val recipes = mutableListOf<Recipe>()
    
    override fun getRecipes() = flowOf(Result.Success(recipes.toList()))
    
    fun addRecipe(recipe: Recipe) {
        recipes.add(recipe)
    }
}
```

## Coverage Goals

| Layer | Target Coverage |
|-------|-----------------|
| Domain (UseCases) | 90%+ |
| ViewModels | 80%+ |
| Repositories | 80%+ |
| UI Components | 60%+ |

## Running Tests

```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests
./gradlew connectedAndroidTest

# All tests with coverage
./gradlew testDebugUnitTest jacocoTestReport
```
