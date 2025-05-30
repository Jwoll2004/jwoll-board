package example.android.package2;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResizableLatinKeyboard extends LatinKeyboard {
    private static final String TAG = "ResizableLatinKeyboard";
    private float mScaleFactor = 1.0f;
    private Context mContext;

    // Store references to the scaled saved keys
    private Key mScaledSavedModeChangeKey;
    private Key mScaledSavedLanguageSwitchKey;

    // Store key properties instead of Key objects
    private static class KeyProperties {
        int x, width;
        Drawable icon, iconPreview;

        KeyProperties(int x, int width, Drawable icon, Drawable iconPreview) {
            this.x = x;
            this.width = width;
            this.icon = icon;
            this.iconPreview = iconPreview;
        }
    }

    private KeyProperties mScaledModeChangeKey;
    private KeyProperties mScaledLanguageSwitchKey;


    public ResizableLatinKeyboard(Context context, int xmlLayoutResId) {
        super(context, xmlLayoutResId);
        mContext = context;
    }

    // 2. Update the ResizableLatinKeyboard constructor to add logging
    public ResizableLatinKeyboard(Context context, int xmlLayoutResId, int targetWidth) {
        super(context, xmlLayoutResId);

        Log.d(TAG, "Creating ResizableLatinKeyboard with target width: " + targetWidth);
        logKeyboardLayout("BEFORE_RESIZE");

        if (targetWidth > 0 && targetWidth != getMinWidth()) {
            mScaleFactor = (float) targetWidth / getMinWidth();
            resizeKeyboard(targetWidth);
            logKeyboardLayout("AFTER_RESIZE");

            // Save scaled versions of the special keys after resizing
            saveScaledSpecialKeys();
        }
    }


    // Update the resizeKeyboard method to save the scale factor
    private void resizeKeyboard(int targetWidth) {
        Log.d(TAG, "=== RESIZING KEYBOARD ===");
        Log.d(TAG, "Original width: " + getMinWidth() + ", Target width: " + targetWidth);

        List<Key> keys = getKeys();
        if (keys.isEmpty()) {
            Log.w(TAG, "No keys found to resize");
            return;
        }

        float scaleX = (float) targetWidth / getMinWidth();
        mScaleFactor = scaleX; // Store the scale factor
        Log.d(TAG, "Scale factor: " + scaleX);

        // Log before scaling
        Log.d(TAG, "Before scaling - sample key positions:");
        for (int i = Math.max(0, keys.size() - 6); i < keys.size(); i++) {
            Key key = keys.get(i);
            Log.d(TAG, String.format("Key %d: x=%d, width=%d", i, key.x, key.width));
        }

        // Scale all keys
        for (Key key : keys) {
            int oldX = key.x;
            int oldWidth = key.width;
            key.x = (int) (key.x * scaleX);
            key.width = (int) (key.width * scaleX);

            // Log significant changes
            if (Math.abs(oldX - key.x) > 5 || Math.abs(oldWidth - key.width) > 5) {
                Log.d(TAG, String.format("Scaled key: x %d->%d, width %d->%d",
                        oldX, key.x, oldWidth, key.width));
            }
        }

        // Log after scaling
        Log.d(TAG, "After scaling - sample key positions:");
        for (int i = Math.max(0, keys.size() - 6); i < keys.size(); i++) {
            Key key = keys.get(i);
            Log.d(TAG, String.format("Key %d: x=%d, width=%d", i, key.x, key.width));
        }

        // Update keyboard dimensions using reflection
        try {
            Field totalWidthField = Keyboard.class.getDeclaredField("mTotalWidth");
            totalWidthField.setAccessible(true);
            int oldTotalWidth = totalWidthField.getInt(this);
            totalWidthField.setInt(this, targetWidth);

            // Try both possible field names for display width
            try {
                Field displayWidthField = Keyboard.class.getDeclaredField("mDisplayWidth");
                displayWidthField.setAccessible(true);
                int oldDisplayWidth = displayWidthField.getInt(this);
                displayWidthField.setInt(this, targetWidth);

                Log.d(TAG, String.format("Updated dimensions: totalWidth %d->%d, displayWidth %d->%d",
                        oldTotalWidth, targetWidth, oldDisplayWidth, targetWidth));
            } catch (NoSuchFieldException e) {
                // Try alternative field name
                try {
                    Field displayWidthField = Keyboard.class.getDeclaredField("mMinWidth");
                    displayWidthField.setAccessible(true);
                    int oldDisplayWidth = displayWidthField.getInt(this);
                    displayWidthField.setInt(this, targetWidth);

                    Log.d(TAG, String.format("Updated dimensions: totalWidth %d->%d, minWidth %d->%d",
                            oldTotalWidth, targetWidth, oldDisplayWidth, targetWidth));
                } catch (NoSuchFieldException e2) {
                    Log.w(TAG, "Could not find display width field, only updated total width");
                }
            }

            Log.d(TAG, "Final keyboard width: " + getMinWidth());
        } catch (Exception e) {
            Log.e(TAG, "Failed to resize keyboard via reflection", e);
        }

        Log.d(TAG, "=== RESIZE COMPLETE ===");
    }


    private void saveScaledSpecialKeys() {
        Log.d(TAG, "Saving scaled special keys with scale factor: " + mScaleFactor);

        List<Key> keys = getKeys();
        for (Key key : keys) {
            if (key.codes != null && key.codes.length > 0) {
                if (key.codes[0] == Keyboard.KEYCODE_MODE_CHANGE) {
                    // Save the scaled mode change key properties
                    mScaledModeChangeKey = new KeyProperties(key.x, key.width, key.icon, key.iconPreview);
                    Log.d(TAG, "Saved scaled mode change key: x=" + key.x + ", width=" + key.width);
                } else if (key.codes[0] == LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
                    // Save the scaled language switch key properties
                    mScaledLanguageSwitchKey = new KeyProperties(key.x, key.width, key.icon, key.iconPreview);
                    Log.d(TAG, "Saved scaled language switch key: x=" + key.x + ", width=" + key.width);
                }
            }
        }
    }


    private void updateSavedKeysForScaling() {
        try {
            // Access the private saved key fields using reflection
            Field savedModeChangeKeyField = LatinKeyboard.class.getDeclaredField("mSavedModeChangeKey");
            savedModeChangeKeyField.setAccessible(true);
            Key originalSavedModeChangeKey = (Key) savedModeChangeKeyField.get(this);

            Field savedLanguageSwitchKeyField = LatinKeyboard.class.getDeclaredField("mSavedLanguageSwitchKey");
            savedLanguageSwitchKeyField.setAccessible(true);
            Key originalSavedLanguageSwitchKey = (Key) savedLanguageSwitchKeyField.get(this);

            if (originalSavedModeChangeKey != null) {
                // Create scaled copies of the saved keys
                mScaledSavedModeChangeKey = createScaledKey(originalSavedModeChangeKey);
                savedModeChangeKeyField.set(this, mScaledSavedModeChangeKey);
                Log.d(TAG, "Updated saved mode change key with scaling");
            }

            if (originalSavedLanguageSwitchKey != null) {
                mScaledSavedLanguageSwitchKey = createScaledKey(originalSavedLanguageSwitchKey);
                savedLanguageSwitchKeyField.set(this, mScaledSavedLanguageSwitchKey);
                Log.d(TAG, "Updated saved language switch key with scaling");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to update saved keys for scaling", e);
        }
    }

    private Key createScaledKey(Key originalKey) {
        try {
            // Get the Row from the original key using reflection
            Field rowField = Key.class.getDeclaredField("row");
            rowField.setAccessible(true);
            Keyboard.Row row = (Keyboard.Row) rowField.get(originalKey);

            // Create a new key using the proper constructor
            Key scaledKey = new LatinKey(mContext.getResources(), row,
                    originalKey.x, originalKey.y, null);

            // Copy all available properties from original key
            scaledKey.codes = originalKey.codes.clone();
            scaledKey.label = originalKey.label;
            scaledKey.icon = originalKey.icon;
            scaledKey.iconPreview = originalKey.iconPreview;
            scaledKey.popupCharacters = originalKey.popupCharacters;
            scaledKey.popupResId = originalKey.popupResId;
            scaledKey.repeatable = originalKey.repeatable;
            scaledKey.modifier = originalKey.modifier;
            scaledKey.sticky = originalKey.sticky;
            scaledKey.edgeFlags = originalKey.edgeFlags;

            // Apply scaling to position and size
            scaledKey.x = (int) (originalKey.x * mScaleFactor);
            scaledKey.y = originalKey.y; // Don't scale Y
            scaledKey.width = (int) (originalKey.width * mScaleFactor);
            scaledKey.height = originalKey.height; // Don't scale height
            scaledKey.gap = (int) (originalKey.gap * mScaleFactor);

            Log.d(TAG, "Created scaled key: original width=" + originalKey.width +
                    ", scaled width=" + scaledKey.width + ", scale=" + mScaleFactor);

            return scaledKey;

        } catch (Exception e) {
            Log.e(TAG, "Failed to create scaled key", e);
            // If we can't create a new key, try to modify the original key's copy
            return createScaledKeyFallback(originalKey);
        }
    }

    private Key createScaledKeyFallback(Key originalKey) {
        try {
            // Create a simple key copy by cloning using reflection
            Key scaledKey = originalKey.getClass().newInstance();

            // Copy basic fields
            scaledKey.codes = originalKey.codes.clone();
            scaledKey.label = originalKey.label;
            scaledKey.icon = originalKey.icon;
            scaledKey.iconPreview = originalKey.iconPreview;
            scaledKey.popupCharacters = originalKey.popupCharacters;
            scaledKey.popupResId = originalKey.popupResId;
            scaledKey.repeatable = originalKey.repeatable;
            scaledKey.modifier = originalKey.modifier;
            scaledKey.sticky = originalKey.sticky;
            scaledKey.edgeFlags = originalKey.edgeFlags;

            // Apply scaling
            scaledKey.x = (int) (originalKey.x * mScaleFactor);
            scaledKey.y = originalKey.y;
            scaledKey.width = (int) (originalKey.width * mScaleFactor);
            scaledKey.height = originalKey.height;
            scaledKey.gap = (int) (originalKey.gap * mScaleFactor);

            return scaledKey;

        } catch (Exception e) {
            Log.e(TAG, "Fallback key creation also failed", e);
            // Last resort: just return the original key
            return originalKey;
        }
    }

    @Override
    void setLanguageSwitchKeyVisibility(boolean visible) {
        Log.d(TAG, "setLanguageSwitchKeyVisibility: " + visible + ", scale factor: " + mScaleFactor);

        if (mScaleFactor == 1.0f) {
            // No scaling applied, use parent implementation
            Log.d(TAG, "No scaling applied, using parent implementation");
            super.setLanguageSwitchKeyVisibility(visible);
            return;
        }

        if (mScaledModeChangeKey == null || mScaledLanguageSwitchKey == null) {
            Log.w(TAG, "Scaled saved keys are null, falling back to super");
            super.setLanguageSwitchKeyVisibility(visible);
            return;
        }

        // Find the actual keys in the keyboard
        Key actualModeChangeKey = null;
        Key actualLanguageSwitchKey = null;

        List<Key> keys = getKeys();
        for (Key key : keys) {
            if (key.codes != null && key.codes.length > 0) {
                if (key.codes[0] == Keyboard.KEYCODE_MODE_CHANGE) {
                    actualModeChangeKey = key;
                } else if (key.codes[0] == LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
                    actualLanguageSwitchKey = key;
                }
            }
        }

        if (actualModeChangeKey == null || actualLanguageSwitchKey == null) {
            Log.w(TAG, "Could not find actual keys, falling back to super");
            super.setLanguageSwitchKeyVisibility(visible);
            return;
        }

        Log.d(TAG, "Before adjustment - Mode change: x=" + actualModeChangeKey.x + ", width=" + actualModeChangeKey.width);
        Log.d(TAG, "Before adjustment - Language switch: x=" + actualLanguageSwitchKey.x + ", width=" + actualLanguageSwitchKey.width);

        if (visible) {
            // The language switch key should be visible. Restore both keys to their scaled sizes
            actualModeChangeKey.width = mScaledModeChangeKey.width;
            actualModeChangeKey.x = mScaledModeChangeKey.x;
            actualLanguageSwitchKey.width = mScaledLanguageSwitchKey.width;
            actualLanguageSwitchKey.icon = mScaledLanguageSwitchKey.icon;
            actualLanguageSwitchKey.iconPreview = mScaledLanguageSwitchKey.iconPreview;

            Log.d(TAG, "Set to visible - restored scaled dimensions");
        } else {
            // The language switch key should be hidden. Expand mode change key and hide language key
            actualModeChangeKey.width = mScaledModeChangeKey.width + mScaledLanguageSwitchKey.width;
            actualLanguageSwitchKey.width = 0;
            actualLanguageSwitchKey.icon = null;
            actualLanguageSwitchKey.iconPreview = null;

            Log.d(TAG, "Set to hidden - expanded mode change key width");
        }

        Log.d(TAG, "After adjustment - Mode change: x=" + actualModeChangeKey.x + ", width=" + actualModeChangeKey.width);
        Log.d(TAG, "After adjustment - Language switch: x=" + actualLanguageSwitchKey.x + ", width=" + actualLanguageSwitchKey.width);
    }
    public void logKeyboardLayout(String when) {
        Log.d(TAG, "=== KEYBOARD LAYOUT " + when + " ===");
        Log.d(TAG, "Keyboard total width: " + getMinWidth());

        List<Key> keys = getKeys();
        Log.d(TAG, "Total keys: " + keys.size());

        // Focus on bottom row keys (typically the last row)
        List<List<Key>> rows = getRowKeys();
        if (!rows.isEmpty()) {
            List<Key> bottomRow = rows.get(rows.size() - 1);
            Log.d(TAG, "Bottom row keys: " + bottomRow.size());

            for (int i = 0; i < bottomRow.size(); i++) {
                Key key = bottomRow.get(i);
                String keyLabel = getKeyDescription(key);
                Log.d(TAG, String.format("Key %d: %s | x=%d, width=%d, right=%d",
                        i, keyLabel, key.x, key.width, key.x + key.width));
            }

            // Check for gaps or overlaps
            for (int i = 0; i < bottomRow.size() - 1; i++) {
                Key current = bottomRow.get(i);
                Key next = bottomRow.get(i + 1);
                int gap = next.x - (current.x + current.width);
                if (gap != 0) {
                    Log.w(TAG, String.format("GAP/OVERLAP between key %d and %d: %d pixels",
                            i, i + 1, gap));
                }
            }
        }

        Log.d(TAG, "=== END KEYBOARD LAYOUT ===");
    }

    private List<List<Key>> getRowKeys() {
        List<List<Key>> rows = new ArrayList<>();
        List<Key> allKeys = getKeys();

        if (allKeys.isEmpty()) return rows;

        // Group keys by their Y position (row)
        Map<Integer, List<Key>> rowMap = new HashMap<>();
        for (Key key : allKeys) {
            List<Key> rowKeys = rowMap.get(key.y);
            if (rowKeys == null) {
                rowKeys = new ArrayList<>();
                rowMap.put(key.y, rowKeys);
            }
            rowKeys.add(key);
        }

        // Sort rows by Y position and sort keys within each row by X position
        List<Integer> sortedYPositions = new ArrayList<>(rowMap.keySet());
        Collections.sort(sortedYPositions);

        for (Integer y : sortedYPositions) {
            List<Key> rowKeys = rowMap.get(y);
            Collections.sort(rowKeys, (k1, k2) -> Integer.compare(k1.x, k2.x));
            rows.add(rowKeys);
        }

        return rows;
    }

    private String getKeyDescription(Key key) {
        if (key.label != null) {
            return "'" + key.label + "'";
        } else if (key.codes != null && key.codes.length > 0) {
            int code = key.codes[0];
            switch (code) {
                case -3: return "DONE";
                case -2: return "MODE_CHANGE(123/ABC)";
                case -101: return "LANGUAGE_SWITCH";
                case 32: return "SPACE";
                case 10: return "ENTER";
                case -5: return "DELETE";
                case -1: return "SHIFT";
                default: return "CODE_" + code;
            }
        } else {
            return "UNKNOWN";
        }
    }

}