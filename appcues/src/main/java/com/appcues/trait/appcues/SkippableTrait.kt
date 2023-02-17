package com.appcues.trait.appcues

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appcues.AppcuesCoroutineScope
import com.appcues.R
import com.appcues.data.model.AppcuesConfigMap
import com.appcues.data.model.getConfig
import com.appcues.data.model.getConfigOrDefault
import com.appcues.trait.BackdropDecoratingTrait
import com.appcues.trait.ContainerDecoratingTrait
import com.appcues.trait.ContainerDecoratingTrait.ContainerDecoratingType
import com.appcues.trait.appcues.SkippableTrait.ButtonAppearance.Default
import com.appcues.trait.appcues.SkippableTrait.ButtonAppearance.Hidden
import com.appcues.trait.appcues.SkippableTrait.ButtonAppearance.Minimal
import com.appcues.ui.ExperienceRenderer
import kotlinx.coroutines.launch

internal class SkippableTrait(
    override val config: AppcuesConfigMap,
    private val experienceRenderer: ExperienceRenderer,
    private val appcuesCoroutineScope: AppcuesCoroutineScope,
) : ContainerDecoratingTrait, BackdropDecoratingTrait {

    companion object {

        const val TYPE = "@appcues/skippable"

        private const val CONFIG_BUTTON_APPEARANCE = "buttonAppearance"
        private const val CONFIG_IGNORE_BACKDROP_TAP = "ignoreBackdropTap"
    }

    private sealed class ButtonAppearance(val margin: Dp, val size: Dp, val padding: Dp) {

        // calculates rippleRadius based on size and padding provided
        val rippleRadius = (size / 2) - padding

        object Hidden : ButtonAppearance(0.dp, 0.dp, 0.dp)
        object Minimal : ButtonAppearance(4.dp, 32.dp, 6.dp)
        object Default : ButtonAppearance(4.dp, 48.dp, 8.dp)
    }

    override val containerComposeOrder = ContainerDecoratingType.OVERLAY

    private val buttonAppearance = when (config.getConfig<String>(CONFIG_BUTTON_APPEARANCE)) {
        "hidden" -> Hidden
        "minimal" -> Minimal
        "default" -> Default
        else -> Default
    }

    private val ignoreBackdropTap = config.getConfigOrDefault(CONFIG_IGNORE_BACKDROP_TAP, false)

    @Composable
    override fun BoxScope.DecorateContainer() {
        Spacer(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(buttonAppearance.margin)
                .size(buttonAppearance.size)
                .clip(CircleShape)
                .clickable(
                    onClick = {
                        appcuesCoroutineScope.launch {
                            experienceRenderer.dismissCurrentExperience(markComplete = false, destroyed = false)
                        }
                    },
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = false, radius = buttonAppearance.rippleRadius),
                    onClickLabel = stringResource(id = R.string.appcues_skippable_trait_dismiss)
                )
                .padding(buttonAppearance.padding)
                .clip(CircleShape)
                .drawSkippableButton(buttonAppearance)

        )
    }

    @Composable
    override fun BoxScope.BackdropDecorate(content: @Composable BoxScope.() -> Unit) {
        if (ignoreBackdropTap.not()) {
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    // add click listener but without any ripple effect.
                    .pointerInput(Unit) {
                        detectTapGestures {
                            appcuesCoroutineScope.launch {
                                experienceRenderer.dismissCurrentExperience(markComplete = false, destroyed = false)
                            }
                        }
                    },
            )
        }

        content()
    }

    /**
     * Apply different modifiers based on ButtonAppearance
     */
    private fun Modifier.drawSkippableButton(buttonAppearance: ButtonAppearance): Modifier {
        return this.then(
            when (buttonAppearance) {
                Hidden -> Modifier
                Default ->
                    Modifier
                        .background(Color(color = 0x54000000))
                        .drawBehind {
                            xShapePath(Color(color = 0xFFEFEFEF), deflateDp = 8.dp)
                                .also { drawPath(path = it, color = Color.Transparent) }
                        }
                Minimal ->
                    Modifier.drawBehind {
                        xShapePath(Color(color = 0xFFB2B2B2))
                            .also { drawPath(path = it, color = Color.Transparent, blendMode = BlendMode.Difference) }
                    }
            }
        )
    }

    /**
     * Defines a path to draw the X by drawing two lines crossing from edge to edge.
     */
    private fun DrawScope.xShapePath(color: Color, deflateDp: Dp = 0.dp): Path {
        return Path()
            .apply {
                val strokeWidth = 2.dp.toPx()
                val deflate = deflateDp.toPx()
                val sizeRect = size
                    .toRect()
                    .deflate(deflate)

                drawLine(
                    color = color,
                    start = sizeRect.topLeft,
                    end = sizeRect.bottomRight,
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = color,
                    start = sizeRect.bottomLeft,
                    end = sizeRect.topRight,
                    strokeWidth = strokeWidth,
                )
            }
    }
}
