package app.switchboard.core

import org.json.JSONObject
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion

/** The logged-in account, kept in the app's private storage. */
data class StoredSession(val session: Session, val storeDir: String, val passphrase: String)

object SessionStore {
    private const val KEY = "session"

    fun load(): StoredSession? {
        val raw = Prefs.getString(KEY) ?: return null
        return try {
            val o = JSONObject(raw)
            StoredSession(
                Session(
                    accessToken = o.getString("access_token"),
                    refreshToken = o.optString("refresh_token").ifEmpty { null },
                    userId = o.getString("user_id"),
                    deviceId = o.getString("device_id"),
                    homeserverUrl = o.getString("homeserver_url"),
                    oauthData = o.optString("oauth_data").ifEmpty { null },
                    slidingSyncVersion = if (o.optString("sliding_sync") == "none") SlidingSyncVersion.NONE else SlidingSyncVersion.NATIVE,
                ),
                storeDir = o.getString("store_dir"),
                passphrase = o.getString("passphrase"),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(s: StoredSession) {
        val o = JSONObject()
            .put("access_token", s.session.accessToken)
            .put("refresh_token", s.session.refreshToken ?: "")
            .put("user_id", s.session.userId)
            .put("device_id", s.session.deviceId)
            .put("homeserver_url", s.session.homeserverUrl)
            .put("oauth_data", s.session.oauthData ?: "")
            .put("sliding_sync", if (s.session.slidingSyncVersion == SlidingSyncVersion.NONE) "none" else "native")
            .put("store_dir", s.storeDir)
            .put("passphrase", s.passphrase)
        Prefs.putString(KEY, o.toString())
    }

    fun clear() = Prefs.putString(KEY, null)
}
