package com.eko.home

import org.junit.Assert.*
import org.junit.Test

class LauncherPolicyTest {
    @Test fun streamingAppsComeBeforeUnknownApps() {
        assertTrue(LauncherPolicy.rank("com.netflix.ninja") < LauncherPolicy.rank("vendor.settings"))
        assertEquals(Int.MAX_VALUE, LauncherPolicy.rank("unrecognized"))
    }
    @Test fun youtubeFollowsNetflix() {
        assertTrue(LauncherPolicy.rank("com.netflix.ninja") < LauncherPolicy.rank("com.google.android.youtube.tv"))
    }
    @Test fun staleMappingNeverSilentlySelectsAnotherPort() {
        assertNull(LauncherPolicy.resolveInput("HDMI_1", listOf("HDMI_2")))
        assertNull(LauncherPolicy.resolveInput(null, listOf("HDMI_2")))
        assertNull(LauncherPolicy.resolveInput("HDMI_1", emptyList()))
    }
    @Test fun onlyExactSavedInputIsResolved() {
        assertEquals("HDMI_2", LauncherPolicy.resolveInput("HDMI_2", listOf("HDMI_1", "HDMI_2")))
    }
}
