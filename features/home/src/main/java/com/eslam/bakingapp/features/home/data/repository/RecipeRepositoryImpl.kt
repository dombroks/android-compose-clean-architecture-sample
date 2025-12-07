package com.eslam.bakingapp.features.home.data.repository

import com.eslam.bakingapp.core.common.result.Result
import com.eslam.bakingapp.core.database.dao.RecipeDao
import com.eslam.bakingapp.features.home.data.datasource.FakeRecipeDataSource
import com.eslam.bakingapp.features.home.data.mapper.toDomain
import com.eslam.bakingapp.features.home.domain.model.Recipe
import com.eslam.bakingapp.features.home.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of RecipeRepository.
 * Implements offline-first strategy with Room caching.
 */
@Singleton
class RecipeRepositoryImpl @Inject constructor(
    private val recipeDao: RecipeDao,
    private val fakeDataSource: FakeRecipeDataSource
    // In production, inject: private val recipesApi: RecipesApi
) : RecipeRepository {
    
    override fun getRecipes(): Flow<Result<List<Recipe>>> = flow {
        emit(Result.Loading)
        
        // First, emit from local database (offline-first)
        recipeDao.getAllRecipes()
            .map { entities -> entities.map { it.toDomain() } }
            .collect { recipes ->
                if (recipes.isEmpty()) {
                    // If empty, load fake data
                    val fakeRecipes = fakeDataSource.getFakeRecipes()
                    // Save to database
                    recipeDao.insertRecipes(fakeRecipes.map { it.toEntity() })
                    emit(Result.Success(fakeRecipes))
                } else {
                    emit(Result.Success(recipes))
                }
            }
    }.catch { e ->
        emit(Result.Error(e as Exception))
    }
    
    override fun getRecipeById(id: String): Flow<Result<Recipe>> = flow {
        emit(Result.Loading)
        
        recipeDao.getRecipeWithDetails(id)
            .collect { recipeWithDetails ->
                if (recipeWithDetails != null) {
                    emit(Result.Success(recipeWithDetails.toDomain()))
                } else {
                    // Try to get from fake data
                    val fakeRecipe = fakeDataSource.getFakeRecipeById(id)
                    if (fakeRecipe != null) {
                        emit(Result.Success(fakeRecipe))
                    } else {
                        emit(Result.Error(NoSuchElementException("Recipe not found")))
                    }
                }
            }
    }.catch { e ->
        emit(Result.Error(e as Exception))
    }
    
    override fun searchRecipes(query: String): Flow<Result<List<Recipe>>> = flow {
        emit(Result.Loading)
        
        recipeDao.searchRecipes(query)
            .map { entities -> entities.map { it.toDomain() } }
            .collect { recipes ->
                emit(Result.Success(recipes))
            }
    }.catch { e ->
        emit(Result.Error(e as Exception))
    }
    
    override fun getRecipesByCategory(category: String): Flow<Result<List<Recipe>>> = flow {
        emit(Result.Loading)
        
        recipeDao.getRecipesByCategory(category)
            .map { entities -> entities.map { it.toDomain() } }
            .collect { recipes ->
                emit(Result.Success(recipes))
            }
    }.catch { e ->
        emit(Result.Error(e as Exception))
    }
    
    override fun getFavoriteRecipes(): Flow<Result<List<Recipe>>> = flow {
        emit(Result.Loading)
        
        recipeDao.getFavoriteRecipes()
            .map { entities -> entities.map { it.toDomain() } }
            .collect { recipes ->
                emit(Result.Success(recipes))
            }
    }.catch { e ->
        emit(Result.Error(e as Exception))
    }
    
    override suspend fun toggleFavorite(recipeId: String): Result<Unit> {
        return try {
            val entity = recipeDao.getRecipeById(recipeId).firstOrNull()
            if (entity != null) {
                recipeDao.updateFavoriteStatus(recipeId, !entity.isFavorite)
                Result.Success(Unit)
            } else {
                Result.Error(NoSuchElementException("Recipe not found"), "Recipe not found")
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun refreshRecipes(): Result<Unit> {
        return try {
            // In production, fetch from API and update database
            // For now, use fake data
            val fakeRecipes = fakeDataSource.getFakeRecipes()
            recipeDao.insertRecipes(fakeRecipes.map { it.toEntity() })
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    // Extension function for mapping to entity
    private fun Recipe.toEntity() = com.eslam.bakingapp.core.database.entity.RecipeEntity(
        id = id,
        name = name,
        description = description,
        imageUrl = imageUrl,
        servings = servings,
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = cookTimeMinutes,
        difficulty = difficulty.toDisplayString(),
        category = category,
        isFavorite = isFavorite
    )
}

