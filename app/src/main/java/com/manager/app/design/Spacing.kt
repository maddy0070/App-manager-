package com.manager.app.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A 4dp-derived scale with intentional gaps. Values that do not appear here do not appear in the
 * product; the gaps are what stop the layout drifting into arbitrary padding.
 */
@Immutable
data class ManagerSpacing(
    val hair: Dp = 2.dp,
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val section: Dp = 40.dp,
    val chapter: Dp = 56.dp,
    /** The one horizontal margin every screen shares. */
    val gutter: Dp = 22.dp,
)

val LocalManagerSpacing = staticCompositionLocalOf { ManagerSpacing() }
