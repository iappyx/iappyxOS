import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class BuildResult {
  final String packageName;
  final String apkPath;
  BuildResult({required this.packageName, required this.apkPath});
}

class Generator {
  static const _ch = MethodChannel('com.iappyx.container/generator');
  static bool _building = false;

  static Future<BuildResult> generateFromTemplate({
    required String label,
    required String templateId,
    required void Function(String) onProgress,
    String? packageName,
    String? iconConfig,
  }) async {
    if (_building) throw PlatformException(code: 'BUSY', message: 'A build is already in progress');
    _building = true;
    _ch.setMethodCallHandler((call) async {
      if (call.method == 'onProgress') onProgress(call.arguments as String);
    });
    try {
      final result = await _ch.invokeMethod<Map>('generateApp', {
        'label': label,
        'templateId': templateId,
        if (packageName != null) 'packageName': packageName,
        if (iconConfig != null) 'iconConfig': iconConfig,
      });
      return BuildResult(
        packageName: result?['packageName'] as String? ?? '',
        apkPath: result?['apkPath'] as String? ?? '',
      );
    } finally {
      _ch.setMethodCallHandler(null);
      _building = false;
    }
  }

  static Future<BuildResult> injectHtml({
    required String label,
    required String htmlContent,
    required void Function(String) onProgress,
    String? packageName,
    String? iconConfig,
    String? firebaseConfig,
    bool webOnly = false,
  }) async {
    if (_building) throw PlatformException(code: 'BUSY', message: 'A build is already in progress');
    _building = true;
    _ch.setMethodCallHandler((call) async {
      if (call.method == 'onProgress') onProgress(call.arguments as String);
    });
    try {
      final result = await _ch.invokeMethod<Map>('injectHtml', {
        'label': label,
        'html': htmlContent,
        if (packageName != null) 'packageName': packageName,
        if (iconConfig != null) 'iconConfig': iconConfig,
        if (firebaseConfig != null && firebaseConfig.isNotEmpty) 'firebaseConfig': firebaseConfig,
        'webOnly': webOnly,
      });
      return BuildResult(
        packageName: result?['packageName'] as String? ?? '',
        apkPath: result?['apkPath'] as String? ?? '',
      );
    } finally {
      _ch.setMethodCallHandler(null);
      _building = false;
    }
  }

  static Future<void> shareFile({
    required String path,
    String mimeType = 'application/vnd.android.package-archive',
  }) async {
    await _ch.invokeMethod('shareFile', {
      'path': path,
      'mimeType': mimeType,
    });
  }

  static Future<void> shareText({
    required String content,
    required String filename,
  }) async {
    await _ch.invokeMethod('shareText', {
      'content': content,
      'filename': filename,
    });
  }

  static Future<bool> launchApp({required String packageName}) async {
    final result = await _ch.invokeMethod<bool>('launchApp', {'packageName': packageName});
    return result ?? false;
  }

  static Future<void> openUrl(String url) async {
    await _ch.invokeMethod('openUrl', {'url': url});
  }

  static Future<void> uninstallApp({required String packageName}) async {
    await _ch.invokeMethod('uninstallApp', {'packageName': packageName});
  }

  static Future<String?> getInstalledApkPath(String packageName) async {
    final result = await _ch.invokeMethod<String>('getInstalledApkPath', {'packageName': packageName});
    return result;
  }

  /// Returns "not_installed", "same_signer", or "different_signer".
  static Future<String> checkSignature({required String packageName}) async {
    final result = await _ch.invokeMethod<String>('checkSignature', {'packageName': packageName});
    return result ?? 'not_installed';
  }

  /// Checks for signature conflict and shows uninstall dialog if needed.
  /// Returns true if safe to proceed with install, false if user cancelled.
  static Future<bool> handleSignatureConflict({
    required String packageName,
    required BuildContext context,
  }) async {
    if (packageName.isEmpty) return true;
    final sig = await checkSignature(packageName: packageName);
    if (sig != 'different_signer') return true;

    final proceed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Signature mismatch'),
        content: const Text(
          'This app was built on a different device and has a different signature. '
          'To install a new version, the existing app must be uninstalled first.\n\n'
          'App data will be lost.',
          style: TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel', style: TextStyle(color: Colors.white38)),
          ),
          TextButton(
            onPressed: () async {
              await uninstallApp(packageName: packageName);
              if (ctx.mounted) Navigator.pop(ctx, true);
            },
            child: const Text('Uninstall & replace', style: TextStyle(color: Color(0xFFEA5455))),
          ),
        ],
      ),
    );
    if (proceed != true) return false;

    // Poll until uninstalled or timeout (15s)
    for (var i = 0; i < 30; i++) {
      await Future.delayed(const Duration(milliseconds: 500));
      final recheck = await checkSignature(packageName: packageName);
      if (recheck != 'different_signer') return true;
    }
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Existing app was not uninstalled')),
      );
    }
    return false;
  }

  static Future<Map<String, dynamic>> getKeyInfo() async {
    final result = await _ch.invokeMethod<Map>('getKeyInfo', {});
    return Map<String, dynamic>.from(result ?? {});
  }
}
