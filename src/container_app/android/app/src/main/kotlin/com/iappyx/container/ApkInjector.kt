package com.iappyx.container

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * iappyxOS APK Injector — APK Signature Scheme v2
 *
 * Key requirement: resources.arsc must be 4-byte aligned in the ZIP
 * (targeting Android R+ / API 30+)
 */
class ApkInjector(private val keystoreAlias: String = "iappyx_signing_key") {

    companion object {
        private const val TAG = "iappyxOS"
        private const val MANIFEST_PATH = "AndroidManifest.xml"
        private const val LABEL_PLACEHOLDER = "IAPPYX_PLACEHOLDER_LABEL_XXXXXXXXXXXX"
        private const val TEMPLATE_PACKAGE   = "com.iappyx.generated.placeholder"
        private const val TEMPLATE_AUTHORITY = "com.iappyx.generated.placeholder.provider"
        private const val TEMPLATE_DYNPERM  = "com.iappyx.generated.placeholder.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        private const val TEMPLATE_MLKIT   = "com.iappyx.generated.placeholder.mlkitinitprovider"
        private const val TEMPLATE_STARTUP = "com.iappyx.generated.placeholder.androidx-startup"

        // These must be STORED (uncompressed) AND 4-byte aligned
        private val STORED_ALIGNED = setOf("AndroidManifest.xml", "resources.arsc")
        // These must be STORED but don't need alignment
        private val STORED_ONLY = setOf<String>()

        private val APK_SIG_BLOCK_MAGIC = byteArrayOf(
            0x41, 0x50, 0x4b, 0x20, 0x53, 0x69, 0x67, 0x20,
            0x42, 0x6c, 0x6f, 0x63, 0x6b, 0x20, 0x34, 0x32
        )
        private const val V2_BLOCK_ID = 0x7109871a
        private const val SIG_ALGO = 0x0103
        private const val CHUNK_SIZE = 1024 * 1024
    }

    fun inject(
        templateApk: File, outputApk: File,
        packageName: String, appLabel: String,
        assets: Map<String, ByteArray>,
        icons: Map<String, ByteArray> = emptyMap(),
    ) {
        log("=== Inject: $appLabel / $packageName ===")
        val entries = readApk(templateApk)
        log("Read ${entries.size} entries")

        entries[MANIFEST_PATH]?.let {
            entries[MANIFEST_PATH] = patchManifest(it, packageName, appLabel)
        } ?: throw IllegalStateException("No AndroidManifest.xml in template")

        for ((name, bytes) in assets) {
            entries["assets/app/$name"] = bytes
            log("Injected assets/app/$name (${bytes.size}b)")
        }

        for ((path, bytes) in icons) {
            if (entries.containsKey(path)) {
                entries[path] = bytes
                log("Replaced icon $path (${bytes.size}b)")
            } else {
                log("Icon path $path not found in template, skipping")
            }
        }

        entries.keys.removeAll { it.startsWith("META-INF/") }

        // Strip ML Kit native libs + models if the HTML doesn't use them
        val html = assets.values.firstOrNull()?.toString(Charsets.UTF_8) ?: ""
        val needsMLKit = html.contains("scanQR") || html.contains("scanText") || html.contains("classify") || html.contains("removeBackground")
        if (!needsMLKit) {
            val before = entries.size
            entries.keys.removeAll { key ->
                (key.startsWith("lib/") && (key.contains("mlkit") || key.contains("barhopper") || key.contains("xeno"))) ||
                key.startsWith("assets/mlkit_") || key.startsWith("assets/mlkit-") || key.contains("selfiesegmentation")
            }
            val removed = before - entries.size
            if (removed > 0) log("Stripped $removed ML Kit entries (not needed by this app)")
        }

        // Write APK with proper alignment
        val unsigned = File(outputApk.parent, "unsigned_${outputApk.name}")
        writeApkAligned(entries, unsigned)
        log("Unsigned: ${unsigned.length()/1024}KB")

        applyV2Signature(unsigned, outputApk)
        unsigned.delete()
        log("=== Done: ${outputApk.length()/1024}KB ===")
    }

    // ── ZIP read ──

    private fun readApk(apk: File): LinkedHashMap<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipFile(apk).use { zip ->
            for (e in zip.entries()) map[e.name] = zip.getInputStream(e).readBytes()
        }
        return map
    }

    // ── ZIP write with alignment ──
    // We write the ZIP manually to control alignment.
    // resources.arsc must start at a 4-byte aligned offset within the ZIP file.
    // The data offset = local file header size + preceding content.
    // Local file header size = 30 + filename.length + extra.length
    // We pad the extra field to achieve 4-byte alignment for the data.

    private fun writeApkAligned(entries: Map<String, ByteArray>, out: File) {
        val fos = FileOutputStream(out)
        try {
        val buf = ByteArrayOutputStream()

        data class LocalEntry(
            val name: String,
            val offset: Int,
            val compressedSize: Int,
            val uncompressedSize: Int,
            val crc: Long,
            val method: Int,
            val data: ByteArray,
        )

        val localEntries = mutableListOf<LocalEntry>()

        for ((name, bytes) in entries) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val needsAlignment = name in STORED_ALIGNED || name.endsWith(".so")

            // Store everything uncompressed to avoid deflate corruption
            // DEX, PNG etc are already compressed so gains are minimal
            val crc = CRC32().also { it.update(bytes) }.value
            val dataBytes = bytes
            val method = ZipEntry.STORED

            // Local file header is 30 bytes + nameBytes.size + extraBytes.size
            // Data starts at: currentOffset + 30 + nameBytes.size + extraBytes.size
            val currentOffset = buf.size()
            val headerBase = 30 + nameBytes.size
            val dataStart = currentOffset + headerBase // with 0 extra bytes

            var extraSize = 0
            if (needsAlignment) {
                // Pad extra field so data is 4-byte aligned
                val remainder = dataStart % 4
                if (remainder != 0) {
                    extraSize = 4 - remainder
                }
            }

            val extraBytes = ByteArray(extraSize) // zeros

            val offset = buf.size()

            // Write local file header
            buf.write(intLE(0x04034b50))     // signature
            buf.write(shortLE(20))            // version needed
            buf.write(shortLE(0))             // flags
            buf.write(shortLE(method))        // compression
            buf.write(shortLE(0))             // mod time
            buf.write(shortLE(0))             // mod date
            buf.write(intLE(crc.toInt()))     // CRC-32
            buf.write(intLE(dataBytes.size))  // compressed size
            buf.write(intLE(bytes.size))      // uncompressed size
            buf.write(shortLE(nameBytes.size))// filename length
            buf.write(shortLE(extraSize))     // extra length
            buf.write(nameBytes)
            buf.write(extraBytes)
            buf.write(dataBytes)

            localEntries.add(LocalEntry(name, offset, dataBytes.size, bytes.size, crc, method, bytes))
        }

        // Central directory
        val cdOffset = buf.size()
        for (e in localEntries) {
            val nameBytes = e.name.toByteArray(Charsets.UTF_8)
            buf.write(intLE(0x02014b50))         // signature
            buf.write(shortLE(20))                // version made by
            buf.write(shortLE(20))                // version needed
            buf.write(shortLE(0))                 // flags
            buf.write(shortLE(e.method))          // compression
            buf.write(shortLE(0))                 // mod time
            buf.write(shortLE(0))                 // mod date
            buf.write(intLE(e.crc.toInt()))       // CRC-32
            buf.write(intLE(e.compressedSize))    // compressed size
            buf.write(intLE(e.uncompressedSize))  // uncompressed size
            buf.write(shortLE(nameBytes.size))    // filename length
            buf.write(shortLE(0))                 // extra length
            buf.write(shortLE(0))                 // comment length
            buf.write(shortLE(0))                 // disk number start
            buf.write(shortLE(0))                 // internal attributes
            buf.write(intLE(0))                   // external attributes
            buf.write(intLE(e.offset))            // local header offset
            buf.write(nameBytes)
        }

        val cdSize = buf.size() - cdOffset

        // End of central directory
        buf.write(intLE(0x06054b50))             // signature
        buf.write(shortLE(0))                     // disk number
        buf.write(shortLE(0))                     // disk with CD
        buf.write(shortLE(localEntries.size))     // entries on disk
        buf.write(shortLE(localEntries.size))     // total entries
        buf.write(intLE(cdSize))                  // CD size
        buf.write(intLE(cdOffset))               // CD offset
        buf.write(shortLE(0))                     // comment length

        fos.write(buf.toByteArray())
        log("ZIP written: ${out.length()/1024}KB, ${localEntries.size} entries")
        } finally { fos.close() }

        verifyAlignment(out)
    }

    private fun verifyAlignment(apk: File) {
        ZipFile(apk).use { zip ->
            for (e in zip.entries()) {
                if (e.name == "resources.arsc") {
                    log("resources.arsc: method=${e.method} size=${e.size} csize=${e.compressedSize}")
                }
            }
        }
    }

    // ── Manifest patching ──

    private fun patchManifest(data: ByteArray, packageName: String, label: String): ByteArray {
        val authority = "$packageName.provider"
        val dynPerm = "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val mlkit = "$packageName.mlkitinitprovider"
        val startup = "$packageName.androidx-startup"
        var r = data
        // Order matters: patch longer strings first to avoid partial matches
        r = replaceAllUtf16(r, TEMPLATE_DYNPERM,   dynPerm)
        r = replaceAllUtf16(r, TEMPLATE_STARTUP,   startup)
        r = replaceAllUtf16(r, TEMPLATE_MLKIT,     mlkit)
        r = replaceAllUtf16(r, TEMPLATE_AUTHORITY, authority)
        r = replaceAllUtf16(r, TEMPLATE_PACKAGE,   packageName)
        r = replaceAllUtf16(r, LABEL_PLACEHOLDER,  label)
        return r
    }

    private fun replaceAllUtf16(data: ByteArray, placeholder: String, replacement: String): ByteArray {
        val ph = placeholder.toByteArray(Charsets.UTF_16LE)
        val rep = replacement.toByteArray(Charsets.UTF_16LE)
        if (rep.size > ph.size) throw IllegalArgumentException(
            "Replacement '$replacement' too long. Max ${placeholder.length} chars.")
        var result = data
        var count = 0
        var searchFrom = 0
        while (true) {
            val idx = indexOf(result, ph, searchFrom)
            if (idx == -1) break
            // Check this is an exact string pool entry:
            // 2 bytes before = length prefix matching placeholder length,
            // AND followed by 0x00 0x00 null terminator
            val lenLo = result[idx - 2].toInt() and 0xFF
            val lenHi = result[idx - 1].toInt() and 0xFF
            val storedLen = lenLo or (lenHi shl 8)
            val afterIdx = idx + ph.size
            val isExactMatch = storedLen == placeholder.length &&
                afterIdx + 1 < result.size &&
                result[afterIdx] == 0x00.toByte() &&
                result[afterIdx + 1] == 0x00.toByte()
            if (!isExactMatch) {
                // This is a substring match inside a longer string — skip it
                searchFrom = idx + ph.size
                continue
            }
            result = result.copyOf()
            result[idx - 2] = (replacement.length and 0xFF).toByte()
            result[idx - 1] = ((replacement.length shr 8) and 0xFF).toByte()
            rep.copyInto(result, idx)
            result[idx + rep.size] = 0x00
            result[idx + rep.size + 1] = 0x00
            count++
            searchFrom = idx + rep.size + 2
        }
        if (count > 0) log("Patched '$placeholder' → '$replacement' ($count occurrences)")
        else log("'$placeholder' not found")
        return result
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    // ── APK v2 signing ──

    private fun applyV2Signature(unsigned: File, signed: File) {
        val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        val privateKey = ks.getKey(keystoreAlias, null) as? PrivateKey
            ?: throw IllegalStateException("Key '$keystoreAlias' not found")
        val cert = ks.getCertificate(keystoreAlias) as X509Certificate

        val apk = unsigned.readBytes()
        val eocdOffset = findEocd(apk)
        val cdOffset = le32(apk, eocdOffset + 16)
        log("APK=${apk.size} EOCD=$eocdOffset CD=$cdOffset")

        val section1 = apk.sliceArray(0 until cdOffset)
        val section2 = apk.sliceArray(cdOffset until eocdOffset)
        val cdOffsetForDigest = section1.size
        val section3 = apk.sliceArray(eocdOffset until apk.size).copyOf().also { e ->
            e[16] = (cdOffsetForDigest and 0xFF).toByte()
            e[17] = ((cdOffsetForDigest shr 8) and 0xFF).toByte()
            e[18] = ((cdOffsetForDigest shr 16) and 0xFF).toByte()
            e[19] = ((cdOffsetForDigest shr 24) and 0xFF).toByte()
        }

        val topDigest = computeContentDigest(section1, section2, section3)
        log("Digest: ${topDigest.hex8()}")

        val digestEntry = i32le(SIG_ALGO) + lp(topDigest)
        val signedData = lpl(lp(digestEntry)) + lpl(lp(cert.encoded)) + lpl()

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(signedData)
        val sigBytes = sig.sign()

        val sigEntry = i32le(SIG_ALGO) + lp(sigBytes)
        val signer = lp(signedData) + lpl(lp(sigEntry)) + lp(cert.publicKey.encoded)
        val v2Value = lpl(lp(signer))

        val pairSize = 4L + v2Value.size
        val blockSize = 8L + pairSize + 8L + 16L

        val signingBlock = ByteArrayOutputStream().also { b ->
            b.write(i64le(blockSize))
            b.write(i64le(pairSize))
            b.write(i32le(V2_BLOCK_ID))
            b.write(v2Value)
            b.write(i64le(blockSize))
            b.write(APK_SIG_BLOCK_MAGIC)
        }.toByteArray()

        val newCdOffset = section1.size + signingBlock.size
        val newEocd = apk.sliceArray(eocdOffset until apk.size).copyOf()
        newEocd[16] = (newCdOffset and 0xFF).toByte()
        newEocd[17] = ((newCdOffset shr 8) and 0xFF).toByte()
        newEocd[18] = ((newCdOffset shr 16) and 0xFF).toByte()
        newEocd[19] = ((newCdOffset shr 24) and 0xFF).toByte()

        FileOutputStream(signed).use { out ->
            out.write(section1)
            out.write(signingBlock)
            out.write(section2)
            out.write(newEocd)
        }
        log("V2 signed: ${signed.length()/1024}KB")
    }

    private fun computeContentDigest(vararg sections: ByteArray): ByteArray {
        var totalChunks = 0
        for (s in sections) if (s.isNotEmpty()) totalChunks += (s.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        val topInput = ByteArray(1 + 4 + totalChunks * 32)
        topInput[0] = 0x5a
        i32le(totalChunks).copyInto(topInput, 1)
        var idx = 0
        for (section in sections) {
            if (section.isEmpty()) continue
            val n = (section.size + CHUNK_SIZE - 1) / CHUNK_SIZE
            for (i in 0 until n) {
                val start = i * CHUNK_SIZE
                val end = minOf(start + CHUNK_SIZE, section.size)
                val md = MessageDigest.getInstance("SHA-256")
                md.update(0xa5.toByte())
                md.update(i32le(end - start))
                md.update(section, start, end - start)
                md.digest().copyInto(topInput, 5 + idx * 32)
                idx++
            }
        }
        return sha256(topInput)
    }

    // ── ZIP helpers ──

    private fun findEocd(apk: ByteArray): Int {
        for (i in apk.size - 22 downTo maxOf(0, apk.size - 65557)) {
            if (apk[i] == 0x50.toByte() && apk[i+1] == 0x4b.toByte() &&
                apk[i+2] == 0x05.toByte() && apk[i+3] == 0x06.toByte()) return i
        }
        throw IllegalStateException("EOCD not found")
    }

    private fun le32(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or ((data[off+1].toInt() and 0xFF) shl 8) or
        ((data[off+2].toInt() and 0xFF) shl 16) or ((data[off+3].toInt() and 0xFF) shl 24)

    // ── Binary helpers ──

    private fun intLE(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun shortLE(v: Int): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
    private fun i32le(v: Int): ByteArray = intLE(v)
    private fun i64le(v: Long): ByteArray = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
    private fun lp(d: ByteArray): ByteArray = i32le(d.size) + d
    private fun lpl(vararg items: ByteArray): ByteArray {
        val c = items.fold(ByteArray(0)) { a, b -> a + b }
        return i32le(c.size) + c
    }
    private fun sha256(d: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(d)
    private operator fun ByteArray.plus(o: ByteArray): ByteArray {
        val r = copyOf(size + o.size); o.copyInto(r, size); return r
    }
    private fun ByteArray.hex8() = take(8).joinToString("") { "%02x".format(it) } + "..."
    private fun ByteArray.take(n: Int) = copyOf(minOf(n, size))
    private fun log(msg: String) = Log.i(TAG, msg)
}
