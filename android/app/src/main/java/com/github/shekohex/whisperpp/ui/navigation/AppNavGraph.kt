package com.github.shekohex.whisperpp.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.github.shekohex.whisperpp.ui.about.AboutScreen
import com.github.shekohex.whisperpp.ui.analytics.InsightsScreen
import com.github.shekohex.whisperpp.ui.apps.AppMappingDetailScreen
import com.github.shekohex.whisperpp.ui.apps.AppMappingsScreen
import com.github.shekohex.whisperpp.ui.apps.LanguageDefaultsScreen
import com.github.shekohex.whisperpp.ui.backup.BackupRestoreScreen
import com.github.shekohex.whisperpp.ui.dictation.AudioSettingsScreen
import com.github.shekohex.whisperpp.ui.dictation.DictationSettingsScreen
import com.github.shekohex.whisperpp.ui.dictionary.DictionaryScreen
import com.github.shekohex.whisperpp.ui.enhancement.EnhancementScreen
import com.github.shekohex.whisperpp.ui.enhancement.PresetsScreen
import com.github.shekohex.whisperpp.ui.enhancement.PromptEditScreen
import com.github.shekohex.whisperpp.ui.enhancement.PromptsScreen
import com.github.shekohex.whisperpp.ui.history.HistoryDetailScreen
import com.github.shekohex.whisperpp.ui.history.HistoryScreen
import com.github.shekohex.whisperpp.ui.home.HomeScreen
import com.github.shekohex.whisperpp.ui.modes.ModeEditScreen
import com.github.shekohex.whisperpp.ui.modes.ModesScreen
import com.github.shekohex.whisperpp.ui.more.MoreScreen
import com.github.shekohex.whisperpp.ui.onboarding.OnboardingScreen
import com.github.shekohex.whisperpp.ui.privacy.PrivacySafetyScreen
import com.github.shekohex.whisperpp.ui.providers.ProviderEditScreen
import com.github.shekohex.whisperpp.ui.providers.ProviderListScreen
import com.github.shekohex.whisperpp.ui.providers.ProviderSelectionsScreen

/** Registers every [Destination]; screens only see `onNavigate` / `onBack` callbacks. */
internal fun NavGraphBuilder.appGraph(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    checkForUpdateOnAbout: () -> Boolean,
    onOnboardingFinished: () -> Unit,
) {
    composable<Destination.Onboarding> {
        OnboardingScreen(onNavigate = onNavigate, onFinished = onOnboardingFinished)
    }
    composable<Destination.Home> { HomeScreen(onNavigate = onNavigate) }
    composable<Destination.Modes> { ModesScreen(onNavigate = onNavigate) }
    composable<Destination.ModeEdit> { entry ->
        ModeEditScreen(modeId = entry.toRoute<Destination.ModeEdit>().id, onBack = onBack)
    }
    composable<Destination.History> { HistoryScreen(onNavigate = onNavigate) }
    composable<Destination.HistoryDetail> { entry ->
        HistoryDetailScreen(entryId = entry.toRoute<Destination.HistoryDetail>().id, onBack = onBack)
    }
    composable<Destination.Dictionary> { DictionaryScreen() }
    composable<Destination.More> { MoreScreen(onNavigate = onNavigate) }

    composable<Destination.ProviderList> { ProviderListScreen(onBack = onBack, onNavigate = onNavigate) }
    composable<Destination.ProviderSelections> { ProviderSelectionsScreen(onBack = onBack, onNavigate = onNavigate) }
    composable<Destination.ProviderEdit> { entry ->
        val route = entry.toRoute<Destination.ProviderEdit>()
        ProviderEditScreen(providerId = route.id, cloneFromId = route.cloneFromId, onBack = onBack, onNavigate = onNavigate)
    }

    composable<Destination.DictationSettings> { DictationSettingsScreen(onBack = onBack) }
    composable<Destination.AudioSettings> { AudioSettingsScreen(onBack = onBack) }

    composable<Destination.Enhancement> { EnhancementScreen(onBack = onBack, onNavigate = onNavigate) }
    composable<Destination.Prompts> { PromptsScreen(onBack = onBack, onNavigate = onNavigate) }
    composable<Destination.PromptEdit> { entry ->
        PromptEditScreen(profileId = entry.toRoute<Destination.PromptEdit>().id, onBack = onBack)
    }
    composable<Destination.Presets> { PresetsScreen(onBack = onBack) }

    composable<Destination.AppMappings> { AppMappingsScreen(onBack = onBack, onNavigate = onNavigate) }
    composable<Destination.AppMappingDetail> { entry ->
        AppMappingDetailScreen(packageName = entry.toRoute<Destination.AppMappingDetail>().packageName, onBack = onBack)
    }
    composable<Destination.LanguageDefaults> { LanguageDefaultsScreen(onBack = onBack) }

    composable<Destination.PrivacySafety> { PrivacySafetyScreen(onBack = onBack) }
    composable<Destination.BackupRestore> { BackupRestoreScreen(onBack = onBack, onNavigate = onNavigate) }
    composable<Destination.Insights> { InsightsScreen(onBack = onBack) }
    composable<Destination.About> {
        AboutScreen(onBack = onBack, onNavigate = onNavigate, showUpdate = checkForUpdateOnAbout())
    }
}
