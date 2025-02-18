package com.appcues.ui.primitive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.appcues.R
import com.appcues.data.model.ExperiencePrimitive
import com.appcues.data.model.ExperiencePrimitive.OptionSelectPrimitive
import com.appcues.data.model.ExperienceStepFormState
import com.appcues.data.model.styling.ComponentControlPosition
import com.appcues.data.model.styling.ComponentControlPosition.BOTTOM
import com.appcues.data.model.styling.ComponentControlPosition.HIDDEN
import com.appcues.data.model.styling.ComponentControlPosition.LEADING
import com.appcues.data.model.styling.ComponentControlPosition.TOP
import com.appcues.data.model.styling.ComponentControlPosition.TRAILING
import com.appcues.data.model.styling.ComponentDisplayFormat.HORIZONTAL_LIST
import com.appcues.data.model.styling.ComponentDisplayFormat.NPS
import com.appcues.data.model.styling.ComponentDisplayFormat.PICKER
import com.appcues.data.model.styling.ComponentSelectMode
import com.appcues.data.model.styling.ComponentSelectMode.MULTIPLE
import com.appcues.data.model.styling.ComponentSelectMode.SINGLE
import com.appcues.data.model.styling.ComponentStyle
import com.appcues.ui.composables.LocalExperienceStepFormStateDelegate
import com.appcues.ui.extensions.checkErrorStyle
import com.appcues.ui.extensions.getColor
import com.appcues.ui.extensions.getHorizontalAlignment
import com.appcues.ui.extensions.styleBorder

@Composable
internal fun OptionSelectPrimitive.Compose(modifier: Modifier) {
    val formState = LocalExperienceStepFormStateDelegate.current.apply {
        register(this@Compose)
    }
    val showError = formState.shouldShowError(this)

    // this avoids recalculating the label style unless the showError state changes
    val updatedLabel = remember(showError) {
        label.checkErrorStyle(showError, errorLabel)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = style.getHorizontalAlignment(Alignment.CenterHorizontally),
    ) {

        // the form item label / question
        Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = updatedLabel.style.getHorizontalAlignment(Alignment.Start)) {
            updatedLabel.Compose()
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ComposeOptions(formState, showError)
        }

        if (showError && errorLabel != null) {
            Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = errorLabel.style.getHorizontalAlignment(Alignment.Start)) {
                errorLabel.Compose()
            }
        }
    }
}

@Composable
private fun OptionSelectPrimitive.ComposeOptions(
    formState: ExperienceStepFormState,
    showError: Boolean,
) {
    val errorTint = if (showError) errorLabel?.style?.foregroundColor?.getColor(isSystemInDarkTheme()) else null

    when {
        selectMode == SINGLE && displayFormat == PICKER -> {
            options.ComposePicker(
                selectedValues = formState.getValue(this@ComposeOptions),
                modifier = Modifier.styleBorder(pickerStyle ?: ComponentStyle(), isSystemInDarkTheme()),
                placeholder = placeholder,
                accentColor = accentColor?.getColor(isSystemInDarkTheme()),
            ) {
                formState.setValue(this@ComposeOptions, it)
            }
        }
        selectMode == SINGLE && displayFormat == NPS -> {
            options.ComposeNPS(
                selectedValues = formState.getValue(this@ComposeOptions)
            ) {
                formState.setValue(this@ComposeOptions, it)
            }
        }
        displayFormat == HORIZONTAL_LIST -> {
            Row(verticalAlignment = controlPosition.getVerticalAlignment() ?: Alignment.CenterVertically) {
                options.ComposeSelections(
                    selectedValues = formState.getValue(this@ComposeOptions),
                    selectMode = selectMode,
                    controlPosition = controlPosition,
                    optionSelectPrimitive = this@ComposeOptions,
                    errorTint = errorTint,
                    leadingFill = leadingFill,
                ) {
                    formState.setValue(this@ComposeOptions, it)
                }
            }
        }
        else -> { // VERTICAL_LIST case or a fallback (i.e. a PICKER but with multi-select, invalid)
            Column(horizontalAlignment = controlPosition.getHorizontalAlignment() ?: Alignment.CenterHorizontally) {
                options.ComposeSelections(
                    selectedValues = formState.getValue(this@ComposeOptions),
                    selectMode = selectMode,
                    controlPosition = controlPosition,
                    optionSelectPrimitive = this@ComposeOptions,
                    errorTint = errorTint,
                    leadingFill = leadingFill,
                ) {
                    formState.setValue(this@ComposeOptions, it)
                }
            }
        }
    }
}

private fun ComponentControlPosition.getVerticalAlignment() =
    when (this) {
        TOP -> Alignment.Top
        BOTTOM -> Alignment.Bottom
        else -> null
    }

private fun ComponentControlPosition.getHorizontalAlignment() =
    when (this) {
        LEADING -> Alignment.Start
        TRAILING -> Alignment.End
        else -> null
    }

@Composable
private fun List<OptionSelectPrimitive.OptionItem>.ComposeSelections(
    selectedValues: Set<String>,
    selectMode: ComponentSelectMode,
    controlPosition: ComponentControlPosition,
    optionSelectPrimitive: OptionSelectPrimitive,
    errorTint: Color?,
    leadingFill: Boolean,
    itemSelected: (String) -> Unit,
) {
    // allow a leading fill only if requested, and in single select mode, and something is selected, and no checkbox or radio
    // i.e. the ratings use case
    var leadingFillOverride = leadingFill && selectMode == SINGLE && selectedValues.any() && controlPosition == HIDDEN

    forEach { option ->

        val isSelected = selectedValues.contains(option.value)

        if (isSelected) {
            // no more leading fill after this item
            leadingFillOverride = false
        }

        val styleSelected = isSelected || leadingFillOverride

        val contentView by remember(styleSelected) {
            derivedStateOf {
                option.selectedContent?.let { if (styleSelected) it else option.content } ?: option.content
            }
        }

        when (controlPosition) {
            LEADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                selectMode.Compose(isSelected, optionSelectPrimitive, errorTint) { itemSelected(option.value) }
                contentView.Compose()
            }
            TRAILING -> Row(verticalAlignment = Alignment.CenterVertically) {
                contentView.Compose()
                selectMode.Compose(isSelected, optionSelectPrimitive, errorTint) { itemSelected(option.value) }
            }
            TOP -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                selectMode.Compose(isSelected, optionSelectPrimitive, errorTint) { itemSelected(option.value) }
                contentView.Compose()
            }
            BOTTOM -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                contentView.Compose()
                selectMode.Compose(isSelected, optionSelectPrimitive, errorTint) { itemSelected(option.value) }
            }
            HIDDEN -> {
                contentView.Compose(modifier = Modifier.clickable { itemSelected(option.value) })
            }
        }
    }
}

@Composable
private fun ComponentSelectMode.Compose(
    selected: Boolean,
    optionSelectPrimitive: OptionSelectPrimitive,
    errorTint: Color?,
    selectionToggled: () -> Unit,
) {
    val selectedColor = optionSelectPrimitive.selectedColor.getColor(isSystemInDarkTheme())
    val unselectedColor = optionSelectPrimitive.unselectedColor.getColor(isSystemInDarkTheme())
    val accentColor = optionSelectPrimitive.accentColor.getColor(isSystemInDarkTheme())

    when (this) {
        SINGLE -> {
            RadioButton(
                selected = selected,
                onClick = selectionToggled,
                colors = RadioButtonDefaults.colors(
                    // the builder should always send these values, but default to the theme like the standard default behavior
                    selectedColor = selectedColor ?: MaterialTheme.colors.secondary,
                    unselectedColor = errorTint ?: unselectedColor ?: MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            )
        }
        MULTIPLE -> {
            Checkbox(
                checked = selected,
                onCheckedChange = { selectionToggled() },
                colors = CheckboxDefaults.colors(
                    // the builder should always send these values, but default to the theme like the standard default behavior
                    checkedColor = selectedColor ?: MaterialTheme.colors.secondary,
                    uncheckedColor = errorTint ?: unselectedColor ?: MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    checkmarkColor = accentColor ?: MaterialTheme.colors.surface,
                )
            )
        }
    }
}

@Composable
private fun List<OptionSelectPrimitive.OptionItem>.ComposePicker(
    selectedValues: Set<String>,
    modifier: Modifier,
    placeholder: ExperiencePrimitive?,
    accentColor: Color?,
    itemSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedValue = selectedValues.firstOrNull()
    Box(modifier = Modifier.clickable(onClick = { expanded = true })) {
        // 1. render the selected item as the collapsed state
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // we should always either have a selected item, or a placeholder, or this will just be a blank
            // box with a dropdown arrow
            if (selectedValue != null) {
                // if we have a selected value, we assume one of our options will match it, and compose it
                this@ComposePicker.firstOrNull { it.value == selectedValue }?.let {
                    // show selectedContent if available, since this is the selected item, else default content
                    (it.selectedContent ?: it.content).Compose()
                }
            } else {
                // no selection, render a placeholder, if exists
                placeholder?.Compose()
            }
            Spacer(modifier = Modifier.weight(1.0f))
            Icon(
                modifier = Modifier
                    .padding(all = 14.dp)
                    .size(size = 20.dp),
                contentDescription = null,
                imageVector = ImageVector.vectorResource(id = R.drawable.appcues_ic_drop_down),
                tint = accentColor ?: MaterialTheme.colors.secondary,
            )
        }
        // 2. the dropdown menu for selection that shows on expanded state
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            forEach { optionItem ->
                val isSelected = selectedValue == optionItem.value
                val contentView by remember(isSelected) {
                    derivedStateOf {
                        optionItem.selectedContent?.let { if (isSelected) it else optionItem.content } ?: optionItem.content
                    }
                }
                DropdownMenuItem(onClick = {
                    expanded = false
                    itemSelected(optionItem.value)
                }) {
                    contentView.Compose()
                }
            }
        }
    }
}

@Composable
private fun List<OptionSelectPrimitive.OptionItem>.ComposeNPS(
    selectedValues: Set<String>,
    itemSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // (count + 1) / 2 --> will always give the midpoint rounded up if necessary
        // normally this is 6 items in a row, in an 11 item NPS. 0-5 on first row, 6-10 on second row
        this@ComposeNPS.chunked((this@ComposeNPS.count() + 1) / 2).forEach {
            it.ComposeNPSRow(selectedValues = selectedValues, itemSelected = itemSelected)
        }
    }
}

@Composable
private fun List<OptionSelectPrimitive.OptionItem>.ComposeNPSRow(
    selectedValues: Set<String>,
    itemSelected: (String) -> Unit,
) {
    Row {
        this@ComposeNPSRow.forEach {
            val selected = selectedValues.contains(it.value)
            val content = if (selected) it.selectedContent ?: it.content else it.content
            Box(modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                itemSelected(it.value)
            }) {
                content.Compose()
            }
        }
    }
}
