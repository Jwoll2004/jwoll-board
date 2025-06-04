package example.android.package2.emoji.extensions

import android.view.View
import android.view.inputmethod.InputConnection
import example.android.package2.keyboard.SoftKeyboard
import example.android.package2.emoji.manager.EmojiManager

/**
 * Kotlin extension functions for SoftKeyboard to handle emoji functionality with sharing
 * This version integrates WhatsApp sharing capabilities
 */

fun SoftKeyboard.setupEmojiSupport(inputView: View): EmojiManager {
    return EmojiManager(this) { emojiUnicode ->
        insertEmoji(emojiUnicode)
    }.apply {
        setupEmojiRow(inputView)
    }
}

fun SoftKeyboard.insertEmoji(emojiUnicode: String) {
    val inputConnection: InputConnection? = currentInputConnection
    inputConnection?.let { ic ->
        // Insert the emoji at the current cursor position
        ic.commitText(emojiUnicode, 1)
    }
}

fun SoftKeyboard.handleEmojiInsertion(emoji: String) {
    try {
        val ic = currentInputConnection
        if (ic != null) {
            // Begin batch edit for better performance
            ic.beginBatchEdit()

            // Commit the emoji
            ic.commitText(emoji, 1)

            // End batch edit
            ic.endBatchEdit()
        }
    } catch (e: Exception) {
        // Fallback: simple insertion
        currentInputConnection?.commitText(emoji, 1)
    }
}