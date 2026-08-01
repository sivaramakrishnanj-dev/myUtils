package dev.sivarj.braingame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.sivarj.braingame.ui.BrainGameRoot
import dev.sivarj.braingame.ui.theme.BrainGameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrainGameTheme {
                BrainGameRoot()
            }
        }
    }
}
