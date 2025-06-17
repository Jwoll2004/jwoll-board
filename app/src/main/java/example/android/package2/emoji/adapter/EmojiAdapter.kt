package example.android.package2.emoji.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aosp_poc.R
import example.android.package2.emoji.data.Emoji
import kotlin.math.roundToInt

class EmojiAdapter(
    private val emojis: List<Emoji>,
    private val onEmojiClick: (Emoji) -> Unit,
    private val onEmojiLongClick: (Emoji) -> Unit = {},
    private val dynamicEmojiSize: Int = 0,
    private val dynamicSpacing: Int = 0
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emojiText: TextView = itemView.findViewById(R.id.emoji_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.emoji_item_emoji, parent, false)
        return EmojiViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = emojis[position]
        holder.emojiText.text = emoji.unicode

        // Apply dynamic sizing if available
        applyDynamicSizing(holder)

        // Handle regular click (insert emoji)
        holder.itemView.setOnClickListener {
            onEmojiClick(emoji)
        }

        holder.itemView.setOnLongClickListener {
            onEmojiLongClick(emoji)
            true
        }

        // Remove conflicting touch handling from TextView
        holder.emojiText.isClickable = false
        holder.emojiText.isFocusable = false

        // Container should handle all touch events
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true

        // Add visual feedback for better UX
        holder.itemView.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.alpha = 0.7f
                    false // Allow click to continue
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1.0f
                    false // Allow click to continue
                }
                else -> false
            }
        }
    }

    private fun applyDynamicSizing(holder: EmojiViewHolder) {
        if (dynamicEmojiSize > 0) {
            val context = holder.itemView.context
            val density = context.resources.displayMetrics.density

            // Apply size to the container
            val containerLayoutParams = holder.itemView.layoutParams
            containerLayoutParams.width = dynamicEmojiSize
            containerLayoutParams.height = dynamicEmojiSize
            holder.itemView.layoutParams = containerLayoutParams

            // Apply size to the emoji text view
            val textLayoutParams = holder.emojiText.layoutParams
            textLayoutParams.width = dynamicEmojiSize
            textLayoutParams.height = dynamicEmojiSize
            holder.emojiText.layoutParams = textLayoutParams

            // Calculate text size based on emoji size (approximately 60% of container)
            val textSize = (dynamicEmojiSize * 0.6f / density).roundToInt()
            val clampedTextSize = textSize.coerceIn(16, 32) // Min 16sp, Max 32sp

            holder.emojiText.textSize = clampedTextSize.toFloat()

            // Ensure text is centered
            holder.emojiText.gravity = android.view.Gravity.CENTER

            // Clear any existing margins since we handle spacing via ItemDecoration
            if (holder.itemView.layoutParams is ViewGroup.MarginLayoutParams) {
                val marginParams = holder.itemView.layoutParams as ViewGroup.MarginLayoutParams
                marginParams.setMargins(0, 0, 0, 0)
                holder.itemView.layoutParams = marginParams
            }
        }
    }

    override fun getItemCount(): Int = emojis.size
}