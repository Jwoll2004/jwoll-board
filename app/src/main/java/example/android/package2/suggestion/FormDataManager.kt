package example.android.package2.suggestion

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo

/**
 * Enhanced form data management with smart deduplication
 */
class FormDataManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("form_autofill", Context.MODE_PRIVATE)

    // ============================================
    // Field Type Detection

    enum class FieldType {
        FIRST_NAME, LAST_NAME, FULL_NAME,
        EMAIL, PHONE,
        ADDRESS, CITY, STATE, ZIP,
        COMPANY, USERNAME,
        UNKNOWN
    }

    fun detectFieldType(editorInfo: EditorInfo?): FieldType {
        if (editorInfo == null) return FieldType.UNKNOWN

        val hint = editorInfo.hintText?.toString()?.lowercase() ?: ""
        val inputType = editorInfo.inputType

        Log.d("SuggestionDebug", "=== FIELD DETECTION DEBUG ===")
        Log.d("SuggestionDebug", "Raw hint: '${editorInfo.hintText}'")
        Log.d("SuggestionDebug", "Input type: 0x${Integer.toHexString(inputType)}")

        // Hint text analysis
        val detectedType = when {
            hint.contains("first") && hint.contains("name") -> FieldType.FIRST_NAME
            hint.contains("last") && hint.contains("name") -> FieldType.LAST_NAME
            hint.contains("full") && hint.contains("name") -> FieldType.FULL_NAME
            hint.contains("email") -> FieldType.EMAIL
            hint.contains("phone") -> FieldType.PHONE
            hint.contains("address") -> FieldType.ADDRESS
            hint.contains("city") -> FieldType.CITY
            hint.contains("state") -> FieldType.STATE
            hint.contains("zip") -> FieldType.ZIP
            hint.contains("company") -> FieldType.COMPANY
            hint.contains("username") -> FieldType.USERNAME
            else -> {
                // Input type fallback
                val inputVariation = inputType and InputType.TYPE_MASK_VARIATION
                when {
                    inputVariation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> FieldType.EMAIL
                    inputVariation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> FieldType.FULL_NAME
                    inputVariation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> FieldType.ADDRESS
                    inputType and InputType.TYPE_CLASS_PHONE != 0 -> FieldType.PHONE
                    else -> FieldType.UNKNOWN
                }
            }
        }

        Log.d("SuggestionDebug", "Final detected type: $detectedType")
        return detectedType
    }

    // ============================================
    // Enhanced Data Storage & Retrieval

    fun storeSuggestion(fieldType: FieldType, value: String) {
        val cleanValue = value.trim()
        Log.d("SuggestionDebug", "=== storeSuggestion called ===")
        Log.d("SuggestionDebug", "Field type: $fieldType")
        Log.d("SuggestionDebug", "Raw value: '$value'")
        Log.d("SuggestionDebug", "Clean value: '$cleanValue'")

        if (cleanValue.isBlank() || cleanValue.length < 2) {
            Log.d("SuggestionDebug", "✗ Value rejected - too short or blank")
            return
        }

        val key = fieldType.name
        val existingData = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        Log.d("SuggestionDebug", "Existing data for $key: $existingData")

        // Smart deduplication - remove variations of the same value
        val normalizedValue = normalizeValue(cleanValue)
        val removedItems = mutableListOf<String>()
        existingData.removeAll {
            val shouldRemove = normalizeValue(it) == normalizedValue
            if (shouldRemove) removedItems.add(it)
            shouldRemove
        }

        if (removedItems.isNotEmpty()) {
            Log.d("SuggestionDebug", "Removed duplicate items: $removedItems")
        }

        // Add the new value
        existingData.add(cleanValue)
        Log.d("SuggestionDebug", "Added new value: '$cleanValue'")

        // Keep only last 8 entries per field type (reduced for quality)
        if (existingData.size > 8) {
            val sortedList = existingData.toList()
            existingData.clear()
            existingData.addAll(sortedList.takeLast(8))
            Log.d("SuggestionDebug", "Trimmed to 8 entries")
        }

        val success = prefs.edit().putStringSet(key, existingData).commit()
        Log.d("SuggestionDebug", "✓ Storage ${if (success) "SUCCESS" else "FAILED"} for $fieldType: '$cleanValue'")
        Log.d("SuggestionDebug", "Final data for $key: $existingData")
    }

    fun getSuggestions(fieldType: FieldType): List<String> {
        val key = fieldType.name
        val suggestions = prefs.getStringSet(key, emptySet())?.toList() ?: emptyList()

        Log.d("SuggestionDebug", "=== getSuggestions called ===")
        Log.d("SuggestionDebug", "Field type: $fieldType, Key: $key")
        Log.d("SuggestionDebug", "Raw suggestions: $suggestions")

        // Filter out short or invalid suggestions
        val filteredSuggestions = suggestions.filter {
            val isValid = it.trim().length >= 2 && isValidSuggestion(it, fieldType)
            if (!isValid) {
                Log.d("SuggestionDebug", "Filtered out invalid suggestion: '$it'")
            }
            isValid
        }

        val finalSuggestions = filteredSuggestions.reversed() // Most recent first
        Log.d("SuggestionDebug", "✓ Returning ${finalSuggestions.size} filtered suggestions: $finalSuggestions")
        return finalSuggestions
    }

    fun hasSuggestions(fieldType: FieldType): Boolean {
        return getSuggestions(fieldType).isNotEmpty()
    }

    // ============================================
    // Helper Methods

    private fun normalizeValue(value: String): String {
        // Normalize for comparison - lowercase, no extra spaces
        return value.lowercase().trim().replace(Regex("\\s+"), " ")
    }

    private fun isValidSuggestion(value: String, fieldType: FieldType): Boolean {
        val trimmed = value.trim()

        return when (fieldType) {
            FieldType.EMAIL -> trimmed.contains("@") && trimmed.contains(".")
            FieldType.PHONE -> trimmed.length >= 7 && trimmed.any { it.isDigit() }
            FieldType.ZIP -> trimmed.length >= 4 && trimmed.all { it.isDigit() || it == '-' }
            else -> trimmed.length >= 2
        }
    }
}