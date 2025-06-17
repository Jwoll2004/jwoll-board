package example.android.package2.sharing.extensions

import android.content.Context
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import example.android.package2.keyboard.SoftKeyboard
import example.android.package2.sharing.manager.DirectSharingManager
import example.android.package2.sharing.service.SharingService

/**
 * Extension functions for SoftKeyboard sharing functionality
 */

/**
 * Send emoji directly using commitContent API
 */
fun SoftKeyboard.sendEmojiDirectly(emojiUnicode: String): Boolean {
    val ic = currentInputConnection
    if (ic == null || !isChatTextBox) {
        Log.d("DirectSend", "Cannot send directly - no input connection or not in chat")
        return false
    }

    return try {
        val editorInfo = currentInputEditorInfo
        val packageName = editorInfo?.packageName ?: ""
        val isWhatsApp = packageName.contains("whatsapp") ||
                packageName == "com.whatsapp" ||
                packageName == "com.whatsapp.w4b"

        Log.d("DirectSend", "Package: $packageName, isWhatsApp: $isWhatsApp")

        val directSharingManager = DirectSharingManager(this)

        val result = if (isWhatsApp) {
            sendToWhatsApp(ic, editorInfo, emojiUnicode, directSharingManager)
        } else {
            sendToOtherApp(ic, editorInfo, emojiUnicode, directSharingManager)
        }

        Log.d("DirectSend", "Direct send result: $result")
        result
    } catch (e: Exception) {
        Log.e("DirectSend", "Error in sendEmojiDirectly", e)
        false
    }
}

/**
 * Send emoji to WhatsApp as WebP sticker
 */
private fun Context.sendToWhatsApp(
    ic: InputConnection,
    editorInfo: EditorInfo,
    emoji: String,
    manager: DirectSharingManager
): Boolean {
    // Create WhatsApp sticker image
    val bitmap = manager.createWhatsAppStickerImage(emoji) ?: return false

    // Save as WebP sticker
    val filename = "emoji_sticker_${System.currentTimeMillis()}.webp"
    val file = manager.saveAsWebPSticker(bitmap, filename) ?: return false

    // Get FileProvider URI
    val uri = manager.getFileProviderUri(file) ?: return false

    // Create content info
    val contentInfo = InputContentInfoCompat(
        uri,
        android.content.ClipDescription("Sticker", arrayOf("image/webp.wasticker")),
        null
    )

    // Commit content
    val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
    val success = InputConnectionCompat.commitContent(ic, editorInfo, contentInfo, flags, null)

    // Cleanup with longer delay for WhatsApp
    manager.cleanupFileDelayed(file, 10000)

    Log.d("DirectSend", "WhatsApp sticker sent: $success")
    return success
}

/**
 * Send emoji to other apps as PNG
 */
private fun Context.sendToOtherApp(
    ic: InputConnection,
    editorInfo: EditorInfo,
    emoji: String,
    manager: DirectSharingManager
): Boolean {
    // Create regular emoji image
    val bitmap = manager.createRegularEmojiImage(emoji) ?: return false

    // Save as PNG
    val filename = "direct_emoji_${System.currentTimeMillis()}.png"
    val file = manager.saveAsPNG(bitmap, filename) ?: return false

    // Get FileProvider URI
    val uri = manager.getFileProviderUri(file) ?: return false

    // Create content info
    val contentInfo = InputContentInfoCompat(
        uri,
        android.content.ClipDescription("Emoji", arrayOf("image/png")),
        null
    )

    // Commit content
    val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
    val success = InputConnectionCompat.commitContent(ic, editorInfo, contentInfo, flags, null)

    // Cleanup
    manager.cleanupFileDelayed(file)

    Log.d("DirectSend", "Other app emoji sent: $success")
    return success
}

/**
 * Share current text (existing functionality)
 */
fun SoftKeyboard.shareCurrentText() {
    try {
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
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

private fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}