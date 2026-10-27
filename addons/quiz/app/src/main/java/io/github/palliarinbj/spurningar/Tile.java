package io.github.palliarinbj.spurningar;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Every focusable thing in the quiz: a rounded glass box that turns white with dark text under focus, as the
 * launcher's pills do, and takes the colours of the game (locked in, right, wrong, removed).
 */
final class Tile extends TextView {
    enum Look { NORMAL, LOCKED, RIGHT, WRONG, REMOVED, OFF }

    static final int WHITE = 0xFFFFFFFF;
    static final int DARK = 0xFF15111F;
    static final int GLASS = 0x1FFFFFFF;
    static final int AMBER = 0xFFF2B33D;
    static final int GREEN = 0xFF34C759;
    static final int RED = 0xFFFF453A;

    private final GradientDrawable shape = new GradientDrawable();
    private Look look = Look.NORMAL;

    Tile(Context context, float radiusDp) {
        super(context);
        float dp = context.getResources().getDisplayMetrics().density;
        shape.setCornerRadius(radiusDp * dp);
        setBackground(shape);
        setFocusable(true);
        setClickable(true);
        setGravity(Gravity.CENTER);
        setTypeface(context.getResources().getFont(R.font.outfit_medium));
        refresh();
    }

    Look look() {
        return look;
    }

    void setLook(Look look) {
        this.look = look;
        // The amber answer letter would vanish on the amber, green and red fills: plain text in the fill's colour.
        if (look != Look.NORMAL && getText() instanceof android.text.Spanned) setText(getText().toString());
        boolean usable = look != Look.REMOVED && look != Look.OFF;
        setFocusable(usable);
        setClickable(usable);
        refresh();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        refresh();
    }

    private void refresh() {
        int fill, text;
        float alpha = 1f;
        switch (look) {
            case LOCKED: fill = AMBER; text = DARK; break;
            case RIGHT: fill = GREEN; text = WHITE; break;
            case WRONG: fill = RED; text = WHITE; break;
            case REMOVED: fill = GLASS; text = WHITE; alpha = 0.18f; break;
            case OFF: fill = GLASS; text = WHITE; alpha = 0.4f; break;
            default:
                fill = isFocused() ? WHITE : GLASS;
                text = isFocused() ? DARK : WHITE;
        }
        shape.setColor(fill);
        setTextColor(text);
        setAlpha(alpha);
    }
}
