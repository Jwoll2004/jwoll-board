package example.android.package2.emoji.extensions

import android.view.View
import android.view.inputmethod.InputConnection
import example.android.package2.keyboard.SoftKeyboard
import example.android.package2.emoji.manager.EmojiManager

/**
 * Kotlin extension functions for SoftKeyboard to handle emoji functionality with sharing
 * This version integrates WhatsApp sharing capabilities
 */

fun SoftKeyboard.insertEmoji(emojiUnicode: String) {
    // Use the new Java method that properly handles composing text
    insertEmojiText(emojiUnicode)
}

fun SoftKeyboard.setupEmojiSupport(inputView: View): EmojiManager {
    return EmojiManager(this) { emojiUnicode ->
        insertEmoji(emojiUnicode)
    }.apply {
        setupEmojiRow(inputView)
    }
}