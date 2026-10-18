package com.example.youroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.youroom.ui.YOURoomApp
import com.example.youroom.ui.YOURoomViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: YOURoomViewModel = viewModel(
                factory = YOURoomViewModel.factory((application as YOURoomApplication).container))
            YOURoomApp(model)
        }
    }
}
