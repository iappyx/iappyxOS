package com.iappyx.container

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * WiFi Direct P2P service for sharing APK files between devices.
 *
 * Sender flow: createGroup → start HTTP server → wait for receiver
 * Receiver flow: discoverPeers → connect → download APK from sender's HTTP server
 */
class P2PService(private val activity: Activity) {

    companion object {
        private const val TAG = "iappyxOS-P2P"
        private const val PORT = 8888
        private const val REQ_P2P_PERMS = 9001
    }

    private val context: Context get() = activity

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    @Volatile private var isSharing = false

    // Callbacks
    var onPeersChanged: ((List<Map<String, String>>) -> Unit)? = null
    var onConnectionChanged: ((WifiP2pInfo?) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null

    fun init() {
        manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(context, Looper.getMainLooper(), null)
        registerReceiver()
    }

    fun destroy() {
        stopSharing()
        unregisterReceiver()
        channel = null
        manager = null
    }

    // Pending action to retry after permission grant
    private var pendingAction: (() -> Unit)? = null

    private fun ensurePermissions(onGranted: () -> Unit): Boolean {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        if (needed.isNotEmpty()) {
            pendingAction = onGranted
            ActivityCompat.requestPermissions(activity, needed.toTypedArray(), REQ_P2P_PERMS)
            return false
        }
        return true
    }

    /** Call from Activity.onRequestPermissionsResult */
    fun onPermissionsResult(requestCode: Int) {
        if (requestCode == REQ_P2P_PERMS) {
            pendingAction?.invoke()
            pendingAction = null
        }
    }

    // ── Sender ──

    @SuppressLint("MissingPermission")
    fun startSharing(apkPath: String, appName: String, appSize: Long, metadata: Map<String, String>? = null, onReady: (Boolean, String?) -> Unit) {
        if (!ensurePermissions { startSharing(apkPath, appName, appSize, metadata, onReady) }) return
        val mgr = manager ?: run { onReady(false, "WiFi Direct not available"); return }
        val ch = channel ?: run { onReady(false, "WiFi Direct not initialized"); return }

        isSharing = true

        mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Group created, starting HTTP server")
                startHttpServer(apkPath, appName, appSize, metadata)
                onReady(true, null)
            }
            override fun onFailure(reason: Int) {
                isSharing = false
                val msg = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "WiFi Direct not supported"
                    WifiP2pManager.BUSY -> "WiFi Direct busy"
                    WifiP2pManager.ERROR -> "WiFi Direct error"
                    else -> "WiFi Direct failed (code $reason)"
                }
                Log.e(TAG, "createGroup failed: $msg")
                onReady(false, msg)
            }
        })
    }

    fun stopSharing() {
        isSharing = false
        stopHttpServer()
        removeGroup()
    }

    private fun startHttpServer(apkPath: String, appName: String, appSize: Long, metadata: Map<String, String>? = null) {
        stopHttpServer()
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(PORT)
                serverSocket?.soTimeout = 0 // block indefinitely
                Log.i(TAG, "HTTP server started on port $PORT")
                onStatusChanged?.invoke("waiting")

                while (isSharing && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        handleClient(client, apkPath, appName, appSize, metadata)
                    } catch (e: java.net.SocketException) {
                        if (isSharing) Log.e(TAG, "Server socket error: ${e.message}")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "HTTP server error: ${e.message}")
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun handleClient(client: Socket, apkPath: String, appName: String, appSize: Long, metadata: Map<String, String>?) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            Log.i(TAG, "HTTP request: $requestLine")

            val output = client.getOutputStream()

            when {
                requestLine.contains("GET /info.json") -> {
                    val sb = StringBuilder()
                    sb.append("""{"name":"${appName.replace("\"", "\\\"")}","size":$appSize""")
                    if (metadata != null) {
                        sb.append(",\"hasSource\":true")
                        for ((k, v) in metadata) {
                            val escaped = v.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r")
                                .replace("\t", "\\t")
                            sb.append(",\"$k\":\"$escaped\"")
                        }
                    }
                    sb.append("}")
                    val json = sb.toString()
                    val body = json.toByteArray()
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(body)
                }
                requestLine.contains("GET /app.apk") -> {
                    val file = File(apkPath)
                    if (!file.exists()) {
                        output.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray())
                        return
                    }
                    onStatusChanged?.invoke("transferring")
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Length: ${file.length()}\r\nConnection: close\r\n\r\n".toByteArray())
                    FileInputStream(file).use { fis ->
                        val buf = ByteArray(65536)
                        var read: Int
                        while (fis.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                        }
                    }
                    output.flush()
                    Log.i(TAG, "APK sent: ${file.length()} bytes")
                    onStatusChanged?.invoke("done")
                }
                else -> {
                    output.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }
            output.flush()
            client.close()
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error: ${e.message}")
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun stopHttpServer() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverThread?.interrupt()
        serverThread = null
    }

    @SuppressLint("MissingPermission")
    private fun removeGroup() {
        try {
            val ch = channel ?: return
            manager?.removeGroup(ch, null)
        } catch (_: Exception) {}
    }

    // ── Receiver ──

    @SuppressLint("MissingPermission")
    fun discoverPeers(onResult: (Boolean, String?) -> Unit) {
        if (!ensurePermissions { discoverPeers(onResult) }) return
        val mgr = manager ?: run { onResult(false, "WiFi Direct not available"); return }
        val ch = channel ?: run { onResult(false, "WiFi Direct not initialized"); return }

        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Peer discovery started")
                onResult(true, null)
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "discoverPeers failed: $reason")
                onResult(false, "Discovery failed (code $reason)")
            }
        })
    }

    fun stopDiscovery() {
        try { manager?.stopPeerDiscovery(channel!!, null) } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String, onResult: (Boolean, String?) -> Unit) {
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        manager?.connect(channel!!, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Connection initiated to $deviceAddress")
                onResult(true, null)
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "connect failed: $reason")
                onResult(false, "Connection failed (code $reason)")
            }
        })
    }

    fun downloadApk(hostIp: String, destPath: String, onProgress: (Int, Long, Long) -> Unit, onDone: (Boolean, String?, String?) -> Unit) {
        Thread {
            try {
                // Get info first
                val infoResponse: String
                val infoSocket = Socket()
                try {
                    infoSocket.connect(InetSocketAddress(hostIp, PORT), 5000)
                    infoSocket.getOutputStream().write("GET /info.json HTTP/1.1\r\nHost: $hostIp\r\nConnection: close\r\n\r\n".toByteArray())
                    infoResponse = BufferedReader(InputStreamReader(infoSocket.getInputStream())).readText()
                } finally {
                    try { infoSocket.close() } catch (_: Exception) {}
                }
                // Extract JSON body (after empty line)
                val infoJson = infoResponse.substringAfter("\r\n\r\n", "{}").trim()
                Log.i(TAG, "Info: $infoJson")

                // Download APK
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(hostIp, PORT), 10000)
                    socket.getOutputStream().write("GET /app.apk HTTP/1.1\r\nHost: $hostIp\r\nConnection: close\r\n\r\n".toByteArray())

                    val input = socket.getInputStream()
                    // Read HTTP headers — look for \r\n\r\n
                    val headerBuf = StringBuilder()
                    var crlfCount = 0
                    while (true) {
                        val b = input.read()
                        if (b == -1) break
                        headerBuf.append(b.toChar())
                        if (b == '\r'.code || b == '\n'.code) crlfCount++ else crlfCount = 0
                        if (crlfCount >= 4) break // \r\n\r\n
                    }

                    val headers = headerBuf.toString()
                    val contentLength = Regex("Content-Length:\\s*(\\d+)", RegexOption.IGNORE_CASE)
                        .find(headers)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                    // Download body
                    val outFile = File(destPath)
                    FileOutputStream(outFile).use { fos ->
                        val buf = ByteArray(65536)
                        var totalRead = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            fos.write(buf, 0, read)
                            totalRead += read
                            if (contentLength > 0) {
                                val pct = (totalRead * 100 / contentLength).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct, totalRead, contentLength)
                                }
                            }
                        }
                    }
                    Log.i(TAG, "APK downloaded: ${outFile.length()} bytes")
                    onDone(true, null, infoJson)
                } finally {
                    try { socket.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}")
                onDone(false, e.message ?: "Download failed", null)
            }
        }.start()
    }

    fun disconnect() {
        stopDiscovery()
        removeGroup()
    }

    // ── Broadcast Receiver ──

    @SuppressLint("MissingPermission")
    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager?.requestPeers(channel!!) { peers ->
                            val list = peers.deviceList.map { d ->
                                mapOf(
                                    "name" to (d.deviceName ?: "Unknown"),
                                    "address" to d.deviceAddress,
                                    "status" to when (d.status) {
                                        WifiP2pDevice.CONNECTED -> "connected"
                                        WifiP2pDevice.INVITED -> "invited"
                                        WifiP2pDevice.AVAILABLE -> "available"
                                        else -> "unavailable"
                                    }
                                )
                            }
                            onPeersChanged?.invoke(list)
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        manager?.requestConnectionInfo(channel!!) { info ->
                            onConnectionChanged?.invoke(info)
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun unregisterReceiver() {
        try { receiver?.let { context.unregisterReceiver(it) } } catch (_: Exception) {}
        receiver = null
    }
}
