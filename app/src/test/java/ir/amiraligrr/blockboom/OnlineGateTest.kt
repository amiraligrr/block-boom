package ir.amiraligrr.blockboom

import ir.amiraligrr.blockboom.data.GateStatus
import ir.amiraligrr.blockboom.data.OnlineGate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineGateTest {

    @Test
    fun noValidatedNetworkIsOffline() {
        assertEquals(
            GateStatus.OFFLINE,
            OnlineGate.classify(false, 200 to JSONObject().put("ok", true).put("healthy", true))
        )
    }

    @Test
    fun noResponseIsServerError() {
        assertEquals(GateStatus.SERVER_ERROR, OnlineGate.classify(true, null))
    }

    @Test
    fun httpFailureIsServerError() {
        assertEquals(
            GateStatus.SERVER_ERROR,
            OnlineGate.classify(true, 503 to JSONObject().put("ok", false))
        )
    }

    @Test
    fun healthyBodyIsOnline() {
        assertEquals(
            GateStatus.ONLINE,
            OnlineGate.classify(
                true,
                200 to JSONObject().put("ok", true).put("healthy", true)
            )
        )
    }

    @Test
    fun unhealthyBodyIsServerError() {
        assertEquals(
            GateStatus.SERVER_ERROR,
            OnlineGate.classify(
                true,
                200 to JSONObject().put("ok", true).put("healthy", false)
            )
        )
    }
}
