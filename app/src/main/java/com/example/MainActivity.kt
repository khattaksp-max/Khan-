package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.screens.SanaChatScreen
import com.example.ui.screens.VoiceSettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SanaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SanaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showPermissionDialog by remember { mutableStateOf(false) }

                    val requiredPermissions = remember {
                        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            list.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        list.toTypedArray()
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
                        if (recordGranted) {
                            showPermissionDialog = false
                            viewModel.toggleMicrophone()
                        } else {
                            showPermissionDialog = true
                        }
                    }

                    val requestMicPermission = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasMic) {
                            viewModel.toggleMicrophone()
                        } else {
                            permissionLauncher.launch(requiredPermissions)
                        }
                    }

                    val isVoiceSettingsOpen by viewModel.isVoiceSettingsOpen.collectAsState()

                    if (isVoiceSettingsOpen) {
                        VoiceSettingsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.closeVoiceSettings() }
                        )
                    } else {
                        SanaChatScreen(
                            viewModel = viewModel,
                            onOpenVoiceSettings = { viewModel.openVoiceSettings() },
                            onRequestMicrophone = requestMicPermission
                        )
                    }

                    if (showPermissionDialog) {
                        AlertDialog(
                            onDismissRequest = { showPermissionDialog = false },
                            title = { Text("Microphone Permission Required") },
                            text = {
                                Text(
                                    "Boss, SANA needs access to your microphone to listen and enable continuous two-way conversation."
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showPermissionDialog = false
                                        permissionLauncher.launch(requiredPermissions)
                                    }
                                ) {
                                    Text("Try Again")
                                }
                            },
                            dismissButton = {
                                RowButton(
                                    onOpenSettings = {
                                        showPermissionDialog = false
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        startActivity(intent)
                                    },
                                    onDismiss = { showPermissionDialog = false }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RowButton(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.foundation.layout.Row {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
        OutlinedButton(onClick = onOpenSettings) {
            Text("Open Settings")
        }
    }
}
