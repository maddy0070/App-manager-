package com.manager.app.ui.overlays

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manager.app.data.ApkExtractor
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape
import com.manager.app.design.components.ActionRow
import com.manager.app.design.components.ButtonTone
import com.manager.app.design.components.Hairline
import com.manager.app.design.components.ManagerButton
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.ProgressRail
import com.manager.app.design.components.Txt
import com.manager.app.ui.ExtractionState
import com.manager.app.util.Format

/**
 * Extraction, start to finish, in one surface.
 *
 * While it runs it shows the file being written and honest overall progress. When it ends it
 * becomes a receipt: what succeeded, what did not and exactly why, with the resulting files one
 * tap from being shared or opened. Nothing here reports success it did not observe.
 */
@Composable
fun ExtractionSurface(
    state: ExtractionState?,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onShare: (ApkExtractor.Outcome.Success) -> Unit,
    onOpen: (ApkExtractor.Outcome.Success) -> Unit,
) {
    val colors = ManagerTheme.colors
    val visible = state != null

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = if (colors.isLight) 0.36f else 0.58f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { if (state?.finished == true) onDismiss() },
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 400f)) { it / 2 } +
                fadeIn(tween(200)) +
                scaleIn(initialScale = 0.95f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)),
            exit = slideOutVertically(tween(240, easing = ManagerTheme.motion.exit)) { it / 3 } +
                fadeOut(tween(180)) + scaleOut(targetScale = 0.96f),
        ) {
            if (state != null) {
                ExtractionPanel(state, onDismiss, onCancel, onShare, onOpen)
            }
        }
    }
}

@Composable
private fun ExtractionPanel(
    state: ExtractionState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onShare: (ApkExtractor.Outcome.Success) -> Unit,
    onOpen: (ApkExtractor.Outcome.Success) -> Unit,
) {
    val colors = ManagerTheme.colors
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .shadow(
                elevation = 32.dp,
                shape = ManagerTheme.shapes.sheet,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.48f),
                spotColor = colors.ink.copy(alpha = 0.34f),
            )
            .clip(ManagerTheme.shapes.sheet)
            .background(colors.surface),
    ) {
        Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 28.dp)) {
            AnimatedContent(
                targetState = state.finished,
                transitionSpec = {
                    (fadeIn(tween(280, delayMillis = 80)) + scaleIn(initialScale = 0.92f, animationSpec = tween(360, delayMillis = 80)))
                        .togetherWith(fadeOut(tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(200)))
                },
                label = "extractionPhase",
            ) { finished ->
                if (finished) FinishedHeader(state) else RunningHeader(state)
            }
        }

        if (state.results.isNotEmpty() && state.finished) {
            Spacer(Modifier.height(20.dp))
            Hairline(Modifier.padding(horizontal = 26.dp))
            Column(
                Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                state.results.forEach { outcome ->
                    when (outcome) {
                        is ApkExtractor.Outcome.Success -> SuccessRow(outcome, onShare, onOpen)
                        is ApkExtractor.Outcome.Failure -> FailureRow(outcome)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Hairline()
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + bottomInset)) {
            if (state.finished) {
                ManagerButton("Done", onDismiss, fillWidth = true)
            } else {
                ManagerButton("Cancel", onCancel, tone = ButtonTone.Outline, fillWidth = true)
            }
        }
    }
}

@Composable
private fun RunningHeader(state: ExtractionState) {
    val colors = ManagerTheme.colors
    val current = state.current
    Column {
        Txt(
            if (state.total == 1) "EXTRACTING" else "EXTRACTING ${state.index + 1} OF ${state.total}",
            style = ManagerTheme.type.eyebrow,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(12.dp))
        Txt(
            current?.label ?: "Preparing",
            style = ManagerTheme.type.titleL,
            color = colors.ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Txt(
            when {
                current == null -> "Reading package paths"
                current.isSplit -> "${current.splitCount + 1} APK parts into one archive"
                else -> Format.bytes(current.apkBytes)
            },
            style = ManagerTheme.type.metaS,
            color = colors.inkTertiary,
            maxLines = 1,
        )
        Spacer(Modifier.height(22.dp))
        ProgressRail(state.overallProgress, height = 5.dp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt("Downloads / Manager", style = ManagerTheme.type.metaS, color = colors.inkTertiary)
            Txt(Format.percent(state.overallProgress), style = ManagerTheme.type.metaS, color = colors.inkSecondary)
        }
    }
}

@Composable
private fun FinishedHeader(state: ExtractionState) {
    val colors = ManagerTheme.colors
    val ok = state.succeeded.size
    val bad = state.failed.size

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(SquircleShape(15.dp, 0.8f))
                .background(if (bad > 0 && ok == 0) colors.emberSoft else colors.signalSoft),
            contentAlignment = Alignment.Center,
        ) {
            ManagerIcon(
                if (bad > 0 && ok == 0) ManagerIcons.Alert else ManagerIcons.Check,
                null,
                tint = if (bad > 0 && ok == 0) colors.ember else colors.signal,
                size = 21.dp,
            )
        }
        Spacer(Modifier.width(15.dp))
        Column {
            Txt(
                when {
                    state.cancelled -> "Extraction stopped"
                    ok > 0 && bad == 0 -> if (ok == 1) "APK extracted" else "$ok APKs extracted"
                    ok > 0 -> "$ok of ${state.total} extracted"
                    else -> "Extraction failed"
                },
                style = ManagerTheme.type.titleM,
                color = colors.ink,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Txt(
                if (ok > 0) "Saved to Downloads / Manager" else "Nothing was written",
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SuccessRow(
    outcome: ApkExtractor.Outcome.Success,
    onShare: (ApkExtractor.Outcome.Success) -> Unit,
    onOpen: (ApkExtractor.Outcome.Success) -> Unit,
) {
    val colors = ManagerTheme.colors
    Column(Modifier.padding(horizontal = 26.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ManagerIcon(
                if (outcome.isArchive) ManagerIcons.Split else ManagerIcons.Package,
                null,
                tint = colors.signal,
                size = 15.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Txt(
                    outcome.displayName,
                    style = ManagerTheme.type.labelS,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Spacer(Modifier.height(3.dp))
                Txt(
                    if (outcome.isArchive) {
                        "${Format.bytes(outcome.bytes)} · ${outcome.partCount} parts"
                    } else {
                        Format.bytes(outcome.bytes)
                    },
                    style = ManagerTheme.type.metaS,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ActionRow(spacing = 8.dp) {
            ManagerButton("Share", { onShare(outcome) }, tone = ButtonTone.Quiet, compact = true, icon = ManagerIcons.Share)
            ManagerButton("Open", { onOpen(outcome) }, tone = ButtonTone.Outline, compact = true)
        }
    }
}

@Composable
private fun FailureRow(outcome: ApkExtractor.Outcome.Failure) {
    val colors = ManagerTheme.colors
    Row(
        Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ManagerIcon(ManagerIcons.Alert, null, tint = colors.ember, size = 15.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Txt(outcome.label, style = ManagerTheme.type.labelS, color = colors.ink, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Txt(outcome.reason, style = ManagerTheme.type.metaS, color = colors.inkSecondary)
        }
    }
}
