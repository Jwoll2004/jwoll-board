package example.android.package2.emoji.manager

import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aosp_poc.R
import example.android.package2.emoji.adapter.EmojiAdapter
import example.android.package2.emoji.data.Emoji
import example.android.package2.emoji.data.EmojiData
import example.android.package2.emoji.data.EmojiMappingData
import example.android.package2.keyboard.SoftKeyboard
import example.android.package2.sharing.service.SharingService

class EmojiManager(
    private val keyboardService: SoftKeyboard,
    private val onEmojiSelected: (String) -> Unit
) {
    private var emojiRecyclerView: RecyclerView? = null
    private var emojiRowContainer: View? = null
    private lateinit var emojiAdapter: EmojiAdapter

    // State management for suggestions
    private var isShowingSuggestions = false
    private var currentKeyword: String = ""
    private var isShowingSpaceSuggestions = false

    // Dynamic sizing constants
    private companion object {
        private const val TAG = "EmojiManager"
        private const val EMOJI_SPAN_COUNT = 8
        private const val MIN_EMOJI_SIZE_DP = 32
        private const val MAX_EMOJI_SIZE_DP = 48
    }

    // Dynamic sizing variables
    private var containerWidth = 0
    private var emojiSize = 0
    private var horizontalSpacing = 0

    fun setupEmojiRow(containerView: View) {
        // Find views
        emojiRowContainer = containerView.findViewById(R.id.emoji_row_container)
        emojiRecyclerView = containerView.findViewById(R.id.emoji_recycler_view)

        setupDynamicSizing()
        setupRecyclerView()
        showDefaultEmojis()
    }

    private fun setupDynamicSizing() {
        emojiRowContainer?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val width = emojiRowContainer?.width ?: 0
                if (width > 0 && width != containerWidth) {
                    containerWidth = width
                    calculateDynamicSizing()
                    updateRecyclerViewLayout()

                    // Remove listener after first measurement
                    emojiRowContainer?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                }
            }
        })
    }

    private fun calculateDynamicSizing() {
        if (containerWidth <= 0) return

        val context = emojiRecyclerView?.context ?: return
        val density = context.resources.displayMetrics.density

        // Calculate spacing for exactly 8 visible emojis with equal spacing
        // We need 9 equal spaces for the 8 visible emojis: left edge + 7 between + right edge
        val visibleSpaces = EMOJI_SPAN_COUNT + 1

        // Start with a reasonable emoji size and work backwards
        val maxEmojiSize = (MAX_EMOJI_SIZE_DP * density).toInt()
        val minEmojiSize = (MIN_EMOJI_SIZE_DP * density).toInt()

        // Try to fit 8 emojis with equal spacing in the visible area
        emojiSize = maxEmojiSize

        do {
            val visibleEmojiWidth = emojiSize * EMOJI_SPAN_COUNT
            val remainingWidth = containerWidth - visibleEmojiWidth
            horizontalSpacing = remainingWidth / visibleSpaces

            // If spacing is too small, reduce emoji size
            if (horizontalSpacing < (4 * density).toInt()) { // Minimum 4dp spacing
                emojiSize -= (2 * density).toInt()
            } else {
                break
            }
        } while (emojiSize >= minEmojiSize)

        // Final validation - ensure we don't go below minimum
        emojiSize = emojiSize.coerceAtLeast(minEmojiSize)

        // Recalculate final spacing with confirmed emoji size
        val visibleEmojiWidth = emojiSize * EMOJI_SPAN_COUNT
        val remainingWidth = containerWidth - visibleEmojiWidth
        horizontalSpacing = remainingWidth / visibleSpaces

        Log.d(TAG, "Dynamic sizing calculated:")
        Log.d(TAG, "  Container width: $containerWidth")
        Log.d(TAG, "  Emoji size: $emojiSize")
        Log.d(TAG, "  Horizontal spacing: $horizontalSpacing")
        Log.d(TAG, "  Visible spaces: $visibleSpaces")
        Log.d(TAG, "  Check: ${EMOJI_SPAN_COUNT * emojiSize + visibleSpaces * horizontalSpacing} should equal $containerWidth")
    }

    private fun updateRecyclerViewLayout() {
        emojiRecyclerView?.let { recyclerView ->
            // Remove existing decoration
            while (recyclerView.itemDecorationCount > 0) {
                recyclerView.removeItemDecorationAt(0)
            }

            // Add new dynamic decoration
            if (emojiSize > 0 && horizontalSpacing > 0) {
                recyclerView.addItemDecoration(DynamicEmojiItemDecoration(emojiSize, horizontalSpacing))
            }

            // Force adapter to refresh with new sizing
            emojiAdapter.notifyItemRangeChanged(0, emojiAdapter.itemCount)
        }
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

            // Enable scrolling and remove restrictions
            recyclerView.setPadding(0, 0, 0, 0)
            recyclerView.clipToPadding = false
            recyclerView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS // Enable overscroll when needed
            recyclerView.isHorizontalScrollBarEnabled = false // Hide scrollbar but keep scrolling

            // Create adapter with emoji click handling
            emojiAdapter = EmojiAdapter(
                emojis = emptyList(), // Will be populated by showDefaultEmojis()
                onEmojiClick = { emoji ->
                    handleEmojiSelection(emoji)
                },
                onEmojiLongClick = { emoji ->
                    // Long click - share emoji as image
                    shareEmoji(emoji.unicode)
                },
                // Pass dynamic sizing info to adapter
                dynamicEmojiSize = emojiSize,
                dynamicSpacing = horizontalSpacing
            )
            recyclerView.adapter = emojiAdapter
        }
    }

    /**
     * Handle emoji selection based on current state
     */
    private fun handleEmojiSelection(emoji: Emoji) {
        Log.d(TAG, "handleEmojiSelection: ${emoji.unicode}")

        if (isShowingSuggestions) {
            if (isShowingSpaceSuggestions) {
                onEmojiSelected(emoji.unicode)
            } else {
                keyboardService.replaceCurrentWordWithEmoji(emoji.unicode)
            }
            showDefaultEmojis()
        } else {
            onEmojiSelected(emoji.unicode)
        }
    }

    /**
     * Handle emoji long press for direct sending in chat apps
     */
    private fun handleEmojiLongPress(emoji: Emoji) {
        Log.d(TAG, "handleEmojiLongPress: ${emoji.unicode}")

        // Only attempt direct send in chat text boxes
        if (keyboardService.isChatTextBox()) {
            val success = keyboardService.sendEmojiDirectly(emoji.unicode)
            if (success) {
                Log.d(TAG, "Emoji sent directly via commitContent")
                return
            }
        }

        // Fallback to image sharing if direct send fails or not in chat
        shareEmoji(emoji.unicode)
    }

    /**
     * Show default emoji set
     */
    private fun showDefaultEmojis() {
        isShowingSuggestions = false
        isShowingSpaceSuggestions = false  // Always reset both flags
        currentKeyword = ""
        val defaultEmojis = EmojiData.getTopUsedEmojis()
        updateEmojiList(defaultEmojis)
        Log.d(TAG, "Showing default emojis - all suggestion states cleared")
    }

    /**
     * Show suggested emojis for a keyword
     */
    private fun showSuggestedEmojis(keyword: String, suggestedEmojis: List<Emoji>, isSpaceMode: Boolean) {
        isShowingSuggestions = true
        isShowingSpaceSuggestions = isSpaceMode
        currentKeyword = keyword
        updateEmojiList(suggestedEmojis)

        Log.d(TAG, "Showing suggestions for keyword: $keyword, count: ${suggestedEmojis.size}")
        Log.d(TAG, "Mode: ${if (isSpaceMode) "SPACE (insert)" else "COMPOSING (replace)"}")
    }

    /**
     * Update the emoji list in the RecyclerView
     */
    private fun updateEmojiList(emojis: List<Emoji>) {
        emojiAdapter = EmojiAdapter(
            emojis = emojis,
            onEmojiClick = { emoji ->
                handleEmojiSelection(emoji)
            },
            onEmojiLongClick = { emoji ->
                handleEmojiLongPress(emoji)  // Use the new handler
            },
            dynamicEmojiSize = emojiSize,
            dynamicSpacing = horizontalSpacing
        )
        emojiRecyclerView?.adapter = emojiAdapter
        updateRecyclerViewLayout()
    }

    /**
     * Handle text change during typing (for composing text)
     * This is called when user is typing but hasn't committed the word yet
     */
    fun handleComposingTextChange(composingText: String) {
        Log.d(TAG, "handleComposingTextChange: '$composingText', was showing space suggestions: $isShowingSpaceSuggestions")

        // IMPORTANT: Clear space suggestion state since we're now in composing mode
        isShowingSpaceSuggestions = false

        if (composingText.isBlank()) {
            // No composing text, show default emojis
            if (isShowingSuggestions) {
                showDefaultEmojis()
            }
            return
        }

        // Check if the composing text matches any keyword
        val suggestions = EmojiMappingData.getSuggestedEmojis(composingText)

        if (suggestions.isNotEmpty()) {
            // Show suggestions for this keyword (composing mode)
            showSuggestedEmojis(composingText, suggestions, isSpaceMode = false)
        } else {
            // No match found, show default emojis if currently showing suggestions
            if (isShowingSuggestions) {
                showDefaultEmojis()
            }
        }
    }

    /**
     * Handle space press or word completion
     * This is called when user presses space and we need to check the last committed word
     */
    fun handleWordCompletion(lastWord: String) {
        Log.d(TAG, "handleWordCompletion: '$lastWord'")

        if (lastWord.isBlank()) {
            // No last word, show default emojis
            if (isShowingSuggestions) {
                showDefaultEmojis()
            }
            return
        }

        // Check if the last word matches any keyword
        val suggestions = EmojiMappingData.getSuggestedEmojis(lastWord)

        if (suggestions.isNotEmpty()) {
            // Show suggestions for this keyword (space mode)
            showSuggestedEmojis(lastWord, suggestions, isSpaceMode = true)
        } else {
            // No match found, show default emojis
            if (isShowingSuggestions) {
                showDefaultEmojis()
            }
        }
    }

    /**
     * Reset to default state
     * Called when user commits text or starts fresh
     */
    fun resetToDefault() {
        Log.d(TAG, "resetToDefault called")
        if (isShowingSuggestions) {
            showDefaultEmojis()
        }
    }

    private fun shareEmoji(emoji: String) {
        try {
            SharingService.shareEmoji(keyboardService, emoji)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to share emoji as image")
        }
    }

    /**
     * Dynamic ItemDecoration that ensures perfect spacing for visible emojis while allowing scroll
     */
    private class DynamicEmojiItemDecoration(
        private val emojiSize: Int,
        private val spacing: Int
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            val itemCount = parent.adapter?.itemCount ?: 0

            // Force emoji size
            val layoutParams = view.layoutParams
            layoutParams.width = emojiSize
            layoutParams.height = emojiSize
            view.layoutParams = layoutParams

            // Apply spacing that works for both visible and scrolled items
            when (position) {
                0 -> {
                    // First item: full spacing on left (edge spacing), half on right
                    outRect.left = spacing
                    outRect.right = spacing / 2
                }
                itemCount - 1 -> {
                    // Last item: half spacing on left, full spacing on right (edge spacing)
                    outRect.left = spacing / 2
                    outRect.right = spacing
                }
                else -> {
                    // Middle items: half spacing on both sides (creates full spacing between items)
                    outRect.left = spacing / 2
                    outRect.right = spacing / 2
                }
            }

            // No vertical spacing
            outRect.top = 0
            outRect.bottom = 0
        }
    }
}