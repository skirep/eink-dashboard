package com.eink.launcher.fold

import org.junit.Assert.*
import org.junit.Test

class FoldPolicyTest {
    @Test fun coverDisplayUsesCompactLayout() { assertFalse(FoldPolicy.expanded(390, false)) }
    @Test fun innerDisplayWithoutOemHingeApiStillExpands() { assertTrue(FoldPolicy.expanded(720, false)) }
    @Test fun hingeOverridesNarrowWindow() { assertTrue(FoldPolicy.expanded(540, true)) }
    @Test fun widthBoundaryAndRotation() {
        assertFalse(FoldPolicy.expanded(599, false))
        assertTrue(FoldPolicy.expanded(600, false))
        assertTrue(FoldPolicy.expanded(840, false))
        assertFalse(FoldPolicy.expanded(360, false))
    }
}
