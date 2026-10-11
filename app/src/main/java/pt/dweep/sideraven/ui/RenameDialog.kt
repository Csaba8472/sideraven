/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.mozilla.fenix.R

/**
 * Renames an Essential or Pinned Tab. Its custom name shows instead of the page title; saving an
 * empty name goes back to the title.
 */
@Composable
internal fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (name: String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sideraven_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.sideraven_rename_hint)) },
                modifier = Modifier.fillMaxWidth().testTag(SidebarTestTag.RENAME_FIELD),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name.trim().ifBlank { null }) },
                modifier = Modifier.testTag(SidebarTestTag.RENAME_CONFIRM),
            ) { Text(stringResource(R.string.sideraven_space_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sideraven_cancel)) }
        },
    )
}
