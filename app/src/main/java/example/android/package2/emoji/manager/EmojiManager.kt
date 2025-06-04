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
    private lateinit var emojiAdapter: EmojiAdapter

    fun setupEmojiRow(containerView: View) {
        // Find views
        emojiRowContainer = containerView.findViewById(R.id.emoji_row_container)
        emojiRecyclerView = containerView.findViewById(R.id.emoji_recycler_view)

        setupRecyclerView()
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
                    onEmojiSelected(emoji.unicode)
                },
                onEmojiLongClick = { emoji ->
                    // Long click - share as img
                    shareEmoji(emoji.unicode)
                }
            )
            recyclerView.adapter = emojiAdapter

            // Add item decoration for spacing
            val spacing = recyclerView.context.resources.getDimensionPixelSize(R.dimen.emoji_spacing)
            recyclerView.addItemDecoration(EmojiItemDecoration(spacing))
        }
    }

    private fun shareEmoji(emoji: String) {
        try {
            SharingService.shareEmoji(keyboardService, emoji)
        } catch (e: Exception) {
            Log.d("EmojiManager","Failed to share emoji as image")
        }
    }
}