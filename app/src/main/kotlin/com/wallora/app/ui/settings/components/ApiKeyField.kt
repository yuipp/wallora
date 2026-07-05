package com.wallora.app.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.wallora.app.R

@Composable
fun ApiKeyField(
    label: String,
    hint: String,
    currentKey: String,
    onSave: (String) -> Unit,
    getKeyUrl: String? = null,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember(currentKey) { mutableStateOf(currentKey) }
    var showKey by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            if (editing) {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(hint) },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onSave(draft)
                            editing = false
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (getKeyUrl != null) {
                            TextButton(onClick = { openUrl(context, getKeyUrl) }) {
                                Text(stringResource(R.string.settings_api_get_key))
                            }
                        }
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(
                                if (showKey) stringResource(R.string.settings_api_key_hide)
                                else stringResource(R.string.settings_api_key_show),
                            )
                        }
                        TextButton(onClick = { onSave(draft); editing = false }) {
                            Text(stringResource(R.string.settings_api_key_save))
                        }
                        TextButton(onClick = { onSave(""); draft = ""; editing = false }) {
                            Text(stringResource(R.string.settings_api_key_clear))
                        }
                    }
                }
            } else {
                Text(
                    text = if (currentKey.isNotBlank()) {
                        "${stringResource(R.string.settings_api_key_configured)} (${currentKey.take(6)}…)"
                    } else hint,
                    color = if (currentKey.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            if (!editing) {
                TextButton(onClick = { editing = true }) {
                    Text(stringResource(R.string.settings_api_key_edit))
                }
            }
        },
    )
}

/** Open [url] in a browser; silently no-ops if no browser handles the intent. */
private fun openUrl(context: Context, url: String) {
    val normalized = if (url.startsWith("http")) url else "https://$url"
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // No browser available — nothing to do.
    }
}
