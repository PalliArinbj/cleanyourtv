package com.gathin.tvhome;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Days and months in the launcher's own language. With a per-app language (Android 13+, `cmd locale
 * set-app-locales com.gathin.tvhome --locales is`) Home loads its strings in it, but TextClock names days and
 * months in Locale.getDefault(), which follows the process. Once the process hosts an accessibility service
 * (HomeKeyService, InputKeyService) Android 14 keeps the process itself in the TV's language, and moves the default
 * back to it whenever it sends the process a configuration, which can come after the screen is built. After a
 * reboot the greeting was Icelandic and the date English. AppTextClock puts the default back before it shows the
 * time; the screensaver, a service, gets its views from localized().
 */
final class AppLocale {
    private AppLocale() { }

    /** Moves the default to the language ctx's resources were loaded in; true when it had to. */
    static boolean matchDefault(Context ctx) {
        LocaleList shown = ctx.getResources().getConfiguration().getLocales();
        if (shown.isEmpty() || !needsDefault(Locale.getDefault(), shown.get(0))) return false;
        LocaleList.setDefault(shown);
        return true;
    }

    /** ctx in the launcher's per-app language: a service's resources follow the process, in the TV's language. */
    static Context localized(Context ctx) {
        if (Build.VERSION.SDK_INT < 33) return ctx;   // per-app languages start with Android 13
        LocaleList app = ctx.getSystemService(LocaleManager.class).getApplicationLocales();
        if (app.isEmpty() || !needsDefault(ctx.getResources().getConfiguration().getLocales().get(0), app.get(0))) {
            return ctx;
        }
        Configuration language = new Configuration();
        language.fontScale = 0;   // only the language: 0 keeps the TV's text size
        language.setLocales(app);
        return ctx.createConfigurationContext(language);
    }

    /** True when the default differs from the language the resources were loaded in. */
    static boolean needsDefault(Locale current, Locale shown) {
        return shown != null && !shown.equals(current);
    }
}
