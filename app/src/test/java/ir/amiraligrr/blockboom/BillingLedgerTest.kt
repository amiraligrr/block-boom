package ir.amiraligrr.blockboom

import android.content.Context
import ir.amiraligrr.blockboom.data.BillingLedger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The local duplicate-delivery guard must be idempotent for a token. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillingLedgerTest {

    @Test
    fun `a purchase token is delivered exactly once`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val token = "ledger-test-${System.nanoTime()}"

        assertFalse("a fresh token must not be marked delivered", BillingLedger.isDelivered(context, token))
        assertTrue("the first delivery claims the token", BillingLedger.markDelivered(context, token))
        assertTrue("the token is visible after delivery", BillingLedger.isDelivered(context, token))
        assertFalse("a replay must not claim the token twice", BillingLedger.markDelivered(context, token))
    }

    @Test
    fun `blank tokens are ignored`() {
        val context: Context = RuntimeEnvironment.getApplication()
        assertFalse(BillingLedger.markDelivered(context, ""))
        assertFalse(BillingLedger.isDelivered(context, ""))
    }
}
