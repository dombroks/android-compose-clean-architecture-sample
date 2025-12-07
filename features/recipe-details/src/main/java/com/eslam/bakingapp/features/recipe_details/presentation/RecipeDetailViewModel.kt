package com.eslam.bakingapp.features.recipe_details.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eslam.bakingapp.core.common.result.Result
import com.eslam.bakingapp.features.home.domain.repository.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Recipe Detail screen.
 */
@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val recipeId: String = checkNotNull(savedStateHandle["recipeId"])
    
    private val _uiState = MutableStateFlow(RecipeDetailUiState())
    val uiState: StateFlow<RecipeDetailUiState> = _uiState.asStateFlow()
    
    init {
        loadRecipeDetails()
    }
    
    /**
     * Load recipe details by ID.
     */
    fun loadRecipeDetails() {
        viewModelScope.launch {
            recipeRepository.getRecipeById(recipeId).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    is Result.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                recipe = result.data,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                errorMessage = result.message ?: "Failed to load recipe"
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Select a tab.
     */
    fun onTabSelected(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
    }
    
    /**
     * Toggle favorite status.
     */
    fun onFavoriteClick() {
        val currentRecipe = _uiState.value.recipe ?: return
        val currentFavoriteState = currentRecipe.isFavorite
        
        // Optimistic update - update UI immediately
        _uiState.update { state ->
            state.copy(
                recipe = state.recipe?.copy(isFavorite = !currentFavoriteState)
            )
        }
        
        // Perform actual update in background
        viewModelScope.launch {
            val result = recipeRepository.toggleFavorite(recipeId)
            
            // If failed, revert the optimistic update
            if (result is Result.Error) {
                _uiState.update { state ->
                    state.copy(
                        recipe = state.recipe?.copy(isFavorite = currentFavoriteState)
                    )
                }
            }
        }
    }
}

