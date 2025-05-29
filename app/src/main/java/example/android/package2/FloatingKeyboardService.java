package example.android.package2;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.example.aosp_poc.R;

public class FloatingKeyboardService extends Service {
    private static final String TAG = "FloatingKeyboardSvc";

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private SoftKeyboard imeService;
    private boolean isViewAdded = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void setImeService(SoftKeyboard imeService) {
        Log.d(TAG, "Setting IME service");
        this.imeService = imeService;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "=== FloatingKeyboardService onCreate ===");

        try {
            createFloatingView();
            setupWindowManager();
            setupLayoutParams();
            setupDragFunctionality();
            setupKeyboard();
            Log.d(TAG, "FloatingKeyboardService created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    private void createFloatingView() {
        Log.d(TAG, "Creating floating view");
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        try {
            floatingView = inflater.inflate(R.layout.floating_keyboard_overlay, null);
            Log.d(TAG, "Floating view created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating floating view, falling back to simple view", e);
            // Fallback: create a simple debug view
            floatingView = new android.widget.TextView(this);
            ((android.widget.TextView) floatingView).setText("DEBUG FLOATING KEYBOARD");
            ((android.widget.TextView) floatingView).setBackgroundColor(android.graphics.Color.BLUE);
            ((android.widget.TextView) floatingView).setTextColor(android.graphics.Color.WHITE);
            ((android.widget.TextView) floatingView).setPadding(50, 50, 50, 50);
        }
    }

    private void setupWindowManager() {
        Log.d(TAG, "Setting up window manager");
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            Log.e(TAG, "WindowManager is null!");
        } else {
            Log.d(TAG, "WindowManager obtained successfully");
        }
    }

    private void setupLayoutParams() {
        Log.d(TAG, "Setting up layout parameters");

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            Log.d(TAG, "Using TYPE_APPLICATION_OVERLAY (API >= 26)");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
            Log.d(TAG, "Using TYPE_PHONE (API 23-25)");
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            Log.d(TAG, "Using TYPE_SYSTEM_ALERT (API < 23)");
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 100;
        params.y = 200;

        Log.d(TAG, "Layout params: type=" + layoutFlag + ", flags=" + params.flags +
                ", position=(" + params.x + "," + params.y + ")");
    }

    private void setupDragFunctionality() {
        Log.d(TAG, "Setting up drag functionality");

        // For debug view, make the entire view draggable
        View dragTarget = floatingView;

        // If using the proper layout, find the drag handle
        try {
            View dragHandle = floatingView.findViewById(R.id.drag_handle);
            if (dragHandle != null) {
                dragTarget = dragHandle;
                Log.d(TAG, "Found drag handle, using it as drag target");
            } else {
                Log.d(TAG, "No drag handle found, making entire view draggable");
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception finding drag handle, using entire view");
        }

        dragTarget.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.d(TAG, "Touch event: " + event.getAction() + " at screen (" +
                        event.getRawX() + ", " + event.getRawY() + ")");
                Log.d(TAG, "Current window position: (" + params.x + ", " + params.y + ")");

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        Log.d(TAG, "ACTION_DOWN: initial window pos (" + initialX + "," + initialY +
                                "), initial touch (" + initialTouchX + "," + initialTouchY + ")");
                        return true;

                    case MotionEvent.ACTION_UP:
                        Log.d(TAG, "ACTION_UP: final position (" + params.x + "," + params.y + ")");
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int newX = initialX + (int) (event.getRawX() - initialTouchX);
                        int newY = initialY + (int) (event.getRawY() - initialTouchY);

                        Log.d(TAG, "ACTION_MOVE: calculated new position (" + newX + "," + newY + ")");

                        // Get screen dimensions for debugging
                        Display display = windowManager.getDefaultDisplay();
                        android.graphics.Point size = new android.graphics.Point();
                        display.getSize(size);
                        Log.d(TAG, "Screen size: " + size.x + "x" + size.y);

                        // Optional: Add screen bounds checking for debugging
                        if (newX < 0) {
                            Log.d(TAG, "X position clamped from " + newX + " to 0");
                            newX = 0;
                        }
                        if (newY < 0) {
                            Log.d(TAG, "Y position clamped from " + newY + " to 0");
                            newY = 0;
                        }
                        if (newX > size.x - floatingView.getWidth()) {
                            Log.d(TAG, "X position clamped to screen edge");
                            newX = size.x - floatingView.getWidth();
                        }
                        if (newY > size.y - floatingView.getHeight()) {
                            Log.d(TAG, "Y position clamped to screen edge");
                            newY = size.y - floatingView.getHeight();
                        }

                        params.x = newX;
                        params.y = newY;

                        try {
                            windowManager.updateViewLayout(floatingView, params);
                            Log.d(TAG, "Successfully updated view layout to (" + params.x + "," + params.y + ")");
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating view layout", e);
                        }

                        return true;
                }
                return false;
            }
        });
    }

    private void setupKeyboard() {
        Log.d(TAG, "Setting up keyboard");
        try {
            LatinKeyboardView keyboardView = floatingView.findViewById(R.id.keyboard);
            if (keyboardView != null && imeService != null) {
                keyboardView.setOnKeyboardActionListener(imeService);
                Log.d(TAG, "Keyboard listener set successfully");
            } else {
                Log.d(TAG, "Keyboard view or IME service is null");
            }

            // Add close button functionality
            View closeButton = floatingView.findViewById(R.id.close_button);
            if (closeButton != null) {
                closeButton.setOnClickListener(v -> {
                    Log.d(TAG, "Close button clicked");
                    hideFloatingKeyboard();
                });
                Log.d(TAG, "Close button listener set");
            } else {
                Log.d(TAG, "Close button not found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up keyboard", e);
        }
    }

    public void showFloatingKeyboard() {
        Log.d(TAG, "=== showFloatingKeyboard called ===");

        if (floatingView == null) {
            Log.e(TAG, "floatingView is null, cannot show");
            return;
        }

        if (windowManager == null) {
            Log.e(TAG, "windowManager is null, cannot show");
            return;
        }

        try {
            if (!isViewAdded) {
                Log.d(TAG, "Adding view to window manager");
                windowManager.addView(floatingView, params);
                isViewAdded = true;
                Log.d(TAG, "View added successfully, should be visible now");

                // Show a toast for debugging
                Toast.makeText(this, "Floating keyboard should be visible", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "View already added to window manager");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding view to window manager", e);
            isViewAdded = false;
        }
    }

    public void hideFloatingKeyboard() {
        Log.d(TAG, "=== hideFloatingKeyboard called ===");

        if (floatingView != null && isViewAdded) {
            try {
                windowManager.removeView(floatingView);
                isViewAdded = false;
                Log.d(TAG, "View removed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error removing view", e);
            }
        } else {
            Log.d(TAG, "View not added or floatingView is null");
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "=== FloatingKeyboardService onDestroy ===");
        super.onDestroy();
        hideFloatingKeyboard();
    }
}