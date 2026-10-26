package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TopBarInputsTest {

    @Test public void anEntryIsALogoAndAnInputId() {
        TopBarInputs.Entry e = TopBarInputs.parse(" PlayStation | com.tcl.tvinput/.TvPassThroughService/HW15 ");
        assertEquals("playstation", e.logo);
        assertEquals("com.tcl.tvinput/.TvPassThroughService/HW15", e.inputId);
    }

    @Test public void anEntryWithoutBothPartsIsSkipped() {
        assertNull(TopBarInputs.parse("com.tcl.tvinput/.TvPassThroughService/HW15"));
        assertNull(TopBarInputs.parse("switch|"));
        assertNull(TopBarInputs.parse("|com.tcl.tvinput/.TvPassThroughService/HW16"));
        assertNull(TopBarInputs.parse(null));
    }

    @Test public void knownLogosHaveTheirOwnPictureAndName() {
        assertEquals(R.drawable.logo_playstation, TopBarInputs.logoRes("playstation"));
        assertEquals(R.drawable.logo_switch, TopBarInputs.logoRes("switch"));
        assertEquals(R.drawable.ic_input, TopBarInputs.logoRes("xbox"));
        assertEquals("Nintendo Switch", TopBarInputs.name("switch"));
        assertNull(TopBarInputs.name("xbox"));
    }

    @Test public void onlyAPictureCountsAsOn() {
        assertTrue(TopBarInputs.on(0));    // INPUT_STATE_CONNECTED
        assertFalse(TopBarInputs.on(1));   // INPUT_STATE_CONNECTED_STANDBY: a console resting
        assertFalse(TopBarInputs.on(2));   // INPUT_STATE_DISCONNECTED
    }
}
