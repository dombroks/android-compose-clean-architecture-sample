package com.eslam.bakingapp.features.home.presentation

import com.eslam.bakingapp.core.common.testing.MainDispatcherRule
import com.eslam.bakingapp.features.home.data.repository.FakeRecipeRepository
import com.eslam.bakingapp.features.home.domain.usecase.GetRecipesUseCase
import com.eslam.bakingapp.features.home.domain.usecase.SearchRecipesUseCase
import com.eslam.bakingapp.features.home.domain.usecase.ToggleFavoriteUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var viewModel: HomeViewModel
    private lateinit var fakeRepository: FakeRecipeRepository
    private lateinit var getRecipesUseCase: GetRecipesUseCase
    private lateinit var searchRecipesUseCase: SearchRecipesUseCase
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    
    @Before
    fun setup() {
        fakeRepository = FakeRecipeRepository()
        getRecipesUseCase = GetRecipesUseCase(fakeRepository)
        searchRecipesUseCase = SearchRecipesUseCase(fakeRepository)
        toggleFavoriteUseCase = ToggleFavoriteUseCase(fakeRepository)
        
        viewModel = HomeViewModel(
            getRecipesUseCase = getRecipesUseCase,
            searchRecipesUseCase = searchRecipesUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase
        )
    }
    
    @Test
    fun `initial state loads recipes`() = runTest {
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.recipes).hasSize(3)
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
    }
    
    @Test
    fun `loadRecipes updates state with recipes`() = runTest {
        viewModel.loadRecipes()
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.recipes).isNotEmpty()
        assertThat(state.isLoading).isFalse()
    }
    
    @Test
    fun `loadRecipes shows error when repository fails`() = runTest {
        fakeRepository.shouldReturnError = true
        fakeRepository.errorMessage = "Network error"
        
        // Create new ViewModel to trigger init load with error
        viewModel = HomeViewModel(
            getRecipesUseCase = getRecipesUseCase,
            searchRecipesUseCase = searchRecipesUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase
        )
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.errorMessage).isEqualTo("Network error")
        assertThat(state.isLoading).isFalse()
    }
    
    @Test
    fun `refreshRecipes sets isRefreshing to true initially`() = runTest {
        advanceUntilIdle()
        
        viewModel.refreshRecipes()
        
        // isRefreshing should be true before data loads
        assertThat(viewModel.uiState.value.isRefreshing).isTrue()
        
        advanceUntilIdle()
        
        // After loading completes, isRefreshing should be false
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
    }
    
    @Test
    fun `onSearchQueryChange updates search query in state`() = runTest {
        advanceUntilIdle()
        
        viewModel.onSearchQueryChange("Chocolate")
        
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("Chocolate")
    }
    
    @Test
    fun `onSearchQueryChange filters recipes`() = runTest {
        advanceUntilIdle()
        
        viewModel.onSearchQueryChange("Chocolate")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.recipes).hasSize(1)
        assertThat(state.recipes.first().name).contains("Chocolate")
    }
    
    @Test
    fun `onSearchQueryChange with blank query loads all recipes`() = runTest {
        advanceUntilIdle()
        
        // First filter
        viewModel.onSearchQueryChange("Chocolate")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.recipes).hasSize(1)
        
        // Then clear filter
        viewModel.onSearchQueryChange("")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.recipes).hasSize(3)
    }
    
    @Test
    fun `onCategorySelected filters recipes by category`() = runTest {
        advanceUntilIdle()
        
        viewModel.onCategorySelected("Cookies")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.selectedCategory).isEqualTo("Cookies")
        assertThat(state.recipes.all { it.category == "Cookies" }).isTrue()
    }
    
    @Test
    fun `onCategorySelected with All loads all recipes`() = runTest {
        advanceUntilIdle()
        
        // First filter by category
        viewModel.onCategorySelected("Cookies")
        advanceUntilIdle()
        
        // Then select All
        viewModel.onCategorySelected("All")
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.selectedCategory).isNull()
        assertThat(viewModel.uiState.value.recipes).hasSize(3)
    }
    
    @Test
    fun `onCategorySelected with null loads all recipes`() = runTest {
        advanceUntilIdle()
        
        viewModel.onCategorySelected(null)
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.selectedCategory).isNull()
        assertThat(viewModel.uiState.value.recipes).hasSize(3)
    }
    
    @Test
    fun `onFavoriteClick toggles favorite status optimistically`() = runTest {
        advanceUntilIdle()
        
        val recipeId = "1"
        val initialFavorite = viewModel.uiState.value.recipes.first { it.id == recipeId }.isFavorite
        
        viewModel.onFavoriteClick(recipeId)
        
        // Check optimistic update
        val updatedFavorite = viewModel.uiState.value.recipes.first { it.id == recipeId }.isFavorite
        assertThat(updatedFavorite).isEqualTo(!initialFavorite)
    }
    
    @Test
    fun `onFavoriteClick reverts on error`() = runTest {
        advanceUntilIdle()
        
        fakeRepository.shouldReturnError = true
        
        val recipeId = "1"
        val initialFavorite = viewModel.uiState.value.recipes.first { it.id == recipeId }.isFavorite
        
        viewModel.onFavoriteClick(recipeId)
        advanceUntilIdle()
        
        // Should revert to original state on error
        val finalFavorite = viewModel.uiState.value.recipes.first { it.id == recipeId }.isFavorite
        assertThat(finalFavorite).isEqualTo(initialFavorite)
    }
    
    @Test
    fun `clearError removes error message`() = runTest {
        fakeRepository.shouldReturnError = true
        fakeRepository.errorMessage = "Test error"
        
        viewModel = HomeViewModel(
            getRecipesUseCase = getRecipesUseCase,
            searchRecipesUseCase = searchRecipesUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase
        )
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.errorMessage).isNotNull()
        
        viewModel.clearError()
        
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }
    
    @Test
    fun `isEmpty returns true when no recipes and not loading`() = runTest {
        fakeRepository.clearRecipes()
        fakeRepository.shouldReturnError = false
        
        viewModel = HomeViewModel(
            getRecipesUseCase = getRecipesUseCase,
            searchRecipesUseCase = searchRecipesUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase
        )
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.isEmpty).isTrue()
    }
    
    @Test
    fun `hasError returns true when error exists and not loading`() = runTest {
        fakeRepository.shouldReturnError = true
        
        viewModel = HomeViewModel(
            getRecipesUseCase = getRecipesUseCase,
            searchRecipesUseCase = searchRecipesUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase
        )
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.hasError).isTrue()
    }
}


