package com.example.myapplication.data.repository

import com.example.myapplication.domain.model.User
import com.example.myapplication.domain.repository.LoginRepository
import kotlinx.coroutines.delay

class LoginRepositoryImpl : LoginRepository {
    override suspend fun login(username: String, password: String): Result<User> {
        delay(1500) // Giả lập mạng chậm
        return if (username == "admin" && password == "123456") {
            Result.success(User("1", "Admin User", "admin@example.com"))
        } else {
            Result.failure(Exception("Sai tài khoản hoặc mật khẩu"))
        }
    }
}
