package example.android.package2.keyboard;

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

// Add these imports for overlay functionality
import android.provider.Settings;
import android.net.Uri;
import android.util.Log;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.content.Intent;
import android.widget.LinearLayout;
import example.android.package2.emoji.manager.EmojiManager;
import example.android.package2.emoji.extensions.SoftKeyboardEmojiExtensionKt;

public class SoftKeyboard extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    static final boolean PROCESS_HARD_KEYS = true;

    // Add these fields to your SoftKeyboard class
    private EmojiManager normalEmojiManager;
    private EmojiManager floatingEmojiManager;

    // Original fields

    // Add these fields at the top of the class
    private boolean isFloatingMode = false;
    private View mNormalModeBar;

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
    private WindowManager.LayoutParams overlayParams;
    private boolean isOverlayVisible = false;
    private LatinKeyboardView mOverlayKeyboardView; // For the overlay keyboard

    private LatinKeyboard mNormalQwertyKeyboard;
    private LatinKeyboard mNormalSymbolsKeyboard;
    private LatinKeyboard mNormalSymbolsShiftedKeyboard;
    private LatinKeyboard mFloatingQwertyKeyboard;
    private LatinKeyboard mFloatingSymbolsKeyboard;
    private LatinKeyboard mFloatingSymbolsShiftedKeyboard;

    @Override public void onCreate() {
        super.onCreate();
        mInputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        mWordSeparators = getResources().getString(R.string.word_separators);
    }

    @NonNull Context getDisplayContext() {
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

        if (mQwertyKeyboard != null && displayWidth == mLastDisplayWidth && !isFloatingMode) {
            return;
        }
        mLastDisplayWidth = displayWidth;

        // Create normal mode keyboards with full width
        mNormalQwertyKeyboard = new LatinKeyboard(displayContext, R.xml.qwerty);
        mNormalSymbolsKeyboard = new LatinKeyboard(displayContext, R.xml.symbols);
        mNormalSymbolsShiftedKeyboard = new LatinKeyboard(displayContext, R.xml.symbols_shift);

        // Create floating mode keyboards with reduced width (80% of screen)
        int floatingWidth = (int)(displayWidth * 0.8f);

        // Use ResizableLatinKeyboard for floating keyboards
        mFloatingQwertyKeyboard = new ResizableLatinKeyboard(getApplicationContext(), R.xml.qwerty, floatingWidth);
        mFloatingSymbolsKeyboard = new ResizableLatinKeyboard(getApplicationContext(), R.xml.symbols, floatingWidth);
        mFloatingSymbolsShiftedKeyboard = new ResizableLatinKeyboard(getApplicationContext(), R.xml.symbols_shift, floatingWidth);

        // Set initial keyboard based on current mode
        if (isFloatingMode) {
            mQwertyKeyboard = mFloatingQwertyKeyboard;
            mSymbolsKeyboard = mFloatingSymbolsKeyboard;
            mSymbolsShiftedKeyboard = mFloatingSymbolsShiftedKeyboard;
            mCurKeyboard = mFloatingQwertyKeyboard;
        } else {
            mQwertyKeyboard = mNormalQwertyKeyboard;
            mSymbolsKeyboard = mNormalSymbolsKeyboard;
            mSymbolsShiftedKeyboard = mNormalSymbolsShiftedKeyboard;
            mCurKeyboard = mNormalQwertyKeyboard;
        }
    }

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

    // Modify your createNormalKeyboard() method
    private View createNormalKeyboard() {
        // Use the new layout with emoji support
        View normalLayout = getLayoutInflater().inflate(R.layout.normal_keyboard_layout_with_emoji, null);

        // Force the layout to use full width
        normalLayout.setLayoutParams(new ViewGroup.LayoutParams(
                600,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        mNormalModeBar = normalLayout.findViewById(R.id.normal_mode_bar);
        mInputView = normalLayout.findViewById(R.id.keyboard);

        mInputView.setOnKeyboardActionListener(this);

        // Ensure keyboard is set to full width
        mInputView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Setup emoji functionality using Kotlin extension
        normalEmojiManager = SoftKeyboardEmojiExtensionKt.setupEmojiSupport(this, normalLayout);

        View floatToggleButton = normalLayout.findViewById(R.id.float_toggle_button);
        floatToggleButton.setOnClickListener(v -> {
            switchToFloatingMode();
        });

        return normalLayout;
    }

    // Update switchToFloatingMode and switchToNormalMode to recreate keyboards:
    private void switchToFloatingMode() {
        isFloatingMode = true;

        // Force recreation of keyboards with proper sizing
        onInitializeInterface();

        if (mInputView != null) {
            mInputView.setVisibility(View.GONE);
        }

        requestHideSelf(0);

        if (canDrawOverlays()) {
            if (mInputView != null) {
                mInputView.postDelayed(() -> showFloatingKeyboard(), 100);
            } else {
                showFloatingKeyboard();
            }
        } else {
            requestOverlayPermission();
            isFloatingMode = false;
        }
    }

    private void switchToNormalMode() {
        isFloatingMode = false;
        hideFloatingKeyboard();

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
    // Update showFloatingKeyboard to remove the fresh keyboard creation:
    private void showFloatingKeyboard() {

        if (overlayView == null) {
            createFloatingKeyboard();
        }

        // The keyboards should already be properly sized from onInitializeInterface
        // Just ensure the current keyboard is set properly
        if (mOverlayKeyboardView != null && mCurKeyboard != null) {
            mOverlayKeyboardView.setKeyboard(mCurKeyboard);
            mOverlayKeyboardView.invalidateAllKeys();
            mOverlayKeyboardView.requestLayout();
        }

        if (overlayView != null && overlayWindowManager != null && !isOverlayVisible) {
            Display display = overlayWindowManager.getDefaultDisplay();
            android.graphics.Point size = new android.graphics.Point();
            display.getSize(size);

            // Set overlay width to match the floating keyboard width
            int keyboardWidth = mCurKeyboard != null ? mCurKeyboard.getMinWidth() : (int)(size.x * 0.8);

            overlayParams.width = keyboardWidth;
            overlayParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

            // Center position
            overlayParams.x = (size.x - keyboardWidth) / 2;
            overlayParams.y = size.y / 2 - 200;

            try {
                overlayWindowManager.addView(overlayView, overlayParams);
                isOverlayVisible = true;

                overlayView.post(() -> {
                    if (mOverlayKeyboardView != null) {
                        mOverlayKeyboardView.invalidateAllKeys();
                        mOverlayKeyboardView.requestLayout();
                    }
                    overlayView.requestLayout();
                });

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
            overlayWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

            if (overlayWindowManager == null) {
                return false;
            }
            createOverlayView();
            setupOverlayParams();
            setupOverlayDrag();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Modify your createOverlayView() method
    private void createOverlayView() {
        try {
            // Use the new floating layout with emoji support
            overlayView = getLayoutInflater().inflate(R.layout.floating_keyboard_overlay_with_emoji, null);

            mOverlayKeyboardView = overlayView.findViewById(R.id.keyboard);
            if (mOverlayKeyboardView != null) {
                mOverlayKeyboardView.setOnKeyboardActionListener(this);

                // Set layout params to match parent width (will be constrained by overlay width)
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                mOverlayKeyboardView.setLayoutParams(params);

                if (mCurKeyboard != null) {
                    mOverlayKeyboardView.setKeyboard(mCurKeyboard);
                    mOverlayKeyboardView.invalidateAllKeys();
                    mOverlayKeyboardView.requestLayout();
                }
            } else {
                Log.e(TAG, "Failed to find overlay keyboard view!");
            }

            // Setup emoji functionality for floating mode
            floatingEmojiManager = SoftKeyboardEmojiExtensionKt.setupEmojiSupport(this, overlayView);

            // Add normal mode toggle button functionality
            View normalToggleButton = overlayView.findViewById(R.id.normal_toggle_button);
            if (normalToggleButton != null) {
                normalToggleButton.setOnClickListener(v -> {
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
                400,
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
        View dragTarget = overlayView;

        // Try to find drag handle
        try {
            View dragHandle = overlayView.findViewById(R.id.drag_handle);
            if (dragHandle != null) {
                dragTarget = dragHandle;
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception finding drag handle: " + e.getMessage());
        }

        dragTarget.setOnTouchListener(new View.OnTouchListener() {
            private float lastRawX;
            private float lastRawY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastRawX = event.getRawX();
                        lastRawY = event.getRawY();
                        isDragging = true;

                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (!isDragging) {
                            return false;
                        }

                        float currentRawX = event.getRawX();
                        float currentRawY = event.getRawY();

                        // Calculate movement delta
                        float deltaX = currentRawX - lastRawX;
                        float deltaY = currentRawY - lastRawY;

                        // Calculate new overlay position
                        int currentOverlayX = overlayParams.x;
                        int currentOverlayY = overlayParams.y;

                        int newOverlayX = currentOverlayX + (int) deltaX;
                        int newOverlayY = currentOverlayY + (int) deltaY;

                        // Get screen bounds
                        Display display = overlayWindowManager.getDefaultDisplay();
                        android.graphics.Point screenSize = new android.graphics.Point();
                        display.getSize(screenSize);

                        // Calculate bounds (allowing half the overlay to go off-screen)
                        int overlayWidth = overlayView.getWidth();
                        int overlayHeight = overlayView.getHeight();

                        int minX = -overlayWidth / 2;
                        int maxX = screenSize.x - overlayWidth / 2;
                        int minY = -overlayHeight / 2;
                        int maxY = screenSize.y - overlayHeight / 2;

                        // Apply bounds
                        int boundedX = Math.max(minX, Math.min(newOverlayX, maxX));
                        int boundedY = Math.max(minY, Math.min(newOverlayY, maxY));

                        boolean xWasBounded = (boundedX != newOverlayX);
                        boolean yWasBounded = (boundedY != newOverlayY);

                        // Update overlay position
                        overlayParams.x = boundedX;
                        overlayParams.y = boundedY;

                        // CRITICAL: Always update lastRawX/Y regardless of bounding
                        lastRawX = currentRawX;
                        lastRawY = currentRawY;

                        // Apply the change
                        try {
                            overlayWindowManager.updateViewLayout(overlayView, overlayParams);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        return true;

                    default:
                        return false;
                }
            }

            private String getActionString(int action) {
                switch (action) {
                    case MotionEvent.ACTION_DOWN: return "DOWN";
                    case MotionEvent.ACTION_MOVE: return "MOVE";
                    case MotionEvent.ACTION_UP: return "UP";
                    case MotionEvent.ACTION_CANCEL: return "CANCEL";
                    default: return "UNKNOWN(" + action + ")";
                }
            }
        });
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

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean canDraw = Settings.canDrawOverlays(this);
            return canDraw;
        } else {
            return true;
        }
    }

    private void requestOverlayPermission() {
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
        setActiveKeyboard(nextKeyboard);
    }
    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        // Reset composing state
        mComposing.setLength(0);

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

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        if (isFloatingMode) {
            if (mInputView != null) {
                mInputView.setVisibility(View.GONE);
            }
            return;
        }

        // Normal mode - show keyboard
        if (mInputView != null) {
            mInputView.setVisibility(View.VISIBLE);
        }

        // Apply the selected keyboard to the input view
        setLatinKeyboard(mCurKeyboard);

        if (mInputView != null) {
            mInputView.closing();
            final InputMethodSubtype subtype = mInputMethodManager.getCurrentInputMethodSubtype();
            mInputView.setSubtypeOnSpaceKey(subtype);

            // Force layout update
            mInputView.requestLayout();
        }
    }

    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        if (mInputView != null) {
            mInputView.setSubtypeOnSpaceKey(subtype);
        }
    }

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


    public void onKey(int primaryCode, int[] keyCodes) {
        if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            LatinKeyboardView activeKeyboardView = isFloatingMode ? mOverlayKeyboardView : mInputView;
            if (activeKeyboardView != null) {
                Keyboard current = activeKeyboardView.getKeyboard();
                String currentType = "UNKNOWN";
                String targetType = "UNKNOWN";

                if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                    currentType = "SYMBOLS";
                    targetType = "QWERTY";
                } else {
                    currentType = "QWERTY";
                    targetType = "SYMBOLS";
                }

                // Perform the switch
                if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                    setActiveKeyboard(mQwertyKeyboard);
                } else {
                    setActiveKeyboard(mSymbolsKeyboard);
                    mSymbolsKeyboard.setShifted(false);
                }
            }
            return;
        }
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

    // Replace the handleShift method with this null-safe version:
    private void handleShift() {
        LatinKeyboardView activeKeyboardView = isFloatingMode ? mOverlayKeyboardView : mInputView;

        if (activeKeyboardView == null) {
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
    // 6. Update setActiveKeyboard to add more detailed logging
    private void setActiveKeyboard(LatinKeyboard nextKeyboard) {
        final boolean shouldSupportLanguageSwitchKey =
                mInputMethodManager.shouldOfferSwitchingToNextInputMethod(getToken());

        // Determine which keyboard to use based on the type and current mode
        LatinKeyboard keyboardToUse = null;

        // Check what type of keyboard is being requested
        if (nextKeyboard == mNormalQwertyKeyboard || nextKeyboard == mFloatingQwertyKeyboard ||
                nextKeyboard == mQwertyKeyboard) {
            // QWERTY keyboard requested
            keyboardToUse = isFloatingMode ? mFloatingQwertyKeyboard : mNormalQwertyKeyboard;
            mQwertyKeyboard = keyboardToUse;
        } else if (nextKeyboard == mNormalSymbolsKeyboard || nextKeyboard == mFloatingSymbolsKeyboard ||
                nextKeyboard == mSymbolsKeyboard) {
            // Symbols keyboard requested
            keyboardToUse = isFloatingMode ? mFloatingSymbolsKeyboard : mNormalSymbolsKeyboard;
            mSymbolsKeyboard = keyboardToUse;
        } else if (nextKeyboard == mNormalSymbolsShiftedKeyboard || nextKeyboard == mFloatingSymbolsShiftedKeyboard ||
                nextKeyboard == mSymbolsShiftedKeyboard) {
            // Symbols shifted keyboard requested
            keyboardToUse = isFloatingMode ? mFloatingSymbolsShiftedKeyboard : mNormalSymbolsShiftedKeyboard;
            mSymbolsShiftedKeyboard = keyboardToUse;
        }

        if (keyboardToUse == null) {
            return;
        }

        keyboardToUse.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey);

        // Apply to the appropriate view
        if (isFloatingMode && mOverlayKeyboardView != null) {
            mOverlayKeyboardView.setKeyboard(keyboardToUse);
            mOverlayKeyboardView.invalidateAllKeys();
            mOverlayKeyboardView.requestLayout();

            // Force parent layout update
            if (overlayView != null) {
                overlayView.requestLayout();
            }
        } else if (mInputView != null) {
            mInputView.setKeyboard(keyboardToUse);
            mInputView.invalidateAllKeys();
            mInputView.requestLayout();
        }

        mCurKeyboard = keyboardToUse;
    }

    @Override public void onFinishInput() {
        super.onFinishInput();

        // Clear current composing text and candidates.
        mComposing.setLength(0);

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

    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
        }
    }

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
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
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
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

    public void onPress(int primaryCode) {
    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override
    public void onDestroy() {
        hideOverlay();
        super.onDestroy();
    }
}