package com.manager.app.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.manager.app.R

/**
 * Both families ship as variable fonts, so a single file covers the whole weight range and every
 * weight in the scale is a real instance rather than a synthesised bold.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight): Font = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Space Grotesk — display voice. Chosen for its confident, slightly technical numerals. */
private val Display = FontFamily(
    variableFont(R.font.space_grotesk, FontWeight.Light),
    variableFont(R.font.space_grotesk, FontWeight.Normal),
    variableFont(R.font.space_grotesk, FontWeight.Medium),
    variableFont(R.font.space_grotesk, FontWeight.SemiBold),
    variableFont(R.font.space_grotesk, FontWeight.Bold),
)

/** Inter — text voice. Carries every piece of metadata, at small sizes, without fatigue. */
private val Text = FontFamily(
    variableFont(R.font.inter, FontWeight.Normal),
    variableFont(R.font.inter, FontWeight.Medium),
    variableFont(R.font.inter, FontWeight.SemiBold),
    variableFont(R.font.inter, FontWeight.Bold),
)

private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun display(size: Int, lineHeight: Int, weight: FontWeight, tracking: Float) = TextStyle(
    fontFamily = Display,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimBoth,
    fontFeatureSettings = "tnum, ss01",
)

private fun text(size: Float, lineHeight: Int, weight: FontWeight, tracking: Float) = TextStyle(
    fontFamily = Text,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimBoth,
)

@Immutable
data class ManagerTypography(
    /** Onboarding headline. One per screen, never repeated. */
    val displayXl: TextStyle = display(52, 54, FontWeight.Medium, -2.0f),
    val displayL: TextStyle = display(40, 43, FontWeight.Medium, -1.5f),
    /** The dashboard's hero figures. */
    val displayM: TextStyle = display(32, 34, FontWeight.Medium, -1.1f),
    val displayS: TextStyle = display(24, 27, FontWeight.Medium, -0.7f),
    val titleL: TextStyle = display(21, 26, FontWeight.Medium, -0.5f),
    val titleM: TextStyle = display(17, 22, FontWeight.Medium, -0.3f),
    /** App names, list primaries. */
    val strong: TextStyle = text(15.5f, 20, FontWeight.SemiBold, -0.15f),
    val body: TextStyle = text(14.5f, 21, FontWeight.Normal, 0f),
    val bodyS: TextStyle = text(13f, 18, FontWeight.Normal, 0f),
    /** Metadata rows, secondary lines. */
    val meta: TextStyle = text(12.5f, 16, FontWeight.Medium, 0.05f),
    val metaS: TextStyle = text(11.5f, 14, FontWeight.Medium, 0.1f),
    /** Section eyebrows. Always uppercase at the call site. */
    val eyebrow: TextStyle = text(11f, 13, FontWeight.SemiBold, 1.1f),
    val label: TextStyle = text(14f, 17, FontWeight.SemiBold, -0.1f),
    val labelS: TextStyle = text(12.5f, 15, FontWeight.SemiBold, 0f),
    /** Numeric readouts inside visualisations. */
    val numeric: TextStyle = display(15, 18, FontWeight.Medium, -0.3f),
    val numericS: TextStyle = display(13, 15, FontWeight.Medium, -0.1f),
)

val LocalManagerTypography = staticCompositionLocalOf { ManagerTypography() }
