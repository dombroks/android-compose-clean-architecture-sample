package com.eslam.bakingapp.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object for Recipe from API
 */
@JsonClass(generateAdapter = true)
data class RecipeDto(
    @Json(name = "id")
    val id: String,
    
    @Json(name = "name")
    val name: String,
    
    @Json(name = "description")
    val description: String,
    
    @Json(name = "image_url")
    val imageUrl: String?,
    
    @Json(name = "servings")
    val servings: Int,
    
    @Json(name = "prep_time_minutes")
    val prepTimeMinutes: Int,
    
    @Json(name = "cook_time_minutes")
    val cookTimeMinutes: Int,
    
    @Json(name = "difficulty")
    val difficulty: String,
    
    @Json(name = "category")
    val category: String,
    
    @Json(name = "ingredients")
    val ingredients: List<IngredientDto>,
    
    @Json(name = "steps")
    val steps: List<StepDto>,
    
    @Json(name = "created_at")
    val createdAt: String?,
    
    @Json(name = "updated_at")
    val updatedAt: String?
)

@JsonClass(generateAdapter = true)
data class IngredientDto(
    @Json(name = "id")
    val id: String,
    
    @Json(name = "name")
    val name: String,
    
    @Json(name = "quantity")
    val quantity: Double,
    
    @Json(name = "unit")
    val unit: String
)

@JsonClass(generateAdapter = true)
data class StepDto(
    @Json(name = "id")
    val id: String,
    
    @Json(name = "order")
    val order: Int,
    
    @Json(name = "description")
    val description: String,
    
    @Json(name = "video_url")
    val videoUrl: String?,
    
    @Json(name = "thumbnail_url")
    val thumbnailUrl: String?
)

@JsonClass(generateAdapter = true)
data class RecipeListResponse(
    @Json(name = "recipes")
    val recipes: List<RecipeDto>,
    
    @Json(name = "total_count")
    val totalCount: Int,
    
    @Json(name = "page")
    val page: Int,
    
    @Json(name = "total_pages")
    val totalPages: Int
)

