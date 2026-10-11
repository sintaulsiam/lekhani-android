package com.lekhani.android.service

import org.junit.Assert.assertNotNull
import org.junit.Test

class LekhaniSpellCheckerServiceTest {

    @Test
    fun testSpellCheckerServiceCreation() {
        val service = LekhaniSpellCheckerService()
        assertNotNull(service)
    }
}
