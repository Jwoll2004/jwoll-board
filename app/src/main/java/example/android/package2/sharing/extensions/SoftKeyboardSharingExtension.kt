package example.android.package2.sharing.extensions

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.example.aosp_poc.R
import example.android.package2.keyboard.SoftKeyboard
import example.android.package2.sharing.service.SharingService

/**
 * Kotlin extension functions for SoftKeyboard to handle sharing functionality
 * This provides easy integration with WhatsApp sharing
 */

fun SoftKeyboard.setupSharingSupport(inputView: View) {
    // Find sharing buttons in the layout
    val shareTextButton = inputView.findViewById<Button>(R.id.share_text_button)
    val shareEmojiButton = inputView.findViewById<Button>(R.id.share_emoji_button)

    shareTextButton?.setOnClickListener {
        shareCurrentText()
    }

    shareEmojiButton?.setOnClickListener {
        shareLastEmoji()
    }
}

fun SoftKeyboard.shareCurrentText() {
    try {
        // Get current input text
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            // Get text before cursor (last 100 characters as example)
            val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)
            if (!textBeforeCursor.isNullOrBlank()) {
                val textToShare = textBeforeCursor.toString().trim()
                if (textToShare.isNotEmpty()) {
                    SharingService.shareText(this, textToShare)
                    showToast("Sharing text...")
                } else {
                    showToast("No text to share")
                }
            } else {
                showToast("No text to share")
            }
        } else {
            showToast("Cannot access text to share")
        }
    } catch (e: Exception) {
        showToast("Failed to share text")
    }
}

fun SoftKeyboard.shareLastEmoji() {
    // For demonstration, we'll use a default emoji
    // In a real implementation, you'd track the last used emoji
    val defaultEmoji = "😀"
    shareEmoji(defaultEmoji)
}

fun SoftKeyboard.shareEmoji(emoji: String) {
    try {
        SharingService.shareEmoji(this, emoji)
        showToast("Sharing emoji...")
    } catch (e: Exception) {
        showToast("Failed to share emoji")
    }
}

fun SoftKeyboard.shareEmojiAsImage(emoji: String) {
    try {
        SharingService.shareEmojiAsImage(this, emoji)
        showToast("Sharing emoji as image...")
    } catch (e: Exception) {
        showToast("Failed to share emoji as image")
    }
}

fun SoftKeyboard.shareTextWithEmoji(text: String, emoji: String) {
    try {
        SharingService.shareTextAndEmoji(this, text, emoji)
        showToast("Sharing text and emoji...")
    } catch (e: Exception) {
        showToast("Failed to share text and emoji")
    }
}

private fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}