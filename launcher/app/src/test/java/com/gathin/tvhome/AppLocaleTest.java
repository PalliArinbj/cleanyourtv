package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AppLocaleTest {
    @Test public void aPerAppLanguageBecomesTheDefault() {
        // The TV in English, the launcher alone in Icelandic: the date has to follow the launcher.
        assertTrue(AppLocale.needsDefault(Locale.US, Locale.forLanguageTag("is")));
    }

    @Test public void theSameLanguageIsLeftAlone() {
        assertFalse(AppLocale.needsDefault(Locale.US, Locale.US));
        assertFalse(AppLocale.needsDefault(Locale.forLanguageTag("is"), Locale.forLanguageTag("is")));
    }

    @Test public void aRegionIsPartOfTheLanguage() {
        assertTrue(AppLocale.needsDefault(Locale.US, Locale.UK));
    }

    @Test public void noLanguageChangesNothing() {
        assertFalse(AppLocale.needsDefault(Locale.US, null));
    }

    @Test public void everyClockIsAnAppTextClock() throws IOException {
        // A plain TextClock names days in the TV's language again after a reboot (AppLocale).
        File[] folders = new File("src/main/res").listFiles((dir, name) -> name.startsWith("layout"));
        assertNotNull("run from launcher/app", folders);
        int clocks = 0;
        for (File folder : folders) {
            for (File layout : folder.listFiles()) {
                String xml = new String(Files.readAllBytes(layout.toPath()), StandardCharsets.UTF_8);
                assertFalse(layout.getPath(), xml.contains("<TextClock"));
                if (xml.contains("<com.gathin.tvhome.AppTextClock")) clocks++;
            }
        }
        assertTrue("no clocks found in the layouts", clocks > 0);
    }
}
