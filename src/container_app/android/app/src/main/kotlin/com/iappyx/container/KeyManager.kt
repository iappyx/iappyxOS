package com.iappyx.container

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Calendar

/**
 * iappyxOS Key Manager
 *
 * Manages the signing key used for all generated APKs.
 *
 * The key lives in the Android Keystore — a hardware-backed secure enclave
 * on modern devices. The raw private key bytes never leave the chip.
 * We sign data by passing it to the Keystore and getting back the signature.
 *
 * The same key is used for all generated apps, which means:
 * - Apps can be updated (same signature required for updates)
 * - All generated apps are "trusted" by the same signer
 * - If the container app is uninstalled, the key is lost and generated apps
 *   can no longer be updated (but still run)
 *
 * Key is generated once on first run and persists indefinitely.
 */
object KeyManager {

    private const val TAG = "iappyxOS"
    const val KEY_ALIAS = "iappyx_signing_key"
    private const val KEY_STORE = "AndroidKeyStore"

    /**
     * Ensure the signing key exists. Call this once on app startup.
     * If the key already exists, does nothing.
     * If not, generates a new RSA-2048 key pair in the secure enclave.
     */
    fun ensureKeyExists(context: Context) {
        val keyStore = KeyStore.getInstance(KEY_STORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            Log.i(TAG, "Signing key already exists")
            return
        }

        Log.i(TAG, "Generating new signing key...")
        generateKey()
        Log.i(TAG, "Signing key generated and stored in Android Keystore")
    }

    /**
     * Returns true if the signing key exists.
     */
    fun keyExists(): Boolean {
        val keyStore = KeyStore.getInstance(KEY_STORE)
        keyStore.load(null)
        return keyStore.containsAlias(KEY_ALIAS)
    }

    /**
     * Delete the signing key. Use with caution —
     * existing generated apps can no longer be updated after this.
     */
    fun deleteKey() {
        val keyStore = KeyStore.getInstance(KEY_STORE)
        keyStore.load(null)
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
            Log.i(TAG, "Signing key deleted")
        }
    }

    /**
     * Get the signing certificate for display (fingerprint, expiry).
     */
    fun getCertificateInfo(): String {
        val keyStore = KeyStore.getInstance(KEY_STORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) return "No key"

        val cert = keyStore.getCertificate(KEY_ALIAS)
        val fingerprint = cert.encoded.let {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.digest(it).joinToString(":") { b -> "%02X".format(b) }
        }

        return "SHA-256: ${fingerprint.take(29)}..."
    }

    /**
     * Check whether an installed app's signing certificate matches this device's key.
     * Returns: "not_installed", "same_signer", or "different_signer".
     */
    fun checkSignatureMatch(context: Context, packageName: String): String {
        val pm = context.packageManager
        val installedSigs = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                pi.signingInfo?.apkContentsSigners?.map { sigDigest(it.toByteArray()) } ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                pi.signatures?.map { sigDigest(it.toByteArray()) } ?: emptyList()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return "not_installed"
        }
        if (installedSigs.isEmpty()) return "not_installed"

        val keyStore = KeyStore.getInstance(KEY_STORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) return "different_signer"
        val localDigest = sigDigest(keyStore.getCertificate(KEY_ALIAS).encoded)

        return if (installedSigs.contains(localDigest)) "same_signer" else "different_signer"
    }

    private fun sigDigest(certBytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(certBytes)
            .joinToString(":") { "%02X".format(it) }
    }

    // ─────────────────────────────────────────────
    // Private
    // ─────────────────────────────────────────────

    private fun generateKey() {
        val validityEnd = Calendar.getInstance().apply {
            add(Calendar.YEAR, 25) // 25-year validity, same as typical app signing keys
        }

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setCertificateSubject(
                javax.security.auth.x500.X500Principal(
                    "CN=iappyxOS, O=iappyx, C=NL"
                )
            )
            .setCertificateSerialNumber(java.math.BigInteger.ONE)
            .setCertificateNotBefore(Calendar.getInstance().time)
            .setCertificateNotAfter(validityEnd.time)
            // Don't require user authentication — the key is used in background
            .setUserAuthenticationRequired(false)
            .build()

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            KEY_STORE
        )
        keyPairGenerator.initialize(keyGenSpec)
        keyPairGenerator.generateKeyPair()
    }
}
