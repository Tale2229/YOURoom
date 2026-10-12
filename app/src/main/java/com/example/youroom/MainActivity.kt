package com.example.youroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.youroom.ui.YouRoomApp
import com.example.youroom.ui.YouRoomViewModel
import com.example.youroom.ui.theme.YouRoomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YouRoomTheme {
                val model: YouRoomViewModel = viewModel(
                    factory = YouRoomViewModel.factory((application as YouRoomApplication).container))
                YouRoomApp(model)
            }
        }
    }
}

