package com.nicky.chargemonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nicky.chargemonitor.session.SessionExportFormat
import com.nicky.chargemonitor.ui.ChargeMonitorActions
import com.nicky.chargemonitor.ui.ChargeMonitorApp
import com.nicky.chargemonitor.ui.theme.ChargeMonitorTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val chargingViewModel: ChargingViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            chargingViewModel.startExtendedRecording()
        } else {
            Toast.makeText(
                this,
                "Notification permission is required for screen-off recording",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                chargingViewModel.monitorWhileResumed()
            }
        }

        val actions = ChargeMonitorActions(
            selectSession = chargingViewModel::selectSession,
            shareSession = ::exportAndShareSession,
            nameAccessory = chargingViewModel::saveCurrentAccessoryName,
            selectComparison = chargingViewModel::selectComparison,
            beginGuidedTest = chargingViewModel::beginGuidedTest,
            armGuidedTest = chargingViewModel::armGuidedTestPhase,
            finishGuidedTestWindow = chargingViewModel::finishGuidedTestPhase,
            cancelGuidedTest = chargingViewModel::cancelGuidedTest,
            startExtendedRecording = ::requestExtendedRecording,
            stopExtendedRecording = chargingViewModel::stopExtendedRecording,
        )

        setContent {
            ChargeMonitorTheme {
                val state by chargingViewModel.state.collectAsStateWithLifecycle()
                ChargeMonitorApp(state = state, actions = actions)
            }
        }
    }

    private fun requestExtendedRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            chargingViewModel.startExtendedRecording()
        }
    }

    private fun exportAndShareSession(
        sessionId: String,
        format: SessionExportFormat,
    ) {
        lifecycleScope.launch {
            runCatching {
                chargingViewModel.exportSession(sessionId, format)
            }.onSuccess { file ->
                shareFile(file, format)
            }.onFailure {
                Toast.makeText(
                    this@MainActivity,
                    "Could not export this session",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun shareFile(file: File, format: SessionExportFormat) {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share charging session"))
    }
}
