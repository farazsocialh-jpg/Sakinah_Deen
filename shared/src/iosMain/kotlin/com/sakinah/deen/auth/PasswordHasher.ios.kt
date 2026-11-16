package com.sakinah.deen.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    if (size == 0) return bytes
    bytes.usePinned { pinned ->
        val status = SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
        check(status == 0) { "SecRandomCopyBytes failed with status $status" }
    }
    return bytes
}
