package com.fluid.dropx.core

import android.content.Context

/**
 * Remembers the port and session code of a sharing session that Android paused
 * (Android 15+ dataSync time limit), so "Resume" can bring back the SAME link.
 *
 * Cleared whenever the user stops the server on purpose or starts a fresh session.
 */
object SessionStore {
    private const val PREFS = "dropx_session"
    private const val K_PAUSED = "paused"
    private const val K_PORT = "port"
    private const val K_SESSION = "session"

    data class PausedSession(val port: Int, val sessionId: String)

    fun savePaused(context: Context, port: Int, sessionId: String) {
        prefs(context).edit()
            .putBoolean(K_PAUSED, true)
            .putInt(K_PORT, port)
            .putString(K_SESSION, sessionId)
            .apply()
    }

    fun loadPaused(context: Context): PausedSession? {
        val p = prefs(context)
        if (!p.getBoolean(K_PAUSED, false)) return null
        val session = p.getString(K_SESSION, null) ?: return null
        val port = p.getInt(K_PORT, 0)
        if (port <= 0) return null
        return PausedSession(port, session)
    }

    fun isPaused(context: Context): Boolean = loadPaused(context) != null

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}