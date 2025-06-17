package example.android.package2.sharing.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Handles direct content sharing for different messaging apps via commitContent API
 */
class DirectSharingManager(private val context: Context) {

    companion object {
        private const val TAG = "DirectSharingManager"
        private const val WHATSAPP_STICKER_SIZE = 512
        private const val REGULAR_EMOJI_SIZE = 200
        private const val MAX_FILE_SIZE = 500000 // 500KB
    }

    /**
     * Create emoji image optimized for WhatsApp stickers
     */
    fun createWhatsAppStickerImage(emoji: String): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(
                WHATSAPP_STICKER_SIZE,
                WHATSAPP_STICKER_SIZE,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)

            // Transparent background for stickers
            canvas.drawColor(android.graphics.Color.TRANSPARENT)

            // Setup paint for emoji
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT
            }

            // Calculate optimal text size
            var textSize = 380f
            paint.textSize = textSize

            // Measure and adjust text size if needed
            val bounds = android.graphics.Rect()
            paint.getTextBounds(emoji, 0, emoji.length, bounds)

            val maxDimension = maxOf(bounds.width(), bounds.height())
            if (maxDimension > WHATSAPP_STICKER_SIZE - 60) { // 30px padding
                val scale = (WHATSAPP_STICKER_SIZE - 60).toFloat() / maxDimension
                textSize *= scale
                paint.textSize = textSize
            }

            // Center the emoji
            val x = WHATSAPP_STICKER_SIZE / 2f
            val y = WHATSAPP_STICKER_SIZE / 2f - (paint.descent() + paint.ascent()) / 2f

            canvas.drawText(emoji, x, y, paint)

            Log.d(TAG, "Created WhatsApp sticker: ${WHATSAPP_STICKER_SIZE}x${WHATSAPP_STICKER_SIZE}, textSize: $textSize")
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error creating WhatsApp emoji image", e)
            null
        }
    }

    /**
     * Create regular emoji image for other apps
     */
    fun createRegularEmojiImage(emoji: String): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(
                REGULAR_EMOJI_SIZE,
                REGULAR_EMOJI_SIZE,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)

            // White background for regular emojis
            canvas.drawColor(android.graphics.Color.WHITE)

            val paint = Paint().apply {
                isAntiAlias = true
                textSize = 120f
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.CENTER
            }

            val x = REGULAR_EMOJI_SIZE / 2f
            val y = REGULAR_EMOJI_SIZE / 2f - (paint.descent() + paint.ascent()) / 2f

            canvas.drawText(emoji, x, y, paint)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error creating regular emoji image", e)
            null
        }
    }

    /**
     * Save bitmap as WebP sticker for WhatsApp
     */
    fun saveAsWebPSticker(bitmap: Bitmap, filename: String): File? {
        return try {
            val directory = File(context.filesDir, "temp_stickers")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, filename)

            FileOutputStream(file).use { out ->
                // Try lossless WebP first
                var success = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)

                if (!success) {
                    Log.w(TAG, "Lossless WebP failed, trying regular WebP")
                    // Fallback to regular WebP
                    FileOutputStream(file).use { fallbackOut ->
                        success = bitmap.compress(Bitmap.CompressFormat.WEBP, 100, fallbackOut)
                    }
                }

                if (!success) {
                    Log.e(TAG, "Failed to compress to WebP format")
                    return null
                }
            }

            // Validate file
            if (!file.exists() || file.length().toInt() == 0) {
                Log.e(TAG, "WebP file was not created properly")
                return null
            }

            val fileSize = file.length()
            if (fileSize < 100) {
                Log.e(TAG, "WebP file too small: $fileSize bytes")
                return null
            }

            if (fileSize > MAX_FILE_SIZE) {
                Log.w(TAG, "WebP file quite large: $fileSize bytes")
            }

            Log.d(TAG, "Saved WebP sticker: ${file.absolutePath}, size: $fileSize bytes")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving WebP sticker", e)
            null
        }
    }

    /**
     * Save bitmap as PNG for regular apps
     */
    fun saveAsPNG(bitmap: Bitmap, filename: String): File? {
        return try {
            val directory = File(context.filesDir, "temp_emoji")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Log.d(TAG, "Saved PNG emoji: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving PNG emoji", e)
            null
        }
    }

    /**
     * Get FileProvider URI for a file
     */
    fun getFileProviderUri(file: File): Uri? {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "FileProvider error for file: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Clean up temporary file with delay
     */
    fun cleanupFileDelayed(file: File, delayMs: Long = 5000) {
        android.os.Handler().postDelayed({
            try {
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Cleaned up temp file: ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up temp file", e)
            }
        }, delayMs)
    }
}