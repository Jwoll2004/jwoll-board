package example.android.package2.sharing.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class WhatsAppSharingManager(private val context: Context) {

    companion object {
        private const val TAG = "WhatsAppSharingManager"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        private const val TELEGRAM_PACKAGE = "org.telegram.messenger"
        private const val TELEGRAM_X_PACKAGE = "org.thunderdog.challegram"
    }

    fun shareTextToWhatsApp(text: String) {
        val targetApp = getAvailableMessagingApp()
        if (targetApp == null) {
            showMessagingAppNotInstalledMessage()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(targetApp.packageName)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Log.d(TAG, "Text shared to ${targetApp.name}: $text")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to share text to ${targetApp?.name}", e)
            Toast.makeText(context, "Failed to share text to ${targetApp?.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareEmojiToWhatsApp(emoji: String) {
        val targetApp = getAvailableMessagingApp()
        if (targetApp == null) {
            showMessagingAppNotInstalledMessage()
            return
        }

        try {
            // For simple emoji sharing, we can just share as text
            shareTextToWhatsApp(emoji)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to share emoji to ${targetApp?.name}", e)
            Toast.makeText(context, "Failed to share emoji to ${targetApp?.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareEmojiAsImageToWhatsApp(emoji: String) {
        val targetApp = getAvailableMessagingApp()
        if (targetApp == null) {
            showMessagingAppNotInstalledMessage()
            return
        }

        try {
            // Create emoji image
            val emojiImage = createEmojiImage(emoji)

            // Save image to file
            val imageFile = saveImageToFile(emojiImage, "emoji_${System.currentTimeMillis()}.png")

            if (imageFile != null) {
                shareImageToMessagingApp(imageFile, targetApp)
            } else {
                Toast.makeText(context, "Failed to create emoji image", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to share emoji as image to ${targetApp?.name}", e)
            Toast.makeText(context, "Failed to share emoji as image", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareTextAndEmojiToWhatsApp(text: String, emoji: String) {
        val combinedText = "$emoji $text"
        shareTextToWhatsApp(combinedText)
    }

    private fun createEmojiImage(emoji: String): Bitmap {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Set background color (transparent or white)
        canvas.drawColor(android.graphics.Color.WHITE)

        // Setup paint for emoji
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 120f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        // Calculate position to center the emoji
        val x = size / 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f

        // Draw emoji
        canvas.drawText(emoji, x, y, paint)

        return bitmap
    }

    private fun saveImageToFile(bitmap: Bitmap, filename: String): File? {
        return try {
            // Use app's private external files directory
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "emoji_images")

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Log.d(TAG, "Image saved to: ${file.absolutePath}")
            file

        } catch (e: IOException) {
            Log.e(TAG, "Failed to save image", e)
            null
        }
    }

    private fun shareImageToMessagingApp(imageFile: File, targetApp: MessagingApp) {
        try {
            val imageUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                setPackage(targetApp.packageName)
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
            Log.d(TAG, "Image shared to ${targetApp.name}: ${imageFile.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to share image to ${targetApp.name}", e)
            Toast.makeText(context, "Failed to share image to ${targetApp.name}", Toast.LENGTH_SHORT).show()
        }
    }

    // Data class to represent messaging apps
    data class MessagingApp(val packageName: String, val name: String)

    private fun getAvailableMessagingApp(): MessagingApp? {
        // Priority order: WhatsApp, WhatsApp Business, Telegram, Telegram X
        val messagingApps = listOf(
            MessagingApp(WHATSAPP_PACKAGE, "WhatsApp"),
            MessagingApp(WHATSAPP_BUSINESS_PACKAGE, "WhatsApp Business"),
            MessagingApp(TELEGRAM_PACKAGE, "Telegram"),
            MessagingApp(TELEGRAM_X_PACKAGE, "Telegram X")
        )

        for (app in messagingApps) {
            if (isAppInstalled(app.packageName)) {
                Log.d(TAG, "Found available messaging app: ${app.name}")
                return app
            }
        }

        return null
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isWhatsAppInstalled(): Boolean {
        return isAppInstalled(WHATSAPP_PACKAGE) || isAppInstalled(WHATSAPP_BUSINESS_PACKAGE)
    }

    private fun showMessagingAppNotInstalledMessage() {
        Toast.makeText(context, "No supported messaging app (WhatsApp/Telegram) is installed", Toast.LENGTH_LONG).show()
    }
}