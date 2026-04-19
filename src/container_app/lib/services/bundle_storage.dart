// MIT License — Copyright (c) 2026 iappyx

import 'dart:io';
import 'dart:typed_data';
import 'package:path_provider/path_provider.dart';

/// Manages per-app file bundles stored under <appDocDir>/bundles/<appId>/.
/// Files in a bundle are injected into the APK at build time under assets/app/data/.
class BundleStorage {
  static Future<Directory> _bundleDir(String appId) async {
    final dir = await getApplicationDocumentsDirectory();
    final d = Directory('${dir.path}/bundles/$appId');
    if (!d.existsSync()) d.createSync(recursive: true);
    return d;
  }

  static Future<void> addFile(String appId, String filename, Uint8List bytes) async {
    final dir = await _bundleDir(appId);
    await File('${dir.path}/$filename').writeAsBytes(bytes);
  }

  static Future<void> removeFile(String appId, String filename) async {
    final dir = await _bundleDir(appId);
    final f = File('${dir.path}/$filename');
    if (f.existsSync()) f.deleteSync();
  }

  static Future<List<Map<String, dynamic>>> listFiles(String appId) async {
    final dir = await _bundleDir(appId);
    if (!dir.existsSync()) return [];
    final entries = <Map<String, dynamic>>[];
    for (final f in dir.listSync().whereType<File>()) {
      entries.add({
        'name': f.uri.pathSegments.last,
        'size': f.lengthSync(),
      });
    }
    entries.sort((a, b) => (a['name'] as String).compareTo(b['name'] as String));
    return entries;
  }

  static Future<int> totalSize(String appId) async {
    final files = await listFiles(appId);
    int total = 0;
    for (final f in files) total += f['size'] as int;
    return total;
  }

  /// Returns {filename: bytes} for all files in the bundle. Used at build time
  /// to merge into the APK asset map.
  static Future<Map<String, Uint8List>> readAll(String appId) async {
    final dir = await _bundleDir(appId);
    if (!dir.existsSync()) return {};
    final map = <String, Uint8List>{};
    for (final f in dir.listSync().whereType<File>()) {
      map[f.uri.pathSegments.last] = f.readAsBytesSync();
    }
    return map;
  }

  /// Returns file paths for all files in the bundle. Used to pass paths
  /// (not bytes) to Kotlin via MethodChannel to avoid large payload issues.
  static Future<Map<String, String>> paths(String appId) async {
    final dir = await _bundleDir(appId);
    if (!dir.existsSync()) return {};
    final map = <String, String>{};
    for (final f in dir.listSync().whereType<File>()) {
      map[f.uri.pathSegments.last] = f.path;
    }
    return map;
  }

  static Future<void> clearBundle(String appId) async {
    final dir = await _bundleDir(appId);
    if (dir.existsSync()) dir.deleteSync(recursive: true);
  }
}
