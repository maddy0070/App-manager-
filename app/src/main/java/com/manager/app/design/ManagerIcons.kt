package com.manager.app.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * One icon family, drawn by hand on a 24 grid with a single 1.7 stroke, round caps and round
 * joins. Nothing here is borrowed from a stock set, so the icons share the same drawing hand as
 * the rest of the product and never read as a third-party import.
 */
private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathData(block),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            fill = null,
        )
    }.build()

/** A control knob sitting on a rail — the settings mark is built from two of them. */
private fun PathBuilder.knob(cx: Float, cy: Float, r: Float = 2.1f) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    close()
}

private fun filledIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = androidx.compose.ui.graphics.vector.PathData(block), fill = SolidColor(Color.Black))
    }.build()

object ManagerIcons {

    val Search: ImageVector = icon("search") {
        moveTo(11f, 4.2f)
        arcToRelative(6.8f, 6.8f, 0f, true, true, 0f, 13.6f)
        arcToRelative(6.8f, 6.8f, 0f, true, true, 0f, -13.6f)
        close()
        moveTo(16.1f, 16.1f)
        lineTo(20f, 20f)
    }

    val Close: ImageVector = icon("close") {
        moveTo(6.4f, 6.4f); lineTo(17.6f, 17.6f)
        moveTo(17.6f, 6.4f); lineTo(6.4f, 17.6f)
    }

    val ChevronDown: ImageVector = icon("chevron_down") {
        moveTo(5.5f, 9.5f); lineTo(12f, 16f); lineTo(18.5f, 9.5f)
    }

    val Sort: ImageVector = icon("sort") {
        moveTo(7f, 19f); lineTo(7f, 5f)
        moveTo(3.6f, 8.4f); lineTo(7f, 5f); lineTo(10.4f, 8.4f)
        moveTo(17f, 5f); lineTo(17f, 19f)
        moveTo(13.6f, 15.6f); lineTo(17f, 19f); lineTo(20.4f, 15.6f)
    }

    /** Extract — a package leaving its container. */
    val Extract: ImageVector = icon("extract") {
        moveTo(12f, 3.6f); lineTo(12f, 14.2f)
        moveTo(8.2f, 10.6f); lineTo(12f, 14.4f); lineTo(15.8f, 10.6f)
        moveTo(4.6f, 15.4f); lineTo(4.6f, 18f)
        arcToRelative(2.4f, 2.4f, 0f, false, false, 2.4f, 2.4f)
        lineTo(17f, 20.4f)
        arcToRelative(2.4f, 2.4f, 0f, false, false, 2.4f, -2.4f)
        lineTo(19.4f, 15.4f)
    }

    val Trash: ImageVector = icon("trash") {
        moveTo(4.6f, 6.6f); lineTo(19.4f, 6.6f)
        moveTo(9.4f, 6.6f); lineTo(9.4f, 4.8f); arcToRelative(1.2f, 1.2f, 0f, false, true, 1.2f, -1.2f)
        lineTo(13.4f, 3.6f); arcToRelative(1.2f, 1.2f, 0f, false, true, 1.2f, 1.2f); lineTo(14.6f, 6.6f)
        moveTo(6.4f, 6.6f); lineTo(7.3f, 19f); arcToRelative(1.6f, 1.6f, 0f, false, false, 1.6f, 1.5f)
        lineTo(15.1f, 20.5f); arcToRelative(1.6f, 1.6f, 0f, false, false, 1.6f, -1.5f); lineTo(17.6f, 6.6f)
        moveTo(10.2f, 10.2f); lineTo(10.5f, 16.8f)
        moveTo(13.8f, 10.2f); lineTo(13.5f, 16.8f)
    }

    val Share: ImageVector = icon("share") {
        moveTo(12f, 3.8f); lineTo(12f, 14.6f)
        moveTo(8.4f, 7.4f); lineTo(12f, 3.8f); lineTo(15.6f, 7.4f)
        moveTo(6.2f, 11.4f); lineTo(5.2f, 11.4f)
        arcToRelative(1.6f, 1.6f, 0f, false, false, -1.6f, 1.6f)
        lineTo(3.6f, 18.8f); arcToRelative(1.6f, 1.6f, 0f, false, false, 1.6f, 1.6f)
        lineTo(18.8f, 20.4f); arcToRelative(1.6f, 1.6f, 0f, false, false, 1.6f, -1.6f)
        lineTo(20.4f, 13f); arcToRelative(1.6f, 1.6f, 0f, false, false, -1.6f, -1.6f)
        lineTo(17.8f, 11.4f)
    }

    val Check: ImageVector = icon("check") {
        moveTo(5f, 12.6f); lineTo(9.8f, 17.2f); lineTo(19f, 7f)
    }

    val Refresh: ImageVector = icon("refresh") {
        moveTo(20f, 12f)
        arcToRelative(8f, 8f, 0f, true, true, -2.4f, -5.7f)
        moveTo(19.6f, 3.4f); lineTo(19.6f, 7.4f); lineTo(15.6f, 7.4f)
    }

    val Clock: ImageVector = icon("clock") {
        moveTo(12f, 3.8f); arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, 16.4f)
        arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, -16.4f); close()
        moveTo(12f, 7.6f); lineTo(12f, 12.3f); lineTo(15.4f, 14.2f)
    }

    /** Layers — the product's own motif, reused for the "installed" idea. */
    val Layers: ImageVector = icon("layers") {
        moveTo(12f, 3.4f); lineTo(20.6f, 8f); lineTo(12f, 12.6f); lineTo(3.4f, 8f); close()
        moveTo(4.6f, 12.2f); lineTo(12f, 16.2f); lineTo(19.4f, 12.2f)
        moveTo(4.6f, 16.2f); lineTo(12f, 20.2f); lineTo(19.4f, 16.2f)
    }

    val Grid: ImageVector = icon("grid") {
        moveTo(5f, 4.4f); lineTo(9.4f, 4.4f); arcToRelative(0.6f, 0.6f, 0f, false, true, 0.6f, 0.6f)
        lineTo(10f, 9.4f); arcToRelative(0.6f, 0.6f, 0f, false, true, -0.6f, 0.6f)
        lineTo(5f, 10f); arcToRelative(0.6f, 0.6f, 0f, false, true, -0.6f, -0.6f)
        lineTo(4.4f, 5f); arcToRelative(0.6f, 0.6f, 0f, false, true, 0.6f, -0.6f); close()
        moveTo(14.6f, 4.4f); lineTo(19f, 4.4f); arcToRelative(0.6f, 0.6f, 0f, false, true, 0.6f, 0.6f)
        lineTo(19.6f, 9.4f); arcToRelative(0.6f, 0.6f, 0f, false, true, -0.6f, 0.6f)
        lineTo(14.6f, 10f); arcToRelative(0.6f, 0.6f, 0f, false, true, -0.6f, -0.6f)
        lineTo(14f, 5f); arcToRelative(0.6f, 0.6f, 0f, false, true, 0.6f, -0.6f); close()
        moveTo(5f, 14f); lineTo(9.4f, 14f); arcToRelative(0.6f, 0.6f, 0f, false, true, 0.6f, 0.6f)
        lineTo(10f, 19f); arcToRelative(0.6f, 0.6f, 0f, false, true, -0.6f, 0.6f)
        lineTo(5f, 19.6f); arcToRelative(0.6f, 0.6f, 0f, false, true, -0.6f, -0.6f)
        lineTo(4.4f, 14.6f); arcToRelative(0.6f, 0.6f, 0f, false, true, 0.6f, -0.6f); close()
        moveTo(14.6f, 14f); lineTo(19f, 14f); arcToRelative(0.6f, 0.6f, 0f, false, true, 0.6f, 0.6f)
        lineTo(19.6f, 19f); arcToRelative(0.6f, 0.6f, 0f, false, true, -0.6f, 0.6f)
        lineTo(14.6f, 19.6f); arcToRelative(0.6f, 0.6f, 0f, false, true, -0.6f, -0.6f)
        lineTo(14f, 14.6f); arcToRelative(0.6f, 0.6f, 0f, false, true, 0.6f, -0.6f); close()
    }

    val Pulse: ImageVector = icon("pulse") {
        moveTo(3.6f, 13.4f); lineTo(7.4f, 13.4f); lineTo(9.6f, 6.4f); lineTo(13.2f, 18f)
        lineTo(15.6f, 13.4f); lineTo(20.4f, 13.4f)
    }

    val Settings: ImageVector = icon("settings") {
        moveTo(4.2f, 7.6f); lineTo(19.8f, 7.6f)
        moveTo(4.2f, 16.4f); lineTo(19.8f, 16.4f)
        knob(14.6f, 7.6f, 2.3f)
        knob(9.4f, 16.4f, 2.3f)
    }

    /** System app marker — a shield built from the same squircle language. */
    val System: ImageVector = icon("system") {
        moveTo(12f, 3.4f); lineTo(19.2f, 6.2f); lineTo(19.2f, 11.6f)
        curveToRelative(0f, 4f, -3f, 7.2f, -7.2f, 9f)
        curveToRelative(-4.2f, -1.8f, -7.2f, -5f, -7.2f, -9f)
        lineTo(4.8f, 6.2f); close()
    }

    val Person: ImageVector = icon("person") {
        moveTo(12f, 4.4f); arcToRelative(3.6f, 3.6f, 0f, true, true, 0f, 7.2f)
        arcToRelative(3.6f, 3.6f, 0f, true, true, 0f, -7.2f); close()
        moveTo(4.8f, 20f)
        curveToRelative(0.4f, -3.7f, 3.4f, -5.8f, 7.2f, -5.8f)
        reflectiveCurveToRelative(6.8f, 2.1f, 7.2f, 5.8f)
    }

    val Storage: ImageVector = icon("storage") {
        moveTo(12f, 3.6f)
        curveToRelative(4.2f, 0f, 7.4f, 1.1f, 7.4f, 2.5f)
        reflectiveCurveToRelative(-3.2f, 2.5f, -7.4f, 2.5f)
        reflectiveCurveToRelative(-7.4f, -1.1f, -7.4f, -2.5f)
        reflectiveCurveToRelative(3.2f, -2.5f, 7.4f, -2.5f); close()
        moveTo(4.6f, 6.1f); lineTo(4.6f, 17.9f)
        curveToRelative(0f, 1.4f, 3.2f, 2.5f, 7.4f, 2.5f)
        reflectiveCurveToRelative(7.4f, -1.1f, 7.4f, -2.5f)
        lineTo(19.4f, 6.1f)
        moveTo(4.6f, 12f)
        curveToRelative(0f, 1.4f, 3.2f, 2.5f, 7.4f, 2.5f)
        reflectiveCurveToRelative(7.4f, -1.1f, 7.4f, -2.5f)
    }

    val Info: ImageVector = icon("info") {
        moveTo(12f, 3.8f); arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, 16.4f)
        arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, -16.4f); close()
        moveTo(12f, 11f); lineTo(12f, 16.2f)
        moveTo(12f, 7.6f); lineTo(12f, 8.2f)
    }

    val Alert: ImageVector = icon("alert") {
        moveTo(12f, 3.8f); arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, 16.4f)
        arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, -16.4f); close()
        moveTo(12f, 7.6f); lineTo(12f, 12.9f)
        moveTo(12f, 15.9f); lineTo(12f, 16.5f)
    }

    val ArrowUpRight: ImageVector = icon("arrow_up_right") {
        moveTo(7f, 17f); lineTo(17f, 7f)
        moveTo(8.6f, 7f); lineTo(17f, 7f); lineTo(17f, 15.4f)
    }

    val Package: ImageVector = icon("package") {
        moveTo(12f, 3.6f); lineTo(20f, 7.8f); lineTo(20f, 16.2f); lineTo(12f, 20.4f)
        lineTo(4f, 16.2f); lineTo(4f, 7.8f); close()
        moveTo(4f, 7.8f); lineTo(12f, 12f); lineTo(20f, 7.8f)
        moveTo(12f, 12f); lineTo(12f, 20.4f)
    }

    val Split: ImageVector = icon("split") {
        moveTo(12f, 3.8f); lineTo(12f, 8.4f)
        moveTo(12f, 8.4f); curveToRelative(0f, 2.4f, -6.4f, 1.6f, -6.4f, 4.6f)
        lineTo(5.6f, 20.2f)
        moveTo(12f, 8.4f); curveToRelative(0f, 2.4f, 6.4f, 1.6f, 6.4f, 4.6f)
        lineTo(18.4f, 20.2f)
        moveTo(12f, 13.2f); lineTo(12f, 20.2f)
    }

    val CheckSolid: ImageVector = filledIcon("check_solid") {
        moveTo(9.55f, 17.6f)
        lineTo(4.4f, 12.45f)
        lineTo(6.0f, 10.85f)
        lineTo(9.55f, 14.4f)
        lineTo(18.0f, 5.95f)
        lineTo(19.6f, 7.55f)
        close()
    }
}
