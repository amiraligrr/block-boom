package ir.amiraligrr.blockboom

import ir.amiraligrr.blockboom.data.TicketManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketManagerParseTest {

    @Test
    fun adminFlagAcceptsBooleanNumberAndString() {
        assertTrue(TicketManager.parseBool(true))
        assertTrue(TicketManager.parseBool(1))
        assertTrue(TicketManager.parseBool(1L))
        assertTrue(TicketManager.parseBool("1"))
        assertTrue(TicketManager.parseBool("true"))
        assertTrue(TicketManager.parseBool("TRUE"))
        assertTrue(TicketManager.parseBool("yes"))
    }

    @Test
    fun playerFlagIsNeverMistakenForAdmin() {
        assertFalse(TicketManager.parseBool(false))
        assertFalse(TicketManager.parseBool(0))
        assertFalse(TicketManager.parseBool(0L))
        assertFalse(TicketManager.parseBool("0"))
        assertFalse(TicketManager.parseBool("false"))
        assertFalse(TicketManager.parseBool("no"))
        assertFalse(TicketManager.parseBool(null))
    }
}
