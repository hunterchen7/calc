package com.calc.emulator

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Manages save state persistence for the emulator.
 * Handles saving/loading emulator state and ROM data to app storage.
 */
class StateManager private constructor(context: Context) {
    companion object {
        private const val TAG = "StateManager"

        @Volatile
        private var instance: StateManager? = null

        fun getInstance(context: Context): StateManager {
            return instance ?: synchronized(this) {
                instance ?: StateManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Descriptive error for state operations.
         */
        fun stateErrorDescription(code: Int): String {
            return when (code) {
                -100 -> "State persistence not available"
                -101 -> "Buffer too small"
                -102 -> "Invalid state file format"
                -103 -> "State file version mismatch"
                -104 -> "State was saved with a different ROM"
                -105 -> "State file is corrupted"
                else -> "Unknown error ($code)"
            }
        }
    }

    private val statesDirectory: File
    private val romsDirectory: File

    init {
        val appDir = context.filesDir

        statesDirectory = File(appDir, "SaveStates").apply {
            if (!exists()) mkdirs()
        }

        romsDirectory = File(appDir, "ROMs").apply {
            if (!exists()) mkdirs()
        }
    }

    // MARK: - ROM Hash

    /**
     * Compute SHA-256 hash of ROM data (truncated to 16 hex chars for filenames).
     */
    fun romHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        // Use first 8 bytes (16 hex chars) for reasonable uniqueness + short filenames
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }

    // MARK: - ROM Persistence

    /**
     * Get ROM file path for a hash.
     */
    private fun romFilePath(hash: String): File {
        return File(romsDirectory, "$hash.rom")
    }

    /**
     * Save ROM data to app storage (creates our own copy).
     */
    fun saveRom(data: ByteArray, hash: String): Boolean {
        val romPath = romFilePath(hash)

        // Skip if already saved
        if (romPath.exists()) {
            Log.i(TAG, "ROM already cached: $hash")
            return true
        }

        return try {
            romPath.writeBytes(data)
            Log.i(TAG, "Saved ROM copy: $hash (${data.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ROM: ${e.message}")
            false
        }
    }

    /**
     * Load ROM data from app storage.
     */
    fun loadRom(hash: String): ByteArray? {
        val romPath = romFilePath(hash)

        if (!romPath.exists()) {
            Log.i(TAG, "No cached ROM for hash $hash")
            return null
        }

        return try {
            val data = romPath.readBytes()
            Log.i(TAG, "Loaded cached ROM: $hash (${data.size} bytes)")
            data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ROM: ${e.message}")
            null
        }
    }

    /**
     * Check if ROM exists in app storage.
     */
    fun hasRom(hash: String): Boolean {
        return romFilePath(hash).exists()
    }

    // MARK: - State Persistence

    /**
     * Get state file path for a ROM hash.
     */
    private fun stateFilePath(romHash: String): File {
        return File(statesDirectory, "$romHash.state")
    }

    /**
     * Save current emulator state.
     */
    fun saveState(emulator: EmulatorBridge, romHash: String): Boolean {
        val statePath = stateFilePath(romHash)

        val stateData = emulator.saveState()
        if (stateData == null) {
            Log.e(TAG, "Failed to get state data from emulator")
            return false
        }

        return try {
            statePath.writeBytes(stateData)
            Log.i(TAG, "Saved state: ${statePath.name} (${stateData.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write state file: ${e.message}")
            false
        }
    }

    /**
     * Load saved state for a ROM.
     */
    fun loadState(emulator: EmulatorBridge, romHash: String): Boolean {
        val statePath = stateFilePath(romHash)

        if (!statePath.exists()) {
            Log.i(TAG, "No saved state for ROM hash $romHash")
            return false
        }

        return try {
            val stateData = statePath.readBytes()
            val result = emulator.loadState(stateData)

            if (result == 0) {
                Log.i(TAG, "Loaded state from ${statePath.name}")
                true
            } else {
                Log.e(TAG, "Failed to load state: error $result - ${stateErrorDescription(result)}")
                // Delete corrupted/incompatible state file
                statePath.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read state file: ${e.message}")
            false
        }
    }

    /**
     * Check if a saved state exists for a ROM.
     */
    fun hasState(romHash: String): Boolean {
        return stateFilePath(romHash).exists()
    }

    /**
     * Delete saved state for a ROM.
     */
    fun deleteState(romHash: String) {
        stateFilePath(romHash).delete()
        Log.i(TAG, "Deleted state for ROM hash $romHash")
    }
}
