package com.github.shekohex.whisperpp.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.github.shekohex.whisperpp.R
import com.github.shekohex.whisperpp.domain.analytics.Milestones
import com.github.shekohex.whisperpp.ui.analytics.StatFormat
import com.github.shekohex.whisperpp.ui.components.GradientHeroSurface
import com.github.shekohex.whisperpp.ui.components.PreviewSurface
import com.github.shekohex.whisperpp.ui.components.ThemePreviews
import com.github.shekohex.whisperpp.ui.theme.Spacing

const val DASHBOARD_HERO_TAG = "home_dashboard_hero"

/** Home hero (§4.9): lifetime time saved, words dictated against book milestones, "View insights". */
@Composable
fun DashboardHeroCard(
    lifetimeWords: Long,
    timeSavedSeconds: Long,
    onViewInsights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GradientHeroSurface(modifier = modifier.testTag(DASHBOARD_HERO_TAG)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (lifetimeWords <= 0) {
                Text(
                    text = stringResource(R.string.home_hero_empty),
                    style = MaterialTheme.typography.titleLarge,
                )
            } else {
                HeroNumbers(lifetimeWords, timeSavedSeconds)
            }
            Spacer(Modifier.size(Spacing.xs))
            FilledTonalButton(onClick = onViewInsights) {
                Text(stringResource(R.string.home_hero_view_insights))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(start = Spacing.sm).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroNumbers(lifetimeWords: Long, timeSavedSeconds: Long) {
    val locale = LocalLocale.current.platformLocale
    val saved = StatFormat.formatDuration(
        totalSeconds = timeSavedSeconds,
        hoursMinutesTemplate = stringResource(R.string.home_duration_hours_minutes),
        minutesTemplate = stringResource(R.string.home_duration_minutes),
        locale = locale,
    )
    Text(
        text = boldArgument(
            template = stringResource(R.string.home_hero_saved),
            value = saved,
            style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.headlineLarge.fontSize),
        ),
        style = MaterialTheme.typography.titleLarge,
    )
    val words = StatFormat.formatCompact(lifetimeWords, locale)
    val progress = Milestones.progress(lifetimeWords)
    val reached = progress.lastReached
    val line = if (reached != null) {
        stringResource(R.string.home_hero_equivalent, words, milestoneTitle(reached))
    } else {
        stringResource(
            R.string.home_hero_progress,
            words,
            StatFormat.formatCompact(progress.wordsToGo, locale),
            milestoneTitle(progress.next),
        )
    }
    Text(text = line, style = MaterialTheme.typography.bodyMedium)
    if (reached == null) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun milestoneTitle(milestone: Milestones.Milestone): String {
    val title = stringResource(milestone.book.titleRes())
    return if (milestone.multiplier > 1) {
        stringResource(R.string.home_milestone_times, title, milestone.multiplier)
    } else {
        title
    }
}

@StringRes
private fun Milestones.Book.titleRes(): Int = when (this) {
    Milestones.Book.THE_METAMORPHOSIS -> R.string.home_book_metamorphosis
    Milestones.Book.ANIMAL_FARM -> R.string.home_book_animal_farm
    Milestones.Book.THE_GREAT_GATSBY -> R.string.home_book_great_gatsby
    Milestones.Book.HOMERS_ILIAD -> R.string.home_book_iliad
    Milestones.Book.HOMERS_ODYSSEY -> R.string.home_book_odyssey
    Milestones.Book.ILIAD_AND_ODYSSEY -> R.string.home_book_iliad_and_odyssey
    Milestones.Book.WAR_AND_PEACE -> R.string.home_book_war_and_peace
}

/** [template] with its `%1$s` replaced by [value] in [style]; translators may move the placeholder. */
private fun boldArgument(template: String, value: String, style: SpanStyle): AnnotatedString {
    val placeholder = "%1\$s"
    val index = template.indexOf(placeholder)
    if (index < 0) return AnnotatedString(template)
    return buildAnnotatedString {
        append(template.substring(0, index))
        withStyle(style) { append(value) }
        append(template.substring(index + placeholder.length))
    }
}

@ThemePreviews
@Composable
private fun DashboardHeroCardPreview() {
    PreviewSurface {
        DashboardHeroCard(
            lifetimeWords = 165_800,
            timeSavedSeconds = 41 * 3_600L + 30 * 60L,
            onViewInsights = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@ThemePreviews
@Composable
private fun DashboardHeroCardBelowFirstMilestonePreview() {
    PreviewSurface {
        DashboardHeroCard(
            lifetimeWords = 8_880,
            timeSavedSeconds = 2 * 3_600L + 5 * 60L,
            onViewInsights = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@ThemePreviews
@Composable
private fun DashboardHeroCardEmptyPreview() {
    PreviewSurface {
        DashboardHeroCard(lifetimeWords = 0, timeSavedSeconds = 0, onViewInsights = {}, modifier = Modifier.padding(Spacing.lg))
    }
}
