package example.android.package2.emoji.manager

import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aosp_poc.R
import example.android.package2.keyboard.SoftKeyboard
import example.android.package2.emoji.adapter.EmojiAdapter
import example.android.package2.emoji.adapter.EmojiItemDecoration
import example.android.package2.emoji.data.EmojiData
import example.android.package2.sharing.service.SharingService

class EmojiManager(
    private val keyboardService: SoftKeyboard,
    private val onEmojiSelected: (String) -> Unit
) {
    private var emojiRecyclerView: RecyclerView? = null
    private var emojiRowContainer: View? = null
    private var emojiToggleButton: Button? = null
    private var shareEmojiButton: Button? = null
    private var isEmojiRowVisible = true
    private lateinit var emojiAdapter: EmojiAdapter
    private var lastSelectedEmoji: String = "😀"

    fun setupEmojiRow(containerView: View) {
        // Find views
        emojiRowContainer = containerView.findViewById(R.id.emoji_row_container)
        emojiRecyclerView = containerView.findViewById(R.id.emoji_recycler_view)
//        emojiToggleButton = containerView.findViewById(R.id.emoji_toggle_button)
//        shareEmojiButton = containerView.findViewById(R.id.share_emoji_button)

        setupRecyclerView()
//        setupToggleButton()
//        setupShareButton()
    }

    private fun setupRecyclerView() {
        emojiRecyclerView?.let { recyclerView ->
            // Setup horizontal linear layout manager
            val layoutManager = LinearLayoutManager(
                recyclerView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            recyclerView.layoutManager = layoutManager

            // Create adapter with emoji click handling
            val emojis = EmojiData.getTopUsedEmojis()
            emojiAdapter = EmojiAdapter(
                emojis = emojis,
                onEmojiClick = { emoji ->
                    // Insert emoji into text
                    lastSelectedEmoji = emoji.unicode
                    onEmojiSelected(emoji.unicode)
                },
                onEmojiLongClick = { emoji ->
                    // Long click - share as img
                    shareEmojiAsImage(emoji.unicode)
                }
            )
            recyclerView.adapter = emojiAdapter

            // Add item decoration for spacing
            val spacing = recyclerView.context.resources.getDimensionPixelSize(R.dimen.emoji_spacing)
            recyclerView.addItemDecoration(EmojiItemDecoration(spacing))
        }
    }

    private fun shareEmojiAsImage(emoji: String) {
        try {
            SharingService.shareEmojiAsImage(keyboardService, emoji)
        } catch (e: Exception) {
            Log.d("EmojiManager","Failed to share emoji as image")
        }
    }

//    private fun setupToggleButton() {
//        emojiToggleButton?.setOnClickListener {
//            toggleEmojiRow()
//        }
//    }

//    private fun setupShareButton() {
//        shareEmojiButton?.setOnClickListener {
//            shareLastEmojiToWhatsApp()
//        }
//    }

//    private fun toggleEmojiRow() {
//        isEmojiRowVisible = !isEmojiRowVisible
//        emojiRowContainer?.visibility = if (isEmojiRowVisible) {
//            View.VISIBLE
//        } else {
//            View.GONE
//        }
//
//        // Update button text to reflect state
//        emojiToggleButton?.text = if (isEmojiRowVisible) "⌨️" else "😀"
//    }
//
//    private fun shareEmojiToWhatsApp(emoji: String) {
//        try {
//            SharingService.shareEmoji(keyboardService, emoji)
//            showToast("Sharing $emoji...")
//        } catch (e: Exception) {
//            showToast("Failed to share emoji")
//        }
//    }

//    private fun shareLastEmojiToWhatsApp() {
//        shareEmojiToWhatsApp(lastSelectedEmoji)
//    }

//    private fun showToast(message: String) {
//        Toast.makeText(keyboardService, message, Toast.LENGTH_SHORT).show()
//    }
//
//    fun setEmojiRowVisibility(visible: Boolean) {
//        isEmojiRowVisible = visible
//        emojiRowContainer?.visibility = if (visible) View.VISIBLE else View.GONE
//        emojiToggleButton?.text = if (visible) "⌨️" else "😀"
//    }
//
//    fun isEmojiRowVisible(): Boolean = isEmojiRowVisible
//
//    // Optional: Method to scroll to specific emoji category
//    fun scrollToCategory(category: String) {
//        val emojis = EmojiData.getTopUsedEmojis()
//        val position = emojis.indexOfFirst { it.category == category }
//        if (position != -1) {
//            emojiRecyclerView?.smoothScrollToPosition(position)
//        }
//    }
//
//    fun getLastSelectedEmoji(): String = lastSelectedEmoji
}