package com.watchpath.app.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lightweight "shimmer" loading placeholder: a stack of skeleton cards with a
 * highlight sweeping across them.
 *
 * Everything is drawn on the Canvas - no third-party shimmer library - which
 * mirrors the hand-rolled approach used by {@code RatingRingView}.
 */
public class ShimmerView extends View {

    private static final long SWEEP_DURATION_MS = 1400L;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix sweepMatrix = new Matrix();
    private final Path barsPath = new Path();
    private final RectF rect = new RectF();

    private float sweepProgress = 0f;
    private int gradientWidth = 0;

    private ValueAnimator animator;

    public ShimmerView(Context context) {
        this(context, null);
    }

    public ShimmerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ShimmerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        barPaint.setColor(0xFF2A2A3A);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float sidePad = dp(16);
        float barHeight = dp(96);
        float gap = dp(12);
        float radius = dp(10);

        int rows = Math.max(1, (int) ((h - sidePad) / (barHeight + gap)));

        barsPath.reset();
        for (int i = 0; i < rows; i++) {
            float top = sidePad + i * (barHeight + gap);
            rect.set(sidePad, top, w - sidePad, top + barHeight);
            canvas.drawRoundRect(rect, radius, radius, barPaint);
            barsPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        }

        // Sweeping highlight, clipped to the skeleton cards.
        ensureSweepGradient(w);
        sweepMatrix.setTranslate(-gradientWidth + sweepProgress * (w + gradientWidth), 0);
        sweepPaint.getShader().setLocalMatrix(sweepMatrix);

        int save = canvas.save();
        canvas.clipPath(barsPath);
        canvas.drawRect(0, 0, w, h, sweepPaint);
        canvas.restoreToCount(save);
    }

    /** Builds a soft white band whose width is a fixed fraction of the view. */
    private void ensureSweepGradient(int width) {
        int band = Math.max(1, (int) (width * 0.6f));
        if (sweepPaint.getShader() != null && band == gradientWidth) return;

        gradientWidth = band;
        sweepPaint.setShader(new LinearGradient(
                0, 0, band, 0,
                new int[]{0x00FFFFFF, 0x40FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
    }

    private void startShimmer() {
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SWEEP_DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            sweepProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopShimmer() {
        if (animator == null) return;
        animator.cancel();
        animator = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) startShimmer();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopShimmer();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) startShimmer();
        else stopShimmer();
    }
}
