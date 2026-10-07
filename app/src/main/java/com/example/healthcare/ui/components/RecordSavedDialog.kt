package com.example.healthcare.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun RecordSavedDialog(edited: Boolean, body: String = if (edited) "기록이 수정되었습니다." else "기록이 저장되었습니다.", onConfirm: () -> Unit) {
    // Like the existing blocking AlertDialog, acknowledgement requires its explicit button.
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag("record-saved-dialog"),
        title = { Text("저장 완료") },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp).testTag("record-saved-confirm")) { Text("확인") }
        }
    )
}
