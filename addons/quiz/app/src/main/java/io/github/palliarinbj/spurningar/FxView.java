package io.github.palliarinbj.spurningar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The effects layer over every screen: sparks from a right answer, shards from answers a helper removes, confetti
 * for a milestone or a place on the board. It draws only while something is flying and ignores the remote.
 */
final class FxView extends View {
    private static final class Bit {
        float x, y, vx, vy, spin, angle, size, life, age;
        int color;
        boolean square;
    }

    private static final int[] CONFETTI = {0xFFF2B33D, 0xFF34C759, 0xFF5AC8FA, 0xFFFF6482, 0xFFFFFFFF, 0xFFBF5AF2};

    private final List<Bit> bits = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float dp;
    private long last;

    FxView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
    }

    /** Sparks flying out from the middle of a view, in its colours. */
    void burst(View from, int[] colors, int count, float speedDp, boolean square) {
        int[] here = new int[2], there = new int[2];
        getLocationInWindow(here);
        from.getLocationInWindow(there);
        float cx = there[0] - here[0] + from.getWidth() / 2f, cy = there[1] - here[1] + from.getHeight() / 2f;
        for (int i = 0; i < count; i++) {
            Bit b = new Bit();
            b.x = cx + (random.nextFloat() - 0.5f) * from.getWidth() * 0.8f;
            b.y = cy + (random.nextFloat() - 0.5f) * from.getHeight() * 0.6f;
            double a = random.nextDouble() * Math.PI * 2;
            float v = speedDp * dp * (0.4f + random.nextFloat());
            b.vx = (float) Math.cos(a) * v;
            b.vy = (float) Math.sin(a) * v - speedDp * dp * 0.4f;
            b.size = (square ? 4 : 3) * dp * (0.6f + random.nextFloat());
            b.life = 0.7f + random.nextFloat() * 0.5f;
            b.color = colors[random.nextInt(colors.length)];
            b.square = square;
            b.spin = (random.nextFloat() - 0.5f) * 720;
            bits.add(b);
        }
        start();
    }

    /** Confetti falling from above the top edge across the whole width. */
    void confetti(int count) {
        for (int i = 0; i < count; i++) {
            Bit b = new Bit();
            b.x = random.nextFloat() * getWidth();
            b.y = -random.nextFloat() * getHeight() * 0.5f;
            b.vx = (random.nextFloat() - 0.5f) * 80 * dp;
            b.vy = (60 + random.nextFloat() * 120) * dp;
            b.size = (4 + random.nextFloat() * 4) * dp;
            b.life = 3.2f + random.nextFloat();
            b.color = CONFETTI[random.nextInt(CONFETTI.length)];
            b.square = true;
            b.spin = (random.nextFloat() - 0.5f) * 540;
            bits.add(b);
        }
        start();
    }

    void clear() {
        bits.clear();
        invalidate();
    }

    private void start() {
        last = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bits.isEmpty()) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        for (Iterator<Bit> it = bits.iterator(); it.hasNext(); ) {
            Bit b = it.next();
            b.age += dt;
            if (b.age >= b.life || b.y > getHeight() + 40 * dp) {
                it.remove();
                continue;
            }
            b.vy += (b.square ? 120 : 500) * dp * dt;   // confetti floats, sparks fall
            b.vx *= 1 - 0.8f * dt;
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.angle += b.spin * dt;
            float fade = Math.min(1f, (b.life - b.age) / 0.35f);
            paint.setColor(b.color);
            paint.setAlpha((int) (255 * fade));
            if (b.square) {
                canvas.save();
                canvas.rotate(b.angle, b.x, b.y);
                canvas.drawRect(b.x - b.size / 2, b.y - b.size / 4, b.x + b.size / 2, b.y + b.size / 4, paint);
                canvas.restore();
            } else {
                canvas.drawCircle(b.x, b.y, b.size, paint);
            }
        }
        if (!bits.isEmpty()) postInvalidateOnAnimation();
    }
}
