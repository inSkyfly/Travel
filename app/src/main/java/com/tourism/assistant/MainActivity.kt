package com.tourism.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tourism.assistant.ui.navigation.TourismNavHost
import com.tourism.assistant.ui.theme.TourismAssistantTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TourismAssistantTheme {
                TourismNavHost()
            }
        }
    }
}
