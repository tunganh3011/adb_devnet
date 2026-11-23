package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.User
import com.example.myapplication.domain.repository.LoginRepository

class LoginUseCase(private val repository: LoginRepository) {
    suspend operator fun invoke(username: String, password: String): Result<User> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(Exception("Username hoặc password không được để trống"))
        }
        return repository.login(username, password)
    }
}
