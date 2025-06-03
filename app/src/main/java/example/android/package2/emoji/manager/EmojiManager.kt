package example.android.package2.emoji.manager

import android.view.View
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aosp_poc.R
import example.android.package2.keyboard.SoftKeyboard
import example.android.package2.emoji.adapter.EmojiAdapter
import example.android.package2.emoji.adapter.EmojiItemDecoration
import example.android.package2.emoji.data.EmojiData

class EmojiManager(
    private val keyboardService: SoftKeyboard,
    private val onEmojiSelected: (String) -> Unit
) {
    private var emojiRecyclerView: RecyclerView? = null
    private var emojiRowContainer: View? = null
    private var emojiToggleButton: Button? = null
    private var isEmojiRowVisible = true
    private lateinit var emojiAdapter: EmojiAdapter

    fun setupEmojiRow(containerView: View) {
        // Find views
        emojiRowContainer = containerView.findViewById(R.id.emoji_row_container)
        emojiRecyclerView = containerView.findViewById(R.id.emoji_recycler_view)
        emojiToggleButton = containerView.findViewById(R.id.emoji_toggle_button)

        setupRecyclerView()
        setupToggleButton()
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
            emojiAdapter = EmojiAdapter(emojis) { emoji ->
                onEmojiSelected(emoji.unicode)
            }
            recyclerView.adapter = emojiAdapter

            // Optional: Add item decoration for spacing
            val spacing = recyclerView.context.resources.getDimensionPixelSize(R.dimen.emoji_spacing)
            recyclerView.addItemDecoration(EmojiItemDecoration(spacing))
        }
    }

    private fun setupToggleButton() {
        emojiToggleButton?.setOnClickListener {
            toggleEmojiRow()
        }
    }

    private fun toggleEmojiRow() {
        isEmojiRowVisible = !isEmojiRowVisible
        emojiRowContainer?.visibility = if (isEmojiRowVisible) {
            View.VISIBLE
        } else {
            View.GONE
        }

        // Update button text to reflect state
        emojiToggleButton?.text = if (isEmojiRowVisible) "⌨️" else "😀"
    }

    fun setEmojiRowVisibility(visible: Boolean) {
        isEmojiRowVisible = visible
        emojiRowContainer?.visibility = if (visible) View.VISIBLE else View.GONE
        emojiToggleButton?.text = if (visible) "⌨️" else "😀"
    }

    fun isEmojiRowVisible(): Boolean = isEmojiRowVisible

    // Optional: Method to scroll to specific emoji category
    fun scrollToCategory(category: String) {
        val emojis = EmojiData.getTopUsedEmojis()
        val position = emojis.indexOfFirst { it.category == category }
        if (position != -1) {
            emojiRecyclerView?.smoothScrollToPosition(position)
        }
    }
}