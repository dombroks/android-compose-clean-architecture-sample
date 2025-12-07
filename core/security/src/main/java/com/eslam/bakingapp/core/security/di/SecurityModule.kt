package com.eslam.bakingapp.core.security.di

import com.eslam.bakingapp.core.network.interceptor.TokenProvider
import com.eslam.bakingapp.core.security.SecureTokenManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing security-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    
    @Binds
    @Singleton
    abstract fun bindTokenProvider(
        secureTokenManager: SecureTokenManager
    ): TokenProvider
}



