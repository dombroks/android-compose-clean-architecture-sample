package com.eslam.bakingapp.features.recipe_details.presentation

import androidx.lifecycle.SavedStateHandle
import com.eslam.bakingapp.core.common.testing.MainDispatcherRule
import com.eslam.bakingapp.core.common.result.Result
import com.eslam.bakingapp.features.home.domain.model.Difficulty
import com.eslam.bakingapp.features.home.domain.model.Ingredient
import com.eslam.bakingapp.features.home.domain.model.Recipe
import com.eslam.bakingapp.features.home.domain.model.Step
import com.eslam.bakingapp.features.home.domain.repository.RecipeRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModelTest {
    
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var viewModel: RecipeDetailViewModel
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var savedStateHandle: SavedStateHandle
    
    private val testRecipe = Recipe(
        id = "test-recipe-1",
        name = "Test Recipe",
        description = "A delicious test recipe",
        imageUrl = "https://example.com/image.jpg",
        servings = 4,
        prepTimeMinutes = 15,
        cookTimeMinutes = 30,
        difficulty = Difficulty.MEDIUM,
        category = "Test",
        isFavorite = false,
        ingredients = listOf(
            Ingredient("1", "Flour", 2.0, "cups"),
            Ingredient("2", "Sugar", 1.0, "cup")
        ),
        steps = listOf(
            Step("1", 1, "Mix dry ingredients", null, null),
            Step("2", 2, "Add wet ingredients", null, null),
            Step("3", 3, "Bake at 350°F", null, null)
        )
    )
    
    @Before
    fun setup() {
        recipeRepository = mock()
        savedStateHandle = SavedStateHandle(mapOf("recipeId" to "test-recipe-1"))
    }
    
    @Test
    fun `initial state triggers loadRecipeDetails`() = runTest {
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow {
                emit(Result.Loading)
                emit(Result.Success(testRecipe))
            }
        )
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.recipe).isNotNull()
        assertThat(state.recipe?.id).isEqualTo("test-recipe-1")
        assertThat(state.isLoading).isFalse()
    }
    
    @Test
    fun `loadRecipeDetails sets loading state`() = runTest {
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow {
                emit(Result.Loading)
            }
        )
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        
        // During loading
        assertThat(viewModel.uiState.value.isLoading).isTrue()
    }
    
    @Test
    fun `loadRecipeDetails updates state with recipe data`() = runTest {
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow {
                emit(Result.Loading)
                emit(Result.Success(testRecipe))
            }
        )
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.recipe?.name).isEqualTo("Test Recipe")
        assertThat(state.recipe?.description).isEqualTo("A delicious test recipe")
        assertThat(state.recipe?.ingredients).hasSize(2)
        assertThat(state.recipe?.steps).hasSize(3)
        assertThat(state.errorMessage).isNull()
    }
    
    @Test
    fun `loadRecipeDetails shows error on failure`() = runTest {
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow {
                emit(Result.Loading)
                emit(Result.Error(Exception("Recipe not found"), "Recipe not found"))
            }
        )
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.recipe).isNull()
        assertThat(state.errorMessage).isEqualTo("Recipe not found")
        assertThat(state.isLoading).isFalse()
    }
    
    @Test
    fun `onTabSelected updates selectedTabIndex`() = runTest {
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow { emit(Result.Success(testRecipe)) }
        )
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.selectedTabIndex).isEqualTo(0)
        
        viewModel.onTabSelected(1)
        
        assertThat(viewModel.uiState.value.selectedTabIndex).isEqualTo(1)
    }
    
    @Test
    fun `onTabSelected with same index does not change state`() = runTest {
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow { emit(Result.Success(testRecipe)) }
        )
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        advanceUntilIdle()
        
        viewModel.onTabSelected(0)
        
        assertThat(viewModel.uiState.value.selectedTabIndex).isEqualTo(0)
    }
    
    @Test
    fun `onFavoriteClick toggles favorite status`() = runTest {
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow { emit(Result.Success(testRecipe)) }
        )
        whenever(recipeRepository.toggleFavorite(any())).thenReturn(Result.Success(Unit))
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.recipe?.isFavorite).isFalse()
        
        viewModel.onFavoriteClick()
        
        assertThat(viewModel.uiState.value.recipe?.isFavorite).isTrue()
    }
    
    @Test
    fun `onFavoriteClick toggles favorite from true to false`() = runTest {
        val favoriteRecipe = testRecipe.copy(isFavorite = true)
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow { emit(Result.Success(favoriteRecipe)) }
        )
        whenever(recipeRepository.toggleFavorite(any())).thenReturn(Result.Success(Unit))
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.recipe?.isFavorite).isTrue()
        
        viewModel.onFavoriteClick()
        
        assertThat(viewModel.uiState.value.recipe?.isFavorite).isFalse()
    }
    
    @Test
    fun `hasError returns true when errorMessage is not null`() = runTest {
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow {
                emit(Result.Loading)
                emit(Result.Error(Exception("Error"), "Error message"))
            }
        )
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.hasError).isTrue()
    }
    
    @Test
    fun `hasError returns false when loading`() = runTest {
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow { emit(Result.Loading) }
        )
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        
        // While loading, hasError should be false even with no data
        assertThat(viewModel.uiState.value.hasError).isFalse()
    }
    
    @Test
    fun `recipe totalTimeMinutes is calculated correctly`() = runTest {
        whenever(recipeRepository.getRecipeById(any())).thenReturn(
            flow { emit(Result.Success(testRecipe)) }
        )
        
        viewModel = RecipeDetailViewModel(recipeRepository, savedStateHandle)
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.recipe?.totalTimeMinutes).isEqualTo(45)
    }
}


