/*
 * MIT License
 *
 * Copyright (c) 2026 iappyx
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

// MethodChannel dispatcher — routes Flutter calls to AppGenerator, ApkInstaller, etc.

package com.iappyx.container

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class MainActivity : FlutterActivity() {

    companion object {
        private const val CHANNEL = "com.iappyx.container/generator"
    }

    private var speechResult: MethodChannel.Result? = null
    private val SPEECH_REQUEST_CODE = 9999

    private lateinit var generator: AppGenerator
    private lateinit var p2p: P2PService
    private val scope = CoroutineScope(Dispatchers.Main)

    // Preview bridge: TTS + Audio
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsInitializing = false
    private val ttsPendingActions = mutableListOf<(TextToSpeech) -> Unit>()
    private var mediaPlayer: MediaPlayer? = null

    override fun onDestroy() {
        scope.cancel()
        p2p.destroy()
        tts?.shutdown()
        mediaPlayer?.release()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeyManager.ensureKeyExists(this)
        generator = AppGenerator(this)
        generator.cleanWorkDir()
        p2p = P2PService(this)
        p2p.init()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        p2p.onPermissionsResult(requestCode)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        setupP2PChannel(flutterEngine)
        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "generateApp" -> {
                    val label = call.argument<String>("label") ?: ""
                    val templateId = call.argument<String>("templateId") ?: "todo"
                    val packageName = call.argument<String>("packageName")
                    val iconConfig = call.argument<String>("iconConfig")
                    if (label.isBlank()) { result.error("INVALID", "label required", null); return@setMethodCallHandler }
                    scope.launch {
                        try {
                            val info = generator.generateAndInstall(
                                label, templateId, packageName, iconConfig,
                            ) { msg ->
                                channel.invokeMethod("onProgress", msg)
                            }
                            result.success(info)
                        } catch (e: Exception) {
                            result.error("FAILED", e.message, null)
                        }
                    }
                }
                "injectHtml" -> {
                    val label = call.argument<String>("label") ?: ""
                    val html = call.argument<String>("html") ?: ""
                    val packageName = call.argument<String>("packageName")
                    val iconConfig = call.argument<String>("iconConfig")
                    val firebaseConfig = call.argument<String>("firebaseConfig")
                    val webOnly = call.argument<Boolean>("webOnly") ?: false
                    if (label.isBlank()) { result.error("INVALID", "label required", null); return@setMethodCallHandler }
                    if (html.isBlank()) { result.error("INVALID", "html required", null); return@setMethodCallHandler }
                    scope.launch {
                        try {
                            val info = generator.injectAndInstall(
                                label, html, packageName, iconConfig, firebaseConfig, webOnly,
                            ) { msg ->
                                channel.invokeMethod("onProgress", msg)
                            }
                            result.success(info)
                        } catch (e: Exception) {
                            result.error("FAILED", e.message, null)
                        }
                    }
                }
                "shareFile" -> {
                    val path = call.argument<String>("path") ?: ""
                    val mimeType = call.argument<String>("mimeType") ?: "application/octet-stream"
                    if (path.isBlank()) { result.error("INVALID", "path required", null); return@setMethodCallHandler }
                    try {
                        val file = File(path)
                        if (!file.exists()) { result.error("NOT_FOUND", "File not found", null); return@setMethodCallHandler }
                        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, "Share"))
                        result.success(null)
                    } catch (e: Exception) {
                        result.error("FAILED", e.message, null)
                    }
                }
                "shareText" -> {
                    val content = call.argument<String>("content") ?: ""
                    val filename = call.argument<String>("filename") ?: "app.html"
                    if (content.isBlank()) { result.error("INVALID", "content required", null); return@setMethodCallHandler }
                    try {
                        val dir = File(cacheDir, "share")
                        dir.mkdirs()
                        val file = File(dir, filename)
                        file.writeText(content)
                        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                        val mime = when {
                            filename.endsWith(".json") -> "application/json"
                            filename.endsWith(".txt") -> "text/plain"
                            filename.endsWith(".csv") -> "text/csv"
                            filename.endsWith(".xml") -> "application/xml"
                            else -> "text/html"
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, "Share"))
                        result.success(null)
                    } catch (e: Exception) {
                        result.error("FAILED", e.message, null)
                    }
                }
                "launchApp" -> {
                    val pkg = call.argument<String>("packageName") ?: ""
                    if (pkg.isBlank()) { result.error("INVALID", "packageName required", null); return@setMethodCallHandler }
                    try {
                        val intent = packageManager.getLaunchIntentForPackage(pkg)
                        if (intent != null) {
                            startActivity(intent)
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                    } catch (e: Exception) {
                        result.error("FAILED", e.message, null)
                    }
                }
                "uninstallApp" -> {
                    val pkg = call.argument<String>("packageName") ?: ""
                    if (pkg.isBlank()) { result.error("INVALID", "packageName required", null); return@setMethodCallHandler }
                    try {
                        val intent = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg"))
                        startActivity(intent)
                        result.success(null)
                    } catch (e: Exception) {
                        result.error("FAILED", e.message, null)
                    }
                }
                "openUrl" -> {
                    val url = call.argument<String>("url") ?: ""
                    if (url.isBlank()) { result.error("INVALID", "url required", null); return@setMethodCallHandler }
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        startActivity(intent)
                        result.success(null)
                    } catch (e: Exception) {
                        result.error("FAILED", e.message, null)
                    }
                }
                "getKeyInfo" -> {
                    try {
                        val info = mapOf(
                            "exists" to KeyManager.keyExists(),
                            "fingerprint" to KeyManager.getCertificateInfo(),
                        )
                        result.success(info)
                    } catch (e: Exception) {
                        result.error("FAILED", e.message, null)
                    }
                }
                // ── Preview bridge: TTS ──
                "ttsSpeak" -> {
                    val text = call.arguments as? String ?: ""
                    ensureTts { it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview") }
                    result.success(null)
                }
                "ttsStop" -> {
                    tts?.stop()
                    result.success(null)
                }
                "ttsSetLanguage" -> {
                    val lang = call.arguments as? String ?: "en"
                    ensureTts { it.language = Locale.forLanguageTag(lang) }
                    result.success(null)
                }
                "ttsSetPitch" -> {
                    val pitch = (call.arguments as? String)?.toFloatOrNull() ?: 1.0f
                    ensureTts { it.setPitch(pitch) }
                    result.success(null)
                }
                "ttsSetRate" -> {
                    val rate = (call.arguments as? String)?.toFloatOrNull() ?: 1.0f
                    ensureTts { it.setSpeechRate(rate) }
                    result.success(null)
                }

                // ── Preview bridge: Audio ──
                "audioPlay" -> {
                    val url = call.arguments as? String ?: ""
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = null
                        val mp = MediaPlayer()
                        try {
                            mp.setDataSource(url)
                            mp.prepareAsync()
                            mp.setOnPreparedListener { it.start() }
                            mediaPlayer = mp
                        } catch (e: Exception) {
                            mp.release()
                        }
                    } catch (e: Exception) { /* ignore */ }
                    result.success(null)
                }
                "audioPause" -> {
                    try { mediaPlayer?.pause() } catch (_: Exception) {}
                    result.success(null)
                }
                "audioResume" -> {
                    try { mediaPlayer?.start() } catch (_: Exception) {}
                    result.success(null)
                }
                "audioStop" -> {
                    try { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null } catch (_: Exception) {}
                    result.success(null)
                }
                "audioSeekTo" -> {
                    val ms = (call.arguments as? Number)?.toInt() ?: 0
                    try { mediaPlayer?.seekTo(ms) } catch (_: Exception) {}
                    result.success(null)
                }
                "audioSetVolume" -> {
                    val v = (call.arguments as? Number)?.toFloat() ?: 1.0f
                    try { mediaPlayer?.setVolume(v, v) } catch (_: Exception) {}
                    result.success(null)
                }
                "audioSetLooping" -> {
                    val loop = call.arguments as? Boolean ?: false
                    try { mediaPlayer?.isLooping = loop } catch (_: Exception) {}
                    result.success(null)
                }

                "checkSignature" -> {
                    val pkg = call.argument<String>("packageName") ?: ""
                    if (pkg.isBlank()) { result.error("INVALID", "packageName required", null); return@setMethodCallHandler }
                    try {
                        val status = KeyManager.checkSignatureMatch(this, pkg)
                        result.success(status)
                    } catch (e: Exception) {
                        result.error("FAILED", e.message, null)
                    }
                }
                "speechToText" -> {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your app...")
                        }
                        speechResult = result
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, SPEECH_REQUEST_CODE)
                    } catch (e: Exception) {
                        result.error("FAILED", e.message, null)
                    }
                }
                "keepScreenOn" -> {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    result.success(null)
                }
                "releaseScreenOn" -> {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    result.success(null)
                }
                "getInstalledApkPath" -> {
                    val pkg = call.argument<String>("packageName") ?: ""
                    if (pkg.isBlank()) { result.error("INVALID", "packageName required", null); return@setMethodCallHandler }
                    scope.launch {
                        try {
                            val ai = packageManager.getApplicationInfo(pkg, 0)
                            val src = File(ai.sourceDir)
                            val workDir = File(filesDir, "iappyxos_work")
                            workDir.mkdirs()
                            val dest = File(workDir, "${pkg}.apk")
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                src.copyTo(dest, overwrite = true)
                            }
                            result.success(dest.absolutePath)
                        } catch (e: Exception) {
                            result.success(null)
                        }
                    }
                }

                else -> result.notImplemented()
            }
        }
    }

    private fun setupP2PChannel(flutterEngine: FlutterEngine) {
        val p2pChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.iappyx.container/p2p")
        p2pChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "p2pStartSharing" -> {
                    val apkPath = call.argument<String>("apkPath") ?: ""
                    val appName = call.argument<String>("appName") ?: ""
                    val appSize = call.argument<Number>("appSize")?.toLong() ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    val metadata = call.argument<Map<String, String>>("metadata")
                    if (apkPath.isBlank()) { result.error("INVALID", "apkPath required", null); return@setMethodCallHandler }
                    p2p.onStatusChanged = { status ->
                        scope.launch { p2pChannel.invokeMethod("p2pStatus", status) }
                    }
                    p2p.startSharing(apkPath, appName, appSize, metadata) { ok, error ->
                        if (ok) result.success(mapOf("ok" to true))
                        else result.error("FAILED", error, null)
                    }
                }
                "p2pStopSharing" -> { p2p.stopSharing(); result.success(null) }
                "p2pDiscover" -> {
                    p2p.onPeersChanged = { peers ->
                        scope.launch { p2pChannel.invokeMethod("p2pPeers", peers) }
                    }
                    p2p.discoverPeers { ok, error ->
                        if (ok) result.success(mapOf("ok" to true))
                        else result.error("FAILED", error, null)
                    }
                }
                "p2pStopDiscovery" -> { p2p.stopDiscovery(); result.success(null) }
                "p2pConnect" -> {
                    val address = call.argument<String>("address") ?: ""
                    if (address.isBlank()) { result.error("INVALID", "address required", null); return@setMethodCallHandler }
                    p2p.onConnectionChanged = { info ->
                        if (info != null && info.groupFormed) {
                            scope.launch {
                                p2pChannel.invokeMethod("p2pConnected", mapOf(
                                    "isGroupOwner" to info.isGroupOwner,
                                    "groupOwnerAddress" to (info.groupOwnerAddress?.hostAddress ?: ""),
                                ))
                            }
                        }
                    }
                    p2p.connectToPeer(address) { ok, error ->
                        if (ok) result.success(mapOf("ok" to true))
                        else result.error("FAILED", error, null)
                    }
                }
                "p2pDownload" -> {
                    val hostIp = call.argument<String>("hostIp") ?: ""
                    if (hostIp.isBlank()) { result.error("INVALID", "hostIp required", null); return@setMethodCallHandler }
                    val workDir = File(filesDir, "iappyxos_work")
                    workDir.mkdirs()
                    val destPath = File(workDir, "p2p_received_${System.currentTimeMillis()}.apk").absolutePath
                    p2p.downloadApk(hostIp, destPath,
                        onProgress = { pct, downloaded, total ->
                            scope.launch { p2pChannel.invokeMethod("p2pProgress", mapOf("pct" to pct, "downloaded" to downloaded, "total" to total)) }
                        },
                        onDone = { ok, error, infoJson ->
                            scope.launch {
                                if (ok) p2pChannel.invokeMethod("p2pDownloadDone", mapOf("ok" to true, "path" to destPath, "info" to (infoJson ?: "{}")))
                                else p2pChannel.invokeMethod("p2pDownloadDone", mapOf("ok" to false, "error" to (error ?: "unknown")))
                            }
                        }
                    )
                    result.success(mapOf("ok" to true))
                }
                "p2pDisconnect" -> { p2p.disconnect(); result.success(null) }
                "p2pInstallApk" -> {
                    val path = call.argument<String>("path") ?: ""
                    if (path.isBlank()) { result.error("INVALID", "path required", null); return@setMethodCallHandler }
                    scope.launch {
                        try {
                            val installer = ApkInstaller(this@MainActivity)
                            installer.install(File(path))
                            result.success(null)
                        } catch (e: Exception) { result.error("FAILED", e.message, null) }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPEECH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                speechResult?.success(matches?.firstOrNull() ?: "")
            } else {
                speechResult?.success("")
            }
            speechResult = null
        }
    }

    private fun ensureTts(action: (TextToSpeech) -> Unit) {
        if (tts != null && ttsReady) {
            action(tts!!)
        } else if (ttsInitializing) {
            ttsPendingActions.add(action)
        } else {
            ttsInitializing = true
            ttsPendingActions.add(action)
            // Shutdown old instance if it exists but isn't ready
            tts?.shutdown()
            tts = null
            ttsReady = false
            tts = TextToSpeech(this) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                ttsInitializing = false
                if (ttsReady) {
                    ttsPendingActions.forEach { it(tts!!) }
                }
                ttsPendingActions.clear()
            }
        }
    }
}
