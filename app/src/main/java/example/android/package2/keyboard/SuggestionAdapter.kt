package example.android.package2.keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SuggestionAdapter(
    private val onSuggestionClick: (String) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>() {

    private val suggestions = mutableListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return SuggestionViewHolder(textView)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestion = suggestions[position]
        holder.bind(suggestion, onSuggestionClick)
    }

    override fun getItemCount(): Int = suggestions.size

    fun updateSuggestions(newSuggestions: List<String>) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        notifyDataSetChanged()
    }

    class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView as TextView

        fun bind(suggestion: String, onSuggestionClick: (String) -> Unit) {
            textView.apply {
                text = suggestion
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                setPadding(
                    dpToPx(12), // left
                    dpToPx(6),  // top
                    dpToPx(12), // right
                    dpToPx(6)   // bottom
                )

                // Create badge-style background with rounded corners and stroke
                background = createBadgeBackground()

                // Ensure width wraps content
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                }
            }

            itemView.setOnClickListener {
                onSuggestionClick(suggestion)
            }
        }

        private fun dpToPx(dp: Int): Int {
            val density = itemView.context.resources.displayMetrics.density
            return (dp * density).toInt()
        }

        private fun createBadgeBackground(): android.graphics.drawable.Drawable {
            val shape = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(0xFF404040.toInt()) // Dark grey background
                cornerRadius = dpToPx(12).toFloat() // Rounded corners
                setStroke(dpToPx(1), 0xFFFFFFFF.toInt()) // White stroke
            }
            return shape
        }
    }
}