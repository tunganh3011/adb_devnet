package com.example.myapplication.presentation.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    LoginContent(
        uiState = uiState,
        onLoginClick = { username, password ->
            viewModel.login(username, password)
        }
    )
}

@Composable
fun LoginContent(
    uiState: LoginUiState,
    onLoginClick: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Login Demo", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        TextField(
            value = username, 
            onValueChange = { username = it }, 
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = password, 
            onValueChange = { password = it }, 
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onLoginClick(username, password) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
            }
        }

        uiState.user?.let { 
            Spacer(modifier = Modifier.height(16.dp))
            Text("Welcome, ${it.name}!", color = MaterialTheme.colorScheme.primary) 
        }
        uiState.error?.let { 
            Spacer(modifier = Modifier.height(16.dp))
            Text("Error: $it", color = MaterialTheme.colorScheme.error) 
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    MyApplicationTheme {
        LoginContent(
            uiState = LoginUiState(),
            onLoginClick = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginLoadingPreview() {
    MyApplicationTheme {
        LoginContent(
            uiState = LoginUiState(isLoading = true),
            onLoginClick = { _, _ -> }
        )
    }
}
