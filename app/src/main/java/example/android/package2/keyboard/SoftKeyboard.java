package example.android.package2.keyboard;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.text.InputType;
import android.text.method.MetaKeyKeyListener;
import android.util.DisplayMetrics;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Button;

import example.android.package2.emoji.manager.EmojiManager;
import example.android.package2.emoji.extensions.SoftKeyboardEmojiExtensionKt;
import example.android.package2.sharing.extensions.SoftKeyboardSharingExtensionKt;

import androidx.annotation.NonNull;

import com.example.aosp_poc.R;

import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.io.File;

public class SoftKeyboard extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    static final boolean PROCESS_HARD_KEYS = true;

    // Add these fields to your SoftKeyboard class
    private EmojiManager normalEmojiManager;

    // Add these fields at the top of the class
    private boolean isFloatingMode = false;

    private InputMethodManager mInputMethodManager;
    private LatinKeyboardView mInputView;
    private CompletionInfo[] mCompletions;
    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;
    private LatinKeyboard mSymbolsKeyboard;
    private LatinKeyboard mSymbolsShiftedKeyboard;
    private LatinKeyboard mQwertyKeyboard;
    private LatinKeyboard mCurKeyboard;
    private String mWordSeparators;

    // Overlay functionality fields
    private static final String TAG = "FloatingKeyboard";
    private WindowManager overlayWindowManager;
    private View overlayView;
    private boolean isOverlayVisible = false;

    private boolean mKeyboardsInitialized = false;
    private boolean mIsCurrentlyFloating = false;


    // Core float variables following BobbleKeyboard
    private RelativeLayout kFrame;
    private LinearLayout kNavBar;
    private LinearLayout navBarIndicator;

    // Position storage (static to persist between toggles)
    private static float savedFloatX = 0;
    private static float savedFloatY = 0;

    // Touch handling
    private float initialTouchX = 0;
    private float initialTouchY = 0;

    // Window management
    private Dialog mDialog;
    private View parentContainer;

    // Chat detection fields
    private boolean isChatTextBox = false;
    private View emojiRowContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        mWordSeparators = getResources().getString(R.string.word_separators);
    }

    @NonNull
    Context getDisplayContext() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return this;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            return this;
        }
        final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        return createDisplayContext(wm.getDefaultDisplay());
    }

    @Override
    public void onInitializeInterface() {
        final Context displayContext = getDisplayContext();
        int displayWidth = getMaxWidth();

        if (mQwertyKeyboard != null && displayWidth == mLastDisplayWidth && mKeyboardsInitialized) {
            return;
        }
        mLastDisplayWidth = displayWidth;

        // Always create normal keyboards initially
        mQwertyKeyboard = new LatinKeyboard(displayContext, R.xml.qwerty);
        mSymbolsKeyboard = new LatinKeyboard(displayContext, R.xml.symbols);
        mSymbolsShiftedKeyboard = new LatinKeyboard(displayContext, R.xml.symbols_shift);
        mCurKeyboard = mQwertyKeyboard;
        mKeyboardsInitialized = true;

        Log.d("SoftKeyboard", "Keyboards initialized");
    }

    @Override
    public View onCreateInputView() {
        View normalLayout = getLayoutInflater().inflate(R.layout.normal_keyboard_layout_with_emoji, null);

        // Initialize views
        parentContainer = normalLayout.findViewById(R.id.parent_keyboard_container);
        kFrame = normalLayout.findViewById(R.id.parent_keyboard);
        kNavBar = normalLayout.findViewById(R.id.navBar);
        navBarIndicator = normalLayout.findViewById(R.id.navBar_indicator);
        mInputView = normalLayout.findViewById(R.id.keyboard);

        emojiRowContainer = normalLayout.findViewById(R.id.emoji_row_container);

        if (mInputView != null) {
            mInputView.setOnKeyboardActionListener(this);
        }

        setupFloatToggle(normalLayout);
        setupDragHandling();

        // Setup emoji support
        normalEmojiManager = SoftKeyboardEmojiExtensionKt.setupEmojiSupport(this, normalLayout);
        setupSharingButtons(normalLayout);

        updateEmojiRowVisibility();

        return normalLayout;
    }

    private void setupFloatToggle(View layout) {
        Button floatToggle = layout.findViewById(R.id.float_toggle_button);
        if (floatToggle != null) {
            floatToggle.setOnClickListener(v -> {
                isFloatingMode = !isFloatingMode;
                if (isFloatingMode) {
                    enterFloatingMode();
                } else {
                    exitFloatingMode();
                }
            });
        }
    }

    private void enterFloatingMode() {
        if (kFrame == null || parentContainer == null) return;

        Log.d("softkeyboard", "=== ENTERING FLOATING MODE ===");

        // CRITICAL: Set floating mode flag FIRST
        isFloatingMode = true;

        // STEP 1: Position keyboard IMMEDIATELY before any visual changes
        // This prevents the spawn-at-top-then-jump behavior
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        float targetX, targetY;
        if (savedFloatX == 0 && savedFloatY == 0) {
            targetX = screenWidth * 0.05f;
            targetY = screenHeight * 0.3f;
        } else {
            targetX = savedFloatX;
            targetY = savedFloatY;
        }

        // Set position IMMEDIATELY without animation
        kFrame.setX(targetX);
        kFrame.setY(targetY);
        Log.d("softkeyboard", "Position set immediately to prevent teleporting: (" + targetX + ", " + targetY + ")");

        // STEP 2: Modify window to give full screen access
        enableFullScreenWindow();

        // STEP 3: Make parent container use full screen
        makeContainerFullScreen();

        // STEP 4: Apply scaling AFTER position is set
        kFrame.setScaleX(0.8f);
        kFrame.setScaleY(0.8f);
        Log.d("softkeyboard", "kFrame scaled to 0.8");

        // STEP 5: Show drag handle
        if (kNavBar != null) {
            kNavBar.setVisibility(View.VISIBLE);
            Log.d("softkeyboard", "kNavBar made visible");
        }

        // STEP 6: Request insets updates
        kFrame.post(() -> {
            requestInsetUpdate();

            kFrame.postDelayed(() -> {
                requestInsetUpdate();
            }, 50);

            kFrame.postDelayed(() -> {
                requestInsetUpdate();
            }, 100);
        });
    }

    private void requestInsetUpdate() {
        try {
            Log.d("softkeyboard", "=== Requesting insets update ===");

            // Method 1: Request through window
            Dialog dialog = getWindow();
            if (dialog != null && dialog.getWindow() != null) {
                Window window = dialog.getWindow();
                View decorView = window.getDecorView();
                if (decorView != null) {
                    decorView.requestApplyInsets();
                    Log.d("softkeyboard", "Requested insets via decorView");
                }
            }

            // Method 2: Request through input view
            if (mInputView != null) {
                mInputView.requestApplyInsets();
                Log.d("softkeyboard", "Requested insets via mInputView");
            }

            // Method 3: Request through parent container
            if (parentContainer != null) {
                parentContainer.requestApplyInsets();
                Log.d("softkeyboard", "Requested insets via parentContainer");
            }

            // Method 4: Force layout update
            if (kFrame != null) {
                kFrame.requestLayout();
                Log.d("softkeyboard", "Requested layout update for kFrame");
            }

        } catch (Exception e) {
            Log.e("softkeyboard", "Error requesting insets update", e);
        }
    }

    private void exitFloatingMode() {
        if (kFrame == null || parentContainer == null) return;

        Log.d("softkeyboard", "=== EXITING FLOATING MODE ===");

        // STEP 1: Reset floating mode flag FIRST
        isFloatingMode = false;
        Log.d("softkeyboard", "isFloatingMode set to false");

        // STEP 2: Ensure full opacity is restored
        kFrame.setAlpha(1.0f);
        Log.d("softkeyboard", "Opacity restored to full (1.0)");

        // STEP 3: Hide drag handle
        if (kNavBar != null) {
            kNavBar.setVisibility(View.GONE);
            Log.d("softkeyboard", "kNavBar hidden");
        }

        // STEP 4: Reset scale BEFORE position changes
        kFrame.setScaleX(1.0f);
        kFrame.setScaleY(1.0f);
        Log.d("softkeyboard", "Scale reset to 1.0");

        clearFloatingModeStyles();

        // STEP 5: CRITICAL - Reset all layout parameters that might cause clipping
        if (kFrame.getLayoutParams() != null) {
            ViewGroup.LayoutParams params = kFrame.getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            kFrame.setLayoutParams(params);
            Log.d("softkeyboard", "kFrame layout params reset to MATCH_PARENT/WRAP_CONTENT");
        }

        // STEP 6: Reset any padding that might have been applied
        kFrame.setPadding(0, 0, 0, 0);
        Log.d("softkeyboard", "kFrame padding reset to 0");

        // STEP 7: Reset any margins on kFrame
        if (kFrame.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) kFrame.getLayoutParams();
            marginParams.setMargins(0, 0, 0, 0);
            kFrame.setLayoutParams(marginParams);
            Log.d("softkeyboard", "kFrame margins reset to 0");
        }

        // STEP 8: Reset container to normal IME size
        resetContainerToNormal();

        // STEP 9: Reset window properties
        resetToNormalWindow();

        // STEP 10: Reset keyboard position with all transformations cleared
        kFrame.setTranslationX(0f);
        kFrame.setTranslationY(0f);
        kFrame.setX(0f);
        kFrame.setY(0f);

        Log.d("softkeyboard", "Position and translation reset to 0");

        // STEP 11: Force layout update
        kFrame.requestLayout();
        if (parentContainer != null) {
            parentContainer.requestLayout();
        }

        // STEP 12: Final position animation with layout completion check
        kFrame.animate()
                .x(0)
                .y(0)
                .setDuration(0)
                .withEndAction(() -> {
                    // Final checks after animation
                    kFrame.setAlpha(1.0f);
                    kFrame.setTranslationX(0f);
                    kFrame.setTranslationY(0f);

                    // Force final layout update
                    kFrame.post(() -> {
                        kFrame.requestLayout();
                        Log.d("softkeyboard", "Final layout update completed");
                        Log.d("softkeyboard", "Final kFrame position: (" + kFrame.getX() + ", " + kFrame.getY() + ")");
                        Log.d("softkeyboard", "Final kFrame size: " + kFrame.getWidth() + "x" + kFrame.getHeight());
                    });
                })
                .start();

        Log.d("softkeyboard", "Exit floating mode completed");
    }

    private void clearFloatingModeStyles() {
        try {
            // Clear any background or styling that might have been applied during floating
            if (kFrame != null) {
                kFrame.setElevation(0f);
                kFrame.setTranslationZ(0f);
                Log.d("softkeyboard", "Cleared elevation and translationZ");
            }

            // Reset any custom background
            if (parentContainer != null) {
                parentContainer.setBackground(null);
                Log.d("softkeyboard", "Cleared container background");
            }

        } catch (Exception e) {
            Log.e("softkeyboard", "Error clearing floating styles", e);
        }
    }
    private void enableFullScreenWindow() {
        try {
            Dialog dialog = getWindow();
            if (dialog == null || dialog.getWindow() == null) {
                Log.e("softkeyboard", "enableFullScreenWindow: dialog or window is null");
                return;
            }

            Window window = dialog.getWindow();
            WindowManager.LayoutParams params = window.getAttributes();

            // CRITICAL: Make window cover entire screen
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.TOP | Gravity.LEFT;

            // Set window position to top-left of screen
            params.x = 0;
            params.y = 0;

            // FIXED: Proper flag configuration for floating mode
            // CRITICAL: Do NOT use FLAG_NOT_TOUCH_MODAL - this blocks background touches
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            params.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

            // Keep window focusable and touchable
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

            // Add this flag to maintain input connection
            params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

            // Remove soft input adjustment
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;

            window.setAttributes(params);

            // Ensure input view maintains focus
            if (mInputView != null) {
                mInputView.post(() -> {
                    mInputView.requestFocus();
                    Log.d("softkeyboard", "Requested focus for mInputView");
                });
            }

            Log.d("softkeyboard", "Window set to full screen with proper touch handling");

        } catch (Exception e) {
            Log.e("softkeyboard", "Error setting full screen window", e);
        }
    }

    // FIXED: Make parent container use full screen bounds with proper padding
    // In makeContainerFullScreen() method, remove or reduce padding:
    private void makeContainerFullScreen() {
        if (parentContainer == null) return;

        try {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;

            ViewGroup.LayoutParams containerParams = parentContainer.getLayoutParams();
            if (containerParams == null) {
                containerParams = new ViewGroup.LayoutParams(screenWidth, screenHeight);
            } else {
                containerParams.width = screenWidth;
                containerParams.height = screenHeight;
            }
            parentContainer.setLayoutParams(containerParams);

            // CRITICAL: Remove padding to prevent touch issues
            parentContainer.setPadding(0, 0, 0, 0);

            parentContainer.requestLayout();

            Log.d("FloatKeyboard", "Container set to full screen without padding");

        } catch (Exception e) {
            Log.e("FloatKeyboard", "Error making container full screen", e);
        }
    }

    private void positionKeyboardInCenter() {
        // Get screen dimensions
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        // Calculate position
        float targetX, targetY;

        if (savedFloatX == 0 && savedFloatY == 0) {
            // First time - position in center-ish area
            targetX = screenWidth * 0.05f; // 10% from left
            targetY = screenHeight * 0.3f; // 30% from top
        } else {
            // Use saved position
            targetX = savedFloatX;
            targetY = savedFloatY;
        }

        // Ensure position is within bounds
        float maxX = screenWidth - (kFrame.getWidth() * 0.8f);
        float maxY = screenHeight - (kFrame.getHeight() * 0.8f);

        targetX = Math.max(0, Math.min(targetX, maxX));
        targetY = Math.max(0, Math.min(targetY, maxY));

        Log.d("FloatKeyboard", String.format("Positioning keyboard at: x=%f, y=%f", targetX, targetY));

        // Move keyboard using BobbleKeyboard method
        kFrame.animate().x(targetX).y(targetY).setDuration(0).start();

        // Save position
        savedFloatX = targetX;
        savedFloatY = targetY;
    }

    private void resetContainerToNormal() {
        if (parentContainer == null) return;

        try {
            Log.d("softkeyboard", "=== Resetting container to normal ===");

            // Reset container layout parameters
            ViewGroup.LayoutParams containerParams = parentContainer.getLayoutParams();
            if (containerParams != null) {
                containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                containerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                parentContainer.setLayoutParams(containerParams);
                Log.d("softkeyboard", "Container params reset to MATCH_PARENT/WRAP_CONTENT");
            }

            // Reset container padding
            parentContainer.setPadding(0, 0, 0, 0);
            Log.d("softkeyboard", "Container padding reset");

            // Reset container margins if applicable
            if (containerParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) containerParams;
                marginParams.setMargins(0, 0, 0, 0);
                parentContainer.setLayoutParams(marginParams);
                Log.d("softkeyboard", "Container margins reset");
            }

            // Force container layout update
            parentContainer.requestLayout();
            Log.d("softkeyboard", "Container reset completed");

        } catch (Exception e) {
            Log.e("softkeyboard", "Error resetting container", e);
        }
    }

    private void resetToNormalWindow() {
        try {
            Dialog dialog = getWindow();
            if (dialog == null || dialog.getWindow() == null) return;

            Window window = dialog.getWindow();

            // CRITICAL: Create completely new window params instead of modifying existing ones
            WindowManager.LayoutParams newParams = new WindowManager.LayoutParams();

            // Set normal IME window properties from scratch
            newParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            newParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            newParams.gravity = Gravity.BOTTOM;
            newParams.format = android.graphics.PixelFormat.TRANSLUCENT;

            // CRITICAL: Set normal IME flags from scratch (don't modify existing)
            newParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;

            // Normal IME window type
            newParams.type = WindowManager.LayoutParams.TYPE_INPUT_METHOD;

            // Reset position
            newParams.x = 0;
            newParams.y = 0;

            // Reset soft input mode
            newParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

            // Apply new params
            window.setAttributes(newParams);

            Log.d("FloatKeyboard", "Window completely reset with new params for normal IME mode");

        } catch (Exception e) {
            Log.e("FloatKeyboard", "Error resetting window", e);
        }
    }

    private void setupDragHandling() {
        if (kNavBar == null) return;

        kNavBar.setOnTouchListener((v, event) -> {
            if (!isFloatingMode) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.d("softkeyboard", "DRAG: ACTION_DOWN - touch detected on kNavBar");
                    Log.d("softkeyboard", "DRAG: isFloatingMode = " + isFloatingMode);
                    Log.d("softkeyboard", "DRAG: kFrame position: (" + kFrame.getX() + ", " + kFrame.getY() + ")");
                    kFrame.setAlpha(0.5f);
                    initialTouchX = kFrame.getX() - event.getRawX();
                    initialTouchY = kFrame.getY() - event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    Log.d("softkeyboard", "DRAG: ACTION_MOVE - dragging");
                    float newX = event.getRawX() + initialTouchX;
                    float newY = event.getRawY() + initialTouchY;

                    // Get screen bounds
                    DisplayMetrics metrics = getResources().getDisplayMetrics();
                    int screenWidth = metrics.widthPixels;
                    int screenHeight = metrics.heightPixels;

                    // IMPORTANT: Use kFrame dimensions directly, not scaled dimensions for bounds
                    float kFrameWidth = kFrame.getWidth();
                    float kFrameHeight = kFrame.getHeight();

                    // Calculate what the actual visual bounds would be at newX, newY
                    float scaleX = kFrame.getScaleX(); // 0.8f
                    float scaleY = kFrame.getScaleY(); // 0.8f

                    // Visual dimensions after scaling
                    float visualWidth = kFrameWidth * scaleX;
                    float visualHeight = kFrameHeight * scaleY;

                    // Offset due to scaling from center
                    float xOffset = (kFrameWidth - visualWidth) / 2f;
                    float yOffset = (kFrameHeight - visualHeight) / 2f;

                    // Calculate where the visual edges would be
                    float visualLeft = newX + xOffset;
                    float visualRight = newX + xOffset + visualWidth;
                    float visualTop = newY + yOffset;
                    float visualBottom = newY + yOffset + visualHeight;

                    Log.d("softkeyboard", "VISUAL BOUNDS CHECK:");
                    Log.d("softkeyboard", "  Proposed kFrame position: (" + newX + ", " + newY + ")");
                    Log.d("softkeyboard", "  Visual left: " + visualLeft + ", right: " + visualRight);
                    Log.d("softkeyboard", "  Visual top: " + visualTop + ", bottom: " + visualBottom);
                    Log.d("softkeyboard", "  Screen: " + screenWidth + "x" + screenHeight);

                    // FIXED: Constrain based on visual bounds, not kFrame bounds
                    float minVisibleWidth = 100f; // 100px must remain visible

                    // Left constraint: visual left edge can't go past screen left
                    if (visualLeft < 0) {
                        newX = newX - visualLeft; // Adjust kFrame position to keep visual on screen
                        Log.d("softkeyboard", "LEFT BOUND: Adjusted newX to " + newX);
                    }

                    // Right constraint: visual right edge can't go past screen right
                    if (visualRight > screenWidth) {
                        float overshoot = visualRight - screenWidth;
                        newX = newX - overshoot; // Adjust kFrame position
                        Log.d("softkeyboard", "RIGHT BOUND: Adjusted newX to " + newX);
                    }

                    // Top constraint: visual top can't go above screen
                    if (visualTop < 0) {
                        newY = newY - visualTop;
                        Log.d("softkeyboard", "TOP BOUND: Adjusted newY to " + newY);
                    }

                    Log.d("softkeyboard", "FINAL: newX=" + newX + ", newY=" + newY);

                    // Check for docking at bottom (use visual bottom)
                    float finalVisualBottom = newY + yOffset + visualHeight;
                    if (finalVisualBottom > screenHeight - 50) {
                        Log.d("softkeyboard", "DOCKING: Threshold reached, docking keyboard");
                        kFrame.setAlpha(1.0f);
                        isFloatingMode = false;
                        exitFloatingMode();
                        return true;
                    }

                    // Update position using BobbleKeyboard method
                    kFrame.animate().x(newX).y(newY).setDuration(0).start();
                    return true;

                case MotionEvent.ACTION_UP:
                    Log.d("softkeyboard", "DRAG: ACTION_UP - drag ended");
                    // FIXED: Always restore full opacity on drag end
                    kFrame.setAlpha(1.0f);
                    savedFloatX = kFrame.getX();
                    savedFloatY = kFrame.getY();
                    Log.d("softkeyboard", "DRAG: Opacity restored, position saved: (" + savedFloatX + ", " + savedFloatY + ")");
                    return true;
            }
            return false;
        });
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        Log.d("softkeyboard", "=== onComputeInsets called ===");
        Log.d("softkeyboard", "isFloatingMode: " + isFloatingMode);

        // CRITICAL: Always call super first
        super.onComputeInsets(outInsets);

        if (isFloatingMode && kFrame != null && kFrame.getWidth() > 0 && kFrame.getHeight() > 0) {
            Log.d("softkeyboard", "Setting floating mode insets");

            // CRITICAL: These settings allow background touches
            outInsets.contentTopInsets = 0;
            outInsets.visibleTopInsets = 0;
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;

            // Calculate scaled keyboard bounds
            float kFrameX = kFrame.getX();
            float kFrameY = kFrame.getY();
            float kFrameWidth = kFrame.getWidth();
            float kFrameHeight = kFrame.getHeight();
            float scaleX = kFrame.getScaleX();
            float scaleY = kFrame.getScaleY();

            float scaledWidth = kFrameWidth * scaleX;
            float scaledHeight = kFrameHeight * scaleY;

            // Account for scaling offset (scaling happens from center)
            float xOffset = (kFrameWidth - scaledWidth) / 2f;
            float yOffset = (kFrameHeight - scaledHeight) / 2f;

            // Calculate actual touchable area
            int left = Math.max(0, (int)(kFrameX + xOffset));
            int top = Math.max(0, (int)(kFrameY + yOffset));
            int right = (int)(kFrameX + xOffset + scaledWidth);
            int bottom = (int)(kFrameY + yOffset + scaledHeight);

            // Validate bounds
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            right = Math.min(right, metrics.widthPixels);
            bottom = Math.min(bottom, metrics.heightPixels);

            // CRITICAL: Only set keyboard area as touchable, rest is for background app
            if (right > left && bottom > top) {
                outInsets.touchableRegion.set(left, top, right, bottom);
                Log.d("softkeyboard", "Touchable region set to keyboard only: (" + left + ", " + top + ", " + right + ", " + bottom + ")");
            } else {
                // Fallback - this should not happen
                Log.e("softkeyboard", "Invalid bounds, using minimal fallback");
                outInsets.touchableRegion.set((int)kFrameX, (int)kFrameY,
                        (int)(kFrameX + kFrameWidth), (int)(kFrameY + kFrameHeight));
            }

        } else {
            // Normal mode - let super handle it completely
            Log.d("softkeyboard", "Using default insets for normal mode");
        }
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        if (mIsCurrentlyFloating) {
            return true; // Always show input view in floating mode
        }
        return super.onEvaluateInputViewShown();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // Never use fullscreen mode
    }

    // Add this new method to SoftKeyboard.java:
    private void setupSharingButtons(View layout) {
        Button shareTextButton = layout.findViewById(R.id.share_text_button);

        if (shareTextButton != null) {
            shareTextButton.setOnClickListener(v -> {
                SoftKeyboardSharingExtensionKt.shareCurrentText(this);
            });
        }
    }

    // Update switchToFloatingMode and switchToNormalMode to recreate keyboards:

    private void switchToNormalMode() {
        isFloatingMode = false;

        // Force recreation of keyboards with proper sizing
        onInitializeInterface();

        View newInputView = onCreateInputView();
        setInputView(newInputView);

        EditorInfo currentEditorInfo = getCurrentInputEditorInfo();
        if (currentEditorInfo != null) {
            onStartInput(currentEditorInfo, true);
            onStartInputView(currentEditorInfo, true);
        }
    }

    private void hideOverlay() {
        if (overlayView != null && overlayWindowManager != null && isOverlayVisible) {
            try {
                overlayWindowManager.removeView(overlayView);
                isOverlayVisible = false;
            } catch (Exception e) {
                Log.e(TAG, "Failed to hide overlay", e);
            }
        }
    }

    private void setLatinKeyboard(LatinKeyboard nextKeyboard) {
        setActiveKeyboard(nextKeyboard);
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        mComposing.setLength(0);

        if (!restarting) {
            mMetaState = 0;
        }

        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;

        isChatTextBox = detectChatTextBox(attribute);
        updateEmojiRowVisibility();

        // Ensure keyboards are initialized
        if (!mKeyboardsInitialized || mQwertyKeyboard == null) {
            onInitializeInterface();
        }

        // Determine keyboard type based on input type
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                mCurKeyboard = mSymbolsKeyboard;
                break;
            case InputType.TYPE_CLASS_PHONE:
                mCurKeyboard = mSymbolsKeyboard;
                break;
            case InputType.TYPE_CLASS_TEXT:
                mCurKeyboard = mQwertyKeyboard;
                mPredictionOn = true;

                int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    mPredictionOn = false;
                }

                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == InputType.TYPE_TEXT_VARIATION_URI
                        || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    mPredictionOn = false;
                }

                if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
                }

                updateShiftKeyState(attribute);
                break;
            default:
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
        }

        // Only set IME options if keyboard is not null
        if (mCurKeyboard != null) {
            mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
        }
    }

    private boolean detectChatTextBox(EditorInfo editorInfo) {
        if (editorInfo == null) return false;

        boolean isChatDetected = false;

        int inputType = editorInfo.inputType;
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int inputVariation = inputType & InputType.TYPE_MASK_VARIATION;
        int inputFlags = inputType & InputType.TYPE_MASK_FLAGS;

        Log.d("ChatDetection", "=== ANALYZING INPUT FIELD ===");
        Log.d("ChatDetection", "Full inputType: 0x" + Integer.toHexString(inputType));
        Log.d("ChatDetection", "inputClass: 0x" + Integer.toHexString(inputClass) + " (" + getInputClassName(inputClass) + ")");
        Log.d("ChatDetection", "inputVariation: 0x" + Integer.toHexString(inputVariation) + " (" + getInputVariationName(inputVariation) + ")");
        Log.d("ChatDetection", "inputFlags: 0x" + Integer.toHexString(inputFlags));

        logInputFlags(inputFlags);

        // Check IME options first - CRITICAL: Exclude search fields
        int imeOptions = editorInfo.imeOptions;
        int actionId = imeOptions & EditorInfo.IME_MASK_ACTION;

        Log.d("ChatDetection", "imeOptions: 0x" + Integer.toHexString(imeOptions));
        Log.d("ChatDetection", "actionId: 0x" + Integer.toHexString(actionId) + " (" + getImeActionName(actionId) + ")");

        // CRITICAL: If it's a search field, it's definitely NOT a chat field
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            Log.d("ChatDetection", "✗ NOT CHAT - Search field detected");
            Log.d("ChatDetection", "FINAL RESULT: NON-CHAT FIELD (SEARCH)");
            Log.d("ChatDetection", "=============================");
            return false;
        }

        // Now check for chat characteristics only if it's NOT a search field
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            // Strategy 1: Check for messaging-friendly variations
            if (inputVariation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE ||
                    inputVariation == InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE) {
                Log.d("ChatDetection", "✓ CHAT detected via input variation");
                isChatDetected = true;
            }

            // Strategy 2: Check for multiline + cap sentences combination (like Telegram chat)
            if ((inputFlags & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 &&
                    (inputFlags & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
                Log.d("ChatDetection", "✓ CHAT detected via multiline + cap sentences");
                isChatDetected = true;
            }

            // Strategy 3: Check for auto-correct flags (common in messaging)
            if ((inputFlags & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0 &&
                    (inputFlags & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
                Log.d("ChatDetection", "✓ CHAT detected via auto-correct + cap sentences");
                isChatDetected = true;
            }
        }

        // Strategy 4: Check hint text for messaging keywords (restricted list)
        // But only if hint doesn't contain "search"
        CharSequence hintText = editorInfo.hintText;
        Log.d("ChatDetection", "hintText: '" + hintText + "'");
        if (hintText != null) {
            String hint = hintText.toString().toLowerCase();

            // Exclude if hint contains "search"
            if (hint.contains("search")) {
                Log.d("ChatDetection", "✗ NOT CHAT - Hint contains 'search'");
            } else if (hint.contains("message") || hint.contains("chat") ||
                    hint.contains("say") || hint.contains("reply")) {
                Log.d("ChatDetection", "✓ CHAT detected via hint text: " + hint);
                isChatDetected = true;
            }
        }

        // Strategy 5: Check for IME_ACTION_SEND (definitive chat indicator)
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            Log.d("ChatDetection", "✓ CHAT detected via IME_ACTION_SEND");
            isChatDetected = true;
        }

        // Log package name for reference
        String packageName = editorInfo.packageName;
        Log.d("ChatDetection", "packageName: " + packageName);

        Log.d("ChatDetection", "FINAL RESULT: " + (isChatDetected ? "CHAT FIELD" : "NON-CHAT FIELD"));
        Log.d("ChatDetection", "=============================");

        return isChatDetected;
    }

    private void logInputFlags(int inputFlags) {
        Log.d("ChatDetection", "Input Flags Analysis:");

        if ((inputFlags & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
            Log.d("ChatDetection", "  - TYPE_TEXT_FLAG_CAP_CHARACTERS");
        }
        if ((inputFlags & InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
            Log.d("ChatDetection", "  - TYPE_TEXT_FLAG_CAP_WORDS");
        }
        if ((inputFlags & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
            Log.d("ChatDetection", "  - TYPE_TEXT_FLAG_CAP_SENTENCES");
        }
        if ((inputFlags & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0) {
            Log.d("ChatDetection", "  - TYPE_TEXT_FLAG_AUTO_CORRECT");
        }
        if ((inputFlags & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
            Log.d("ChatDetection", "  - TYPE_TEXT_FLAG_AUTO_COMPLETE");
        }
        if ((inputFlags & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
            Log.d("ChatDetection", "  - TYPE_TEXT_FLAG_MULTI_LINE");
        }
        if ((inputFlags & InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0) {
            Log.d("ChatDetection", "  - TYPE_TEXT_FLAG_IME_MULTI_LINE");
        }
        if ((inputFlags & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
            Log.d("ChatDetection", "  - TYPE_TEXT_FLAG_NO_SUGGESTIONS");
        }
    }

    private String getInputClassName(int inputClass) {
        switch (inputClass) {
            case InputType.TYPE_CLASS_TEXT: return "TYPE_CLASS_TEXT";
            case InputType.TYPE_CLASS_NUMBER: return "TYPE_CLASS_NUMBER";
            case InputType.TYPE_CLASS_PHONE: return "TYPE_CLASS_PHONE";
            case InputType.TYPE_CLASS_DATETIME: return "TYPE_CLASS_DATETIME";
            default: return "UNKNOWN";
        }
    }

    private String getInputVariationName(int inputVariation) {
        switch (inputVariation) {
            case InputType.TYPE_TEXT_VARIATION_NORMAL: return "TYPE_TEXT_VARIATION_NORMAL";
            case InputType.TYPE_TEXT_VARIATION_URI: return "TYPE_TEXT_VARIATION_URI";
            case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS: return "TYPE_TEXT_VARIATION_EMAIL_ADDRESS";
            case InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT: return "TYPE_TEXT_VARIATION_EMAIL_SUBJECT";
            case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE: return "TYPE_TEXT_VARIATION_SHORT_MESSAGE";
            case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE: return "TYPE_TEXT_VARIATION_LONG_MESSAGE";
            case InputType.TYPE_TEXT_VARIATION_PERSON_NAME: return "TYPE_TEXT_VARIATION_PERSON_NAME";
            case InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS: return "TYPE_TEXT_VARIATION_POSTAL_ADDRESS";
            case InputType.TYPE_TEXT_VARIATION_PASSWORD: return "TYPE_TEXT_VARIATION_PASSWORD";
            case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD: return "TYPE_TEXT_VARIATION_VISIBLE_PASSWORD";
            case InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT: return "TYPE_TEXT_VARIATION_WEB_EDIT_TEXT";
            case InputType.TYPE_TEXT_VARIATION_FILTER: return "TYPE_TEXT_VARIATION_FILTER";
            case InputType.TYPE_TEXT_VARIATION_PHONETIC: return "TYPE_TEXT_VARIATION_PHONETIC";
            case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS: return "TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS";
            case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD: return "TYPE_TEXT_VARIATION_WEB_PASSWORD";
            default: return "UNKNOWN";
        }
    }

    private String getImeActionName(int actionId) {
        switch (actionId) {
            case EditorInfo.IME_ACTION_UNSPECIFIED: return "IME_ACTION_UNSPECIFIED";
            case EditorInfo.IME_ACTION_NONE: return "IME_ACTION_NONE";
            case EditorInfo.IME_ACTION_GO: return "IME_ACTION_GO";
            case EditorInfo.IME_ACTION_SEARCH: return "IME_ACTION_SEARCH";
            case EditorInfo.IME_ACTION_SEND: return "IME_ACTION_SEND";
            case EditorInfo.IME_ACTION_NEXT: return "IME_ACTION_NEXT";
            case EditorInfo.IME_ACTION_DONE: return "IME_ACTION_DONE";
            case EditorInfo.IME_ACTION_PREVIOUS: return "IME_ACTION_PREVIOUS";
            default: return "UNKNOWN";
        }
    }

    private void updateEmojiRowVisibility() {
        if (emojiRowContainer != null) {
            int visibility = isChatTextBox ? View.VISIBLE : View.GONE;
            emojiRowContainer.setVisibility(visibility);

            Log.d("ChatDetection", "Emoji row visibility: " + (isChatTextBox ? "VISIBLE" : "GONE"));
        }
    }

    /**
     * Send emoji directly using commitContent API for chat apps
     */
    public boolean sendEmojiDirectly(String emojiUnicode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || !isChatTextBox) {
            Log.d("EmojiSend", "Cannot send directly - no input connection or not in chat");
            return false;
        }

        try {
            // Create emoji image
            Bitmap emojiImage = createEmojiImage(emojiUnicode);
            if (emojiImage == null) {
                Log.e("EmojiSend", "Failed to create emoji image");
                return false;
            }

            // Save to temporary file using internal storage to avoid FileProvider issues
            File imageFile = saveEmojiImageToInternal(emojiImage, "direct_emoji_" + System.currentTimeMillis() + ".png");
            if (imageFile == null) {
                Log.e("EmojiSend", "Failed to save emoji image");
                return false;
            }

            // Get URI using FileProvider with correct path
            Uri imageUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imageFile
            );

            Log.d("EmojiSend", "Created URI: " + imageUri.toString());

            // Create InputContentInfo
            androidx.core.view.inputmethod.InputContentInfoCompat contentInfo =
                    new androidx.core.view.inputmethod.InputContentInfoCompat(
                            imageUri,
                            new android.content.ClipDescription("Emoji", new String[]{"image/png"}),
                            null
                    );

            // Use InputConnectionCompat to commit content
            int flags = androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;

            boolean success = androidx.core.view.inputmethod.InputConnectionCompat.commitContent(
                    ic,
                    getCurrentInputEditorInfo(),
                    contentInfo,
                    flags,
                    null
            );

            Log.d("EmojiSend", "Direct emoji send result: " + success);

            // Clean up temp file after a delay
            new android.os.Handler().postDelayed(() -> {
                try {
                    if (imageFile.exists()) {
                        imageFile.delete();
                        Log.d("EmojiSend", "Cleaned up temp file");
                    }
                } catch (Exception e) {
                    Log.e("EmojiSend", "Error cleaning up temp file", e);
                }
            }, 5000); // 5 second delay

            return success;

        } catch (Exception e) {
            Log.e("EmojiSend", "Error in sendEmojiDirectly", e);
            return false;
        }
    }

    /**
     * Save emoji image to internal temp directory
     */
    private File saveEmojiImageToInternal(Bitmap bitmap, String filename) {
        try {
            // Use internal files directory instead of external
            File directory = new File(getFilesDir(), "temp_emoji");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File file = new File(directory, filename);
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();

            Log.d("EmojiSend", "Saved emoji image to: " + file.getAbsolutePath());
            return file;
        } catch (Exception e) {
            Log.e("EmojiSend", "Error saving temp emoji image", e);
            return null;
        }
    }

    /**
     * Create bitmap image from emoji unicode
     */
    private Bitmap createEmojiImage(String emoji) {
        try {
            int size = 200;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // White background
            canvas.drawColor(android.graphics.Color.WHITE);

            // Setup paint for emoji
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setTextSize(120f);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);

            // Calculate position to center the emoji
            float x = size / 2f;
            float y = size / 2f - (paint.descent() + paint.ascent()) / 2f;

            // Draw emoji
            canvas.drawText(emoji, x, y, paint);

            return bitmap;
        } catch (Exception e) {
            Log.e("EmojiSend", "Error creating emoji image", e);
            return null;
        }
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);

        setLatinKeyboard(mCurKeyboard);

        if (mInputView != null) {
            mInputView.closing();
            final InputMethodSubtype subtype = mInputMethodManager.getCurrentInputMethodSubtype();
            mInputView.setSubtypeOnSpaceKey(subtype);
            mInputView.requestLayout();
        }

        updateEmojiRowVisibility();
    }

    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        if (mInputView != null) {
            mInputView.setSubtypeOnSpaceKey(subtype);
        }
    }

    private void updateShiftKeyState(EditorInfo attr) {
        Log.d("softkeyboard", "=== updateShiftKeyState called ===");
        Log.d("softkeyboard", "Before update - shift: " + (mInputView != null && mQwertyKeyboard == mInputView.getKeyboard() ? mQwertyKeyboard.isShifted() : "N/A"));
        Log.d("softkeyboard", "Before update - caps lock: " + mCapsLock);

        if (attr != null && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }

            Log.d("softkeyboard", "Cursor caps mode: " + caps);

            // IMPORTANT: Don't override manual shift/caps lock state
            boolean shouldBeShifted = mCapsLock || caps != 0;

            // If we manually set shift (not caps lock), preserve it
            if (!mCapsLock && mQwertyKeyboard.isShifted()) {
                shouldBeShifted = true;
                Log.d("softkeyboard", "Preserving manual shift state");
            }

            mInputView.setShifted(shouldBeShifted);
            Log.d("softkeyboard", "After update - shift set to: " + shouldBeShifted);
        }

        Log.d("softkeyboard", "=== updateShiftKeyState completed ===");
    }


    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        Log.d("softkeyboard", "=== onKey called ===");
        Log.d("softkeyboard", "primaryCode: " + primaryCode);
        Log.d("softkeyboard", "Keyboard.KEYCODE_SHIFT = " + Keyboard.KEYCODE_SHIFT);
        Log.d("softkeyboard", "isFloatingMode: " + isFloatingMode);

        // Check if this is the shift key
        if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            Log.d("softkeyboard", "SHIFT KEY MATCHED!");
        }

        // CRITICAL: Ensure input connection is active in floating mode
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            Log.e("softkeyboard", "No input connection available");
            return;
        }

        Log.d("softkeyboard", "Input connection available, processing key");

        if (isWordSeparator(primaryCode)) {
            // Handle separator (including space)
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());

            // Special handling for space - check the word that was just completed
            if (primaryCode == ' ') {
                String lastWord = getLastWordFromCommittedText();
                Log.d("EmojiDebug", "Space pressed, last word: '" + lastWord + "'");
                notifyEmojiManagersWordCompletion(lastWord);
            } else {
                // For other separators, use the general word change notification
                notifyEmojiManagersWordChange();
            }
            return;

        }

        // Process the key normally
        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                Log.d("softkeyboard", "Processing backspace");
                ic.deleteSurroundingText(1, 0);
                notifyEmojiManagersWordChange();
                break;

            case Keyboard.KEYCODE_SHIFT:
                Log.d("softkeyboard", "SHIFT KEY DETECTED - Processing shift");
                handleShiftKey();
                return; // Important: return here, don't break

            case Keyboard.KEYCODE_MODE_CHANGE:
                Log.d("softkeyboard", "Processing mode change");
                handleModeChange();
                break;


            case '\n':
                Log.d("softkeyboard", "Processing enter/return");
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                break;

            default:
                if (primaryCode > 0) {
                    char code = (char) primaryCode;

                    Log.d("softkeyboard", "=== Character processing start ===");
                    Log.d("softkeyboard", "mCurKeyboard.isShifted(): " + (mCurKeyboard != null ? mCurKeyboard.isShifted() : "null keyboard"));
                    Log.d("softkeyboard", "mCapsLock: " + mCapsLock);
                    Log.d("softkeyboard", "mInputView.isShifted(): " + (mInputView != null ? mInputView.isShifted() : "null inputview"));

                    // IMPORTANT: Check shift/caps state BEFORE any modifications
                    boolean shouldCapitalize = false;
                    if (Character.isLetter(code)) {
                        shouldCapitalize = mCapsLock || (mCurKeyboard != null && mCurKeyboard.isShifted());
                        Log.d("softkeyboard", "shouldCapitalize: " + shouldCapitalize + " (caps: " + mCapsLock + ", shift: " + (mCurKeyboard != null ? mCurKeyboard.isShifted() : "null") + ")");
                    }

                    // Apply capitalization
                    if (shouldCapitalize) {
                        code = Character.toUpperCase(code);
                        Log.d("softkeyboard", "Applied caps/shift: " + code);
                    } else {
                        code = Character.toLowerCase(code);
                    }

                    Log.d("softkeyboard", "Processing character: " + code + " (code: " + primaryCode + ")");
                    ic.commitText(String.valueOf(code), 1);

                    // IMPORTANT: Reset shift state AFTER character processing (but ONLY if not in caps lock mode)
                    if (!mCapsLock && mCurKeyboard != null && mCurKeyboard.isShifted()) {
                        Log.d("softkeyboard", "About to reset shift state");
                        mCurKeyboard.setShifted(false);
                        mInputView.invalidateAllKeys();
                        Log.d("softkeyboard", "Shift reset after character input");
                    }

                    notifyEmojiManagersWordChange();
                    Log.d("softkeyboard", "=== Character processing end ===");
                } else {
                    Log.w("softkeyboard", "Unhandled primaryCode: " + primaryCode);
                }
                break;
        }
    }

    public boolean isWordSeparator(int code) {
        String separators = mWordSeparators;
        return separators.contains(String.valueOf((char)code));
    }
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    private String getLastWordFromCommittedText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";

        try {
            // Get text before cursor (up to 50 characters to find last word)
            CharSequence textBeforeCursor = ic.getTextBeforeCursor(50, 0);
            if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
                return "";
            }

            String text = textBeforeCursor.toString();
            // Split by spaces and get the last word
            String[] words = text.split("\\s+");
            if (words.length > 0) {
                String lastWord = words[words.length - 1].trim();
                // Remove punctuation from the end
                lastWord = lastWord.replaceAll("[^a-zA-Z0-9]$", "");
                return lastWord;
            }
        } catch (Exception e) {
            Log.e("SoftKeyboard", "Error getting last word", e);
        }

        return "";
    }

    private void notifyEmojiManagersWordCompletion(String lastWord) {
        Log.d("EmojiDebug", "notifyEmojiManagersWordCompletion: last word: '" + lastWord + "'");

        if (normalEmojiManager != null) {
            normalEmojiManager.handleWordCompletion(lastWord);
        }
    }
    private void notifyEmojiManagersWordChange() {
        String currentWord = getCurrentWord();

        Log.d("EmojiDebug", "notifyEmojiManagersWordChange: current word: '" + currentWord + "'");
        Log.d("EmojiDebug", "mComposing length: " + mComposing.length() + ", text: '" + mComposing.toString() + "'");

        if (normalEmojiManager != null) {
            if (mComposing.length() > 0) {
                // We have composing text - use composing text change
                Log.d("EmojiDebug", "Using handleComposingTextChange because mComposing has content");
                normalEmojiManager.handleComposingTextChange(currentWord);
            } else {
                // No composing text - but we need to be smarter about this
                // Check if we're at the end of a word (cursor immediately after word characters)
                if (isAtEndOfWord()) {
                    Log.d("EmojiDebug", "At end of word, treating as composing text change");
                    normalEmojiManager.handleComposingTextChange(currentWord);
                } else {
                    Log.d("EmojiDebug", "Not at end of word, treating as word completion");
                    normalEmojiManager.handleWordCompletion(currentWord);
                }
            }
        }


    }

    private String getCurrentWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";

        // First check if we have composing text
        if (mComposing.length() > 0) {
            Log.d("EmojiDebug", "getCurrentWord: Using composing text: '" + mComposing.toString() + "'");
            return mComposing.toString();
        }

        // No composing text, extract current word from committed text
        try {
            // Get text before and after cursor
            CharSequence textBefore = ic.getTextBeforeCursor(50, 0);
            CharSequence textAfter = ic.getTextAfterCursor(10, 0);

            if (textBefore == null) textBefore = "";
            if (textAfter == null) textAfter = "";

            Log.d("EmojiDebug", "getCurrentWord: textBefore: '" + textBefore + "'");
            Log.d("EmojiDebug", "getCurrentWord: textAfter: '" + textAfter + "'");

            // Find the current word by looking at character boundaries
            String beforeStr = textBefore.toString();
            String afterStr = textAfter.toString();

            // Find start of current word (work backwards from cursor)
            int wordStart = beforeStr.length();
            for (int i = beforeStr.length() - 1; i >= 0; i--) {
                char c = beforeStr.charAt(i);
                if (Character.isWhitespace(c) || isPunctuation(c)) {
                    wordStart = i + 1;
                    break;
                }
                if (i == 0) {
                    wordStart = 0;
                }
            }

            // Find end of current word (work forwards from cursor)
            int wordEnd = 0;
            for (int i = 0; i < afterStr.length(); i++) {
                char c = afterStr.charAt(i);
                if (Character.isWhitespace(c) || isPunctuation(c)) {
                    wordEnd = i;
                    break;
                }
                if (i == afterStr.length() - 1) {
                    wordEnd = afterStr.length();
                }
            }

            // Extract the current word
            String wordPart1 = beforeStr.substring(wordStart);
            String wordPart2 = afterStr.substring(0, wordEnd);
            String currentWord = (wordPart1 + wordPart2).trim();

            Log.d("EmojiDebug", "getCurrentWord: extracted word: '" + currentWord + "'");
            Log.d("EmojiDebug", "getCurrentWord: wordStart=" + wordStart + ", wordEnd=" + wordEnd);
            Log.d("EmojiDebug", "getCurrentWord: wordPart1='" + wordPart1 + "', wordPart2='" + wordPart2 + "'");

            return currentWord;

        } catch (Exception e) {
            Log.e("EmojiDebug", "Error in getCurrentWord", e);
            return "";
        }
    }

    private boolean isAtEndOfWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;

        try {
            // Get one character after cursor
            CharSequence charAfterCursor = ic.getTextAfterCursor(1, 0);

            // If there's no character after cursor, we're at end of text (treat as end of word)
            if (charAfterCursor == null || charAfterCursor.length() == 0) {
                Log.d("EmojiDebug", "isAtEndOfWord: true (end of text)");
                return true;
            }

            char nextChar = charAfterCursor.charAt(0);

            // If next character is whitespace or punctuation, we're at end of word
            boolean atEndOfWord = Character.isWhitespace(nextChar) || isPunctuation(nextChar);

            Log.d("EmojiDebug", "isAtEndOfWord: " + atEndOfWord + " (next char: '" + nextChar + "')");
            return atEndOfWord;

        } catch (Exception e) {
            Log.e("EmojiDebug", "Error in isAtEndOfWord", e);
            return false;
        }
    }

    private void handleShiftKey() {
        Log.d("softkeyboard", "=== handleShiftKey called ===");

        if (mInputView != null && mCurKeyboard == mQwertyKeyboard) {
            long now = System.currentTimeMillis();

            Log.d("softkeyboard", "Current shift state: " + mCurKeyboard.isShifted());
            Log.d("softkeyboard", "Current caps lock: " + mCapsLock);
            Log.d("softkeyboard", "Time since last shift: " + (now - mLastShiftTime));

            if (mLastShiftTime + 800 > now) {
                // Double tap within 800ms - toggle caps lock
                mCapsLock = !mCapsLock;
                mCurKeyboard.setShifted(mCapsLock);
                mLastShiftTime = 0;
                Log.d("softkeyboard", "Double tap - Caps lock toggled: " + mCapsLock);
            } else {
                // Single tap
                if (mCapsLock) {
                    // If caps lock is on, turn it off
                    mCapsLock = false;
                    mCurKeyboard.setShifted(false);
                    Log.d("softkeyboard", "Single tap - Caps lock turned off");
                } else {
                    // Normal single tap - toggle shift
                    boolean newShiftState = !mCurKeyboard.isShifted();
                    mCurKeyboard.setShifted(newShiftState);
                    Log.d("softkeyboard", "Single tap - Shift toggled to: " + newShiftState);
                }
                mLastShiftTime = now;
            }

            Log.d("softkeyboard", "Final shift state: " + mCurKeyboard.isShifted());
            Log.d("softkeyboard", "Final caps lock: " + mCapsLock);

            // Force visual update
            mInputView.invalidateAllKeys();
            mInputView.invalidate();
            updateShiftKeyState(getCurrentInputEditorInfo());

            Log.d("softkeyboard", "=== handleShiftKey completed ===");
        } else {
            Log.e("softkeyboard", "handleShiftKey failed - mInputView: " + mInputView + ", mCurKeyboard: " + mCurKeyboard + ", mQwertyKeyboard: " + mQwertyKeyboard);
        }
    }
    private void handleModeChange() {
        if (mInputView != null) {
            if (mCurKeyboard == mQwertyKeyboard) {
                mCurKeyboard = mSymbolsKeyboard;
                Log.d("softkeyboard", "Switched to symbols keyboard");
            } else {
                mCurKeyboard = mQwertyKeyboard;
                Log.d("softkeyboard", "Switched to qwerty keyboard");
            }
            mInputView.setKeyboard(mCurKeyboard);
        }
    }

    // 6. Update setActiveKeyboard to add more detailed logging
    private void setActiveKeyboard(LatinKeyboard nextKeyboard) {
        final boolean shouldSupportLanguageSwitchKey =
                mInputMethodManager.shouldOfferSwitchingToNextInputMethod(getToken());

        nextKeyboard.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey);

        if (mInputView != null) {
            mInputView.setKeyboard(nextKeyboard);
            mInputView.invalidateAllKeys();
            mInputView.requestLayout();
        }

        mCurKeyboard = nextKeyboard;
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        mComposing.setLength(0);
        setCandidatesViewShown(false);
        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                  int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    private boolean translateKeyDown(int keyCode, KeyEvent event) {
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }

        boolean dead = false;

        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }

        if (mComposing.length() > 0) {
            char accent = mComposing.charAt(mComposing.length() - 1);
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length() - 1);
            }
        }

        onKey(c, null);

        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;

            default:
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE
                            && (event.getMetaState() & KeyEvent.META_ALT_ON) != 0) {
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                            ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
                            keyDownUp(KeyEvent.KEYCODE_A);
                            keyDownUp(KeyEvent.KEYCODE_N);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            keyDownUp(KeyEvent.KEYCODE_R);
                            keyDownUp(KeyEvent.KEYCODE_O);
                            keyDownUp(KeyEvent.KEYCODE_I);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            // And we consume this event.
                            return true;
                        }
                    }
                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
                        return true;
                    }
                }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
                        keyCode, event);
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    public void insertEmojiText(String emojiUnicode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        // Begin batch edit for better performance
        ic.beginBatchEdit();

        // Commit any composing text first to preserve it
        if (mComposing.length() > 0) {
            ic.commitText(mComposing, 1);
            mComposing.setLength(0);  // Clear the composing buffer
        }

        // Insert the emoji
        ic.commitText(emojiUnicode, 1);

        // End batch edit
        ic.endBatchEdit();

        // Update shift key state
        updateShiftKeyState(getCurrentInputEditorInfo());

        // Reset emoji managers to default after emoji insertion
        if (normalEmojiManager != null) {
            normalEmojiManager.resetToDefault();
        }
    }

    public boolean isChatTextBox(){
        return isChatTextBox;
    }

    /**
     * Helper method to check if a character is punctuation
     */
    private boolean isPunctuation(char c) {
        return ".,!?;:()[]{}\"'".indexOf(c) != -1;
    }

    public void replaceCurrentWordWithEmoji(String emojiUnicode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        Log.d("EmojiDebug", "=== REPLACE CURRENT WORD WITH EMOJI ===");
        Log.d("EmojiDebug", "Emoji to insert: " + emojiUnicode);

        ic.beginBatchEdit();

        if (mComposing.length() > 0) {
            // Case 1: We have composing text - use the original method
            Log.d("EmojiDebug", "Case 1: Replacing composing text");
            int composingLength = mComposing.length();
            Log.d("EmojiDebug", "Composing text: '" + mComposing.toString() + "' (length: " + composingLength + ")");

            ic.finishComposingText(); // Commit the composing text
            ic.deleteSurroundingText(composingLength, 0); // Delete it
            mComposing.setLength(0); // Clear internal buffer

        } else {
            // Case 2: No composing text - need to find and replace the current word
            Log.d("EmojiDebug", "Case 2: No composing text, finding current word in committed text");

            CharSequence textBefore = ic.getTextBeforeCursor(50, 0);
            CharSequence textAfter = ic.getTextAfterCursor(10, 0);

            if (textBefore == null) textBefore = "";
            if (textAfter == null) textAfter = "";

            String beforeStr = textBefore.toString();
            String afterStr = textAfter.toString();

            Log.d("EmojiDebug", "Text before cursor: '" + beforeStr + "'");
            Log.d("EmojiDebug", "Text after cursor: '" + afterStr + "'");

            // Find the current word boundaries
            int charsToDeleteBefore = 0;
            int charsToDeleteAfter = 0;

            // Count characters to delete before cursor (back to start of word)
            for (int i = beforeStr.length() - 1; i >= 0; i--) {
                char c = beforeStr.charAt(i);
                if (Character.isWhitespace(c) || isPunctuation(c)) {
                    break;
                }
                charsToDeleteBefore++;
            }

            // Count characters to delete after cursor (forward to end of word)
            for (int i = 0; i < afterStr.length(); i++) {
                char c = afterStr.charAt(i);
                if (Character.isWhitespace(c) || isPunctuation(c)) {
                    break;
                }
                charsToDeleteAfter++;
            }

            Log.d("EmojiDebug", "Characters to delete - before: " + charsToDeleteBefore + ", after: " + charsToDeleteAfter);

            // Delete the current word
            if (charsToDeleteBefore > 0 || charsToDeleteAfter > 0) {
                ic.deleteSurroundingText(charsToDeleteBefore, charsToDeleteAfter);
                Log.d("EmojiDebug", "Deleted current word using deleteSurroundingText(" + charsToDeleteBefore + ", " + charsToDeleteAfter + ")");
            }
        }

        // Insert the emoji
        Log.d("EmojiDebug", "Inserting emoji: " + emojiUnicode);
        ic.commitText(emojiUnicode, 1);

        ic.endBatchEdit();

        // Debug after operation
        CharSequence textAfter = ic.getTextBeforeCursor(20, 0);
        Log.d("EmojiDebug", "Text after operation (20 chars): '" + textAfter + "'");
        Log.d("EmojiDebug", "=== END REPLACE OPERATION ===");

        // Update shift key state
        updateShiftKeyState(getCurrentInputEditorInfo());

        // Notify emoji managers to reset to default
        if (normalEmojiManager != null) {
            normalEmojiManager.resetToDefault();
        }
    }

    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            // DEBUG LOG - Add this
            Log.d("EmojiDebug", "commitTyped: committing '" + mComposing.toString() + "' (length: " + mComposing.length() + ")");

            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);

            Log.d("EmojiDebug", "commitTyped: mComposing cleared");

            // Reset emoji managers to default when text is committed
            if (normalEmojiManager != null) {
                normalEmojiManager.resetToDefault();
            }
        }
    }

    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleBackspace() {
        final int length = mComposing.length();

        Log.d("EmojiDebug", "handleBackspace: mComposing length before: " + length + ", text: '" + mComposing.toString() + "'");

        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            Log.d("EmojiDebug", "Backspace: removed 1 char from composing, new: '" + mComposing.toString() + "'");
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            Log.d("EmojiDebug", "Backspace: cleared composing text");
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
            Log.d("EmojiDebug", "Backspace: sent delete key event (no composing text)");
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    // Update handleClose to always return to normal mode
    private void handleClose() {
        commitTyped(getCurrentInputConnection());

        if (isFloatingMode) {
            switchToNormalMode();
        } else {
            requestHideSelf(0);
            if (mInputView != null) {
                mInputView.closing();
            }
        }
    }

    private IBinder getToken() {
        final Dialog dialog = getWindow();
        if (dialog == null) {
            return null;
        }
        final Window window = dialog.getWindow();
        if (window == null) {
            return null;
        }
        return window.getAttributes().token;
    }

    public void swipeLeft() {
        handleBackspace();
    }

    @Override
    public void swipeRight() {

    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
    }

    @Override
    public void onPress(int primaryCode) {
        Log.d("softkeyboard", "onPress: " + primaryCode + " (floating: " + isFloatingMode + ")");

    }

    @Override
    public void onRelease(int primaryCode) {
        Log.d("softkeyboard", "onRelease: " + primaryCode + " (floating: " + isFloatingMode + ")");
    }

    @Override
    public void onDestroy() {
        hideOverlay();
        super.onDestroy();
    }
}