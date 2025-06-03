package example.android.package2.emoji.data

data class Emoji(
    val unicode: String,
    val description: String,
    val category: String
)

object EmojiData {
    fun getTopUsedEmojis(): List<Emoji> {
        return listOf(
            // Faces & Emotions (Most Popular)
            Emoji("😀", "Grinning Face", "faces"),
            Emoji("😂", "Face with Tears of Joy", "faces"),
            Emoji("🤣", "Rolling on the Floor Laughing", "faces"),
            Emoji("😊", "Smiling Face with Smiling Eyes", "faces"),
            Emoji("😍", "Smiling Face with Heart-Eyes", "faces"),
            Emoji("🥰", "Smiling Face with Hearts", "faces"),
            Emoji("😘", "Face Blowing a Kiss", "faces"),
            Emoji("😉", "Winking Face", "faces"),
            Emoji("😎", "Smiling Face with Sunglasses", "faces"),
            Emoji("😢", "Crying Face", "faces"),
            Emoji("😭", "Loudly Crying Face", "faces"),
            Emoji("😤", "Face with Steam From Nose", "faces"),
            Emoji("😡", "Pouting Face", "faces"),
            Emoji("🤔", "Thinking Face", "faces"),
            Emoji("😴", "Sleeping Face", "faces"),

            // Hand Gestures & People
            Emoji("👍", "Thumbs Up", "hands"),
            Emoji("👎", "Thumbs Down", "hands"),
            Emoji("👏", "Clapping Hands", "hands"),
            Emoji("🙏", "Folded Hands", "hands"),
            Emoji("✌️", "Victory Hand", "hands"),
            Emoji("🤞", "Crossed Fingers", "hands"),
            Emoji("👌", "OK Hand", "hands"),
            Emoji("✋", "Raised Hand", "hands"),
            Emoji("🤚", "Raised Back of Hand", "hands"),
            Emoji("👋", "Waving Hand", "hands"),

            // Hearts & Symbols
            Emoji("❤️", "Red Heart", "hearts"),
            Emoji("💙", "Blue Heart", "hearts"),
            Emoji("💚", "Green Heart", "hearts"),
            Emoji("💛", "Yellow Heart", "hearts"),
            Emoji("🧡", "Orange Heart", "hearts"),
            Emoji("💜", "Purple Heart", "hearts"),
            Emoji("🖤", "Black Heart", "hearts"),
            Emoji("🤍", "White Heart", "hearts"),
            Emoji("💕", "Two Hearts", "hearts"),
            Emoji("💖", "Sparkling Heart", "hearts"),

            // Objects & Food
            Emoji("🔥", "Fire", "objects"),
            Emoji("💯", "Hundred Points", "objects"),
            Emoji("⭐", "Star", "objects"),
            Emoji("🎉", "Party Popper", "objects"),
            Emoji("🎊", "Confetti Ball", "objects"),
            Emoji("🎈", "Balloon", "objects"),
            Emoji("🎂", "Birthday Cake", "food"),
            Emoji("🍕", "Pizza", "food"),
            Emoji("🍔", "Hamburger", "food"),
            Emoji("🍟", "French Fries", "food"),
            Emoji("☕", "Hot Beverage", "food"),
            Emoji("🍺", "Beer Mug", "food")
        )
    }
}