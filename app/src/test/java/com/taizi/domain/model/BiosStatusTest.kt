package com.taizi.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BiosStatusTest {

    @Test
    fun `biosStatus has only implemented states`() {
        val values = BiosStatus.values().toList()
        assertEquals(listOf(BiosStatus.MISSING, BiosStatus.PRESENT), values)
    }
}
