package com.cami.neonloop

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.cami.neonloop.ui.screens.LooperScreen
import com.cami.neonloop.ui.theme.NeonLoopTheme

class MainActivity : ComponentActivity() {
    private val vm: LooperViewModel by viewModels()

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
        setContent {
            NeonLoopTheme { LooperScreen(vm) }
        }
    }
}
