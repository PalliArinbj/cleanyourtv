package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** The TV's own settings screens, for Home's pills and for the remote's Settings button in HomeKeyService. */
final class SystemIntents {
    private SystemIntents() { }

    /**
     * China-market MIUI TV firmware answers no standard Settings intents at all; open Xiaomi's own
     * settings app (com.xiaomi.mitv.settings) there, so the Settings and Wi-Fi pills keep working.
     * On firmware with a standard settings handler the behaviour is unchanged. When neither resolves,
     * the standard intent comes back as it is: the caller's ActivityNotFoundException handling decides.
     */
    static Intent settings(Context ctx, String standardAction) {
        Intent i = new Intent(standardAction);
        if (i.resolveActivity(ctx.getPackageManager()) != null) return i;
        i = new Intent().setComponent(new ComponentName(
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
        if (i.resolveActivity(ctx.getPackageManager()) != null) return i;
        return new Intent(standardAction);
    }
}
