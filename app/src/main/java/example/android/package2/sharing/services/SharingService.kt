package example.android.package2.sharing.service

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.util.Log
import example.android.package2.sharing.manager.WhatsAppSharingManager

class SharingService : IntentService("SharingService") {

    companion object {
        private const val TAG = "SharingService"

        // Action constants
        const val ACTION_SHARE_TEXT = "example.android.package2.ACTION_SHARE_TEXT"
        const val ACTION_SHARE_EMOJI = "example.android.package2.ACTION_SHARE_EMOJI"
        const val ACTION_SHARE_EMOJI_AS_IMAGE = "example.android.package2.ACTION_SHARE_EMOJI_AS_IMAGE"
        const val ACTION_SHARE_TEXT_AND_EMOJI = "example.android.package2.ACTION_SHARE_TEXT_AND_EMOJI"

        // Extra keys
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_EMOJI = "extra_emoji"

        // Helper methods to start the service
        fun shareText(context: Context, text: String) {
            val intent = Intent(context, SharingService::class.java).apply {
                action = ACTION_SHARE_TEXT
                putExtra(EXTRA_TEXT, text)
            }
            context.startService(intent)
        }

        fun shareEmoji(context: Context, emoji: String) {
            val intent = Intent(context, SharingService::class.java).apply {
                action = ACTION_SHARE_EMOJI
                putExtra(EXTRA_EMOJI, emoji)
            }
            context.startService(intent)
        }

        fun shareEmojiAsImage(context: Context, emoji: String) {
            val intent = Intent(context, SharingService::class.java).apply {
                action = ACTION_SHARE_EMOJI_AS_IMAGE
                putExtra(EXTRA_EMOJI, emoji)
            }
            context.startService(intent)
        }

        fun shareTextAndEmoji(context: Context, text: String, emoji: String) {
            val intent = Intent(context, SharingService::class.java).apply {
                action = ACTION_SHARE_TEXT_AND_EMOJI
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_EMOJI, emoji)
            }
            context.startService(intent)
        }
    }

    private lateinit var whatsAppSharingManager: WhatsAppSharingManager

    override fun onCreate() {
        super.onCreate()
        whatsAppSharingManager = WhatsAppSharingManager(this)
        Log.d(TAG, "SharingService created")
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) {
            Log.w(TAG, "Received null intent")
            return
        }

        Log.d(TAG, "Handling intent with action: ${intent.action}")

        when (intent.action) {
            ACTION_SHARE_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    whatsAppSharingManager.shareTextToWhatsApp(text)
                } else {
                    Log.w(TAG, "Text is null or blank")
                }
            }

            ACTION_SHARE_EMOJI -> {
                val emoji = intent.getStringExtra(EXTRA_EMOJI)
                if (!emoji.isNullOrBlank()) {
                    whatsAppSharingManager.shareEmojiToWhatsApp(emoji)
                } else {
                    Log.w(TAG, "Emoji is null or blank")
                }
            }

            ACTION_SHARE_EMOJI_AS_IMAGE -> {
                val emoji = intent.getStringExtra(EXTRA_EMOJI)
                if (!emoji.isNullOrBlank()) {
                    whatsAppSharingManager.shareEmojiAsImageToWhatsApp(emoji)
                } else {
                    Log.w(TAG, "Emoji is null or blank")
                }
            }

            ACTION_SHARE_TEXT_AND_EMOJI -> {
                val text = intent.getStringExtra(EXTRA_TEXT)
                val emoji = intent.getStringExtra(EXTRA_EMOJI)
                if (!text.isNullOrBlank() && !emoji.isNullOrBlank()) {
                    whatsAppSharingManager.shareTextAndEmojiToWhatsApp(text, emoji)
                } else {
                    Log.w(TAG, "Text or emoji is null or blank")
                }
            }

            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SharingService destroyed")
    }
}