package example.android.package2.emoji.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aosp_poc.R
import example.android.package2.emoji.data.Emoji

class EmojiAdapter(
    private val emojis: List<Emoji>,
    private val onEmojiClick: (Emoji) -> Unit
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

        // Simplified click handling - use the container, not the TextView
        holder.itemView.setOnClickListener {
            onEmojiClick(emoji)
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

    override fun getItemCount(): Int = emojis.size
}