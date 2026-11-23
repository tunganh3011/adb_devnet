package com.example.myapplication.presentation.login

import com.example.myapplication.domain.model.User

data class LoginUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val error: String? = null
)
