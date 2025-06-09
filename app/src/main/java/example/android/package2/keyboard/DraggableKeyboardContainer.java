package example.android.package2.keyboard;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

public class DraggableKeyboardContainer extends LinearLayout {
    private static final String TAG = "DraggableKeyboardContainer";

    // Dragging state
    private float dX, dY;
    private boolean isDragging = false;
    private boolean isFloating = false;

    // Size constants
    private static final float FLOATING_SIZE_RATIO = 0.8f; // 80% of original size
    private static final int DOCK_THRESHOLD_DP = 50; // Distance from bottom to auto-dock

    // Callbacks
    public interface DragCallback {
        void onDragStart();
        void onDragMove(boolean isNearBottom);
        void onSmoothMove(float newX, float newY);
        void onDragEnd();
        void onSizeChange(boolean isFloating);
        float[] getCurrentWindowPosition(); // [x, y]
    }

    private DragCallback dragCallback;
    private View dragHandle;
    private int originalWidth, originalHeight;
    private int screenHeight;

    public DraggableKeyboardContainer(Context context) {
        super(context);
        init();
    }

    public DraggableKeyboardContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        // Don't get screen height here - will be set properly later
        post(new Runnable() {
            @Override
            public void run() {
                if (getContext() instanceof Context) {
                    screenHeight = getResources().getDisplayMetrics().heightPixels;
                    Log.d(TAG, "Screen height: " + screenHeight);
                }
            }
        });
    }

    public void setDragCallback(DragCallback callback) {
        this.dragCallback = callback;
    }

    public void setDragHandle(View handle) {
        this.dragHandle = handle;
        if (handle != null) {
            handle.setOnTouchListener(this::onDragHandleTouch);
        }
    }

    private boolean onDragHandleTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Store the offset between touch point and current window position
                if (dragCallback != null) {
                    float[] currentWindowPos = dragCallback.getCurrentWindowPosition();
                    dX = currentWindowPos[0] - event.getRawX();
                    dY = currentWindowPos[1] - event.getRawY();
                }

                if (!isFloating) {
                    // Start floating mode
                    startFloatingMode();
                }

                isDragging = true;
                if (dragCallback != null) {
                    dragCallback.onDragStart();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging && dragCallback != null) {
                    // Simple, smooth movement calculation - no heavy computations
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;

                    // Let callback handle the smooth movement
                    dragCallback.onSmoothMove(newX, newY);

                    // Check if near bottom for docking feedback
                    DisplayMetrics metrics = getResources().getDisplayMetrics();
                    boolean isNearBottom = (newY + getHeight()) > (metrics.heightPixels - dpToPx(DOCK_THRESHOLD_DP));
                    dragCallback.onDragMove(isNearBottom);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;

                    if (dragCallback != null) {
                        dragCallback.onDragEnd();
                    }
                }
                break;
        }
        return true;
    }

    private void startFloatingMode() {
        if (isFloating) return;

        isFloating = true;

        // Store original dimensions
        originalWidth = getWidth();
        originalHeight = getHeight();

        Log.d(TAG, "Starting floating mode - original: " + originalWidth + "x" + originalHeight);
        Log.d(TAG, "Will only reduce width, height stays: " + originalHeight);

        if (dragCallback != null) {
            dragCallback.onSizeChange(true);
        }
    }

    private void dockToBottom() {
        if (!isFloating) return;

        isFloating = false;

        Log.d(TAG, "Docking to bottom - restoring to: " + originalWidth + "x" + originalHeight);

        if (dragCallback != null) {
            dragCallback.onSizeChange(false);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (!isFloating && w > 0 && h > 0) {
            originalWidth = w;
            originalHeight = h;
            Log.d(TAG, "Original size set: " + originalWidth + "x" + originalHeight);
        }
    }

    public void forceDockMode() {
        if (isFloating) {
            dockToBottom();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}