package com.github.shekohex.whisperpp.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.shekohex.whisperpp.R

/** The five bottom-bar / rail entries (§4.8). */
enum class TopLevelDestination(
    val route: Destination,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Destination.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    MODES(Destination.Modes, R.string.nav_modes, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    HISTORY(Destination.History, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
    DICTIONARY(Destination.Dictionary, R.string.nav_dictionary, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    MORE(Destination.More, R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
}
