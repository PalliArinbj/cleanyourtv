package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextClock;

/**
 * A TextClock in the launcher's language. TextClock formats in Locale.getDefault(), which Android can move back to
 * the TV's language at any time (AppLocale); this one puts the default back and formats again before it shows a
 * day, month or AM/PM in the wrong language. Every clock in the layouts is one of these.
 */
public class AppTextClock extends TextClock {

    public AppTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        // TextView's constructor sets text before TextClock can format: then only the default is put back.
        if (AppLocale.matchDefault(getContext()) && isAttachedToWindow()) {
            setFormat12Hour(getFormat12Hour());   // formats again, now in the launcher's language (refreshTime is API 30)
            return;
        }
        super.setText(text, type);
    }
}
