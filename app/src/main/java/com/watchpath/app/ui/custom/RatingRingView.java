package com.watchpath.app.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.watchpath.app.R;

import java.util.Locale;

/**
 * Custom View that draws a TMDB-style circular rating ring (0-10).
 * Pure Canvas + Paint - no images, no libraries.
 */
public class RatingRingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcBounds = new RectF();

    private float rating = -1f; // -1 = "no rating" -> shows N/A

    // Both constructors are required so the view can be inflated from XML.
    public RatingRingView(Context context) {
        super(context);
        init();
    }

    public RatingRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        float stroke = 6f * density;

        int themeColor = resolveThemeColor(android.R.attr.textColorPrimary);

        // 1. Track: the full grey circle behind the arc.
        //    Theme text color at low alpha -> looks right in light AND dark theme.
        trackPaint.setColor(themeColor);
        trackPaint.setAlpha(60);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        // 2. Progress: the colored arc.
        progressPaint.setColor(ContextCompat.getColor(getContext(), R.color.brand_accent));
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        // 3. Text: the score number in the middle.
        textPaint.setColor(themeColor);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(14f * density);
    }

    /** Call with the TMDB rating (0-10). Values outside the range are clamped. */
    public void setRating(float value) {
        if (value < 0f) value = 0f;
        if (value > 10f) value = 10f;
        this.rating = value;
        invalidate(); // request a redraw with the new value
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // A ring is round -> force the view to a perfect square.
        int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;
        float inset = trackPaint.getStrokeWidth() / 2f + 2f * density;
        arcBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);

        // 1. Background track (full circle).
        canvas.drawOval(arcBounds, trackPaint);

        // 2. Score text, centered both ways.
        String label = rating > 0f
                ? String.format(Locale.US, "%.1f", rating)
                : "N/A";
        float baseline = getHeight() / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(label, getWidth() / 2f, baseline, textPaint);

        // 3. Progress arc, starting at 12 o'clock (-90°), sweeping clockwise.
        if (rating > 0f) {
            float sweep = rating / 10f * 360f;
            canvas.drawArc(arcBounds, -90f, sweep, false, progressPaint);
        }
    }

    /**
     * Reads a color from the current theme (light + dark both work).
     * TypedArray.getColor() also resolves color-state-list references,
     * which a raw TypedValue would return as a resource ID instead of a color.
     */
    private int resolveThemeColor(int attr) {
        TypedArray ta = getContext().getTheme().obtainStyledAttributes(new int[]{attr});
        int color = ta.getColor(0, Color.GRAY);
        ta.recycle();
        return color;
    }
}