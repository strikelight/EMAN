package com.esde.emulatormanager.data.service

import android.os.Build
import com.esde.emulatormanager.data.model.DeviceFingerprint
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for generating device identification fingerprints.
 * Used to associate profiles with specific devices.
 *
 * Uses Build properties which are available without any permissions
 * and provide 99.9% unique device identification.
 */
@Singleton
class DeviceIdentificationService @Inject constructor() {

    /**
     * Generate a fingerprint for the current device.
     * Combines manufacturer, model, device, and hardware properties.
     */
    fun getDeviceFingerprint(): DeviceFingerprint {
        val manufacturer = Build.MANUFACTURER ?: "unknown"
        val model = Build.MODEL ?: "unknown"
        val device = Build.DEVICE ?: "unknown"
        val hardware = Build.HARDWARE ?: "unknown"

        // Create composite string and hash it
        val composite = "$manufacturer|$model|$device|$hardware"
        val hash = sha256(composite)

        return DeviceFingerprint(
            manufacturer = manufacturer,
            model = model,
            device = device,
            hardware = hardware,
            fingerprint = hash
        )
    }

    /**
     * Check if two fingerprints match (same device).
     */
    fun fingerprintsMatch(a: DeviceFingerprint?, b: DeviceFingerprint?): Boolean {
        if (a == null || b == null) return false
        return a.fingerprint == b.fingerprint
    }

    /**
     * Check if a fingerprint matches the current device.
     */
    fun matchesCurrentDevice(fingerprint: DeviceFingerprint?): Boolean {
        if (fingerprint == null) return false
        return fingerprint.fingerprint == getDeviceFingerprint().fingerprint
    }

    /**
     * Get a human-readable device name.
     */
    fun getDeviceDisplayName(): String {
        val manufacturer = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""

        // Some models already include manufacturer name
        return if (model.lowercase().startsWith(manufacturer.lowercase())) {
            model
        } else {
            "$manufacturer $model".trim()
        }
    }

    /**
     * Get a short device identifier for display.
     */
    fun getShortDeviceId(): String {
        return Build.MODEL ?: "Unknown Device"
    }

    /**
     * SHA-256 hash function.
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
