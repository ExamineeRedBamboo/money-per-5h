package com.moneyper5h.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import com.moneyper5h.app.ui.UsageScreen
import com.moneyper5h.app.ui.UsageViewModel

class MainActivity : ComponentActivity() {
    private val usage by lazy { ViewModelProvider(this)[UsageViewModel::class.java] }
    override fun onStart() { super.onStart(); usage.foreground(true) }
    override fun onStop() { if (!isChangingConfigurations) usage.foreground(false); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UsageScreen(usage.state.collectAsStateWithLifecycle().value, usage::dispatch, usage::confirmAmount)
        }
    }
}
