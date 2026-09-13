package io.github.augustinavicius.kulendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.augustinavicius.kulendar.system.AppVisibility
import io.github.augustinavicius.kulendar.ui.MainScreen
import io.github.augustinavicius.kulendar.ui.theme.KulendarTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KulendarTheme {
                MainScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.onActivityStarted()
    }

    override fun onStop() {
        AppVisibility.onActivityStopped()
        super.onStop()
    }
}
