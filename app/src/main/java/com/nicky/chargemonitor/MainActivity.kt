package com.nicky.chargemonitor

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nicky.chargemonitor.session.SessionExportFormat
import com.nicky.chargemonitor.ui.ChargeMonitorApp
import com.nicky.chargemonitor.ui.theme.ChargeMonitorTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val chargingViewModel: ChargingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                chargingViewModel.monitorWhileResumed()
            }
        }

        setContent {
            ChargeMonitorTheme {
                val state by chargingViewModel.state.collectAsStateWithLifecycle()
                ChargeMonitorApp(
                    state = state,
                    onSelectSession = chargingViewModel::selectSession,
                    onClearSelectedSession = chargingViewModel::clearSelectedSession,
                    onShareSession = ::exportAndShareSession,
                )
            }
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
