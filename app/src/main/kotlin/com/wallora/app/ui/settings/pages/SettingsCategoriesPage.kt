package com.wallora.app.ui.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallora.app.R
import com.wallora.app.domain.model.Category
import com.wallora.app.ui.settings.SettingsViewModel
import com.wallora.app.ui.settings.components.CATEGORY_SECTIONS
import com.wallora.app.ui.settings.components.SettingsScaffold
import com.wallora.app.ui.settings.components.gradientColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCategoriesPage(
    vm: SettingsViewModel,
    onBack: () -> Unit,
) {
    val selectedCategories by vm.selectedCategories.collectAsStateWithLifecycle()
    val customKeywords by vm.customKeywords.collectAsStateWithLifecycle()

    var showAddKeywordDialog by remember { mutableStateOf(false) }
    var addKeywordInput by remember { mutableStateOf("") }

    SettingsScaffold(
        title = stringResource(R.string.settings_categories_title),
        onBack = onBack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Categories ────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_categories_header))
            HintText(stringResource(R.string.settings_categories_focus_hint))

            CATEGORY_SECTIONS.forEach { section ->
                GroupLabel(stringResource(section.titleRes))
                section.categories.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { cat ->
                            CategoryCard(
                                category = cat,
                                selected = cat in selectedCategories,
                                onClick = { vm.toggleCategory(cat) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            // ── Your topics ───────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_topics_header))
            HintText(stringResource(R.string.settings_topics_desc))
            if (customKeywords.isEmpty()) {
                HintText(stringResource(R.string.settings_topics_empty))
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    customKeywords.sorted().forEach { keyword ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(keyword) },
                            trailingIcon = {
                                IconButton(onClick = { vm.removeCustomKeyword(keyword) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(
                                            R.string.settings_remove_content_desc, keyword,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
            ListItem(
                leadingContent = {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                headlineContent = {
                    Text(
                        stringResource(R.string.settings_topics_add),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAddKeywordDialog = true },
            )
        }
    }

    if (showAddKeywordDialog) {
        AlertDialog(
            onDismissRequest = { showAddKeywordDialog = false; addKeywordInput = "" },
            title = { Text(stringResource(R.string.settings_topics_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_topics_dialog_body),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = addKeywordInput,
                        onValueChange = { addKeywordInput = it },
                        label = { Text(stringResource(R.string.settings_topics_field_label)) },
                        placeholder = { Text(stringResource(R.string.settings_custom_keyword_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (addKeywordInput.isNotBlank()) {
                                vm.addCustomKeyword(addKeywordInput)
                                addKeywordInput = ""
                                showAddKeywordDialog = false
                            }
                        }),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (addKeywordInput.isNotBlank()) {
                        vm.addCustomKeyword(addKeywordInput)
                        addKeywordInput = ""
                        showAddKeywordDialog = false
                    }
                }) { Text(stringResource(R.string.settings_dialog_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddKeywordDialog = false; addKeywordInput = "" }) {
                    Text(stringResource(R.string.settings_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(shape)
            .background(Brush.linearGradient(category.gradientColors()))
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            )
            .clickable { onClick() },
    ) {
        // Bottom scrim so the label stays readable over bright gradients.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))),
                ),
        )
        Text(
            text = category.displayName,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun GroupLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
