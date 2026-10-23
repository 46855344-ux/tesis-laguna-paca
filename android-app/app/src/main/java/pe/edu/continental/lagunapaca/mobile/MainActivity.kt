package pe.edu.continental.lagunapaca.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.edu.continental.lagunapaca.mobile.ui.LagunaPacaApp
import pe.edu.continental.lagunapaca.mobile.ui.MainViewModel
import pe.edu.continental.lagunapaca.mobile.ui.theme.LagunaPacaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LagunaPacaTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                LagunaPacaApp(state = state, viewModel = viewModel)
            }
        }
    }
}
