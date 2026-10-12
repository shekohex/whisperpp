package com.github.shekohex.whisperpp.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.core.permissions.KeyboardSetup
import com.github.shekohex.whisperpp.ui.components.RefreshOnResume
import com.github.shekohex.whisperpp.ui.components.contentWidthLimit
import com.github.shekohex.whisperpp.ui.components.rememberMicPermissionAction
import com.github.shekohex.whisperpp.ui.navigation.Destination
import com.github.shekohex.whisperpp.ui.settings.common.containerViewModel
import com.github.shekohex.whisperpp.ui.theme.Spacing
import kotlinx.coroutines.launch

/** First-run flow (§4.6). Pager position survives rotation and the round trip to provider setup. */
@Composable
fun OnboardingScreen(
    onNavigate: (Destination) -> Unit,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = containerViewModel { container, _ -> OnboardingViewModel(container) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    val pages = OnboardingPage.entries
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    RefreshOnResume(viewModel::refresh)
    LaunchedEffect(completed) {
        if (completed) onFinished()
    }
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.contentWidthLimit().fillMaxSize()) {
                SkipRow(
                    visible = pagerState.currentPage > 0,
                    onSkip = viewModel::complete,
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("onboarding_pager"),
                ) { index ->
                    OnboardingPageContent(
                        page = pages[index],
                        state = state,
                        onNavigate = onNavigate,
                        onMicRequested = viewModel::markMicRequested,
                        onMicResult = { viewModel.refresh() },
                        onFinish = viewModel::complete,
                    )
                }
                BottomBar(
                    pagerState = pagerState,
                    onBack = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    onFinish = viewModel::complete,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    state: OnboardingUiState,
    onNavigate: (Destination) -> Unit,
    onMicRequested: () -> Unit,
    onMicResult: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val status = state.status
    when (page) {
        OnboardingPage.WELCOME -> WelcomePage()
        OnboardingPage.ENABLE_KEYBOARD -> EnableKeyboardPage(
            enabled = status.imeEnabled,
            onOpenSettings = { KeyboardSetup.openInputMethodSettings(context) },
        )
        OnboardingPage.SELECT_KEYBOARD -> SelectKeyboardPage(
            enabled = status.imeEnabled,
            selected = status.imeSelected,
            onShowPicker = { KeyboardSetup.showInputMethodPicker(context) },
        )
        OnboardingPage.MICROPHONE -> {
            val micAction = rememberMicPermissionAction(
                granted = status.micGranted,
                requestedBefore = status.micRequestedBefore,
                onRequested = onMicRequested,
                onResult = onMicResult,
            )
            MicrophonePage(
                granted = status.micGranted,
                permanentlyDenied = micAction.permanentlyDenied,
                onAllow = micAction.onClick,
            )
        }
        OnboardingPage.PROVIDER -> ProviderPage(
            configured = status.sttProviderConfigured,
            hasProviders = state.hasProviders,
            onConnect = {
                onNavigate(if (state.hasProviders) Destination.ProviderSelections else Destination.ProviderEdit())
            },
            onLater = onFinish,
        )
    }
}

@Composable
private fun SkipRow(visible: Boolean, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (visible) {
            TextButton(onClick = onSkip, modifier = Modifier.testTag("onboarding_skip")) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}

@Composable
private fun BottomBar(
    pagerState: PagerState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val current = pagerState.currentPage
    val isLast = current == pagerState.pageCount - 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (current > 0) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.size(Spacing.sm))
                    Text(stringResource(R.string.onboarding_back))
                }
            }
        }
        PageDots(current = current, count = pagerState.pageCount)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (isLast) {
                Button(onClick = onFinish, modifier = Modifier.testTag("onboarding_finish")) {
                    Text(stringResource(R.string.onboarding_finish))
                }
            } else {
                Button(onClick = onNext, modifier = Modifier.testTag("onboarding_next")) {
                    Text(stringResource(R.string.onboarding_next))
                    Spacer(Modifier.size(Spacing.sm))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    val description = stringResource(R.string.onboarding_page_indicator, current + 1, count)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val color = if (index == current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .background(color, CircleShape),
            )
        }
    }
}
