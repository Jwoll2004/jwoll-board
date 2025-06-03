package example.android.package2.emoji.adapter

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class EmojiItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)

        // Add horizontal spacing between items
        outRect.left = spacing / 2
        outRect.right = spacing / 2

        // Add extra spacing for first and last items
        if (position == 0) {
            outRect.left = spacing
        }
        if (position == (parent.adapter?.itemCount ?: 0) - 1) {
            outRect.right = spacing
        }

        // No vertical spacing needed since we increased container height
        outRect.top = 0
        outRect.bottom = 0
    }
}