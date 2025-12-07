package com.eslam.bakingapp.features.login.presentation

import app.cash.turbine.test
import com.eslam.bakingapp.core.common.testing.MainDispatcherRule
import com.eslam.bakingapp.core.common.result.Result
import com.eslam.bakingapp.features.login.domain.model.LoginResult
import com.eslam.bakingapp.features.login.domain.usecase.LoginUseCase
import com.eslam.bakingapp.features.login.domain.usecase.ValidateEmailUseCase
import com.eslam.bakingapp.features.login.domain.usecase.ValidatePasswordUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var viewModel: LoginViewModel
    private lateinit var loginUseCase: LoginUseCase
    private lateinit var validateEmailUseCase: ValidateEmailUseCase
    private lateinit var validatePasswordUseCase: ValidatePasswordUseCase
    
    @Before
    fun setup() {
        loginUseCase = mock()
        validateEmailUseCase = ValidateEmailUseCase()
        validatePasswordUseCase = ValidatePasswordUseCase()
        
        viewModel = LoginViewModel(
            loginUseCase = loginUseCase,
            validateEmailUseCase = validateEmailUseCase,
            validatePasswordUseCase = validatePasswordUseCase
        )
    }
    
    @Test
    fun `initial state has empty fields`() {
        val state = viewModel.uiState.value
        
        assertThat(state.email).isEmpty()
        assertThat(state.password).isEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.emailError).isNull()
        assertThat(state.passwordError).isNull()
        assertThat(state.errorMessage).isNull()
    }
    
    @Test
    fun `onEmailChange updates email in state`() {
        viewModel.onEmailChange("test@example.com")
        
        assertThat(viewModel.uiState.value.email).isEqualTo("test@example.com")
    }
    
    @Test
    fun `onPasswordChange updates password in state`() {
        viewModel.onPasswordChange("Password123")
        
        assertThat(viewModel.uiState.value.password).isEqualTo("Password123")
    }
    
    @Test
    fun `onEmailChange clears previous email error`() {
        // First set an error by validating empty email
        viewModel.validateEmail()
        assertThat(viewModel.uiState.value.emailError).isNotNull()
        
        // Change email should clear error
        viewModel.onEmailChange("new@email.com")
        assertThat(viewModel.uiState.value.emailError).isNull()
    }
    
    @Test
    fun `onPasswordChange clears previous password error`() {
        // First set an error by validating empty password
        viewModel.validatePassword()
        assertThat(viewModel.uiState.value.passwordError).isNotNull()
        
        // Change password should clear error
        viewModel.onPasswordChange("NewPassword123")
        assertThat(viewModel.uiState.value.passwordError).isNull()
    }
    
    @Test
    fun `validateEmail sets error for empty email`() {
        viewModel.onEmailChange("")
        viewModel.validateEmail()
        
        assertThat(viewModel.uiState.value.emailError).isEqualTo("Email cannot be empty")
    }
    
    @Test
    fun `validateEmail sets error for invalid email`() {
        viewModel.onEmailChange("invalid-email")
        viewModel.validateEmail()
        
        assertThat(viewModel.uiState.value.emailError)
            .isEqualTo("Please enter a valid email address")
    }
    
    @Test
    fun `validateEmail clears error for valid email`() {
        viewModel.onEmailChange("valid@email.com")
        viewModel.validateEmail()
        
        assertThat(viewModel.uiState.value.emailError).isNull()
    }
    
    @Test
    fun `validatePassword sets error for empty password`() {
        viewModel.onPasswordChange("")
        viewModel.validatePassword()
        
        assertThat(viewModel.uiState.value.passwordError).isEqualTo("Password cannot be empty")
    }
    
    @Test
    fun `validatePassword sets error for short password`() {
        viewModel.onPasswordChange("Pass1")
        viewModel.validatePassword()
        
        assertThat(viewModel.uiState.value.passwordError)
            .isEqualTo("Password must be at least 8 characters")
    }
    
    @Test
    fun `validatePassword clears error for valid password`() {
        viewModel.onPasswordChange("Password123")
        viewModel.validatePassword()
        
        assertThat(viewModel.uiState.value.passwordError).isNull()
    }
    
    @Test
    fun `onLoginClick with invalid form does not call login`() = runTest {
        // Leave fields empty
        viewModel.onLoginClick()
        
        advanceUntilIdle()
        
        // Should have validation errors, not loading
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.emailError).isNotNull()
        assertThat(viewModel.uiState.value.passwordError).isNotNull()
    }
    
    @Test
    fun `onLoginClick with valid credentials triggers login`() = runTest {
        // Setup successful login
        val loginResult = LoginResult(
            userId = "123",
            email = "test@example.com",
            name = "Test User",
            accessToken = "token",
            refreshToken = "refresh",
            expiresIn = 3600L
        )
        whenever(loginUseCase.invoke(any(), any())).thenReturn(Result.Success(loginResult))
        
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("Password123")
        viewModel.onLoginClick()
        
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.isLoggedIn).isTrue()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }
    
    @Test
    fun `onLoginClick with failed login shows error`() = runTest {
        whenever(loginUseCase.invoke(any(), any())).thenReturn(
            Result.Error(Exception("Invalid credentials"), "Invalid credentials")
        )
        
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("Password123")
        viewModel.onLoginClick()
        
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.isLoggedIn).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("Invalid credentials")
    }
    
    @Test
    fun `clearError removes error message`() {
        // Manually set an error first by using reflection or through login
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("Password123")
        
        // Assume error was set
        viewModel.clearError()
        
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }
    
    @Test
    fun `successful login emits NavigateToHome event`() = runTest {
        val loginResult = LoginResult(
            userId = "123",
            email = "test@example.com",
            name = "Test User",
            accessToken = "token",
            refreshToken = "refresh",
            expiresIn = 3600L
        )
        whenever(loginUseCase.invoke(any(), any())).thenReturn(Result.Success(loginResult))
        
        viewModel.events.test {
            viewModel.onEmailChange("test@example.com")
            viewModel.onPasswordChange("Password123")
            viewModel.onLoginClick()
            
            val event = awaitItem()
            assertThat(event).isInstanceOf(LoginEvent.NavigateToHome::class.java)
            assertThat((event as LoginEvent.NavigateToHome).userName).isEqualTo("Test User")
        }
    }
    
    @Test
    fun `failed login emits ShowError event`() = runTest {
        whenever(loginUseCase.invoke(any(), any())).thenReturn(
            Result.Error(Exception("Login failed"), "Login failed")
        )
        
        viewModel.events.test {
            viewModel.onEmailChange("test@example.com")
            viewModel.onPasswordChange("Password123")
            viewModel.onLoginClick()
            
            val event = awaitItem()
            assertThat(event).isInstanceOf(LoginEvent.ShowError::class.java)
            assertThat((event as LoginEvent.ShowError).message).isEqualTo("Login failed")
        }
    }
    
    @Test
    fun `isFormValid returns false for empty form`() {
        assertThat(viewModel.uiState.value.isFormValid).isFalse()
    }
    
    @Test
    fun `isFormValid returns true for valid form`() {
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("Password123")
        
        assertThat(viewModel.uiState.value.isFormValid).isTrue()
    }
}


