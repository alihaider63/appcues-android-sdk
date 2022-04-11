package com.appcues.ui.debugger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun BoxScope.DebuggerPanel(debuggerState: MutableDebuggerState) {
    AnimatedVisibility(
        visibleState = debuggerState.isExpanded,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(color = 0x30000000))
        )
    }

    AnimatedVisibility(
        visibleState = debuggerState.isExpanded,
        enter = enterTransition(),
        exit = exitTransition(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    ambientColor = Color(color = 0xFF5C5CFF),
                    spotColor = Color(color = 0xFF000000)
                )
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .height(debuggerState.getExpandedContainerHeight())
                .fillMaxWidth()
                .background(Color(color = 0xFFFFFFFF)),
            contentAlignment = Alignment.TopCenter
        ) {
            // content of the debugger view will be placed here
        }
    }
}

private fun enterTransition(): EnterTransition {
    return slideInVertically(tween(durationMillis = 250)) { it }
}

private fun exitTransition(): ExitTransition {
    return slideOutVertically(tween(durationMillis = 200)) { it } +
        fadeOut(tween(durationMillis = 150))
}
