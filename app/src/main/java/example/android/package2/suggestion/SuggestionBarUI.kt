package example.android.package2.suggestion

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aosp_poc.R

/**
 * Enhanced suggestion display and interaction handling
 */
class SuggestionBarUI(
    private val inputMethodService: InputMethodService,
    private val rootView: View
) {

    private var suggestionBar: RecyclerView? = null
    private var suggestionAdapter: SuggestionAdapter? = null

    init {
        setupSuggestionBar()
    }

    // ============================================
    // Suggestion Bar Setup

    private fun setupSuggestionBar() {
        suggestionBar = rootView.findViewById(R.id.suggestion_bar)
        suggestionBar?.let { bar ->
            suggestionAdapter = SuggestionAdapter { suggestion ->
                onSuggestionClicked(suggestion)
            }

            bar.adapter = suggestionAdapter
            bar.layoutManager = LinearLayoutManager(
                inputMethodService,
                LinearLayoutManager.HORIZONTAL,
                false
            )

            // Start hidden
            bar.visibility = View.GONE

            Log.d("SuggestionManager", "Suggestion bar setup completed")
        }
    }

    // ============================================
    // Public Interface

    fun updateSuggestions(suggestions: List<String>) {
        suggestionAdapter?.updateSuggestions(suggestions)
        Log.d("SuggestionDebug", "SuggestionBarUI: Updated suggestions: ${suggestions.size} items")
    }

    fun showSuggestionBar() {
        suggestionBar?.visibility = View.VISIBLE
        Log.d("SuggestionDebug", "SuggestionBarUI: Suggestion bar shown")
    }

    fun hideSuggestionBar() {
        suggestionBar?.visibility = View.GONE
        Log.d("SuggestionDebug", "SuggestionBarUI: Suggestion bar hidden")
    }

    // ============================================
    // User Interaction

    private fun onSuggestionClicked(suggestion: String) {
        val inputConnection: InputConnection? = inputMethodService.currentInputConnection
        inputConnection?.let { ic ->
            ic.beginBatchEdit()

            // Get all text in the field
            val textBefore = ic.getTextBeforeCursor(1000, 0) ?: ""
            val textAfter = ic.getTextAfterCursor(1000, 0) ?: ""

            // Clear entire field content
            ic.deleteSurroundingText(textBefore.length, textAfter.length)

            // Insert the suggestion
            ic.commitText(suggestion, 1)

            ic.endBatchEdit()

            // Hide suggestions after selection
            hideSuggestionBar()

            Log.d("SuggestionBarUI", "Replaced field content with: '$suggestion'")
        }
    }

    // ============================================
    // Adapter Implementation

    private inner class SuggestionAdapter(
        private val onSuggestionClick: (String) -> Unit
    ) : RecyclerView.Adapter<SuggestionViewHolder>() {

        private val suggestions = mutableListOf<String>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
            val textView = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
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
    }

    private inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView as TextView

        fun bind(suggestion: String, onSuggestionClick: (String) -> Unit) {
            val resources = itemView.context.resources

            textView.apply {
                text = suggestion
                setTextColor(0xFFFFFFFF.toInt())
                textSize = resources.getDimension(R.dimen.suggestion_text_size) / resources.displayMetrics.scaledDensity

                val hPadding = resources.getDimensionPixelSize(R.dimen.suggestion_padding_horizontal)
                val vPadding = resources.getDimensionPixelSize(R.dimen.suggestion_padding_vertical)
                setPadding(hPadding, vPadding, hPadding, vPadding)

                setBackgroundResource(R.drawable.suggestion_badge_selector)

                val margin = resources.getDimensionPixelSize(R.dimen.suggestion_margin)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(margin, margin, margin, margin)
                }
            }

            itemView.setOnClickListener {
                onSuggestionClick(suggestion)
            }
        }
    }
}