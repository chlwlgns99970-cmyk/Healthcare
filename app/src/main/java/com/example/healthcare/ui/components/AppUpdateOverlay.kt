package com.example.healthcare.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthcare.data.appupdate.AppUpdatePhase
import com.example.healthcare.data.appupdate.AppUpdateUiState

@Composable
fun AppUpdateOverlay(
    state: AppUpdateUiState,
    currentVersionName: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onDismissStatus: () -> Unit
) {
    when (state.phase) {
        AppUpdatePhase.AVAILABLE -> {
            val release = state.release ?: return
            AlertDialog(
                onDismissRequest = onLater,
                title = { Text("새 버전이 있어요") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("현재 버전  $currentVersionName")
                        Text("새 버전  ${release.versionName}", style = MaterialTheme.typography.titleMedium)
                        if (release.releaseNotes.isNotEmpty()) {
                            Text("주요 변경사항", style = MaterialTheme.typography.labelLarge)
                            release.releaseNotes.take(4).forEach { Text("• $it") }
                        }
                    }
                },
                confirmButton = { Button(onClick = onUpdate) { Text("업데이트") } },
                dismissButton = { TextButton(onClick = onLater) { Text("나중에") } }
            )
        }

        AppUpdatePhase.DOWNLOADING,
        AppUpdatePhase.VERIFYING -> AlertDialog(
            onDismissRequest = {},
            title = {
                Text(if (state.phase == AppUpdatePhase.DOWNLOADING) "업데이트 다운로드 중" else "업데이트 확인 중…")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.phase == AppUpdatePhase.DOWNLOADING) {
                        LinearProgressIndicator(
                            progress = { state.downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${state.downloadProgress}%")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("안전한 제품 업데이트인지 확인하고 있어요.")
                    }
                }
            },
            confirmButton = {}
        )

        AppUpdatePhase.INSTALL_PERMISSION_REQUIRED -> AlertDialog(
            onDismissRequest = onDismissStatus,
            title = { Text("설치 허용이 필요해요") },
            text = {
                Text("다운로드한 업데이트를 Android 시스템 설치 화면으로 전달하려면 이 앱의 ‘알 수 없는 앱 설치’ 허용이 필요합니다.")
            },
            confirmButton = { Button(onClick = onOpenInstallPermission) { Text("설정 열기") } },
            dismissButton = { TextButton(onClick = onDismissStatus) { Text("나중에") } }
        )

        AppUpdatePhase.FAILED -> AlertDialog(
            onDismissRequest = onDismissStatus,
            title = { Text("업데이트를 진행하지 못했어요") },
            text = { Text(state.message ?: "잠시 후 다시 시도해주세요.") },
            confirmButton = { TextButton(onClick = onDismissStatus) { Text("확인") } }
        )

        else -> Unit
    }
}
