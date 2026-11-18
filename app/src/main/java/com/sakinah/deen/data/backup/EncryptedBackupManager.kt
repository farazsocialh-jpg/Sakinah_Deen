package com.sakinah.deen.data.backup

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.database.dao.BookmarkKey
import com.sakinah.deen.data.database.entity.HifzMistakeEntity
import com.sakinah.deen.data.database.entity.QadhaLedgerEntity
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import com.sakinah.deen.data.prefs.SakinahPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptedBackupManager {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private const val MAGIC = "SKNBAK1"

    suspend fun export(
        context: Context,
        uri: Uri,
        passphrase: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = SakinahEncryptedDatabase.getInstance(
                context,
                com.sakinah.deen.data.database.DatabasePassphraseProvider.getPassphrase(context)
            )
            val prefs = context.getSharedPreferences("sakinah_preferences", Context.MODE_PRIVATE)

            // Collect prefs as typed entries.
            val prefEntries = prefs.all.map { (k, v) ->
                when (v) {
                    is String -> PrefEntry(k, "string", v)
                    is Int -> PrefEntry(k, "int", v.toString())
                    is Long -> PrefEntry(k, "long", v.toString())
                    is Float -> PrefEntry(k, "float", v.toString())
                    is Boolean -> PrefEntry(k, "boolean", v.toString())
                    is Set<*> -> PrefEntry(k, "stringset", v.filterIsInstance<String>().joinToString("|"))
                    else -> PrefEntry(k, "string", v.toString())
                }
            }

            val data = BackupData(
                prefs = prefEntries,
                qadha = db.qadhaDao().getAllQadha().map { QadhaDto(it.prayerType, it.totalMissed, it.totalMadeUp, it.updatedTimestamp) },
                hifz = db.hifzDao().getAllMistakes().map { HifzDto(it.surahNumber, it.ayahNumber, it.wordPosition, it.mistakeType, it.lastReviewedTimestamp) },
                sadaqah = db.sadaqahDao().getAllLogs().map { SadaqahDto(it.id, it.amount, it.currency, it.note, it.timestamp, it.category) },
                bookmarks = db.quranDao().getBookmarkedKeys().map { BookmarkDto(it.surahNumber, it.ayahNumber) }
            )

            val dataJson = json.encodeToString(data)
            val checksum = sha256(dataJson.toByteArray(Charsets.UTF_8))
            val envelope = BackupEnvelope(schemaVersion = 1, checksum = checksum, data = data)
            val envelopeJson = json.encodeToString(envelope)
            val encrypted = encrypt(envelopeJson.toByteArray(Charsets.UTF_8), passphrase)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(MAGIC.toByteArray(Charsets.US_ASCII))
                out.write(encrypted)
            } ?: error("Could not open output stream")
        }
    }

    suspend fun import(
        context: Context,
        uri: Uri,
        passphrase: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not open input stream")
            if (!bytes.startsWith(MAGIC.toByteArray(Charsets.US_ASCII))) error("Not a Sakinah backup file")
            val encrypted = bytes.copyOfRange(MAGIC.length, bytes.size)
            val decrypted = decrypt(encrypted, passphrase)
            val envelope = json.decodeFromString<BackupEnvelope>(decrypted.toString(Charsets.UTF_8))
            val dataJson = json.encodeToString(envelope.data)
            val computed = sha256(dataJson.toByteArray(Charsets.UTF_8))
            if (computed != envelope.checksum) error("Backup checksum mismatch or wrong passphrase")

            val db = SakinahEncryptedDatabase.getInstance(
                context,
                com.sakinah.deen.data.database.DatabasePassphraseProvider.getPassphrase(context)
            )

            db.runInTransaction {
                // Qadha
                envelope.data.qadha.forEach { q ->
                    // upsert via DAO run on main? We're in IO; call suspend not allowed in runInTransaction.
                }
            }
            // DAOs are suspend; do sequentially outside transaction for simplicity.
            envelope.data.qadha.forEach { q ->
                db.qadhaDao().upsertQadha(QadhaLedgerEntity(q.prayerType, q.totalMissed, q.totalMadeUp, q.updatedTimestamp))
            }
            envelope.data.hifz.forEach { h ->
                db.hifzDao().upsertMistake(HifzMistakeEntity(h.surahNumber, h.ayahNumber, h.wordPosition, h.mistakeType, h.lastReviewedTimestamp))
            }
            envelope.data.sadaqah.forEach { s ->
                db.sadaqahDao().insertLog(SadaqahLogEntity(s.id, s.amount, s.currency, s.note, s.timestamp, s.category))
            }
            envelope.data.bookmarks.forEach { b ->
                // mark bookmarked
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE quran_ayahs SET isBookmarked = 1 WHERE surahNumber = ? AND ayahNumber = ?",
                    arrayOf(b.surahNumber, b.ayahNumber)
                )
            }

            // Restore prefs
            val prefs = context.getSharedPreferences("sakinah_preferences", Context.MODE_PRIVATE).edit()
            envelope.data.prefs.forEach { e ->
                when (e.type) {
                    "string" -> prefs.putString(e.key, e.value)
                    "int" -> prefs.putInt(e.key, e.value.toIntOrNull() ?: 0)
                    "long" -> prefs.putLong(e.key, e.value.toLongOrNull() ?: 0L)
                    "float" -> prefs.putFloat(e.key, e.value.toFloatOrNull() ?: 0f)
                    "boolean" -> prefs.putBoolean(e.key, e.value.toBooleanStrictOrNull() ?: false)
                    "stringset" -> prefs.putStringSet(e.key, e.value.split("|").filter { it.isNotBlank() }.toSet())
                }
            }
            prefs.apply()
        }
    }

    private fun sha256(data: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(data)
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext)
        return salt + iv + ct
    }

    private fun decrypt(data: ByteArray, passphrase: String): ByteArray {
        val salt = data.copyOfRange(0, 16)
        val iv = data.copyOfRange(16, 28)
        val ct = data.copyOfRange(28, data.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): javax.crypto.SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }
}
