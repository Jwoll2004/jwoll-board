package example.android.package2.emoji.extensions

import android.view.View
import example.android.package2.keyboard.SoftKeyboard
import example.android.package2.emoji.manager.EmojiManager

/**
 * Kotlin extension functions for SoftKeyboard to handle emoji functionality with dynamic sizing
 * This version integrates WhatsApp sharing capabilities and dynamic 8-emoji layout
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