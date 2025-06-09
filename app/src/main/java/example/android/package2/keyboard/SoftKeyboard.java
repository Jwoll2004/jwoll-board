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
import android.util.DisplayMetrics;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
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
    private LatinKeyboardView mOverlayKeyboardView; // For the overlay keyboard

    private DraggableKeyboardContainer mDraggableContainer;
    private boolean mKeyboardsInitialized = false;
    private boolean mIsCurrentlyFloating = false;

    private WindowManager mWindowManager;
    private float mCurrentWindowX = 0;
    private float mCurrentWindowY = 0;

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
        // Ensure keyboards are initialized first
        if (!mKeyboardsInitialized) {
            onInitializeInterface();
        }

        View normalLayout = getLayoutInflater().inflate(R.layout.normal_keyboard_layout_with_emoji, null);

        mDraggableContainer = normalLayout.findViewById(R.id.draggable_container);
        mInputView = normalLayout.findViewById(R.id.keyboard);

        if (mInputView == null) {
            Log.e("SoftKeyboard", "mInputView is null after inflation");
            return normalLayout;
        }

        mInputView.setOnKeyboardActionListener(this);

        // Setup drag functionality
        View dragHandle = normalLayout.findViewById(R.id.drag_handle);
        if (mDraggableContainer != null && dragHandle != null) {
            mDraggableContainer.setDragHandle(dragHandle);
            mDraggableContainer.setDragCallback(new DraggableKeyboardContainer.DragCallback() {
                @Override
                public void onDragStart() {
                    initializeWindowManager();
                    Log.d("SoftKeyboard", "Drag started");
                }

                @Override
                public void onDragMove(boolean isNearBottom) {
                    // Optional: Add visual feedback for docking
                    if (isNearBottom) {
                        Log.d("SoftKeyboard", "Near bottom - ready to dock");
                    }
                }

                @Override
                public void onSmoothMove(float newX, float newY) {
                    if (mIsCurrentlyFloating) {
                        smoothMoveWindow(newX, newY);
                    }
                }

                @Override
                public void onDragEnd() {
                    if (mIsCurrentlyFloating && shouldDockWindow()) {
                        Log.d("SoftKeyboard", "Docking keyboard");
                        mIsCurrentlyFloating = false;
                        mDraggableContainer.forceDockMode();
                    }
                    Log.d("SoftKeyboard", "Drag ended, floating: " + mIsCurrentlyFloating);
                }

                @Override
                public void onSizeChange(boolean isFloating) {
                    mIsCurrentlyFloating = isFloating;
                    handleSizeChange(isFloating);
                }

                @Override
                public float[] getCurrentWindowPosition() {
                    return new float[]{mCurrentWindowX, mCurrentWindowY};
                }
            });

        }

        // Setup emoji functionality
        normalEmojiManager = SoftKeyboardEmojiExtensionKt.setupEmojiSupport(this, normalLayout);

        // Setup sharing functionality
        setupSharingButtons(normalLayout);

        // Remove old float toggle - we now drag to float
        View floatToggleButton = normalLayout.findViewById(R.id.float_toggle_button);
        if (floatToggleButton != null) {
            floatToggleButton.setVisibility(View.GONE);
        }

        return normalLayout;
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);

        if (mIsCurrentlyFloating) {
            // Make the entire screen available for floating keyboard
            outInsets.contentTopInsets = 0;
            outInsets.visibleTopInsets = 0;
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;

            // Set touchable region to the keyboard area only
            if (mDraggableContainer != null) {
                int left = (int) mDraggableContainer.getX();
                int top = (int) mDraggableContainer.getY();
                int right = left + mDraggableContainer.getWidth();
                int bottom = top + mDraggableContainer.getHeight();

                outInsets.touchableRegion.set(left, top, right, bottom);
            }
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

    private void handleSizeChange(boolean isFloating) {
        if (!mKeyboardsInitialized) {
            Log.w("SoftKeyboard", "Keyboards not initialized yet");
            return;
        }

        Log.d("SoftKeyboard", "Size changing to floating: " + isFloating);

        // Remember current keyboard type
        boolean isQwerty = (mCurKeyboard == mQwertyKeyboard);
        boolean isSymbols = (mCurKeyboard == mSymbolsKeyboard);
        boolean isSymbolsShifted = (mCurKeyboard == mSymbolsShiftedKeyboard);

        if (isFloating) {
            // Switch to smaller keyboards for floating mode
            int floatingWidth = (int) (getMaxWidth() * 0.8f);
            mQwertyKeyboard = new ResizableLatinKeyboard(getApplicationContext(), R.xml.qwerty, floatingWidth);
            mSymbolsKeyboard = new ResizableLatinKeyboard(getApplicationContext(), R.xml.symbols, floatingWidth);
            mSymbolsShiftedKeyboard = new ResizableLatinKeyboard(getApplicationContext(), R.xml.symbols_shift, floatingWidth);
        } else {
            // Switch back to normal keyboards
            final Context displayContext = getDisplayContext();
            mQwertyKeyboard = new LatinKeyboard(displayContext, R.xml.qwerty);
            mSymbolsKeyboard = new LatinKeyboard(displayContext, R.xml.symbols);
            mSymbolsShiftedKeyboard = new LatinKeyboard(displayContext, R.xml.symbols_shift);
        }

        // Restore current keyboard type
        if (isQwerty) {
            mCurKeyboard = mQwertyKeyboard;
        } else if (isSymbols) {
            mCurKeyboard = mSymbolsKeyboard;
        } else if (isSymbolsShifted) {
            mCurKeyboard = mSymbolsShiftedKeyboard;
        } else {
            mCurKeyboard = mQwertyKeyboard; // Default fallback
        }

        // Apply current editor info if available
        EditorInfo currentEditorInfo = getCurrentInputEditorInfo();
        if (currentEditorInfo != null && mCurKeyboard != null) {
            mCurKeyboard.setImeOptions(getResources(), currentEditorInfo.imeOptions);
        }

        // Update UI first
        if (mInputView != null && mCurKeyboard != null) {
            mInputView.setKeyboard(mCurKeyboard);
            mInputView.invalidateAllKeys();
            mInputView.requestLayout();
        }

        // Update window after UI is laid out
        if (mDraggableContainer != null) {
            mDraggableContainer.post(new Runnable() {
                @Override
                public void run() {
                    updateWindowForFloating(isFloating);
                }
            });
        } else {
            updateWindowForFloating(isFloating);
        }

        mDraggableContainer.post(new Runnable() {
            @Override
            public void run() {
                debugLayoutSizes(isFloating ? "AFTER_FLOATING" : "AFTER_DOCKING");
                updateWindowForFloating(isFloating);
            }
        });
    }

    private WindowManager.LayoutParams mOriginalWindowParams;
    private boolean mWindowParamsSaved = false;

    private void saveOriginalWindowParams() {
        if (!mWindowParamsSaved) {
            try {
                Dialog imeWindow = getWindow();
                if (imeWindow != null && imeWindow.getWindow() != null) {
                    mOriginalWindowParams = new WindowManager.LayoutParams();
                    WindowManager.LayoutParams current = imeWindow.getWindow().getAttributes();
                    mOriginalWindowParams.copyFrom(current);
                    mWindowParamsSaved = true;
                    Log.d("SoftKeyboard", "Original window params saved: " + current.width + "x" + current.height);
                }
            } catch (Exception e) {
                Log.e("SoftKeyboard", "Error saving window params", e);
            }
        }
    }

    private void initializeWindowManager() {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }
    }

    private void updateWindowForFloating(boolean isFloating) {
        try {
            Dialog imeWindow = getWindow();
            if (imeWindow == null || imeWindow.getWindow() == null) return;

            Window window = imeWindow.getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            View contentView = window.findViewById(android.R.id.content);

            if (isFloating) {
                // Save original params if not saved
                saveOriginalWindowParams();

                // Calculate floating size - get actual height first
                int floatingWidth = (int) (getMaxWidth() * 0.8f);

                // Get the current actual height of the keyboard container
                int actualCurrentHeight = 0;
                if (mDraggableContainer != null) {
                    // Force a layout pass to get accurate measurements
                    mDraggableContainer.measure(
                            View.MeasureSpec.makeMeasureSpec(floatingWidth, View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    );
                    actualCurrentHeight = mDraggableContainer.getMeasuredHeight();
                    Log.d("SoftKeyboard", "Measured container height for floating: " + actualCurrentHeight);
                }

                // If we don't have a measurement, calculate based on components
                if (actualCurrentHeight <= 0) {
                    actualCurrentHeight = getActualKeyboardHeight();
                }

                int floatingHeight = actualCurrentHeight; // Use full height, don't reduce

                // Update window params for floating
                params.width = floatingWidth;
                params.height = floatingHeight;
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

                // Initial position
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                mCurrentWindowX = (metrics.widthPixels - floatingWidth) / 2f;
                mCurrentWindowY = metrics.heightPixels - floatingHeight - 200f;

                params.x = (int) mCurrentWindowX;
                params.y = (int) mCurrentWindowY;

                // Resize content view to prevent overflow/clipping
                if (contentView != null) {
                    ViewGroup.LayoutParams contentParams = contentView.getLayoutParams();
                    if (contentParams == null) {
                        contentParams = new ViewGroup.LayoutParams(floatingWidth, floatingHeight);
                    } else {
                        contentParams.width = floatingWidth;
                        contentParams.height = floatingHeight;
                    }
                    contentView.setLayoutParams(contentParams);
                    Log.d("SoftKeyboard", "Content view set to: " + floatingWidth + "x" + floatingHeight);
                }

                // Resize container to match - IMPORTANT: Keep full height
                if (mDraggableContainer != null) {
                    ViewGroup.LayoutParams containerParams = mDraggableContainer.getLayoutParams();
                    containerParams.width = floatingWidth;
                    containerParams.height = floatingHeight; // Use full height
                    mDraggableContainer.setLayoutParams(containerParams);
                    mDraggableContainer.requestLayout();
                    Log.d("SoftKeyboard", "Container set to: " + floatingWidth + "x" + floatingHeight);
                }

                Log.d("SoftKeyboard", "Floating mode - size: " + floatingWidth + "x" + floatingHeight +
                        " pos: (" + mCurrentWindowX + "," + mCurrentWindowY + ")");
            } else {
                // Restore to docked mode
                if (mOriginalWindowParams != null) {
                    params.copyFrom(mOriginalWindowParams);

                    // Restore sizes
                    if (contentView != null) {
                        ViewGroup.LayoutParams contentParams = contentView.getLayoutParams();
                        contentParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        contentParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        contentView.setLayoutParams(contentParams);
                    }

                    if (mDraggableContainer != null) {
                        ViewGroup.LayoutParams containerParams = mDraggableContainer.getLayoutParams();
                        containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        containerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        mDraggableContainer.setLayoutParams(containerParams);
                    }

                    window.setAttributes(params);
                    Log.d("SoftKeyboard", "Restored to docked mode");
                }
            }

        } catch (Exception e) {
            Log.e("SoftKeyboard", "Error updating window", e);
        }
    }

    private void smoothMoveWindow(float newX, float newY) {
        try {
            Dialog imeWindow = getWindow();
            if (imeWindow == null || imeWindow.getWindow() == null) return;

            Window window = imeWindow.getWindow();
            WindowManager.LayoutParams params = window.getAttributes();

            // Apply bounds constraints - minimal computation
            DisplayMetrics metrics = getResources().getDisplayMetrics();

            // Simple bounds checking - no complex calculations
            int maxX = metrics.widthPixels - params.width;
            int maxY = metrics.heightPixels - params.height;

            int constrainedX = (int) Math.max(0, Math.min(newX, maxX));
            int constrainedY = (int) Math.max(0, Math.min(newY, maxY));

            // Only update if position actually changed to avoid unnecessary calls
            if (params.x != constrainedX || params.y != constrainedY) {
                params.x = constrainedX;
                params.y = constrainedY;

                // Store current position
                mCurrentWindowX = constrainedX;
                mCurrentWindowY = constrainedY;

                // Apply immediately - this is the key for smoothness
                window.setAttributes(params);
            }

        } catch (Exception e) {
            Log.e("SoftKeyboard", "Error in smooth move", e);
        }
    }

    private boolean shouldDockWindow() {
        try {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            Dialog imeWindow = getWindow();
            if (imeWindow != null && imeWindow.getWindow() != null) {
                WindowManager.LayoutParams params = imeWindow.getWindow().getAttributes();
                int windowBottom = params.y + params.height;
                int dockThreshold = metrics.heightPixels - (int) (50 * metrics.density);

                return windowBottom > dockThreshold;
            }
        } catch (Exception e) {
            Log.e("SoftKeyboard", "Error checking dock condition", e);
        }
        return false;
    }

    private int getActualKeyboardHeight() {
        if (mDraggableContainer != null && mDraggableContainer.getHeight() > 0) {
            // Use the actual measured height of the container
            int fullHeight = mDraggableContainer.getHeight();
            Log.d("SoftKeyboard", "Container measured height: " + fullHeight);
            return fullHeight;
        } else if (mInputView != null && mInputView.getHeight() > 0) {
            // Fallback to keyboard view height plus emoji/toolbar areas
            int keyboardHeight = mInputView.getHeight();
            int emojiBarHeight = 46; // dp converted to px
            int toolbarHeight = 40;
            int dragHandleHeight = 40;

            int totalHeight = keyboardHeight + emojiBarHeight + toolbarHeight + dragHandleHeight;
            Log.d("SoftKeyboard", "Calculated height from components: " + totalHeight);
            return totalHeight;
        } else {
            // Fallback to estimated height
            int fallbackHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.3f);
            Log.d("SoftKeyboard", "Using fallback height: " + fallbackHeight);
            return fallbackHeight;
        }
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

    private void debugLayoutSizes(String when) {
        Log.d("SoftKeyboard", "=== LAYOUT DEBUG: " + when + " ===");

        if (mDraggableContainer != null) {
            Log.d("SoftKeyboard", "Container: " + mDraggableContainer.getWidth() + "x" + mDraggableContainer.getHeight());
            Log.d("SoftKeyboard", "Container measured: " + mDraggableContainer.getMeasuredWidth() + "x" + mDraggableContainer.getMeasuredHeight());
        }

        if (mInputView != null) {
            Log.d("SoftKeyboard", "InputView: " + mInputView.getWidth() + "x" + mInputView.getHeight());
            Log.d("SoftKeyboard", "InputView measured: " + mInputView.getMeasuredWidth() + "x" + mInputView.getMeasuredHeight());
        }

        Dialog imeWindow = getWindow();
        if (imeWindow != null && imeWindow.getWindow() != null) {
            WindowManager.LayoutParams params = imeWindow.getWindow().getAttributes();
            Log.d("SoftKeyboard", "Window params: " + params.width + "x" + params.height);

            View contentView = imeWindow.getWindow().findViewById(android.R.id.content);
            if (contentView != null) {
                Log.d("SoftKeyboard", "Content view: " + contentView.getWidth() + "x" + contentView.getHeight());
            }
        }
    }

    // Update the createOverlayView() method in SoftKeyboard.java:

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
    }

    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        if (mInputView != null) {
            mInputView.setSubtypeOnSpaceKey(subtype);
        }
    }

    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }


    public void onKey(int primaryCode, int[] keyCodes) {
        if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
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
            return;
        }

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

        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
            // After backspace, check what word we're now in
            notifyEmojiManagersWordChange();

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
        } else {
            handleCharacter(primaryCode, keyCodes);
            // After adding character, check current word
            notifyEmojiManagersWordChange();
        }
    }

    // Replace the handleShift method with this null-safe version:
    private void handleShift() {
        if (mInputView == null) {
            return;
        }

        Keyboard currentKeyboard = mInputView.getKeyboard();

        if (currentKeyboard == mQwertyKeyboard) {
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else if (currentKeyboard == mSymbolsKeyboard) {
            setActiveKeyboard(mSymbolsShiftedKeyboard);
        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            setActiveKeyboard(mSymbolsKeyboard);
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

    // Replace the emoji manager notification calls with:
    private void notifyEmojiManagersWordChange() {
        String currentWord = getCurrentWord();

        if (normalEmojiManager != null) {
            if (mComposing.length() > 0) {
                normalEmojiManager.handleComposingTextChange(currentWord);
            } else {
                if (isAtEndOfWord()) {
                    normalEmojiManager.handleComposingTextChange(currentWord);
                } else {
                    normalEmojiManager.handleWordCompletion(currentWord);
                }
            }
        }
    }

    private void notifyEmojiManagersWordCompletion(String lastWord) {
        if (normalEmojiManager != null) {
            normalEmojiManager.handleWordCompletion(lastWord);
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

    // Replace the handleCharacter method with this null-safe version:
    private void handleCharacter(int primaryCode, int[] keyCodes) {
        LatinKeyboardView activeKeyboardView = isFloatingMode ? mOverlayKeyboardView : mInputView;

        if (activeKeyboardView == null) {
            return;
        }

        if (isInputViewShown() || isFloatingMode) {
            if (activeKeyboardView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }

        Log.d("EmojiDebug", "handleCharacter: '" + (char) primaryCode + "', isAlphabet: " + isAlphabet(primaryCode) + ", mPredictionOn: " + mPredictionOn);

        if (isAlphabet(primaryCode) && mPredictionOn) {
            mComposing.append((char) primaryCode);

            Log.d("EmojiDebug", "Added to composing. New length: " + mComposing.length() + ", text: '" + mComposing.toString() + "'");

            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());

        } else {
            Log.d("EmojiDebug", "Not adding to composing, committing character: '" + (char) primaryCode + "'");
            getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
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
        return separators.contains(String.valueOf((char) code));
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