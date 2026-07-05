package com.wallora.app.ui.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallora.app.R
import com.wallora.app.domain.model.SourceId
import com.wallora.app.ui.settings.SettingsEvent
import com.wallora.app.ui.settings.SettingsViewModel
import com.wallora.app.ui.settings.components.ApiKeyField
import com.wallora.app.ui.settings.components.SettingsScaffold

/** Sources that need no key — always available, on by default. */
private data class KeylessSource(val id: SourceId, val descRes: Int)

private val KEYLESS_SOURCES = listOf(
    KeylessSource(SourceId.WALLHAVEN, R.string.settings_source_desc_wallhaven),
    KeylessSource(SourceId.NASA, R.string.settings_source_desc_nasa),
    KeylessSource(SourceId.OPENVERSE, R.string.settings_source_desc_openverse),
    KeylessSource(SourceId.WIKIMEDIA, R.string.settings_source_desc_wikimedia),
)

/** Known subreddits with human-readable descriptions, shown as toggle rows on the Reddit card. */
private data class KnownSubreddit(val name: String, val description: String)

private val KNOWN_SUBREDDITS = listOf(
    KnownSubreddit("iWallpaper",         "High-quality phone wallpapers (portrait-focused)"),
    KnownSubreddit("Verticalwallpapers", "Portrait-only wallpapers for mobile screens"),
    KnownSubreddit("phonewallpapers",    "Wallpapers sized for phone screens"),
    KnownSubreddit("wallpapers",         "General wallpaper community"),
    KnownSubreddit("wallpaper",          "Wallpaper collection & requests"),
    KnownSubreddit("MobileWallpaper",    "Portrait wallpapers for mobile"),
    KnownSubreddit("Amoledbackgrounds",  "Dark AMOLED-optimized wallpapers"),
    KnownSubreddit("EarthPorn",          "Stunning natural landscapes"),
    KnownSubreddit("spaceporn",          "Space & astronomy photography"),
    KnownSubreddit("CityPorn",           "Urban & cityscape photography"),
    KnownSubreddit("AIArt",              "AI-generated artwork"),
    KnownSubreddit("midjourney",         "Midjourney AI creations"),
    KnownSubreddit("ImaginaryLandscapes","Fantasy & fictional landscapes"),
    KnownSubreddit("carporn",            "Cars & automotive photography"),
    KnownSubreddit("ArchitecturePorn",   "Architecture & building photography"),
)

@Composable
fun SettingsSourcesPage(
    vm: SettingsViewModel,
    onBack: () -> Unit,
) {
    val enabledSources by vm.enabledSources.collectAsStateWithLifecycle()
    val sourceConfiguredMap by vm.sourceConfiguredMap.collectAsStateWithLifecycle()
    val userPexelsKey by vm.userPexelsKey.collectAsStateWithLifecycle()
    val userUnsplashKey by vm.userUnsplashKey.collectAsStateWithLifecycle()
    val userWallhavenKey by vm.userWallhavenKey.collectAsStateWithLifecycle()
    val userPixabayKey by vm.userPixabayKey.collectAsStateWithLifecycle()
    val userFlickrKey by vm.userFlickrKey.collectAsStateWithLifecycle()
    val userRedditClientId by vm.userRedditClientId.collectAsStateWithLifecycle()
    val userSubreddits by vm.userSubreddits.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            if (event is SettingsEvent.ShowMessage) snackbarHostState.showSnackbar(event.message)
        }
    }

    fun isConfigured(id: SourceId) = sourceConfiguredMap[id] ?: false
    fun isEnabled(id: SourceId) = id in enabledSources

    SettingsScaffold(
        title = stringResource(R.string.settings_sources_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            HintText(stringResource(R.string.settings_sources_reassure))

            // ── Ready to use (keyless) ────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_sources_ready_section))
            HintText(stringResource(R.string.settings_sources_ready_hint))
            KEYLESS_SOURCES.forEach { src ->
                ListItem(
                    overlineContent = {
                        Text(
                            stringResource(R.string.settings_source_ready),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    headlineContent = { Text(src.id.displayName) },
                    supportingContent = { Text(stringResource(src.descRes)) },
                    trailingContent = {
                        Switch(
                            checked = isEnabled(src.id),
                            onCheckedChange = { vm.setSourceEnabled(src.id, it) },
                        )
                    },
                )
            }

            HorizontalDivider()

            // ── Connect for more (keyed) ──────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_sources_connect_section))
            HintText(stringResource(R.string.settings_sources_connect_hint))

            KeyedSourceCard(
                id = SourceId.PEXELS,
                descRes = R.string.settings_source_desc_pexels,
                connected = isConfigured(SourceId.PEXELS),
                enabled = isEnabled(SourceId.PEXELS),
                onToggle = { vm.setSourceEnabled(SourceId.PEXELS, it) },
            ) {
                ApiKeyField(
                    label = stringResource(R.string.settings_api_pexels_label),
                    hint = stringResource(R.string.settings_api_pexels_hint),
                    currentKey = userPexelsKey,
                    onSave = vm::saveUserPexelsKey,
                    getKeyUrl = "https://www.pexels.com/api/",
                )
            }

            KeyedSourceCard(
                id = SourceId.UNSPLASH,
                descRes = R.string.settings_source_desc_unsplash,
                connected = isConfigured(SourceId.UNSPLASH),
                enabled = isEnabled(SourceId.UNSPLASH),
                onToggle = { vm.setSourceEnabled(SourceId.UNSPLASH, it) },
            ) {
                ApiKeyField(
                    label = stringResource(R.string.settings_api_unsplash_label),
                    hint = stringResource(R.string.settings_api_unsplash_hint),
                    currentKey = userUnsplashKey,
                    onSave = vm::saveUserUnsplashKey,
                    getKeyUrl = "https://unsplash.com/developers",
                )
            }

            KeyedSourceCard(
                id = SourceId.PIXABAY,
                descRes = R.string.settings_source_desc_pixabay,
                connected = isConfigured(SourceId.PIXABAY),
                enabled = isEnabled(SourceId.PIXABAY),
                onToggle = { vm.setSourceEnabled(SourceId.PIXABAY, it) },
            ) {
                ApiKeyField(
                    label = stringResource(R.string.settings_api_pixabay_label),
                    hint = stringResource(R.string.settings_api_pixabay_hint),
                    currentKey = userPixabayKey,
                    onSave = vm::saveUserPixabayKey,
                    getKeyUrl = "https://pixabay.com/api/docs/",
                )
            }

            KeyedSourceCard(
                id = SourceId.FLICKR,
                descRes = R.string.settings_source_desc_flickr,
                connected = isConfigured(SourceId.FLICKR),
                enabled = isEnabled(SourceId.FLICKR),
                onToggle = { vm.setSourceEnabled(SourceId.FLICKR, it) },
            ) {
                ApiKeyField(
                    label = stringResource(R.string.settings_api_flickr_label),
                    hint = stringResource(R.string.settings_api_flickr_hint),
                    currentKey = userFlickrKey,
                    onSave = vm::saveUserFlickrKey,
                    getKeyUrl = "https://www.flickr.com/services/apps/create/",
                )
            }

            KeyedSourceCard(
                id = SourceId.REDDIT,
                descRes = R.string.settings_source_desc_reddit,
                connected = isConfigured(SourceId.REDDIT),
                enabled = isEnabled(SourceId.REDDIT),
                onToggle = { vm.setSourceEnabled(SourceId.REDDIT, it) },
            ) {
                ApiKeyField(
                    label = stringResource(R.string.settings_api_reddit_label),
                    hint = stringResource(R.string.settings_api_reddit_hint),
                    currentKey = userRedditClientId,
                    onSave = vm::saveUserRedditClientId,
                    getKeyUrl = "https://www.reddit.com/prefs/apps",
                )
                // Subreddit picker lives with the Reddit source (only useful once connected).
                if (isConfigured(SourceId.REDDIT) && isEnabled(SourceId.REDDIT)) {
                    RedditSubreddits(vm = vm, userSubreddits = userSubreddits)
                }
            }

            // Optional Wallhaven key (raises limits) — keyless source, so shown as an extra.
            HorizontalDivider()
            ApiKeyField(
                label = stringResource(R.string.settings_api_wallhaven_label),
                hint = stringResource(R.string.settings_api_wallhaven_hint),
                currentKey = userWallhavenKey,
                onSave = vm::saveUserWallhavenKey,
                getKeyUrl = "https://wallhaven.cc/settings/account",
            )
        }
    }
}

@Composable
private fun KeyedSourceCard(
    id: SourceId,
    descRes: Int,
    connected: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                overlineContent = {
                    Text(
                        stringResource(
                            if (connected) R.string.settings_source_connected
                            else R.string.settings_source_not_connected,
                        ),
                        color = if (connected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                headlineContent = { Text(id.displayName) },
                supportingContent = { Text(stringResource(descRes)) },
                trailingContent = {
                    Switch(
                        checked = enabled && connected,
                        onCheckedChange = onToggle,
                        enabled = connected,
                    )
                },
            )
            content()
        }
    }
}

@Composable
private fun RedditSubreddits(
    vm: SettingsViewModel,
    userSubreddits: List<String>,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var addInput by remember { mutableStateOf("") }

    val knownNames = KNOWN_SUBREDDITS.map { it.name }
    val customSubreddits = userSubreddits.filter { it !in knownNames }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(stringResource(R.string.settings_reddit_section))
    HintText(stringResource(R.string.settings_reddit_hint))

    KNOWN_SUBREDDITS.forEach { sub ->
        val isActive = sub.name in userSubreddits
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("r/${sub.name}") },
            supportingContent = { Text(sub.description, style = MaterialTheme.typography.bodySmall) },
            trailingContent = {
                Switch(
                    checked = isActive,
                    onCheckedChange = { enabled ->
                        if (enabled) vm.addSubreddit(sub.name) else vm.removeSubreddit(sub.name)
                    },
                )
            },
        )
    }

    if (customSubreddits.isNotEmpty()) {
        Text(
            text = stringResource(R.string.settings_reddit_custom_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        customSubreddits.forEach { sub ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("r/$sub") },
                trailingContent = {
                    IconButton(onClick = { vm.removeSubreddit(sub) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.settings_remove_content_desc, "r/$sub"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { showAddDialog = true }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(stringResource(R.string.settings_reddit_add))
        }
        TextButton(onClick = { vm.resetSubreddits() }) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(stringResource(R.string.settings_reddit_reset))
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; addInput = "" },
            title = { Text(stringResource(R.string.settings_reddit_add)) },
            text = {
                OutlinedTextField(
                    value = addInput,
                    onValueChange = { addInput = it },
                    label = { Text(stringResource(R.string.settings_reddit_field_label)) },
                    placeholder = { Text(stringResource(R.string.settings_reddit_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (addInput.isNotBlank()) {
                            vm.addSubreddit(addInput); addInput = ""; showAddDialog = false
                        }
                    }),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (addInput.isNotBlank()) {
                        vm.addSubreddit(addInput); addInput = ""; showAddDialog = false
                    }
                }) { Text(stringResource(R.string.settings_dialog_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; addInput = "" }) {
                    Text(stringResource(R.string.settings_dialog_cancel))
                }
            },
        )
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
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
