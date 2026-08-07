package com.markel.flowstate.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markel.flowstate.core.data.ThemeMode
import com.markel.flowstate.feature.settings.components.settingsItemShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    currentThemeMode: ThemeMode,
    currentDynamicColor: Boolean,
    currentPureSurfaces: Boolean,
    currentSystemFont: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPureSurfacesChange: (Boolean) -> Unit,
    onSystemFontChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val groupContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.settings_appearance),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.arrow_back_24px),
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── App theme ──
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_app_theme),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(groupContainerColor)
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            ToggleButton(
                                checked = currentThemeMode == mode,
                                onCheckedChange = { onThemeModeChange(mode) },
                                modifier = Modifier.weight(1f),
                                colors = ToggleButtonDefaults.toggleButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                            ) {
                                Text(
                                    text = when (mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = groupContainerColor
                ),
                modifier = Modifier.clip(settingsItemShape(index = 0, totalItems = 4))
            )

            // ── Dynamic color ──
            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.palette_24px),
                        contentDescription = null,
                        tint = if (supportsDynamicColor) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                },
                headlineContent = {
                    Text(text = stringResource(R.string.settings_dynamic_color))
                },
                supportingContent = {
                    Text(
                        text = if (supportsDynamicColor)
                            stringResource(R.string.settings_dynamic_color_desc)
                        else
                            stringResource(R.string.settings_dynamic_color_unavailable)
                    )
                },
                trailingContent = {
                    Switch(
                        checked = currentDynamicColor && supportsDynamicColor,
                        onCheckedChange = { onDynamicColorChange(it) },
                        enabled = supportsDynamicColor
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = groupContainerColor
                ),
                modifier = Modifier.clip(settingsItemShape(index = 1, totalItems = 4))
            )

            // ── Pure surfaces ──
            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.contrast_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = {
                    Text(text = stringResource(R.string.settings_pure_surfaces))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.settings_pure_surfaces_desc))
                },
                trailingContent = {
                    Switch(
                        checked = currentPureSurfaces,
                        onCheckedChange = { onPureSurfacesChange(it) }
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = groupContainerColor
                ),
                modifier = Modifier.clip(settingsItemShape(index = 2, totalItems = 4))
            )

            // ── System font ──
            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.font_download_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = {
                    Text(text = stringResource(R.string.settings_system_font))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.settings_system_font_desc))
                },
                trailingContent = {
                    Switch(
                        checked = currentSystemFont,
                        onCheckedChange = { onSystemFontChange(it) }
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = groupContainerColor
                ),
                modifier = Modifier.clip(settingsItemShape(index = 3, totalItems = 4))
            )
        }
    }
}