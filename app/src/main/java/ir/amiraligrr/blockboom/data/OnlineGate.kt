package ir.amiraligrr.blockboom.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The startup connection gate.
 *
 * Wi-Fi being "on" is not proof that Block Boom can reach its own service. The
 * player needs a real answer from the API before the home screen is allowed to
 * present an account, progress or a leaderboard as synced.
 *
 * The check is intentionally three-state: a missing network and a failing
 * server are different problems, and the screen says which one it is. Nothing
 * here claims success without a 2xx health response.
 */
enum class GateStatus {
    CHECKING,
    ONLINE,
    OFFLINE,
    SERVER_ERROR,
}

object OnlineGate {

    /**
     * Pure classification, used by the unit test and by [check].
     *
     * @param networkAvailable validated internet capability, not merely Wi-Fi.
     * @param response HTTP status and body, or null when no response arrived.
     */
    fun classify(networkAvailable: Boolean, response: Pair<Int, JSONObject?>?): GateStatus {
        if (!networkAvailable) return GateStatus.OFFLINE
        if (response == null) return GateStatus.SERVER_ERROR
        val (code, body) = response
        if (code !in 200..299) return GateStatus.SERVER_ERROR
        if (body == null) return GateStatus.SERVER_ERROR
        return if (body.optBoolean("ok", false) && body.optBoolean("healthy", false)) {
            GateStatus.ONLINE
        } else {
            GateStatus.SERVER_ERROR
        }
    }

    suspend fun check(context: Context): GateStatus = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        // Do NOT require NET_CAPABILITY_VALIDATED. Android validates that
        // capability against international connectivity checks, which can be
        // blocked in Iran even when the domestic network and our own API are
        // perfectly reachable. The real proof is the health response below.
        val networkAvailable =
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!networkAvailable) return@withContext GateStatus.OFFLINE

        val url = "${LeaderboardManager.apiBase()}/health.php"
        val response = HttpJson.getJson(url, connectMs = 5000, readMs = 5000)
        classify(true, response)
    }
}
