package example.android.package2.keyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aosp_poc.R

class SuggestionManager(
    private val inputMethodService: InputMethodService,
    private val rootView: View
) {
    private var suggestionBar: RecyclerView? = null
    private var suggestionAdapter: SuggestionAdapter? = null

    init {
        setupSuggestionBar()
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

            // Load dummy data for testing
            setupDummyData()

            Log.d("SuggestionManager", "Suggestion bar setup completed")
        }
    }

    private fun setupDummyData() {
        val dummySuggestions = listOf(
            "John Doe",
            "john.doe@gmail.com",
            "555-0123"
        )
        updateSuggestions(dummySuggestions)
    }

    private fun onSuggestionClicked(suggestion: String) {
        val inputConnection: InputConnection? = inputMethodService.currentInputConnection
        inputConnection?.let { ic ->
            ic.commitText(suggestion, 1)
            Log.d("SuggestionManager", "Suggestion selected: $suggestion")
        }
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
}