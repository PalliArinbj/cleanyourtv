package com.gathin.tvhome;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;

import java.util.Locale;

/** top_bar_inputs: a console's HDMI input as a round top-bar button with its logo and whether it is on. */
final class TopBarInputs {
    static final String PLAYSTATION = "playstation";
    static final String SWITCH = "switch";
    /** The logo's alpha while the TV reports the input in standby. */
    static final int STANDBY_ALPHA = 110;

    private TopBarInputs() {}

    /** One "logo|input id" entry. */
    static final class Entry {
        final String logo;
        final String inputId;

        Entry(String logo, String inputId) {
            this.logo = logo;
            this.inputId = inputId;
        }
    }

    /** The entry, or null when it has no logo name or no input id. */
    static Entry parse(String entry) {
        if (entry == null) return null;
        int bar = entry.indexOf('|');
        if (bar < 0) return null;
        String logo = entry.substring(0, bar).trim().toLowerCase(Locale.ROOT);
        String id = entry.substring(bar + 1).trim();
        return logo.isEmpty() || id.isEmpty() ? null : new Entry(logo, id);
    }

    static int logoRes(String logo) {
        if (PLAYSTATION.equals(logo)) return R.drawable.logo_playstation;
        if (SWITCH.equals(logo)) return R.drawable.logo_switch;
        return R.drawable.ic_input;
    }

    /** The console's name for a known logo, for TalkBack; null means the input's own label. */
    static String name(String logo) {
        if (PLAYSTATION.equals(logo)) return "PlayStation";
        if (SWITCH.equals(logo)) return "Nintendo Switch";
        return null;
    }

    /** A picture on the input. CONNECTED_STANDBY (a console resting) and DISCONNECTED are both off. */
    static boolean on(int inputState) {
        return inputState == TvInputManager.INPUT_STATE_CONNECTED;
    }

    /**
     * The green "on" dot in the corner of the logo, ringed in the pill's dark so it reads on the logo too.
     * It ignores tints: the pill tints its icon white, and dark while focused, which would hide the green.
     */
    static final class Dot extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

        Dot(float ringWidth) {
            fill.setColor(0xFF34C759);
            ring.setColor(0xFF1C1C1E);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(ringWidth);
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            float r = Math.min(b.width(), b.height()) / 2f;
            float half = ring.getStrokeWidth() / 2f;
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r - half, fill);
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r - half, ring);
        }

        @Override public void setTintList(ColorStateList tint) {}
        @Override public void setTintMode(PorterDuff.Mode mode) {}
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
