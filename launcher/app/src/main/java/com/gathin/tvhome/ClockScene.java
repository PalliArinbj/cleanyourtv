package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import java.util.List;
import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow, with a tidbit from the theme's trivia.txt under the date. Every minute the clock drifts to a new spot and
 * the glow shifts colour, so nothing burns in; every trivia_minutes the tidbit changes as the clock moves.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};
    /** The grain tile's side in pixels, and its strongest alpha: up to 2 levels of white, about two banding steps. */
    static final int GRAIN_SIZE = 128;
    static final int GRAIN_MAX_ALPHA = 2;

    /** Minutes between tidbits when the theme's trivia_minutes is 0, which on Home means "only on return". */
    static final int DEFAULT_TRIVIA_MINUTES = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final GradientDrawable glow;
    /** The theme's trivia (Trivia), shown under the date and drawn from Home's deck; empty hides the line. */
    private final List<String> trivia;
    private final TextView triviaView;
    private final int triviaEvery;
    private int minutesSinceTrivia;
    private int colorIndex;
    private int glowColor;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            minutesSinceTrivia++;
            moveClock(!trivia.isEmpty() && minutesSinceTrivia >= triviaEvery);
            shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout; trivia and triviaMinutes come from the theme (Config). */
    ClockScene(View root, List<String> trivia, int triviaMinutes) {
        this.trivia = trivia;
        triviaEvery = triviaEvery(triviaMinutes);
        triviaView = root.findViewById(R.id.dreamTrivia);
        content = root.findViewById(R.id.dreamContent);
        View ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        // The glow fades from a quarter of a deep colour to nothing across half the screen: a few dozen steps per
        // channel, so it bands into rings on a big panel. A fixed, faint grain over it hides the steps.
        Bitmap grain = Bitmap.createBitmap(grainPixels(GRAIN_SIZE, GRAIN_MAX_ALPHA, 1L), GRAIN_SIZE, GRAIN_SIZE,
                Bitmap.Config.ARGB_8888);
        BitmapDrawable grainTile = new BitmapDrawable(root.getResources(), grain);
        grainTile.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        ambient.setBackground(new LayerDrawable(new Drawable[]{glow, grainTile}));
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    void start() {
        content.setAlpha(0f);
        if (!trivia.isEmpty()) nextTrivia();   // a new tidbit each time the screensaver starts
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
    }

    void stop() {
        handler.removeCallbacks(tick);
    }

    /** Fades the clock out and in somewhere new; a new tidbit, when due, swaps in while it is invisible. */
    private void moveClock(boolean newTrivia) {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            if (newTrivia) nextTrivia();
            // Posted, so it runs after the layout pass and places the clock by its width with the new tidbit.
            content.post(() -> {
                placeClock();
                content.animate().alpha(1f).setDuration(1200).start();
            });
        }).start();
    }

    private void nextTrivia() {
        triviaView.setText(Trivia.next(content.getContext(), trivia));
        triviaView.setVisibility(View.VISIBLE);
        minutesSinceTrivia = 0;
    }

    static int triviaEvery(int triviaMinutes) {
        return triviaMinutes > 0 ? triviaMinutes : DEFAULT_TRIVIA_MINUTES;
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    /**
     * White pixels at random alpha 0..maxAlpha: drawn over the glow they add 0 to maxAlpha levels per pixel, about
     * one banding step, so each ring edge dissolves into noise. Seeded, so the tile is the same every time.
     */
    static int[] grainPixels(int size, int maxAlpha, long seed) {
        Random r = new Random(seed);
        int[] pixels = new int[size * size];
        for (int i = 0; i < pixels.length; i++) pixels[i] = (r.nextInt(maxAlpha + 1) << 24) | 0x00FFFFFF;
        return pixels;
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
