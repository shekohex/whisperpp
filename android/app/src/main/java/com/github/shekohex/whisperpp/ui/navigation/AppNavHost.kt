package com.github.shekohex.whisperpp.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.app.appContainer
import com.github.shekohex.whisperpp.ui.dictionary.AutoLearnEventHost
import com.github.shekohex.whisperpp.ui.dictionary.DictionaryTabIcon

/**
 * App shell: five top-level tabs in a bottom bar on phones (only on tab roots) or a navigation rail
 * on expanded widths (always). Predictive back comes from Navigation Compose.
 */
@Composable
fun AppNavHost(
    widthSizeClass: WindowWidthSizeClass,
    startDestination: Destination,
    launchRequest: LaunchRequest?,
    onLaunchRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var currentTab by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }
    var checkForUpdateOnAbout by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val tabAtDestination = currentDestination?.topLevelTab()
    LaunchedEffect(tabAtDestination) {
        if (tabAtDestination != null) currentTab = tabAtDestination
    }

    val selectTab: (TopLevelDestination) -> Unit = { tab ->
        currentTab = tab
        navController.navigateToTab(tab)
    }

    LaunchedEffect(launchRequest) {
        val request = launchRequest ?: return@LaunchedEffect
        if (request.checkForUpdate) checkForUpdateOnAbout = true
        val tab = TopLevelDestination.entries.firstOrNull { it.route == request.target.tab } ?: TopLevelDestination.HOME
        selectTab(tab)
        request.target.destination?.let { navController.navigate(it) }
        onLaunchRequestHandled()
    }

    val inOnboarding = currentDestination?.hasRoute(Destination.Onboarding::class) == true
    val useRail = widthSizeClass == WindowWidthSizeClass.Expanded && !inOnboarding
    Row(modifier = modifier.fillMaxSize()) {
        if (useRail) {
            AppNavigationRail(currentTab = currentTab, onSelect = selectTab)
        }
        Scaffold(
            bottomBar = {
                if (!useRail && tabAtDestination != null) {
                    AppNavigationBar(currentTab = currentTab, onSelect = selectTab)
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                appGraph(
                    onNavigate = { destination -> navController.navigateFromResumed(destination) },
                    onBack = { navController.popFromResumed() },
                    checkForUpdateOnAbout = { checkForUpdateOnAbout },
                    onOnboardingFinished = { navController.finishOnboarding() },
                )
            }
        }
    }

    MigrationErrorDialog()
    AutoLearnEventHost(snackbarHostState)
}

@Composable
private fun AppNavigationBar(
    currentTab: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = { TabIcon(tab, selected) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    currentTab: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationRail {
        TopLevelDestination.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = { TabIcon(tab, selected) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

/** The Dictionary icon carries the Auto-Learn review badge. */
@Composable
private fun TabIcon(tab: TopLevelDestination, selected: Boolean) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab == TopLevelDestination.DICTIONARY) DictionaryTabIcon(icon) else Icon(icon, contentDescription = null)
}

/** Shown when the startup settings migrations threw (H-10): retry, or continue with data as is. */
@Composable
private fun MigrationErrorDialog() {
    val container = LocalContext.current.appContainer
    val failed by container.migrationsFailed.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (!failed || dismissed) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(stringResource(R.string.migration_error_title)) },
        text = { Text(stringResource(R.string.migration_error_message)) },
        confirmButton = {
            TextButton(onClick = container::retryMigrations) {
                Text(stringResource(R.string.migration_error_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) {
                Text(stringResource(R.string.migration_error_continue))
            }
        },
    )
}

private fun NavDestination.topLevelTab(): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { hasRoute(it.route::class) }

/** Home is the root of the tab stack, also when the graph started at onboarding. */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo<Destination.Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.finishOnboarding() {
    navigate(Destination.Home) {
        popUpTo<Destination.Onboarding> { inclusive = true }
        launchSingleTop = true
    }
}

/** Ignores taps from screens that are already leaving, so double taps don't push twice. */
private fun NavHostController.navigateFromResumed(destination: Destination) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(destination)
    }
}

private fun NavHostController.popFromResumed() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
