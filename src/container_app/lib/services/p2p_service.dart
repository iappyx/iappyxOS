import 'package:flutter/services.dart';

/// P2P APK sharing via WiFi Direct.
/// Uses the same MethodChannel as Generator — event callbacks are routed
/// by the method call handler set up during active P2P operations.
class P2PService {
  static const _ch = MethodChannel('com.iappyx.container/p2p');

  // Callbacks set by UI
  static void Function(String status)? onStatus;
  static void Function(List<Map<String, String>> peers)? onPeers;
  static void Function(Map<String, dynamic> info)? onConnected;
  static void Function(int pct, int downloaded, int total)? onProgress;
  static void Function(bool ok, String? path, String? error, String? infoJson)? onDownloadDone;

  /// Call this before starting any P2P operation to set up the event handler.
  static void _installHandler() {
    _ch.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'p2pStatus':
          onStatus?.call(call.arguments as String);
        case 'p2pPeers':
          final raw = call.arguments as List;
          final peers = raw.map((e) => Map<String, String>.from(e as Map)).toList();
          onPeers?.call(peers);
        case 'p2pConnected':
          final info = Map<String, dynamic>.from(call.arguments as Map);
          onConnected?.call(info);
        case 'p2pProgress':
          final m = Map<String, dynamic>.from(call.arguments as Map);
          onProgress?.call(m['pct'] as int, (m['downloaded'] as num).toInt(), (m['total'] as num).toInt());
        case 'p2pDownloadDone':
          final m = Map<String, dynamic>.from(call.arguments as Map);
          onDownloadDone?.call(m['ok'] as bool, m['path'] as String?, m['error'] as String?, m['info'] as String?);
      }
    });
  }

  static void _removeHandler() {
    _ch.setMethodCallHandler(null);
    onStatus = null;
    onPeers = null;
    onConnected = null;
    onProgress = null;
    onDownloadDone = null;
  }

  // ── Sender ──

  static Future<bool> startSharing({
    required String apkPath,
    required String appName,
    required int appSize,
    Map<String, String>? metadata,
  }) async {
    _installHandler();
    try {
      await _ch.invokeMethod('p2pStartSharing', {
        'apkPath': apkPath,
        'appName': appName,
        'appSize': appSize,
        if (metadata != null) 'metadata': metadata,
      });
      return true;
    } on PlatformException {
      return false;
    }
  }

  static Future<void> stopSharing() async {
    try { await _ch.invokeMethod('p2pStopSharing'); } catch (_) {}
    _removeHandler();
  }

  // ── Receiver ──

  static Future<bool> discoverPeers() async {
    _installHandler();
    try {
      await _ch.invokeMethod('p2pDiscover');
      return true;
    } on PlatformException {
      return false;
    }
  }

  static Future<void> stopDiscovery() async {
    try { await _ch.invokeMethod('p2pStopDiscovery'); } catch (_) {}
  }

  static Future<bool> connectToPeer(String address) async {
    _installHandler();
    try {
      await _ch.invokeMethod('p2pConnect', {'address': address});
      return true;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> downloadApk(String hostIp) async {
    try {
      await _ch.invokeMethod('p2pDownload', {'hostIp': hostIp});
      return true;
    } on PlatformException {
      return false;
    }
  }

  static Future<void> disconnect() async {
    try { await _ch.invokeMethod('p2pDisconnect'); } catch (_) {}
    _removeHandler();
  }

  static Future<void> installApk(String path) async {
    await _ch.invokeMethod('p2pInstallApk', {'path': path});
  }
}
