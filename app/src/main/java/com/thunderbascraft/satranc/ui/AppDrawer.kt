package com.thunderbascraft.satranc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.thunderbascraft.satranc.BuildConfig
import com.thunderbascraft.satranc.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A destination in the navigation drawer. */
data class DrawerEntry(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Animation progress of the drawer, 0 = closed, 1 = open (for the content's push effect). */
@Composable
fun drawerProgress(state: DrawerState): Float {
    val p by animateFloatAsState(
        targetValue = if (state.targetValue == DrawerValue.Open) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "drawerProgress",
    )
    return p
}

/**
 * The app's navigation drawer (modelled on the WiFi Guard drawer): header with logo, name and
 * version, destinations that slide in one after another, the selected one highlighted, and the
 * source code link pinned at the bottom. Choosing an entry closes the drawer.
 */
@Composable
fun AppDrawer(
    state: DrawerState,
    selectedKey: String,
    entries: List<DrawerEntry>,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val uri = LocalUriHandler.current
    val progress = drawerProgress(state)
    val opening = state.targetValue == DrawerValue.Open
    // Push away from the drawer's edge (left in LTR, right in RTL).
    val dir = if (layoutDirection == LayoutDirection.Rtl) 1f else -1f

    ModalNavigationDrawer(
        drawerState = state,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .testTag("drawer")
                    .graphicsLayer {
                        translationX = dir * (1f - progress) * with(density) { 18.dp.toPx() }
                        alpha = 0.55f + 0.45f * progress
                    },
            ) {
                Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                    DrawerHeader(Modifier.graphicsLayer {
                        alpha = progress
                        translationX = dir * (1f - progress) * with(density) { 12.dp.toPx() }
                    })
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    entries.forEachIndexed { index, e ->
                        var visible by remember(e.key) { mutableStateOf(!opening) }
                        LaunchedEffect(opening) {
                            if (opening) {
                                delay(40L + index * 38L); visible = true
                            } else {
                                delay((entries.lastIndex - index).coerceAtLeast(0) * 22L); visible = false
                            }
                        }
                        AnimatedVisibility(
                            visible = visible || state.currentValue == DrawerValue.Open,
                            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { (it / 2) * dir.toInt() },
                            exit = fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { (it / 3) * dir.toInt() },
                        ) {
                            val selected = e.key == selectedKey
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.15f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "drawerIconScale",
                            )
                            NavigationDrawerItem(
                                label = { Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(e.icon, contentDescription = null, modifier = Modifier.scale(iconScale)) },
                                selected = selected,
                                onClick = {
                                    scope.launch { state.close() }
                                    e.onClick()
                                },
                                modifier = Modifier.padding(horizontal = 12.dp).testTag("nav_${e.key}"),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(R.string.source_code_github), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                        selected = false,
                        onClick = {
                            scope.launch { state.close() }
                            runCatching { uri.openUri(BuildConfig.SOURCE_URL) }
                        },
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    )
                }
            }
        },
    ) {
        Box(Modifier.graphicsLayer {
            // The content steps back slightly while the drawer is open.
            val push = with(density) { 12.dp.toPx() } * progress
            scaleX = 1f - 0.035f * progress
            scaleY = 1f - 0.035f * progress
            translationX = -dir * push
            alpha = 1f - 0.06f * progress
        }) { content() }
    }
}

@Composable
private fun DrawerHeader(modifier: Modifier) {
    Row(modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.piece_wn), contentDescription = null, modifier = Modifier.size(38.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The standard drawer entries for the home screen. */
@Composable
fun homeDrawerEntries(
    canContinue: Boolean,
    onHome: () -> Unit,
    onContinue: () -> Unit,
    onOnline: () -> Unit,
    onHistory: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
): List<DrawerEntry> = buildList {
    add(DrawerEntry("home", stringResource(R.string.nav_home), Icons.Outlined.Home, onHome))
    if (canContinue) add(DrawerEntry("continue", stringResource(R.string.continue_game), Icons.Outlined.PlayCircle, onContinue))
    add(DrawerEntry("online", stringResource(R.string.nav_online), Icons.Outlined.Public, onOnline))
    add(DrawerEntry("history", stringResource(R.string.history), Icons.Outlined.History, onHistory))
    add(DrawerEntry("import", stringResource(R.string.import_pgn), Icons.Outlined.FileOpen, onImport))
    add(DrawerEntry("settings", stringResource(R.string.settings), Icons.Outlined.Settings, onSettings))
    add(DrawerEntry("about", stringResource(R.string.about), Icons.Outlined.Info, onAbout))
}
