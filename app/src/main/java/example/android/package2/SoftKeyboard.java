/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package example.android.package2;

import android.app.Dialog;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.IBinder;
import android.text.InputType;
import android.text.method.MetaKeyKeyListener;
import android.view.Display;
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

import androidx.annotation.NonNull;

import com.example.aosp_poc.R;

import java.util.ArrayList;
import java.util.List;

// Add these imports for overlay functionality
import android.provider.Settings;
import android.net.Uri;
import android.util.Log;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.content.Intent;
import android.widget.LinearLayout;

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
public class SoftKeyboard extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    static final boolean PROCESS_HARD_KEYS = true;

    // Original fields

    // Add these fields at the top of the class
    private boolean isFloatingMode = false;
    private View mNormalModeBar;

    private InputMethodManager mInputMethodManager;
    private LatinKeyboardView mInputView;
    private CandidateView mCandidateView;
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
    private WindowManager.LayoutParams overlayParams;
    private boolean isOverlayVisible = false;
    private LatinKeyboardView mOverlayKeyboardView; // For the overlay keyboard

    private LatinKeyboard mNormalQwertyKeyboard;
    private LatinKeyboard mNormalSymbolsKeyboard;
    private LatinKeyboard mNormalSymbolsShiftedKeyboard;
    private LatinKeyboard mFloatingQwertyKeyboard;
    private LatinKeyboard mFloatingSymbolsKeyboard;
    private LatinKeyboard mFloatingSymbolsShiftedKeyboard;


    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mInputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        mWordSeparators = getResources().getString(R.string.word_separators);
    }

    /**
     * Returns the context object whose resources are adjusted to match the metrics of the display.
     *
     * Note that before {@link Build.VERSION_CODES#KITKAT}, there is no way to support
     * multi-display scenarios, so the context object will just return the IME context itself.
     *
     * With initiating multi-display APIs from {@link Build.VERSION_CODES#KITKAT}, the
     * context object has to return with re-creating the display context according the metrics
     * of the display in runtime.
     *
     * Starts from {@link Build.VERSION_CODES#S_V2}, the returning context object has
     * became to IME context self since it ends up capable of updating its resources internally.
     *
     * @see {@link Context#createDisplayContext(Display)}
     */
    @NonNull Context getDisplayContext() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // createDisplayContext is not available.
            return this;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            // IME context sources is now managed by WindowProviderService from Android 12L.
            return this;
        }
        // An issue in Q that non-activity components Resources / DisplayMetrics in
        // Context doesn't well updated when the IME window moving to external display.
        // Currently we do a workaround is to create new display context directly and re-init
        // keyboard layout with this context.
        final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        return createDisplayContext(wm.getDefaultDisplay());
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override
    public void onInitializeInterface() {
        final Context displayContext = getDisplayContext();

        int displayWidth = getMaxWidth();
        if (mQwertyKeyboard != null && displayWidth == mLastDisplayWidth) {
            return;
        }
        mLastDisplayWidth = displayWidth;

        // Create separate keyboard instances for normal and floating modes
        mNormalQwertyKeyboard = new LatinKeyboard(displayContext, R.xml.qwerty);
        mNormalSymbolsKeyboard = new LatinKeyboard(displayContext, R.xml.symbols);
        mNormalSymbolsShiftedKeyboard = new LatinKeyboard(displayContext, R.xml.symbols_shift);

        mFloatingQwertyKeyboard = new LatinKeyboard(displayContext, R.xml.qwerty);
        mFloatingSymbolsKeyboard = new LatinKeyboard(displayContext, R.xml.symbols);
        mFloatingSymbolsShiftedKeyboard = new LatinKeyboard(displayContext, R.xml.symbols_shift);

        // Set initial keyboard to normal mode keyboards
        mQwertyKeyboard = mNormalQwertyKeyboard;
        mSymbolsKeyboard = mNormalSymbolsKeyboard;
        mSymbolsShiftedKeyboard = mNormalSymbolsShiftedKeyboard;
        mCurKeyboard = mQwertyKeyboard;
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override
    public View onCreateInputView() {
        // Always create fresh view to ensure proper sizing
        if (isFloatingMode) {
            // In floating mode, return minimal view
            View minimalView = new View(this);
            minimalView.setLayoutParams(new ViewGroup.LayoutParams(1, 1));
            return minimalView;
        } else {
            // Normal mode - create full keyboard
            return createNormalKeyboard();
        }
    }

    private View createNormalKeyboard() {
        Log.d(TAG, "=== createNormalKeyboard START ===");

        View normalLayout = getLayoutInflater().inflate(R.layout.normal_keyboard_layout, null);

        // Get screen width for debugging
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getSize(size);
        Log.d(TAG, "Screen width: " + size.x);

        // Force the layout to use full width
        normalLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        mNormalModeBar = normalLayout.findViewById(R.id.normal_mode_bar);
        mInputView = normalLayout.findViewById(R.id.keyboard);

        // Log the current keyboard view dimensions
        mInputView.post(() -> {
            Log.d(TAG, "Keyboard view width after creation: " + mInputView.getWidth());
            Log.d(TAG, "Keyboard view measured width: " + mInputView.getMeasuredWidth());
            Log.d(TAG, "Parent layout width: " + normalLayout.getWidth());
        });

        mInputView.setOnKeyboardActionListener(this);

        // Ensure keyboard is set to full width
        mInputView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Log keyboard object info
        if (mCurKeyboard != null) {
            Log.d(TAG, "Current keyboard: " + mCurKeyboard.toString());
            Log.d(TAG, "Current keyboard width: " + mCurKeyboard.getMinWidth());
        }

        View floatToggleButton = normalLayout.findViewById(R.id.float_toggle_button);
        floatToggleButton.setOnClickListener(v -> {
            Log.d(TAG, "Switching to floating mode");
            switchToFloatingMode();
        });

        Log.d(TAG, "=== createNormalKeyboard END ===");
        return normalLayout;
    }

    private void hideKeyboard() {
        if (mInputView != null) {
            mInputView.setVisibility(View.GONE);
        }
        setCandidatesViewShown(false);
    }

    private void switchToFloatingMode() {
        isFloatingMode = true;

        // Hide the normal keyboard view
        if (mInputView != null) {
            mInputView.setVisibility(View.GONE);
        }

        // Request to hide the IME window
        requestHideSelf(0);

        // Show floating keyboard
        if (canDrawOverlays()) {
            // Small delay to ensure IME is hidden before showing overlay
            mInputView.postDelayed(() -> showFloatingKeyboard(), 100);
        } else {
            requestOverlayPermission();
            isFloatingMode = false; // Reset if no permission
        }
    }

    private void switchToNormalMode() {
        Log.d(TAG, "=== switchToNormalMode START ===");
        isFloatingMode = false;
        hideFloatingKeyboard();

        // Log current keyboard state
        Log.d(TAG, "Before switch - mCurKeyboard: " + (mCurKeyboard != null ? mCurKeyboard.toString() : "null"));
        Log.d(TAG, "Before switch - keyboard min width: " + (mCurKeyboard != null ? mCurKeyboard.getMinWidth() : "null"));

        // Switch to the correct normal keyboard based on current type
        if (mCurKeyboard == mFloatingQwertyKeyboard || mCurKeyboard == mQwertyKeyboard) {
            mCurKeyboard = mNormalQwertyKeyboard;
            mQwertyKeyboard = mNormalQwertyKeyboard;
        } else if (mCurKeyboard == mFloatingSymbolsKeyboard || mCurKeyboard == mSymbolsKeyboard) {
            mCurKeyboard = mNormalSymbolsKeyboard;
            mSymbolsKeyboard = mNormalSymbolsKeyboard;
        } else if (mCurKeyboard == mFloatingSymbolsShiftedKeyboard || mCurKeyboard == mSymbolsShiftedKeyboard) {
            mCurKeyboard = mNormalSymbolsShiftedKeyboard;
            mSymbolsShiftedKeyboard = mNormalSymbolsShiftedKeyboard;
        }

        Log.d(TAG, "After switch - mCurKeyboard: " + mCurKeyboard.toString());
        Log.d(TAG, "After switch - keyboard min width: " + mCurKeyboard.getMinWidth());

        // Force recreation of the input view
        View newInputView = onCreateInputView();
        setInputView(newInputView);

        // Log the new view dimensions
        newInputView.post(() -> {
            Log.d(TAG, "New input view width: " + newInputView.getWidth());
            Log.d(TAG, "New input view measured width: " + newInputView.getMeasuredWidth());
            if (mInputView != null) {
                Log.d(TAG, "mInputView width after setInputView: " + mInputView.getWidth());
            }
        });

        // Trigger the keyboard to show
        EditorInfo currentEditorInfo = getCurrentInputEditorInfo();
        if (currentEditorInfo != null) {
            onStartInput(currentEditorInfo, true);
            onStartInputView(currentEditorInfo, true);
        }

        Log.d(TAG, "=== switchToNormalMode END ===");
    }
    private void showFloatingKeyboard() {
        if (overlayView == null) {
            createFloatingKeyboard();
        }

        // Switch to floating keyboard instances
        if (mCurKeyboard == mNormalQwertyKeyboard || mCurKeyboard == mQwertyKeyboard) {
            mCurKeyboard = mFloatingQwertyKeyboard;
            mQwertyKeyboard = mFloatingQwertyKeyboard;
        } else if (mCurKeyboard == mNormalSymbolsKeyboard || mCurKeyboard == mSymbolsKeyboard) {
            mCurKeyboard = mFloatingSymbolsKeyboard;
            mSymbolsKeyboard = mFloatingSymbolsKeyboard;
        } else if (mCurKeyboard == mNormalSymbolsShiftedKeyboard || mCurKeyboard == mSymbolsShiftedKeyboard) {
            mCurKeyboard = mFloatingSymbolsShiftedKeyboard;
            mSymbolsShiftedKeyboard = mFloatingSymbolsShiftedKeyboard;
        }

        // Update the overlay keyboard view with the floating keyboard
        if (mOverlayKeyboardView != null) {
            mOverlayKeyboardView.setKeyboard(mCurKeyboard);
        }

        if (overlayView != null && overlayWindowManager != null && !isOverlayVisible) {
            // Position at screen center
            Display display = overlayWindowManager.getDefaultDisplay();
            android.graphics.Point size = new android.graphics.Point();
            display.getSize(size);

            // Set to 80% of screen width for floating mode
            int keyboardWidth = (int)(size.x * 0.8);

            // Update params with specific width
            overlayParams.width = keyboardWidth;
            overlayParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

            // Center position
            overlayParams.x = (size.x - keyboardWidth) / 2;
            overlayParams.y = size.y / 2 - 200;

            try {
                overlayWindowManager.addView(overlayView, overlayParams);
                isOverlayVisible = true;
                Log.d(TAG, "Floating keyboard shown at center with width: " + keyboardWidth);
            } catch (Exception e) {
                Log.e(TAG, "Failed to show floating keyboard", e);
            }
        }
    }
    private void hideFloatingKeyboard() {
        if (overlayView != null && overlayWindowManager != null && isOverlayVisible) {
            try {
                overlayWindowManager.removeView(overlayView);
                isOverlayVisible = false;
            } catch (Exception e) {
                Log.e(TAG, "Failed to hide floating keyboard", e);
            }
        }
    }


    private boolean createFloatingKeyboard() {
        try {
            Log.d(TAG, "Creating overlay window manager");
            overlayWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

            if (overlayWindowManager == null) {
                Log.e(TAG, "Failed to get WindowManager");
                return false;
            }

            Log.d(TAG, "Creating overlay view");
            createOverlayView();

            Log.d(TAG, "Setting up overlay layout params");
            setupOverlayParams();

            Log.d(TAG, "Setting up overlay drag functionality");
            setupOverlayDrag();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error creating floating keyboard", e);
            return false;
        }
    }

    private void createOverlayView() {
        try {
            overlayView = getLayoutInflater().inflate(R.layout.floating_keyboard_overlay, null);
            Log.d(TAG, "Successfully inflated floating_keyboard_overlay layout");

            // Set up the keyboard
            mOverlayKeyboardView = overlayView.findViewById(R.id.keyboard);
            if (mOverlayKeyboardView != null) {
                mOverlayKeyboardView.setOnKeyboardActionListener(this);
                if (mCurKeyboard != null) {
                    mOverlayKeyboardView.setKeyboard(mCurKeyboard);
                } else if (mQwertyKeyboard != null) {
                    mOverlayKeyboardView.setKeyboard(mQwertyKeyboard);
                }
            }

            // Add normal mode toggle button functionality
            View normalToggleButton = overlayView.findViewById(R.id.normal_toggle_button);
            if (normalToggleButton != null) {
                normalToggleButton.setOnClickListener(v -> {
                    Log.d(TAG, "Switching to normal mode");
                    switchToNormalMode();
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error creating overlay view", e);
        }
    }

    private void setupOverlayParams() {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        overlayParams.x = 100;
        overlayParams.y = 200;
    }

    private void setupOverlayDrag() {
        // Make the entire view draggable for now
        View dragTarget = overlayView;

        // If we have the proper layout, try to find the drag handle
        try {
            View dragHandle = overlayView.findViewById(R.id.drag_handle);
            if (dragHandle != null) {
                dragTarget = dragHandle;
                Log.d(TAG, "Found drag handle, using it as drag target");
            } else {
                Log.d(TAG, "No drag handle found, making entire view draggable");
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception finding drag handle, using entire view as drag target");
        }

        dragTarget.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.d(TAG, "Overlay touch: " + event.getAction() + " at screen (" +
                        event.getRawX() + ", " + event.getRawY() + ")");

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = overlayParams.x;
                        initialY = overlayParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        Log.d(TAG, "Overlay drag start: window(" + initialX + "," + initialY + ") touch(" + initialTouchX + "," + initialTouchY + ")");
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int newX = initialX + (int) (event.getRawX() - initialTouchX);
                        int newY = initialY + (int) (event.getRawY() - initialTouchY);

                        Log.d(TAG, "Overlay drag move: new position (" + newX + "," + newY + ")");

                        // Get screen dimensions for bounds checking
                        Display display = overlayWindowManager.getDefaultDisplay();
                        android.graphics.Point size = new android.graphics.Point();
                        display.getSize(size);

                        // Optional bounds checking - comment out to allow dragging off screen
                        newX = Math.max(-overlayView.getWidth()/2, Math.min(newX, size.x - overlayView.getWidth()/2));
                        newY = Math.max(-overlayView.getHeight()/2, Math.min(newY, size.y - overlayView.getHeight()/2));

                        overlayParams.x = newX;
                        overlayParams.y = newY;

                        try {
                            overlayWindowManager.updateViewLayout(overlayView, overlayParams);
                            Log.d(TAG, "Successfully updated overlay position to (" + newX + "," + newY + ")");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to update overlay position", e);
                        }

                        return true;

                    case MotionEvent.ACTION_UP:
                        Log.d(TAG, "Overlay drag end at (" + overlayParams.x + "," + overlayParams.y + ")");
                        return true;
                }
                return false;
            }
        });
    }

    // Update the showOverlay method to properly initialize the keyboard:
    private void showOverlay() {
        if (overlayView == null || overlayWindowManager == null) {
            Log.e(TAG, "Cannot show overlay - view or window manager is null");
            return;
        }

        if (isOverlayVisible) {
            Log.d(TAG, "Overlay already visible");
            return;
        }

        // Make sure the overlay keyboard has the current keyboard set
        if (mOverlayKeyboardView != null && mCurKeyboard != null) {
            mOverlayKeyboardView.setKeyboard(mCurKeyboard);
            Log.d(TAG, "Updated overlay keyboard with current keyboard");
        }

        try {
            Log.d(TAG, "Adding overlay view to window manager");
            overlayWindowManager.addView(overlayView, overlayParams);
            isOverlayVisible = true;
            Log.d(TAG, "Overlay shown successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to show overlay", e);
        }
    }

    private void hideOverlay() {
        if (overlayView != null && overlayWindowManager != null && isOverlayVisible) {
            try {
                overlayWindowManager.removeView(overlayView);
                isOverlayVisible = false;
                Log.d(TAG, "Overlay hidden successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to hide overlay", e);
            }
        }
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean canDraw = Settings.canDrawOverlays(this);
            Log.d(TAG, "API >= 23, canDrawOverlays: " + canDraw);
            return canDraw;
        } else {
            Log.d(TAG, "API < 23, assuming overlay permission granted");
            return true;
        }
    }

    private void requestOverlayPermission() {
        Log.d(TAG, "Requesting overlay permission");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error requesting overlay permission", e);
            }
        }
    }

    private void setLatinKeyboard(LatinKeyboard nextKeyboard) {
        Log.d(TAG, "=== setLatinKeyboard START ===");
        Log.d(TAG, "Setting keyboard: " + nextKeyboard.toString());
        Log.d(TAG, "Keyboard min width: " + nextKeyboard.getMinWidth());

        setActiveKeyboard(nextKeyboard);

        Log.d(TAG, "=== setLatinKeyboard END ===");
    }
    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        Log.d(TAG, "=== onComputeInsets ===");
        Log.d(TAG, "contentTopInsets: " + outInsets.contentTopInsets);
        Log.d(TAG, "visibleTopInsets: " + outInsets.visibleTopInsets);
        if (mInputView != null) {
            Log.d(TAG, "mInputView height: " + mInputView.getHeight());
            Log.d(TAG, "mInputView width in onComputeInsets: " + mInputView.getWidth());
        }
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override public View onCreateCandidatesView() {
        mCandidateView = new CandidateView(getDisplayContext());
        mCandidateView.setService(this);
        return mCandidateView;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        // Reset composing state
        mComposing.setLength(0);
        updateCandidates();

        if (!restarting) {
            mMetaState = 0;
        }

        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;

        // Determine keyboard type based on input type
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                mCurKeyboard = isFloatingMode ? mFloatingSymbolsKeyboard : mNormalSymbolsKeyboard;
                break;
            case InputType.TYPE_CLASS_PHONE:
                mCurKeyboard = isFloatingMode ? mFloatingSymbolsKeyboard : mNormalSymbolsKeyboard;
                break;
            case InputType.TYPE_CLASS_TEXT:
                mCurKeyboard = isFloatingMode ? mFloatingQwertyKeyboard : mNormalQwertyKeyboard;
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
                mCurKeyboard = isFloatingMode ? mFloatingQwertyKeyboard : mNormalQwertyKeyboard;
                updateShiftKeyState(attribute);
        }

        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
        super.onFinishInput();

        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();

        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);

        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }

        // Hide overlay when done
        if (isFloatingMode) {
            hideOverlay();
        }
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);

        Log.d(TAG, "=== onStartInputView START ===");
        Log.d(TAG, "isFloatingMode: " + isFloatingMode);
        Log.d(TAG, "restarting: " + restarting);

        if (isFloatingMode) {
            if (mInputView != null) {
                mInputView.setVisibility(View.GONE);
            }
            Log.d(TAG, "=== onStartInputView END (floating mode) ===");
            return;
        }

        // Normal mode - show keyboard
        if (mInputView != null) {
            mInputView.setVisibility(View.VISIBLE);
            Log.d(TAG, "mInputView visibility set to VISIBLE");
            Log.d(TAG, "mInputView width: " + mInputView.getWidth());
            Log.d(TAG, "mInputView parent: " + mInputView.getParent());
        }

        // Apply the selected keyboard to the input view
        setLatinKeyboard(mCurKeyboard);

        if (mInputView != null) {
            mInputView.closing();
            final InputMethodSubtype subtype = mInputMethodManager.getCurrentInputMethodSubtype();
            mInputView.setSubtypeOnSpaceKey(subtype);

            // Force layout update
            mInputView.requestLayout();

            // Log final dimensions
            mInputView.post(() -> {
                Log.d(TAG, "Final mInputView width in onStartInputView: " + mInputView.getWidth());
                Keyboard kb = mInputView.getKeyboard();
                if (kb != null) {
                    Log.d(TAG, "Keyboard min width: " + kb.getMinWidth());
                }
            });
        }

        Log.d(TAG, "=== onStartInputView END ===");
    }

    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        if (mInputView != null) {
            mInputView.setSubtypeOnSpaceKey(subtype);
        }
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                            int newSelStart, int newSelEnd,
                                            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }

            List<String> stringList = new ArrayList<String>();
            for (int i = 0; i < completions.length; i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
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
            char accent = mComposing.charAt(mComposing.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length()-1);
            }
        }

        onKey(c, null);

        return true;
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
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
                            && (event.getMetaState()&KeyEvent.META_ALT_ON) != 0) {
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

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
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

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            updateCandidates();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    // Replace the updateShiftKeyState method with this null-safe version:
    private void updateShiftKeyState(EditorInfo attr) {
        LatinKeyboardView activeKeyboardView = isFloatingMode ? mOverlayKeyboardView : mInputView;

        if (attr != null && activeKeyboardView != null && mQwertyKeyboard == activeKeyboardView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            activeKeyboardView.setShifted(mCapsLock || caps != 0);
        }
    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
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

    // Replace the onKey method with this updated version to handle mode changes:
    public void onKey(int primaryCode, int[] keyCodes) {
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
            handleLanguageSwitch();
            return;
        } else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            // Handle mode change (alphabetic <-> symbols)
            LatinKeyboardView activeKeyboardView = isFloatingMode ? mOverlayKeyboardView : mInputView;
            if (activeKeyboardView != null) {
                Keyboard current = activeKeyboardView.getKeyboard();
                if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                    setActiveKeyboard(mQwertyKeyboard);
                } else {
                    setActiveKeyboard(mSymbolsKeyboard);
                    mSymbolsKeyboard.setShifted(false);
                }
            }
        } else {
            handleCharacter(primaryCode, keyCodes);
        }
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

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() {
        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
                ArrayList<String> list = new ArrayList<String>();
                list.add(mComposing.toString());
                setSuggestions(list, true, true);
            } else {
                setSuggestions(null, false, false);
            }
        }
    }

    public void setSuggestions(List<String> suggestions, boolean completions,
                               boolean typedWordValid) {
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }

    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    // Replace the handleShift method with this null-safe version:
    private void handleShift() {
        LatinKeyboardView activeKeyboardView = isFloatingMode ? mOverlayKeyboardView : mInputView;

        if (activeKeyboardView == null) {
            Log.w(TAG, "No active keyboard view available for shift handling");
            return;
        }

        Keyboard currentKeyboard = activeKeyboardView.getKeyboard();

        // Check against the appropriate keyboard set based on mode
        LatinKeyboard qwertyToCheck = isFloatingMode ? mFloatingQwertyKeyboard : mNormalQwertyKeyboard;
        LatinKeyboard symbolsToCheck = isFloatingMode ? mFloatingSymbolsKeyboard : mNormalSymbolsKeyboard;
        LatinKeyboard symbolsShiftedToCheck = isFloatingMode ? mFloatingSymbolsShiftedKeyboard : mNormalSymbolsShiftedKeyboard;

        if (currentKeyboard == qwertyToCheck) {
            // Alphabet keyboard
            checkToggleCapsLock();
            activeKeyboardView.setShifted(mCapsLock || !activeKeyboardView.isShifted());
        } else if (currentKeyboard == symbolsToCheck) {
            setActiveKeyboard(symbolsShiftedToCheck);
        } else if (currentKeyboard == symbolsShiftedToCheck) {
            setActiveKeyboard(symbolsToCheck);
        }
    }
    private void setActiveKeyboard(LatinKeyboard nextKeyboard) {
        final boolean shouldSupportLanguageSwitchKey =
                mInputMethodManager.shouldOfferSwitchingToNextInputMethod(getToken());

        // Determine which keyboard set to use based on mode
        if (isFloatingMode) {
            // Map to floating keyboards
            if (nextKeyboard == mNormalQwertyKeyboard || nextKeyboard == mQwertyKeyboard) {
                nextKeyboard = mFloatingQwertyKeyboard;
                mQwertyKeyboard = mFloatingQwertyKeyboard;
            } else if (nextKeyboard == mNormalSymbolsKeyboard || nextKeyboard == mSymbolsKeyboard) {
                nextKeyboard = mFloatingSymbolsKeyboard;
                mSymbolsKeyboard = mFloatingSymbolsKeyboard;
            } else if (nextKeyboard == mNormalSymbolsShiftedKeyboard || nextKeyboard == mSymbolsShiftedKeyboard) {
                nextKeyboard = mFloatingSymbolsShiftedKeyboard;
                mSymbolsShiftedKeyboard = mFloatingSymbolsShiftedKeyboard;
            }
        } else {
            // Map to normal keyboards
            if (nextKeyboard == mFloatingQwertyKeyboard || nextKeyboard == mQwertyKeyboard) {
                nextKeyboard = mNormalQwertyKeyboard;
                mQwertyKeyboard = mNormalQwertyKeyboard;
            } else if (nextKeyboard == mFloatingSymbolsKeyboard || nextKeyboard == mSymbolsKeyboard) {
                nextKeyboard = mNormalSymbolsKeyboard;
                mSymbolsKeyboard = mNormalSymbolsKeyboard;
            } else if (nextKeyboard == mFloatingSymbolsShiftedKeyboard || nextKeyboard == mSymbolsShiftedKeyboard) {
                nextKeyboard = mNormalSymbolsShiftedKeyboard;
                mSymbolsShiftedKeyboard = mNormalSymbolsShiftedKeyboard;
            }
        }

        nextKeyboard.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey);

        if (isFloatingMode && mOverlayKeyboardView != null) {
            mOverlayKeyboardView.setKeyboard(nextKeyboard);
        } else if (mInputView != null) {
            mInputView.setKeyboard(nextKeyboard);
        }

        mCurKeyboard = nextKeyboard;
    }

    // Replace the handleCharacter method with this null-safe version:
    private void handleCharacter(int primaryCode, int[] keyCodes) {
        // Determine which keyboard view to check for shift state
        LatinKeyboardView activeKeyboardView = isFloatingMode ? mOverlayKeyboardView : mInputView;

        if (isInputViewShown() || isFloatingMode) {
            if (activeKeyboardView != null && activeKeyboardView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }

        if (isAlphabet(primaryCode) && mPredictionOn) {
            mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        } else {
            getCurrentInputConnection().commitText(
                    String.valueOf((char) primaryCode), 1);
        }
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

    private void handleLanguageSwitch() {
        mInputMethodManager.switchToNextInputMethod(getToken(), false /* onlyCurrentIme */);
    }

    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }

    private String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    public void pickDefaultCandidate() {
        pickSuggestionManually(0);
    }

    public void pickSuggestionManually(int index) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0) {
            // If we were generating candidate suggestions for the current
            // text, we would commit one of them here.  But for this sample,
            // we will just commit the current text.
            commitTyped(getCurrentInputConnection());
        }
    }

    public void swipeRight() {
        if (mCompletionOn) {
            pickDefaultCandidate();
        }
    }

    public void swipeLeft() {
        handleBackspace();
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
    }

    public void onPress(int primaryCode) {
    }

    public void onRelease(int primaryCode) {
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "=== SoftKeyboard onDestroy ===");
        hideOverlay();
        super.onDestroy();
    }
}