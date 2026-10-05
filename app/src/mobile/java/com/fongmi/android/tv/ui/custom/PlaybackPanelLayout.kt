package com.fongmi.android.tv.ui.custom

import kotlin.math.max
import kotlin.math.min

/** Geometry in local dp: short menus fit their content, long menus scroll inside the cap. */
internal object PlaybackPanelLayout {
    data class Bounds(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val above: Boolean, val rowHeight: Float, val headerHeight: Float,
        val footerHeight: Float, val scroll: Boolean
    )

    @JvmStatic
    fun calculate(
        width: Float, height: Float, anchorX: Float, anchorTop: Float, anchorBottom: Float,
        topInset: Float, bottomInset: Float, preferredWidth: Float, maximumHeight: Float,
        count: Int, fontScale: Float, hasTitle: Boolean, hasSubtitles: Boolean = false,
        compactRows: Int = 0
    ): Bounds {
        val marginX = min(12f, width.coerceAtLeast(1f) / 10f)
        val marginY = min(12f, height.coerceAtLeast(1f) / 10f)
        val safeTop = (max(0f, topInset) + marginY).coerceAtMost(max(0f, height - 1f))
        val safeBottom = (height - max(0f, bottomInset) - marginY).coerceIn(safeTop + 1f, max(safeTop + 1f, height))
        val available = safeBottom - safeTop
        val scale = max(1f, fontScale)
        val row = if (hasSubtitles) max(76f, 22f + 46f * scale) else max(48f, 24f + 18f * scale)
        var header = if (hasTitle) max(44f, 16f + 30f * scale) else 0f
        val oneRow = 20f + header + (if (header > 0f) 8f else 0f) + row
        val limit = min(min(maximumHeight.coerceIn(96f, 320f), available), max(oneRow, available * .60f))
        val aboveSpace = (anchorTop - 6f - safeTop).coerceIn(0f, available)
        val belowSpace = (safeBottom - anchorBottom - 6f).coerceIn(0f, available)
        val above = aboveSpace >= min(oneRow, limit) || aboveSpace >= belowSpace
        // If neither side fits even one option, use the safe area instead of a thin unusable strip.
        val room = (if (max(aboveSpace, belowSpace) < 20f + row) available
            else if (above) aboveSpace else belowSpace).coerceAtLeast(1f)
        val cap = min(limit, room)
        // In very short floating windows, prioritize a usable option row over the heading.
        if (cap < oneRow) header = 0f
        val compact = compactRows.coerceIn(0, max(1, count))
        val regular = max(1, count) - compact
        val rows = regular * row + compact * 40f + max(0, count - 1) * 4f
        val chrome = 20f + header + if (header > 0f) 8f else 0f
        val desired = chrome + rows
        val actual = min(desired, cap).coerceAtLeast(1f)
        val scroll = desired > actual + .5f
        val footerSize = max(20f, 14f * scale + 6f)
        val footer = if (scroll && actual >= chrome + row + footerSize) footerSize else 0f
        val panelWidth = min(preferredWidth.coerceIn(176f, if (hasSubtitles) 320f else 260f), max(1f, width - 2f * marginX))
        val x = (anchorX - panelWidth / 2f).coerceIn(marginX, max(marginX, width - marginX - panelWidth))
        val y = (if (above) anchorTop - 6f - actual else anchorBottom + 6f)
            .coerceIn(safeTop, max(safeTop, safeBottom - actual))
        return Bounds(x, y, panelWidth, actual, above, row, header, footer, scroll)
    }
}
