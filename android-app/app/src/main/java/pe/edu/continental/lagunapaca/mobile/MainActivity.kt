package pe.edu.continental.lagunapaca.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pe.edu.continental.lagunapaca.mobile.ui.LagunaPacaApp
import pe.edu.continental.lagunapaca.mobile.ui.theme.LagunaPacaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LagunaPacaTheme {
                LagunaPacaApp()
            }
        }
    }
}
