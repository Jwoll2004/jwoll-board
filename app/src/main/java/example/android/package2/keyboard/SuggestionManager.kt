package example.android.package2.keyboard

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
 * Universal Suggestion System Manager
 *
 * Sections:
 * - Initialize suggestion views
 * - Handle suggestion data & display
 * - Process user interactions
 * - Future: Core autofill logic integration
 */
class SuggestionManager(
    private val inputMethodService: InputMethodService,
    private val rootView: View
) {
    // ============================================
    // Initialize suggestion views

    private var suggestionBar: RecyclerView? = null
    private var suggestionAdapter: SuggestionAdapter? = null

    init {
        setupSuggestionBar()
        setupDummyData()
    }

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

            Log.d("SuggestionManager", "Suggestion bar setup completed")
        }
    }

    // ============================================
    // Handle suggestion data & display

    private fun setupDummyData() {
        val dummySuggestions = listOf(
            "John Doe",
            "john.doe@gmail.com",
            "555-0123"
        )
        updateSuggestions(dummySuggestions)
    }

    fun updateSuggestions(suggestions: List<String>) {
        suggestionAdapter?.updateSuggestions(suggestions)
    }

    fun showSuggestionBar() {
        suggestionBar?.visibility = View.VISIBLE
    }

    fun hideSuggestionBar() {
        suggestionBar?.visibility = View.GONE
    }

    // ============================================
    // Process user interactions

    private fun onSuggestionClicked(suggestion: String) {
        val inputConnection: InputConnection? = inputMethodService.currentInputConnection
        inputConnection?.let { ic ->
            ic.commitText(suggestion, 1)
            Log.d("SuggestionManager", "Suggestion selected: $suggestion")
        }
    }

    // ============================================
    // Internal RecyclerView Adapter

    private inner class SuggestionAdapter(
        private val onSuggestionClick: (String) -> Unit
    ) : RecyclerView.Adapter<SuggestionViewHolder>() {

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