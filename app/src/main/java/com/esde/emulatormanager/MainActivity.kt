package com.esde.emulatormanager

import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.esde.emulatormanager.data.EsdeDefaultConfig
import com.esde.emulatormanager.ui.navigation.AppNavigation
import com.esde.emulatormanager.ui.screens.PermissionScreen
import com.esde.emulatormanager.ui.theme.ESDEEmulatorManagerTheme
import com.esde.emulatormanager.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        EsdeDefaultConfig.load(this)

        setContent {
            ESDEEmulatorManagerTheme {
                MainApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permission when returning to the app
    }
}

@Composable
fun MainApp() {
    val viewModel: MainViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Check storage permission
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true // For older versions, we request at runtime
            }
        )
    }

    // Update ViewModel when permission changes
    LaunchedEffect(hasStoragePermission) {
        viewModel.setStoragePermissionGranted(hasStoragePermission)
    }

    if (!hasStoragePermission) {
        PermissionScreen(
            onPermissionGranted = {
                hasStoragePermission = true
                viewModel.setStoragePermissionGranted(true)
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        AppNavigation(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}
