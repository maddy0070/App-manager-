package com.manager.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import com.manager.app.data.AppEntry

/**
 * A request to open the detail surface, carrying where on screen the tap came from.
 *
 * [originBounds] is the tapped icon's rectangle in root coordinates. The detail surface uses it
 * to fly the real icon from the row into the sheet header, so the app the user touched visibly
 * becomes the app they are now looking at. Null origins (opened from a place with no icon, or
 * restored after a configuration change) fall back to a plain entrance.
 */
@Immutable
data class DetailRequest(
    val entry: AppEntry,
    val originBounds: Rect? = null,
)
