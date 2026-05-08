package com.fluid.dropx.core.session

import java.util.concurrent.ConcurrentHashMap

object SessionState {
    var sessionId: String? = null
    val maxClients = 3

    val clients = ConcurrentHashMap<String, ClientSession> ()

    var createdAt: Long = 0
    var lastActivity: Long = 0

    fun canAcceptNewClient(): Boolean {
        return clients.size < maxClients
    }

    fun initialize(id: String) {
        sessionId = id
        createdAt = System.currentTimeMillis()
        lastActivity = createdAt
        clients.clear()
    }

    fun getClient(clientId: String): ClientSession? = clients[clientId]

    fun performCleanup() {
        val now = System.currentTimeMillis()

        clients.values.removeIf { clientSession ->
            when (clientSession.state) {
                ClientState.CREATED -> {
                    (now - clientSession.createdAt) > 30_000
                }
                ClientState.HANDSHAKE_STARTED, ClientState.KEY_EXCHANGED -> {
                    (now - clientSession.lastSeen) > 30_000
                }

                ClientState.READY -> {
                    (now - clientSession.lastSeen) > 300_000
                }
            }
        }
    }

}