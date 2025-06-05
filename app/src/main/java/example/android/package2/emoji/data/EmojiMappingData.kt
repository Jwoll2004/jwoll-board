package example.android.package2.emoji.data

/**
 * Simple emoji mapping system for keyword-based emoji suggestions
 */
object EmojiMappingData {

    // HashMap for keyword to emoji mappings
    private val keywordToEmojis = hashMapOf<String, List<Emoji>>(
        // Love related
        "love" to listOf(
            Emoji("❤️", "Red Heart", "hearts"),
            Emoji("💕", "Two Hearts", "hearts"),
            Emoji("😍", "Smiling Face with Heart-Eyes", "faces"),
            Emoji("🥰", "Smiling Face with Hearts", "faces")
        ),
        "heart" to listOf(
            Emoji("❤️", "Red Heart", "hearts"),
            Emoji("💙", "Blue Heart", "hearts"),
            Emoji("💚", "Green Heart", "hearts"),
            Emoji("💛", "Yellow Heart", "hearts"),
            Emoji("💜", "Purple Heart", "hearts")
        ),

        // Emotions
        "happy" to listOf(
            Emoji("😀", "Grinning Face", "faces"),
            Emoji("😊", "Smiling Face with Smiling Eyes", "faces"),
            Emoji("😄", "Grinning Face with Smiling Eyes", "faces"),
            Emoji("🎉", "Party Popper", "objects")
        ),
        "sad" to listOf(
            Emoji("😢", "Crying Face", "faces"),
            Emoji("😭", "Loudly Crying Face", "faces"),
            Emoji("☹️", "Frowning Face", "faces")
        ),
        "angry" to listOf(
            Emoji("😡", "Pouting Face", "faces"),
            Emoji("😤", "Face with Steam From Nose", "faces"),
            Emoji("🤬", "Face with Symbols on Mouth", "faces")
        ),
        "laugh" to listOf(
            Emoji("😂", "Face with Tears of Joy", "faces"),
            Emoji("🤣", "Rolling on the Floor Laughing", "faces"),
            Emoji("😆", "Grinning Squinting Face", "faces")
        ),
        "crying" to listOf(
            Emoji("😭", "Loudly Crying Face", "faces"),
            Emoji("😢", "Crying Face", "faces")
        ),

        // Actions
        "thinking" to listOf(
            Emoji("🤔", "Thinking Face", "faces"),
            Emoji("💭", "Thought Balloon", "objects")
        ),
        "sleeping" to listOf(
            Emoji("😴", "Sleeping Face", "faces"),
            Emoji("💤", "Zzz", "objects")
        ),
        "eating" to listOf(
            Emoji("🍽️", "Fork and Knife with Plate", "food"),
            Emoji("🍕", "Pizza", "food"),
            Emoji("🍔", "Hamburger", "food")
        ),

        // Greetings
        "hello" to listOf(
            Emoji("👋", "Waving Hand", "hands"),
            Emoji("👋🏻", "Waving Hand: Light Skin Tone", "hands"),
            Emoji("🙋", "Person Raising Hand", "people")
        ),
        "bye" to listOf(
            Emoji("👋", "Waving Hand", "hands"),
            Emoji("✋", "Raised Hand", "hands")
        ),

        // Common responses
        "yes" to listOf(
            Emoji("👍", "Thumbs Up", "hands"),
            Emoji("✅", "Check Mark Button", "symbols"),
            Emoji("👌", "OK Hand", "hands")
        ),
        "no" to listOf(
            Emoji("👎", "Thumbs Down", "hands"),
            Emoji("❌", "Cross Mark", "symbols"),
            Emoji("🚫", "Prohibited", "symbols")
        ),
        "ok" to listOf(
            Emoji("👌", "OK Hand", "hands"),
            Emoji("👍", "Thumbs Up", "hands"),
            Emoji("✅", "Check Mark Button", "symbols")
        ),

        // Weather
        "sun" to listOf(
            Emoji("☀️", "Sun", "weather"),
            Emoji("🌞", "Sun with Face", "weather"),
            Emoji("🌅", "Sunrise", "weather")
        ),
        "rain" to listOf(
            Emoji("🌧️", "Cloud with Rain", "weather"),
            Emoji("☔", "Umbrella with Rain Drops", "weather"),
            Emoji("💧", "Droplet", "weather")
        ),
        "fire" to listOf(
            Emoji("🔥", "Fire", "objects"),
            Emoji("🌶️", "Hot Pepper", "food")
        ),

        // Food
        "pizza" to listOf(
            Emoji("🍕", "Pizza", "food")
        ),
        "coffee" to listOf(
            Emoji("☕", "Hot Beverage", "food"),
            Emoji("🍵", "Teacup Without Handle", "food")
        ),
        "beer" to listOf(
            Emoji("🍺", "Beer Mug", "food"),
            Emoji("🍻", "Clinking Beer Mugs", "food")
        ),

        // Time
        "time" to listOf(
            Emoji("⏰", "Alarm Clock", "objects"),
            Emoji("🕐", "One O'Clock", "objects"),
            Emoji("⌚", "Watch", "objects")
        ),
        "night" to listOf(
            Emoji("🌙", "Crescent Moon", "objects"),
            Emoji("🌚", "New Moon Face", "objects"),
            Emoji("⭐", "Star", "objects")
        ),

        // Celebration
        "party" to listOf(
            Emoji("🎉", "Party Popper", "objects"),
            Emoji("🎊", "Confetti Ball", "objects"),
            Emoji("🥳", "Partying Face", "faces")
        ),
        "birthday" to listOf(
            Emoji("🎂", "Birthday Cake", "food"),
            Emoji("🎉", "Party Popper", "objects"),
            Emoji("🎈", "Balloon", "objects")
        )
    )

    /**
     * Get suggested emojis for a keyword
     * @param keyword The keyword to search for (case-insensitive)
     * @return List of suggested emojis or empty list if no match
     */
    fun getSuggestedEmojis(keyword: String): List<Emoji> {
        val normalizedKeyword = keyword.lowercase().trim()
        return keywordToEmojis[normalizedKeyword] ?: emptyList()
    }
}