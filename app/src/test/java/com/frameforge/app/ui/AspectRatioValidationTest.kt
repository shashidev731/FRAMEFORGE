package com.frameforge.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AspectRatioValidationTest {
    @Test
    fun acceptsCommonAndCustomRatios() {
        listOf("9:16", "16:9", "1:1", "4:5", "3:2", "21:9").forEach {
            assertTrue("Expected valid ratio: $it", isValidAspectRatio(it))
        }
    }

    @Test
    fun rejectsMalformedOrUnsafeRatios() {
        listOf("", "Custom", "3", "3:2:1", "0:3", "3:0", "-1:2", "10001:2", "wide:tall").forEach {
            assertFalse("Expected invalid ratio: $it", isValidAspectRatio(it))
        }
    }
}
