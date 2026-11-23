package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.User

interface LoginRepository {
    suspend fun login(username: String, password: String): Result<User>
}
