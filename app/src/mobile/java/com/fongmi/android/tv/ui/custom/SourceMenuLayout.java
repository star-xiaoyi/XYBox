package com.fongmi.android.tv.ui.custom;

/** Menu bounds and its expansion origin, using coordinates in the same dialog window. */
public final class SourceMenuLayout {
    private SourceMenuLayout() { }

    public static final class Bounds {
        public final int x, y, width, height;
        public final float pivotX, pivotY, startScaleX, startScaleY;
        private Bounds(int x, int y, int width, int height, float pivotX, float pivotY, int originSize) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.pivotX = pivotX; this.pivotY = pivotY;
            startScaleX = Math.min(1f, (float) originSize / width);
            startScaleY = Math.min(1f, (float) originSize / height);
        }
    }

    public static Bounds calculate(int availableWidth, int availableHeight, int anchorLeft, int anchorTop,
                                   int anchorWidth, int anchorHeight, int requestedWidth, int requestedHeight,
                                   int margin, int originSize) {
        int width = Math.max(1, Math.min(requestedWidth, availableWidth - 2 * margin));
        int height = Math.max(1, Math.min(requestedHeight, availableHeight - 2 * margin));
        int safeX = Math.max(0, Math.min(margin, (availableWidth - width) / 2));
        int safeY = Math.max(0, Math.min(margin, (availableHeight - height) / 2));
        int centerX = anchorLeft + anchorWidth / 2, centerY = anchorTop + anchorHeight / 2;
        int x = clamp(centerX - width + originSize / 2, safeX, Math.max(safeX, availableWidth - safeX - width));
        int y = centerY - originSize / 2;
        if (y + height > availableHeight - safeY) y = centerY - height + originSize / 2;
        y = clamp(y, safeY, Math.max(safeY, availableHeight - safeY - height));
        return new Bounds(x, y, width, height, clamp(centerX - x, 0, width), clamp(centerY - y, 0, height), Math.max(1, originSize));
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(value, max)); }
}
