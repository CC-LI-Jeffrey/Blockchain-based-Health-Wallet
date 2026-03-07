package com.fyp.blockchainhealthwallet.ui.partialshare

import android.content.Context
import com.fyp.blockchainhealthwallet.models.PartialSharePackage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Represents a QR-scanned partial share that has been saved locally.
 */
@Serializable
data class SavedQRShare(
    val id: Long,               // unique id (scan epoch ms)
    val scanTime: Long,         // epoch ms when scanned
    val isValid: Boolean,       // all proofs passed?
    val sharePackage: PartialSharePackage
)

/**
 * Persists scanned QR partial shares to SharedPreferences so they survive
 * navigation away from the scan screen.
 */
object ScannedQRShareRepository {

    private const val PREFS_NAME = "scanned_qr_shares_prefs"
    private const val KEY_SHARES = "saved_shares"

    private val json = Json { ignoreUnknownKeys = true }

    /** Save a newly scanned package. Kept in reverse-chronological order (newest first). */
    fun save(context: Context, pkg: PartialSharePackage, isValid: Boolean) {
        val existing = getAll(context).toMutableList()
        val newShare = SavedQRShare(
            id = System.currentTimeMillis(),
            scanTime = System.currentTimeMillis(),
            isValid = isValid,
            sharePackage = pkg
        )
        existing.add(0, newShare)
        persist(context, existing)
    }

    /** Retrieve all saved scans, newest first. */
    fun getAll(context: Context): List<SavedQRShare> {
        val raw = prefs(context).getString(KEY_SHARES, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Delete a single saved scan by id. */
    fun delete(context: Context, id: Long) {
        val updated = getAll(context).filter { it.id != id }
        persist(context, updated)
    }

    private fun persist(context: Context, shares: List<SavedQRShare>) {
        prefs(context).edit().putString(KEY_SHARES, json.encodeToString(shares)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
