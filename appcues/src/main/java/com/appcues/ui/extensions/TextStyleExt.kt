package com.appcues.ui.extensions

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.appcues.data.model.ExperiencePrimitive.TextSpanPrimitive
import com.appcues.data.model.styling.ComponentStyle

internal fun TextStyle.applyStyle(style: ComponentStyle, context: Context, isDark: Boolean): TextStyle {
    return copy(
        color = style.foregroundColor.getColor(isDark) ?: color,
        fontSize = style.fontSize?.sp ?: fontSize,
        lineHeight = style.lineHeight?.sp ?: lineHeight,
        textAlign = style.getTextAlignment() ?: textAlign,
        fontFamily = style.getFontFamily(context) ?: fontFamily,
        letterSpacing = style.letterSpacing?.sp ?: letterSpacing,
        fontWeight = style.getFontWeight() ?: fontWeight,
    )
}

internal fun List<TextSpanPrimitive>.toAnnotatedString(context: Context, isDark: Boolean): AnnotatedString {
    return buildAnnotatedString {
        forEach {
            withStyle(style = it.style.toSpanStyle(context, isDark)) {
                append(it.text)
            }
        }
    }
}

private fun ComponentStyle.toSpanStyle(context: Context, isDark: Boolean): SpanStyle {
    return SpanStyle(
        color = foregroundColor.getColor(isDark) ?: Color.Unspecified,
        fontSize = fontSize?.sp ?: TextUnit.Unspecified,
        fontFamily = getFontFamily(context),
        letterSpacing = letterSpacing?.sp ?: TextUnit.Unspecified,
        fontWeight = getFontWeight(),
    )
}
