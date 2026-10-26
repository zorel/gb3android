package org.zorel.gb3android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.zorel.gb3android.ui.Gb3App
import org.zorel.gb3android.ui.Gb3ViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: Gb3ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Gb3App(viewModel)
        }
    }
}
