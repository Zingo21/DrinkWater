package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.erbeenjoyers.drinkwater.InviteState

/**
 * The user's invite as a QR code to show and a link to send. Whoever opens it becomes their friend without a request,
 * so the dialog says so, and offers a new link for when one went further than meant.
 *
 * @param shareLink the platform's share sheet, or null where there is none; the link is copied instead.
 */
@Composable
fun InviteDialog(
    state: InviteState,
    inviteLink: (code: String) -> String,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    shareLink: ((String) -> Unit)? = null,
) {
    val link = state.code?.let(inviteLink)
    var copied by remember { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION") // LocalClipboard's ClipEntry has no common way to make one from text yet.
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite a friend") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Let them scan the code with their phone's camera, or send them the link. " +
                        "Whoever opens it becomes your friend right away.",
                )
                // White around the code even in a dark theme: scanners need the quiet zone light.
                Box(
                    Modifier.size(216.dp).background(Color.White).padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (link != null) {
                        Image(
                            rememberQrCodePainter(link),
                            contentDescription = "QR code with your invite link",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (state.error == null) {
                        CircularProgressIndicator()
                    }
                }
                when {
                    state.error != null -> Text(state.error, color = MaterialTheme.colors.error)
                    link != null -> SelectionContainer {
                        Text(link, style = MaterialTheme.typography.caption, textAlign = TextAlign.Center)
                    }
                }
                if (copied) Text("Link copied", style = MaterialTheme.typography.caption)
                if (confirmingReset) {
                    Text(
                        "The link you have shared, and its QR code, stop working.",
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(
                        onClick = {
                            confirmingReset = false
                            copied = false
                            onReset()
                        },
                        enabled = !state.busy,
                    ) { Text("Make a new link", color = MaterialTheme.colors.error) }
                } else {
                    // For a link that went further than meant.
                    TextButton(onClick = { confirmingReset = true }, enabled = link != null && !state.busy) {
                        Text("New link", style = MaterialTheme.typography.caption)
                    }
                }
            }
        },
        confirmButton = {
            if (shareLink != null) {
                TextButton(onClick = { link?.let(shareLink) }, enabled = link != null) { Text("Share link") }
            } else {
                TextButton(
                    onClick = {
                        if (link != null) {
                            clipboard.setText(AnnotatedString(link))
                            copied = true
                        }
                    },
                    enabled = link != null,
                ) { Text("Copy link") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
