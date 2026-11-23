package com.example.myapplication.navdemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R

class NavDemoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight) // Đảm bảo Activity có theme chuẩn XML
        setContentView(R.layout.activity_nav_demo)
    }
}
