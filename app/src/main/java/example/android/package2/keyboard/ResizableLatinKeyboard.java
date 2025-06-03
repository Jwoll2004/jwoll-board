package example.android.package2.keyboard;

import android.content.Context;
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
    // 2. Update the ResizableLatinKeyboard constructor to add logging
    // Update the constructor to use the enhanced resizing
    // Enhanced constructor that uses the new distribution method
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

    // Enhanced resizeKeyboard method with gap fixing
    private void resizeKeyboard(int targetWidth) {
        Log.d(TAG, "=== RESIZING KEYBOARD ===");
        Log.d(TAG, "Original width: " + getMinWidth() + ", Target width: " + targetWidth);

        // Analyze gaps before scaling
        analyzeKeyGaps("BEFORE_SCALING");

        List<Key> keys = getKeys();
        if (keys.isEmpty()) {
            Log.w(TAG, "No keys found to resize");
            return;
        }

        float scaleX = (float) targetWidth / getMinWidth();
        mScaleFactor = scaleX;
        Log.d(TAG, "Scale factor: " + scaleX);

        // First pass: Scale all keys normally
        for (Key key : keys) {
            key.x = (int) (key.x * scaleX);
            key.width = (int) (key.width * scaleX);
            key.gap = 0; // Ensure no gaps
        }

        // Second pass: Distribute keys evenly row by row to eliminate gaps and fill target width
        distributeKeysEvenly(targetWidth);

        // Analyze gaps after distribution
        analyzeKeyGaps("AFTER_DISTRIBUTION");

        // Update keyboard dimensions using reflection
        try {
            Field totalWidthField = Keyboard.class.getDeclaredField("mTotalWidth");
            totalWidthField.setAccessible(true);
            int oldTotalWidth = totalWidthField.getInt(this);
            totalWidthField.setInt(this, targetWidth);

            // Try different possible field names for display width
            try {
                Field displayWidthField = Keyboard.class.getDeclaredField("mDisplayWidth");
                displayWidthField.setAccessible(true);
                int oldDisplayWidth = displayWidthField.getInt(this);
                displayWidthField.setInt(this, targetWidth);

                Log.d(TAG, String.format("Updated dimensions: totalWidth %d->%d, displayWidth %d->%d",
                        oldTotalWidth, targetWidth, oldDisplayWidth, targetWidth));
            } catch (NoSuchFieldException e) {
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
    // New method to distribute keys evenly within each row
    private void distributeKeysEvenly(int targetWidth) {
        Log.d(TAG, "=== DISTRIBUTING KEYS EVENLY ===");

        List<List<Key>> rows = getRowKeys();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<Key> row = rows.get(rowIndex);
            if (row.isEmpty()) continue;

            Log.d(TAG, "Distributing row " + rowIndex + " with " + row.size() + " keys");

            // Calculate total width needed for this row
            int totalKeyWidth = 0;
            for (Key key : row) {
                totalKeyWidth += key.width;
            }

            Log.d(TAG, "Row " + rowIndex + " - Total key width: " + totalKeyWidth + ", Target: " + targetWidth);

            // Check if this row should fill the full width
            // (Most rows should, except maybe some special cases like the second row with offset)
            boolean shouldFillWidth = shouldRowFillWidth(row, rowIndex);
            int effectiveTargetWidth = shouldFillWidth ? targetWidth : totalKeyWidth;

            if (shouldFillWidth && totalKeyWidth != targetWidth) {
                // Distribute keys to fill exact target width
                distributeRowKeys(row, effectiveTargetWidth, rowIndex);
            } else {
                // Just ensure keys are adjacent (no gaps)
                makeKeysAdjacent(row, rowIndex);
            }
        }

        Log.d(TAG, "=== DISTRIBUTION COMPLETE ===");
    }
    // Determine if a row should fill the full keyboard width
    private boolean shouldRowFillWidth(List<Key> row, int rowIndex) {
        if (row.isEmpty()) return false;

        // Check if the first key starts at x=0 and last key should end at keyboard width
        Key firstKey = row.get(0);
        Key lastKey = row.get(row.size() - 1);

        // If first key starts at 0 and row has edge flags, it should fill width
        boolean startsAtZero = firstKey.x <= 5; // Allow small margin for rounding
        boolean hasRightEdge = (lastKey.edgeFlags & 2) != 0; // RIGHT edge flag

        Log.d(TAG, "Row " + rowIndex + " - starts at zero: " + startsAtZero + ", has right edge: " + hasRightEdge);

        return startsAtZero && hasRightEdge;
    }

    // Distribute keys in a row to fill exact target width
    private void distributeRowKeys(List<Key> row, int targetWidth, int rowIndex) {
        Log.d(TAG, "Distributing row " + rowIndex + " to fill " + targetWidth + "px");

        if (row.isEmpty()) return;

        // Calculate current total width
        int currentTotalWidth = 0;
        for (Key key : row) {
            currentTotalWidth += key.width;
        }

        // Calculate how much we need to adjust
        int widthDifference = targetWidth - currentTotalWidth;
        Log.d(TAG, "Width difference to distribute: " + widthDifference);

        if (widthDifference == 0) {
            // Perfect fit, just make adjacent
            makeKeysAdjacent(row, rowIndex);
            return;
        }

        // Distribute the width difference across keys
        // Prioritize distributing to larger keys (like space bar)
        List<Key> keysByWidth = new ArrayList<>(row);
        keysByWidth.sort((a, b) -> Integer.compare(b.width, a.width)); // Largest first

        // Distribute width difference
        int remainingDifference = widthDifference;
        for (Key key : keysByWidth) {
            if (remainingDifference == 0) break;

            // Give larger keys more of the adjustment
            int adjustment = 0;
            if (Math.abs(remainingDifference) >= keysByWidth.size()) {
                adjustment = remainingDifference / keysByWidth.size();
                if (adjustment == 0) {
                    adjustment = remainingDifference > 0 ? 1 : -1;
                }
            } else {
                adjustment = remainingDifference > 0 ? 1 : -1;
            }

            key.width += adjustment;
            remainingDifference -= adjustment;

            Log.d(TAG, "Adjusted " + getKeyDescription(key) + " width by " + adjustment);
        }

        // Now position keys to be adjacent
        makeKeysAdjacent(row, rowIndex);
    }
    // Make keys in a row adjacent (no gaps between them)
    private void makeKeysAdjacent(List<Key> row, int rowIndex) {
        if (row.size() <= 1) return;

        Log.d(TAG, "Making row " + rowIndex + " keys adjacent");

        // Keep first key position, adjust others
        for (int i = 1; i < row.size(); i++) {
            Key prevKey = row.get(i - 1);
            Key currentKey = row.get(i);

            int newX = prevKey.x + prevKey.width;
            if (currentKey.x != newX) {
                Log.d(TAG, "Moved key " + getKeyDescription(currentKey) + " from x=" + currentKey.x + " to x=" + newX);
                currentKey.x = newX;
            }

            currentKey.gap = 0; // Ensure no gap
        }
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

    // Enhanced gap analysis for each row
    private void analyzeKeyGaps(String when) {
        Log.d(TAG, "=== KEY GAP ANALYSIS " + when + " ===");

        List<List<Key>> rows = getRowKeys();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<Key> row = rows.get(rowIndex);
            Log.d(TAG, "Row " + rowIndex + " (" + row.size() + " keys):");

            int totalRowWidth = 0;
            for (int i = 0; i < row.size(); i++) {
                Key key = row.get(i);
                String keyDesc = getKeyDescription(key);

                if (i == 0) {
                    Log.d(TAG, String.format("  Key %d: %s | x=%d, w=%d, gap=%d, right=%d",
                            i, keyDesc, key.x, key.width, key.gap, key.x + key.width));
                } else {
                    Key prevKey = row.get(i - 1);
                    int actualGap = key.x - (prevKey.x + prevKey.width);
                    Log.d(TAG, String.format("  Key %d: %s | x=%d, w=%d, gap=%d, right=%d | actual_gap_from_prev=%d",
                            i, keyDesc, key.x, key.width, key.gap, key.x + key.width, actualGap));

                    if (actualGap > 2) {
                        Log.w(TAG, "    *** VISIBLE GAP DETECTED: " + actualGap + " pixels ***");
                    }
                }

                totalRowWidth = Math.max(totalRowWidth, key.x + key.width);
            }

            Log.d(TAG, "  Row total width: " + totalRowWidth + ", Keyboard width: " + getMinWidth());

            // Check if row fills the keyboard width
            if (totalRowWidth < getMinWidth() - 5) {
                Log.w(TAG, "  *** ROW DOESN'T FILL KEYBOARD WIDTH: missing " + (getMinWidth() - totalRowWidth) + " pixels ***");
            }
        }

        Log.d(TAG, "=== END GAP ANALYSIS ===");
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