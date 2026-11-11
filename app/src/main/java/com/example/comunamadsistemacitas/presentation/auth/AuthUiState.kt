package com.example.comunamadsistemacitas.presentation.auth

data class AuthUiState(
    val isLoading: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String? = null
)
