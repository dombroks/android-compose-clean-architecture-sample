# 🔍 Pull Request Review Guide

## Overview

Code review is one of the most important quality gates in software development. A well-conducted PR review catches bugs, improves code quality, shares knowledge, and maintains consistency across the codebase.

This guide provides a comprehensive framework for conducting effective pull request reviews in our Android/Kotlin codebase.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        PR REVIEW WORKFLOW                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   PR Created → Context Check → Code Review → Testing → Feedback → Merge │
│       │              │              │           │          │         │   │
│       ▼              ▼              ▼           ▼          ▼         ▼   │
│    Author       Understand      Review     Verify    Approve/    CI/CD  │
│    Submits      the "Why"       Changes    Works     Request     Runs   │
│                                                      Changes            │
└─────────────────────────────────────────────────────────────────────────┘
```

## The 7-Step PR Review Framework

### Quick Reference Table

| Step | Focus Area | Key Questions |
|------|------------|---------------|
| **1. Context** | Purpose & Background | Why is this change needed? |
| **2. Correctness** | Technical Accuracy | Does it work correctly? |
| **3. Completeness** | Full Implementation | Are all affected areas updated? |
| **4. Compatibility** | Breaking Changes | Does it break existing code? |
| **5. Consistency** | Code Style | Does it follow conventions? |
| **6. Clarity** | Readability | Is it understandable? |
| **7. Considerations** | Edge Cases | What about performance, security? |

---

## Step 1: Context Analysis 📋

**Goal:** Understand the purpose before reading code.

### Checklist

- [ ] Read the PR title and description thoroughly
- [ ] Understand the problem being solved
- [ ] Review linked issues, tickets, or documentation
- [ ] Check if this is a bug fix, feature, refactor, or dependency update
- [ ] Verify the scope is appropriate (not too large, not too small)

### Questions to Ask

```
✓ What problem does this PR solve?
✓ Why was this approach chosen over alternatives?
✓ Is there sufficient context for reviewers?
✓ Does the PR description explain the "why"?
```

### Red Flags 🚩

- PR with no description
- Very large PRs (500+ lines without justification)
- Scope creep (mixing unrelated changes)
- Missing ticket/issue reference

### Example: Good PR Description

```markdown
## Summary
Add NDK version configuration for Android 15 compatibility

## Problem
Android 15 requires 16KB page size alignment for native libraries.
Without this change, the app will crash on Android 15+ devices.

## Solution
- Set NDK version to 29.0.14206865 in app/build.gradle.kts
- Add linker option for 16KB page alignment in CMakeLists.txt

## Testing
- Tested on Android 15 emulator
- Verified native library loads successfully
- All existing tests pass

## Related
- Fixes #123
- Docs: https://developer.android.com/guide/practices/page-sizes
```

---

## Step 2: Correctness Analysis ✅

**Goal:** Verify the implementation is technically correct.

### Checklist

- [ ] Logic is correct and handles expected use cases
- [ ] Edge cases are handled appropriately
- [ ] Error handling is present and correct
- [ ] Null safety is properly managed (no unnecessary `!!`)
- [ ] Threading is correct (main vs background thread)
- [ ] Memory management is proper (no leaks)

### Android/Kotlin Specific Checks

```kotlin
// ❌ BAD: Force unwrap can cause crashes
val user = repository.getUser()!!

// ✅ GOOD: Safe handling
val user = repository.getUser() ?: return

// ❌ BAD: Blocking main thread
fun loadData() {
    val data = networkCall() // Blocks UI!
}

// ✅ GOOD: Proper coroutine usage
suspend fun loadData() = withContext(Dispatchers.IO) {
    networkCall()
}
```

### Common Correctness Issues

| Issue | Example | Solution |
|-------|---------|----------|
| Race conditions | Concurrent state updates | Use `Mutex` or single-threaded dispatcher |
| Memory leaks | Holding Activity reference | Use `WeakReference` or scoped coroutines |
| Incorrect scope | `GlobalScope.launch` | Use `viewModelScope` or lifecycle-aware scope |
| State loss | Data lost on configuration change | Use `ViewModel` + `SavedStateHandle` |

---

## Step 3: Completeness Analysis 🧩

**Goal:** Ensure all necessary changes are included.

### Checklist

- [ ] All affected files are modified
- [ ] Tests are added/updated for new functionality
- [ ] Documentation is updated if needed
- [ ] Migration scripts provided if needed (database changes)
- [ ] Related modules updated consistently
- [ ] No TODO comments left without tracking issue

### Multi-Module Project Considerations

```
When changing a core module, check:
├── core/network    → Did API interfaces change?
├── core/database   → Did entities/DAOs change?
├── core/common     → Did shared utilities change?
├── features/*      → Are feature modules updated?
└── app             → Is app module configuration updated?
```

### Example: Incomplete PR

```kotlin
// PR adds new field to API response but forgets:
// ❌ Missing: Database entity update
// ❌ Missing: Room migration
// ❌ Missing: Domain model update
// ❌ Missing: UI layer handling

data class RecipeDto(
    val id: String,
    val name: String,
    val calories: Int  // NEW FIELD - where else needs updating?
)
```

---

## Step 4: Compatibility Analysis 🔄

**Goal:** Verify no breaking changes are introduced.

### Checklist

- [ ] Public APIs remain backward compatible
- [ ] Database schema changes include migrations
- [ ] Shared preferences changes handle old data
- [ ] Feature flags used for gradual rollout (if needed)
- [ ] minSdk/targetSdk implications considered

### Breaking Change Categories

| Type | Impact | Mitigation |
|------|--------|------------|
| API signature change | Compile-time error | Deprecate old, add new |
| Database schema change | Runtime crash | Room migration |
| Behavior change | Logic errors | Feature flag |
| Dependency update | Various | Test thoroughly |

### Database Migration Example

```kotlin
// ✅ GOOD: Proper migration
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE recipes ADD COLUMN calories INTEGER DEFAULT 0 NOT NULL"
        )
    }
}

// ❌ BAD: Destructive migration
fallbackToDestructiveMigration() // Loses user data!
```

---

## Step 5: Consistency Analysis 📐

**Goal:** Ensure code follows project conventions.

### Project Conventions Checklist

- [ ] Naming conventions followed
- [ ] Package structure maintained
- [ ] Code formatting applied (ktlint/detekt)
- [ ] Architectural patterns followed (MVI, Clean Architecture)
- [ ] Dependency injection patterns consistent

### Naming Conventions

```kotlin
// Files & Classes
HomeViewModel.kt        // ViewModel suffix
RecipeRepository.kt     // Interface, no Impl suffix
RecipeRepositoryImpl.kt // Implementation with Impl suffix
GetRecipesUseCase.kt    // UseCase suffix

// Functions
suspend fun fetchRecipes()     // Suspend for coroutines
fun validateEmail()            // Verb for actions
fun isEmailValid(): Boolean    // is/has for boolean returns

// State
data class HomeUiState(...)    // UiState suffix
sealed class HomeEvent(...)    // Event suffix for user actions
```

### Architecture Consistency

```kotlin
// ✅ GOOD: Follows Clean Architecture
class HomeViewModel @Inject constructor(
    private val getRecipesUseCase: GetRecipesUseCase  // UseCase, not Repository
) : ViewModel()

// ❌ BAD: Bypasses domain layer
class HomeViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,   // Direct repo access
    private val recipeApi: RecipeApi                  // Direct API access!
) : ViewModel()
```

---

## Step 6: Clarity Analysis 📖

**Goal:** Code should be self-documenting and easy to understand.

### Checklist

- [ ] Variable/function names are descriptive
- [ ] Complex logic has explanatory comments
- [ ] Public APIs have KDoc documentation
- [ ] Magic numbers are replaced with constants
- [ ] Code intent is clear without reading implementation

### Documentation Standards

```kotlin
/**
 * Validates user credentials and initiates login flow.
 *
 * @param email User's email address
 * @param password User's password (plain text, will be hashed)
 * @return [Result] containing [LoginResult] on success or [AuthError] on failure
 * @throws NetworkException if network is unavailable
 *
 * @see LoginResult
 * @see AuthError
 */
suspend fun login(email: String, password: String): Result<LoginResult>
```

### Self-Documenting Code

```kotlin
// ❌ BAD: Magic numbers and unclear intent
if (password.length >= 8 && password.any { it.isDigit() }) { ... }

// ✅ GOOD: Clear intent through naming
private const val MIN_PASSWORD_LENGTH = 8

fun isPasswordStrong(password: String): Boolean {
    val hasMinimumLength = password.length >= MIN_PASSWORD_LENGTH
    val containsDigit = password.any { it.isDigit() }
    val containsUppercase = password.any { it.isUpperCase() }
    
    return hasMinimumLength && containsDigit && containsUppercase
}
```

---

## Step 7: Considerations Analysis 💭

**Goal:** Think about non-functional requirements and edge cases.

### Performance Checklist

- [ ] No unnecessary object allocations in loops
- [ ] Database queries are optimized (indexed columns)
- [ ] Images are properly sized/cached
- [ ] Network calls are batched when possible
- [ ] Compose recomposition is minimized

### Security Checklist

- [ ] Sensitive data not logged
- [ ] API keys not hardcoded
- [ ] User input is validated/sanitized
- [ ] HTTPS enforced for network calls
- [ ] Proper authentication token handling

### Compose Performance

```kotlin
// ❌ BAD: Creates new lambda every recomposition
@Composable
fun RecipeList(recipes: List<Recipe>) {
    LazyColumn {
        items(recipes) { recipe ->
            RecipeItem(
                recipe = recipe,
                onClick = { viewModel.onRecipeClick(recipe.id) }  // New lambda!
            )
        }
    }
}

// ✅ GOOD: Stable reference
@Composable
fun RecipeList(
    recipes: List<Recipe>,
    onRecipeClick: (String) -> Unit  // Hoisted
) {
    LazyColumn {
        items(
            items = recipes,
            key = { it.id }  // Stable key!
        ) { recipe ->
            RecipeItem(
                recipe = recipe,
                onClick = { onRecipeClick(recipe.id) }
            )
        }
    }
}
```

---

## Providing Feedback

### The Right Tone

```
✅ DO:
- "Consider using X because..."
- "Have you thought about...?"
- "This works, but we could improve it by..."
- "Great solution! One small suggestion..."

❌ DON'T:
- "This is wrong."
- "Why didn't you do X?"
- "This doesn't make sense."
- "I would never do it this way."
```

### Feedback Categories

Use prefixes to indicate severity:

| Prefix | Meaning | Required? |
|--------|---------|-----------|
| `[Blocker]` | Must fix before merge | Yes |
| `[Major]` | Should fix, significant issue | Usually |
| `[Minor]` | Nice to fix, small improvement | No |
| `[Nit]` | Nitpick, personal preference | No |
| `[Question]` | Seeking clarification | N/A |
| `[Praise]` | Calling out good work! | N/A |

### Example Review Comments

```markdown
[Blocker] This will cause a crash on null input:
`val user = users.first()` should be `users.firstOrNull()`

[Major] Consider using `remember { }` here to avoid creating 
a new object on every recomposition.

[Minor] Could extract this to a constant for clarity:
`const val MAX_RETRY_COUNT = 3`

[Nit] Personal preference, but I find `when` more readable 
than chained `if-else` here.

[Question] Why is this using `runBlocking` instead of 
`viewModelScope.launch`?

[Praise] Really clean solution! Love how you decomposed 
this into smaller functions. 👏
```

---

## PR Review Checklist Template

Copy this template for your reviews:

```markdown
## PR Review: [PR Title]

### Context ✓
- [ ] Purpose is clear
- [ ] Scope is appropriate
- [ ] Description is sufficient

### Correctness ✓
- [ ] Logic is correct
- [ ] Edge cases handled
- [ ] Error handling present
- [ ] Thread safety correct

### Completeness ✓
- [ ] All files updated
- [ ] Tests included
- [ ] Documentation updated
- [ ] Migrations provided (if needed)

### Compatibility ✓
- [ ] No breaking changes
- [ ] Backward compatible
- [ ] Database migrations included

### Consistency ✓
- [ ] Naming conventions
- [ ] Architecture patterns
- [ ] Code formatting

### Clarity ✓
- [ ] Self-documenting code
- [ ] Comments for complexity
- [ ] Public API documented

### Considerations ✓
- [ ] Performance OK
- [ ] Security OK
- [ ] Accessibility OK

### Verdict
- [ ] ✅ Approved
- [ ] 🔄 Request Changes
- [ ] 💬 Comment Only
```

---

## Android-Specific Review Points

### Gradle/Build Configuration

```kotlin
// Version Management: Check if using version catalog
// ❌ Hardcoded version
implementation("com.squareup.retrofit2:retrofit:2.9.0")

// ✅ Version catalog reference
implementation(libs.retrofit)

// NDK Version: Should be explicit for native modules
android {
    ndkVersion = libs.versions.ndk.get()  // Good
}
```

### Jetpack Compose

| Check | Why |
|-------|-----|
| `remember` usage | Prevents unnecessary recomposition |
| `derivedStateOf` for computed values | Efficient state derivation |
| `collectAsStateWithLifecycle()` | Lifecycle-aware collection |
| Stable/Immutable annotations | Compose compiler optimizations |
| LazyColumn keys | Efficient diff/reorder |

### Hilt/Dependency Injection

```kotlin
// Check scoping is correct
@Singleton           // App-level singleton
@ActivityScoped      // Activity lifecycle
@ViewModelScoped     // ViewModel lifecycle
@ActivityRetainedScoped  // Survives configuration changes

// Check bindings are in correct modules
@Module
@InstallIn(SingletonComponent::class)  // Match scope with @InstallIn
abstract class NetworkModule { ... }
```

---

## Common PR Anti-Patterns

### 1. The Monster PR

```
❌ PR with 50+ files, 2000+ lines
→ Solution: Break into smaller, focused PRs
```

### 2. The Sneaky Refactor

```
❌ Bug fix PR that also refactors unrelated code
→ Solution: Separate refactoring into its own PR
```

### 3. The Copy-Paste

```
❌ Duplicated code instead of abstraction
→ Solution: Create shared utility or component
```

### 4. The Missing Tests

```
❌ New feature with no test coverage
→ Solution: Add unit tests for business logic
```

### 5. The Silent Dependency Update

```
❌ Updating dependencies without changelog review
→ Solution: Document breaking changes and migration
```

---

## Automated Checks

Before manual review, ensure these pass:

```bash
# Linting
./gradlew ktlintCheck

# Static Analysis
./gradlew detekt

# Unit Tests
./gradlew testDebugUnitTest

# Build
./gradlew assembleDebug
```

### CI Pipeline Requirements

| Check | Tool | Required |
|-------|------|----------|
| Lint | ktlint | ✅ |
| Static Analysis | Detekt | ✅ |
| Unit Tests | JUnit | ✅ |
| Build | Gradle | ✅ |
| Coverage | JaCoCo | Recommended |
| UI Tests | Espresso/Compose | On critical paths |

---

## Review Turnaround Guidelines

| PR Size | Expected Review Time |
|---------|---------------------|
| XS (< 50 lines) | Same day |
| S (50-200 lines) | 1 business day |
| M (200-500 lines) | 2 business days |
| L (500+ lines) | Consider splitting |

---

## Summary

Effective code review is a skill that improves with practice. Remember:

1. **Be thorough but efficient** — Focus on what matters most
2. **Be kind but honest** — Critique code, not people
3. **Be curious** — Ask questions to understand intent
4. **Be educational** — Share knowledge through reviews
5. **Be responsive** — Review PRs promptly

> "A good code review is a conversation, not an inspection."

---

## Related Documentation

- [Architecture Overview](./architecture.md)
- [Testing Strategy](./testing.md)
- [Compose Guidelines](./compose_guidelines.md)
- [Security Best Practices](./security.md)

