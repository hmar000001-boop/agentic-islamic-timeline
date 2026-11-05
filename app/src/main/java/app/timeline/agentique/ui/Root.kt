package app.timeline.agentique.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.timeline.agentique.R

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TIMELINE, R.string.nav_timeline, Icons.Default.Home),
    Tab(Routes.HIFZ, R.string.nav_hifz, Icons.Default.Favorite),
    Tab(Routes.HALAL, R.string.nav_halal, Icons.Default.Search),
    Tab(Routes.ZAKAT, R.string.nav_zakat, Icons.Default.Info),
    Tab(Routes.TRAVEL, R.string.nav_travel, Icons.Default.Place),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Routes.TIMELINE
    val title = tabs.firstOrNull { it.route == route }?.label ?: R.string.nav_settings

    fun go(r: String) = nav.navigate(r) {
        popUpTo(Routes.TIMELINE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                actions = {
                    IconButton(onClick = { go(Routes.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { go(t.route) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.TIMELINE, modifier = Modifier.padding(padding)) {
            composable(Routes.TIMELINE) { TimelineScreen(onNavigate = ::go) }
            composable(Routes.HIFZ) { HifzScreen() }
            composable(Routes.HALAL) { HalalScreen() }
            composable(Routes.ZAKAT) { ZakatScreen() }
            composable(Routes.TRAVEL) { TravelScreen(onNavigate = ::go) }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
