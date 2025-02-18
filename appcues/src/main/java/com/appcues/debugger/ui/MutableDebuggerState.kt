package com.appcues.debugger.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.appcues.debugger.DebugMode
import com.appcues.debugger.model.DebuggerToast
import com.appcues.debugger.screencapture.Capture
import kotlin.math.roundToInt

internal class MutableDebuggerState(
    val debugMode: DebugMode,
    private val density: Density,
    private val isCreating: Boolean,
    val fabSize: Dp = 56.dp
) {

    companion object {

        private const val GRID_SCREEN_COUNT = 5
        private const val GRID_FAB_POSITION = 4
        private val EXPANDED_CONTAINER_TOP_PADDING = 24.dp

        private var lastAnchoredPosition = Offset(0f, 0f)
    }

    val isVisible = MutableTransitionState(isCreating.not())
    val isDragging = MutableTransitionState(false)
    val isExpanded = MutableTransitionState(false)
    val screenCapture = mutableStateOf<Capture?>(null)
    val isPaused = mutableStateOf(value = false)
    val toast = MutableTransitionState<DebuggerToast?>(null)

    val fabXOffset = mutableFloatStateOf(value = -1f)
    val fabYOffset = mutableFloatStateOf(value = -1f)
    val isDraggingOverDismiss = mutableStateOf(value = false)

    private var boxSize = IntSize(0, 0)
    private var dismissRect = Rect(Offset(0f, 0f), Size(0f, 0f))
    private var fabRect = Rect(Offset(0f, 0f), Size(0f, 0f))

    fun initFabOffsets(size: IntSize) {
        if (size != boxSize) {
            // if we have a size change (meaning the debugger was started or the orienation or size changed otherwise)
            // we calculate the initial position.
            boxSize = size
            with(density) {
                updatePosition(
                    x = size.width.toFloat() - fabSize.toPx(),
                    y = ((size.height.toFloat() / GRID_SCREEN_COUNT) * GRID_FAB_POSITION) - fabSize.toPx()
                )
            }
        } else {
            // else we just use the last known position we have
            updatePosition(
                x = lastAnchoredPosition.x,
                y = lastAnchoredPosition.y
            )
        }
    }

    fun getFabPositionAsIntOffset(): IntOffset? {
        // in case the value is initial value we return null
        if (fabXOffset.floatValue < 0f || fabYOffset.floatValue < 0f) return null

        return IntOffset(fabXOffset.floatValue.roundToInt(), fabYOffset.floatValue.roundToInt())
    }

    fun getEventsProperties(): EventsProperties {
        with(density) {
            val isStart = fabXOffset.floatValue + (fabSize.toPx() / 2) < boxSize.width / 2
            val drawTop = fabYOffset.floatValue + (fabSize.toPx() / 2) > boxSize.height / 2

            // calculate X/Y based on anchor position
            val offset = when {
                isStart && drawTop -> {
                    IntOffset(0, fabYOffset.floatValue.toInt() - boxSize.height)
                }
                isStart -> {
                    IntOffset(0, fabYOffset.floatValue.toInt() + fabSize.toPx().toInt())
                }
                drawTop -> {
                    IntOffset(0, fabYOffset.floatValue.toInt() - boxSize.height)
                }
                else -> {
                    IntOffset(0, fabYOffset.floatValue.toInt() + fabSize.toPx().toInt())
                }
            }

            return EventsProperties(
                positionOffset = offset,
                anchorToStart = isStart,
                drawTop = drawTop
            )
        }
    }

    data class EventsProperties(
        val positionOffset: IntOffset,
        val anchorToStart: Boolean,
        val drawTop: Boolean
    )

    fun dragFabOffsets(dragAmount: Offset) {
        with(density) {
            updatePosition(
                x = (fabXOffset.floatValue + dragAmount.x).coerceIn(0f, boxSize.width.toFloat() - fabSize.toPx()),
                y = (fabYOffset.floatValue + dragAmount.y).coerceIn(0f, boxSize.height.toFloat() - fabSize.toPx())
            )

            isDraggingOverDismiss.value = dismissRect.overlaps(fabRect)
        }
    }

    private fun updatePosition(x: Float, y: Float) {
        fabXOffset.floatValue = x
        fabYOffset.floatValue = y
        updateFabRect(x, y)

        // update global offset value with latest update
        lastAnchoredPosition = Offset(getAnchorX(x), y)
    }

    private fun getAnchorX(x: Float): Float {
        return with(density) {
            val centerFabX = x + (fabSize.toPx() / 2)
            val centerScreenX = boxSize.width / 2
            if (centerFabX < centerScreenX) {
                0f
            } else {
                boxSize.width - fabSize.toPx()
            }
        }
    }

    private fun updateFabRect(x: Float, y: Float) {
        with(density) {
            fabRect = Rect(
                offset = Offset(x, y),
                size = Size(fabSize.toPx(), fabSize.toPx())
            )
        }
    }

    fun initDismissAreaRect(layoutCoordinates: LayoutCoordinates) {
        with(density) {
            dismissRect = Rect(
                offset = layoutCoordinates.positionInRoot(),
                size = Size(layoutCoordinates.size.width.toFloat(), layoutCoordinates.size.height.toFloat())
            ).run {
                // reduces the rectangle size so we need to drop the
                // fab closer to the center to dismiss it
                deflate(28.dp.toPx())
            }
        }
    }

    fun getDismissAreaTargetXOffset(): Float {
        return dismissRect.center.x - fabRect.size.width / 2
    }

    fun getDismissAreaTargetYOffset(): Float {
        return dismissRect.center.y - fabRect.size.height / 2
    }

    fun getExpandedContainerHeight(): Dp {
        return with(density) {
            boxSize.height.toDp() - (fabSize / 2) - EXPANDED_CONTAINER_TOP_PADDING
        }
    }

    fun getExpandedFabAnchor(): Offset {
        return with(density) {
            Offset((boxSize.width - fabSize.toPx()) / 2, EXPANDED_CONTAINER_TOP_PADDING.toPx())
        }
    }

    fun getLastAnchoredPosition(): Offset {
        return lastAnchoredPosition
    }
}
