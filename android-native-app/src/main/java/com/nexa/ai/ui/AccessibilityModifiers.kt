package com.nexa.ai.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.Role

/**
 * AccessibilityModifiers — Reusable Compose modifiers for TalkBack support.
 * These modifiers add semantic information for screen readers.
 */

/**
 * Makes a composable announce itself as a button with a description.
 */
fun Modifier.accessibleButton(description: String): Modifier = this
    .semantics {
        role = Role.Button
        contentDescription = description
    }

/**
 * Makes a composable announce itself as an image with a description.
 */
fun Modifier.accessibleImage(description: String): Modifier = this
    .semantics {
        contentDescription = description
        role = Role.Image
    }

/**
 * Makes a chat message accessible with role and speaker info.
 */
fun Modifier.accessibleChatMessage(role: String, content: String): Modifier = this
    .semantics {
        // Role.Text does not exist in Compose Semantics Role, we can omit it
        this.contentDescription = when (role) {
            "user" -> "Tu mensaje: $content"
            "assistant" -> "Respuesta de Nexa: $content"
            else -> content
        }
    }

/**
 * Makes a composable announce as a heading (for screen sections).
 */
fun Modifier.accessibleHeading(): Modifier = this
    .semantics {
        heading()
    }

/**
 * Makes a composable announce its state (e.g., toggle on/off).
 */
fun Modifier.accessibleToggle(description: String, isOn: Boolean): Modifier = this
    .semantics {
        role = Role.Switch
        contentDescription = "$description, ${if (isOn) "activado" else "desactivado"}"
        stateDescription = if (isOn) "activado" else "desactivado"
    }

/**
 * Makes a text input field accessible with label.
 */
fun Modifier.accessibleTextField(label: String, text: String): Modifier = this
    .semantics {
        contentDescription = "$label: $text"
        // Role.TextBox does not exist in Compose Semantics Role, omit
    }

/**
 * Marks a composable as a list item.
 */
fun Modifier.accessibleListItem(position: Int, total: Int, description: String): Modifier = this
    .semantics {
        contentDescription = "$description, elemento $position de $total"
        // Role.ListItem does not exist in Compose Semantics Role, omit
    }
