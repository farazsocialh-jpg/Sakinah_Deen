package com.sakinah.deen.network

/**
 * Single source of truth for Ktor HTTP timeouts, shared by the Android (OkHttp) and iOS
 * (Darwin) engines so both platforms degrade identically on slow or stalled networks.
 */
object SakinahHttpTimeout {
    const val CONNECT_TIMEOUT_MS: Long = 15_000L
    const val REQUEST_TIMEOUT_MS: Long = 90_000L
    const val SOCKET_TIMEOUT_MS: Long = 90_000L
}
