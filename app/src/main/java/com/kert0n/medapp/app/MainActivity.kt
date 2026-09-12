package com.kert0n.medapp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kert0n.medapp.ui.theme.MedAppTheme
import dagger.hilt.android.AndroidEntryPoint

/** Единственное окно приложения (PLAN H3): всё остальное — места внутри оболочки. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedAppTheme {
                MedAppApp()
            }
        }
    }
}
