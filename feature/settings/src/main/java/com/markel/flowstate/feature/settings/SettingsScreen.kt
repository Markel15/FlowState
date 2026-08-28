package com.markel.flowstate.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markel.flowstate.feature.settings.components.settingsItemShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appVersion: String,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToBottomNavConfig: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToIntegrations: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    notificationsEnabled: Boolean = true,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val handleItemClick: (SettingsItemData) -> Unit = { item ->
        when (item) {
            SettingsItemData.Notifications -> onNavigateToNotifications()
            SettingsItemData.Appearance -> onNavigateToAppearance()
            SettingsItemData.BottomNavConfig -> onNavigateToBottomNavConfig()
            SettingsItemData.Categories -> onNavigateToCategories()
            SettingsItemData.Achievements -> onNavigateToAchievements()
            SettingsItemData.Integrations -> onNavigateToIntegrations()
            SettingsItemData.About -> onNavigateToAbout()
        }
    }

    Scaffold(
        modifier = Modifier.Companion
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            LargeFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 30.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── General group ──────────────────────────────────
            item {
                SettingsGroupLabel(stringResource(R.string.settings_general))
            }

            item {
                val generalItems = listOf(
                    SettingsItemData.Notifications,
                    SettingsItemData.Appearance,
                    SettingsItemData.Categories,
                    SettingsItemData.Achievements,
                    SettingsItemData.BottomNavConfig
                )
                SettingsGroup(
                    items = generalItems,
                    appVersion = appVersion,
                    onItemClick = handleItemClick,
                    notificationsEnabled = notificationsEnabled
                )
            }

            // ── Integrations group ──────────────────────────────
            item {
                SettingsGroupLabel(stringResource(R.string.settings_integrations))
            }

            item {
                val integrationItems = listOf(
                    SettingsItemData.Integrations
                )
                SettingsGroup(
                    items = integrationItems,
                    appVersion = appVersion,
                    onItemClick = handleItemClick,
                    notificationsEnabled = notificationsEnabled
                )
            }

            // ── Info group ─────────────────────────────────────
            item {
                SettingsGroupLabel(stringResource(R.string.settings_info))
            }
            item {
                val infoItems = listOf(SettingsItemData.About)
                SettingsGroup(
                    items = infoItems,
                    appVersion = appVersion,
                    onItemClick = handleItemClick,
                    notificationsEnabled = notificationsEnabled
                )
            }
        }
    }
}

/**
 * Section label above a group of settings items.
 */
@Composable
private fun SettingsGroupLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.Companion.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/**
 * Data representing a navigable settings item.
 */
private sealed interface SettingsItemData {
    data object Notifications : SettingsItemData
    data object Appearance : SettingsItemData
    data object BottomNavConfig : SettingsItemData
    data object Categories : SettingsItemData
    data object Achievements : SettingsItemData
    data object Integrations : SettingsItemData
    data object About : SettingsItemData
}

/**
 * A vertically grouped set of settings items with M3 Expressive rounded shapes.
 * Items are clipped based on their position.
 */
@Composable
private fun SettingsGroup(
    items: List<SettingsItemData>,
    appVersion: String,
    onItemClick: (SettingsItemData) -> Unit,
    notificationsEnabled: Boolean
) {
    Column(
        modifier = Modifier.Companion.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEachIndexed { index, item ->
            val shape = settingsItemShape(index, items.size)
            val itemColors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            val onClick = { onItemClick(item) }

            when (item) {
                is SettingsItemData.Notifications -> {
                    SettingsNavigationItem(
                        icon = {
                            Icon(
                                imageVector =
                                    if (notificationsEnabled) ImageVector.Companion.vectorResource(R.drawable.notifications_24px)
                                    else ImageVector.Companion.vectorResource(R.drawable.notifications_off_24px),
                                contentDescription = stringResource(R.string.settings_notifications)
                            )
                        },
                        headline = stringResource(R.string.settings_notifications),
                        supporting =
                            if (notificationsEnabled) stringResource(R.string.settings_notifications_enabled)
                            else stringResource(R.string.settings_notifications_disabled),
                        shape = shape,
                        colors = itemColors,
                        onClick = onClick
                    )
                }

                is SettingsItemData.Appearance -> {
                    SettingsNavigationItem(
                        icon = {
                            Icon(
                                imageVector = ImageVector.Companion.vectorResource(
                                    R.drawable.palette_24px
                                ),
                                contentDescription = stringResource(R.string.settings_appearance)
                            )
                        },
                        headline = stringResource(R.string.settings_appearance),
                        supporting = stringResource(R.string.settings_appearance_description),
                        shape = shape,
                        colors = itemColors,
                        onClick = onClick
                    )
                }

                is SettingsItemData.Categories -> {
                    SettingsNavigationItem(
                        icon = {
                            Icon(
                                imageVector = ImageVector.Companion.vectorResource(
                                    R.drawable.category_24px
                                ),
                                contentDescription = stringResource(R.string.categories_title)
                            )
                        },
                        headline = stringResource(R.string.categories_title),
                        supporting = stringResource(R.string.categories_settings_description),
                        shape = shape,
                        colors = itemColors,
                        onClick = onClick
                    )
                }

                is SettingsItemData.Achievements -> {
                    SettingsNavigationItem(
                        icon = {
                            Icon(
                                imageVector = ImageVector.Companion.vectorResource(
                                    R.drawable.workspace_premium_24px
                                ),
                                contentDescription = stringResource(R.string.achievements_title)
                            )
                        },
                        headline = stringResource(R.string.achievements_title),
                        supporting = stringResource(R.string.achievements_settings_description),
                        shape = shape,
                        colors = itemColors,
                        onClick = onClick
                    )
                }

                is SettingsItemData.BottomNavConfig -> {
                    SettingsNavigationItem(
                        icon = {
                            Icon(
                                imageVector = ImageVector.Companion.vectorResource(
                                    R.drawable.bottom_navigation_24px
                                ),
                                contentDescription = stringResource(R.string.bottom_nav_config_title)
                            )
                        },
                        headline = stringResource(R.string.bottom_nav_config_title),
                        supporting = stringResource(R.string.settings_bottom_nav_description),
                        shape = shape,
                        colors = itemColors,
                        onClick = onClick
                    )
                }

                is SettingsItemData.Integrations -> {
                    SettingsNavigationItem(
                        icon = {
                            Icon(
                                imageVector = ImageVector.Companion.vectorResource(
                                    R.drawable.download_for_offline_24px
                                ),
                                contentDescription = stringResource(R.string.backup_title)
                            )
                        },
                        headline = stringResource(R.string.backup_title),
                        supporting = stringResource(R.string.settings_integrations_description),
                        shape = shape,
                        colors = itemColors,
                        onClick = onClick
                    )
                }

                is SettingsItemData.About -> {
                    SettingsNavigationItem(
                        icon = {
                            Icon(
                                imageVector = ImageVector.Companion.vectorResource(
                                    R.drawable.info_24px
                                ),
                                contentDescription = stringResource(R.string.settings_about)
                            )
                        },
                        headline = stringResource(R.string.settings_about),
                        supporting = stringResource(
                            R.string.settings_about_description,
                            appVersion
                        ),
                        shape = shape,
                        colors = itemColors,
                        onClick = onClick
                    )
                }
            }
        }
    }
}

/**
 * A single [ListItem] styled as a navigable settings row.
 * Uses the provided [shape] for clipping and [colors] for background.
 * Shows an arrow icon as trailing content to indicate navigation.
 */
@Composable
private fun SettingsNavigationItem(
    icon: @Composable () -> Unit,
    headline: String,
    supporting: String,
    shape: Shape,
    colors: androidx.compose.material3.ListItemColors,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = icon,
        trailingContent = {
            Icon(
                imageVector = ImageVector.Companion.vectorResource(
                    R.drawable.arrow_forward_24px
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = colors,
        modifier = Modifier.Companion
            .fillMaxWidth()
            .clip(shape)
            .clickable { onClick() }
        ,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    )
}