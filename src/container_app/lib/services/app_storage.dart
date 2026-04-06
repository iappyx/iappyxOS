import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';

class AppData {
  final String id;
  final String name;
  final String description;
  final String prompt;
  final String html;
  final String packageName;
  final String apkPath;
  final String appType; // 'web', 'ai', 'demo'
  final String templateId;
  final String iconConfig; // JSON string — new icon system
  final String firebaseConfig; // google-services.json content (optional)
  final String emoji; // legacy
  final double emojiScale; // legacy
  final double emojiOffsetX; // legacy
  final double emojiOffsetY; // legacy
  final DateTime createdAt;
  final DateTime updatedAt;

  AppData({
    required this.id,
    required this.name,
    required this.description,
    required this.prompt,
    required this.html,
    required this.packageName,
    this.apkPath = '',
    this.appType = '',
    this.templateId = '',
    this.iconConfig = '',
    this.firebaseConfig = '',
    this.emoji = '',
    this.emojiScale = 1.0,
    this.emojiOffsetX = 0.0,
    this.emojiOffsetY = 0.0,
    required this.createdAt,
    required this.updatedAt,
  });

  AppData copyWith({
    String? name,
    String? description,
    String? prompt,
    String? html,
    String? packageName,
    String? apkPath,
    String? appType,
    String? templateId,
    String? iconConfig,
    String? firebaseConfig,
    String? emoji,
    double? emojiScale,
    double? emojiOffsetX,
    double? emojiOffsetY,
    DateTime? updatedAt,
  }) {
    return AppData(
      id: id,
      name: name ?? this.name,
      description: description ?? this.description,
      prompt: prompt ?? this.prompt,
      html: html ?? this.html,
      packageName: packageName ?? this.packageName,
      apkPath: apkPath ?? this.apkPath,
      appType: appType ?? this.appType,
      templateId: templateId ?? this.templateId,
      iconConfig: iconConfig ?? this.iconConfig,
      firebaseConfig: firebaseConfig ?? this.firebaseConfig,
      emoji: emoji ?? this.emoji,
      emojiScale: emojiScale ?? this.emojiScale,
      emojiOffsetX: emojiOffsetX ?? this.emojiOffsetX,
      emojiOffsetY: emojiOffsetY ?? this.emojiOffsetY,
      createdAt: createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'description': description,
    'prompt': prompt,
    'html': html,
    'packageName': packageName,
    'apkPath': apkPath,
    'appType': appType,
    'templateId': templateId,
    'iconConfig': iconConfig,
    if (firebaseConfig.isNotEmpty) 'firebaseConfig': firebaseConfig,
    'emoji': emoji,
    'emojiScale': emojiScale,
    'emojiOffsetX': emojiOffsetX,
    'emojiOffsetY': emojiOffsetY,
    'createdAt': createdAt.toIso8601String(),
    'updatedAt': updatedAt.toIso8601String(),
  };

  factory AppData.fromJson(Map<String, dynamic> j) => AppData(
    id: j['id'] as String? ?? DateTime.now().millisecondsSinceEpoch.toString(),
    name: j['name'] as String? ?? 'Unnamed',
    description: j['description'] as String? ?? '',
    prompt: j['prompt'] as String? ?? '',
    html: j['html'] as String? ?? '',
    packageName: j['packageName'] as String? ?? '',
    apkPath: j['apkPath'] as String? ?? '',
    appType: j['appType'] as String? ?? '',
    templateId: j['templateId'] as String? ?? '',
    iconConfig: j['iconConfig'] as String? ?? '',
    firebaseConfig: j['firebaseConfig'] as String? ?? '',
    emoji: j['emoji'] as String? ?? '',
    emojiScale: (j['emojiScale'] as num?)?.toDouble() ?? 1.0,
    emojiOffsetX: (j['emojiOffsetX'] as num?)?.toDouble() ?? 0.0,
    emojiOffsetY: (j['emojiOffsetY'] as num?)?.toDouble() ?? 0.0,
    createdAt: DateTime.tryParse(j['createdAt'] as String? ?? '') ?? DateTime.now(),
    updatedAt: DateTime.tryParse(j['updatedAt'] as String? ?? '') ?? DateTime.now(),
  );
}

class AppStorage {
  static File? _file;

  static Future<File> _getFile() async {
    if (_file != null) return _file!;
    final dir = await getApplicationDocumentsDirectory();
    _file = File('${dir.path}/apps.json');
    return _file!;
  }

  static const _tag = '<!-- Built with iappyxOS — https://github.com/iappyx/iappyxOS -->\n';

  /// Ensures the iappyxOS attribution tag is present exactly once at the top.
  static String tagHtml(String html) {
    if (html.isEmpty) return html;
    final clean = html.replaceAll(_tag, '');
    return '$_tag$clean';
  }

  static Future<void>? _writeLock;

  /// Serialize write operations to prevent read-modify-write races.
  static Future<void> _serialized(Future<void> Function() fn) async {
    while (_writeLock != null) await _writeLock;
    final c = Completer<void>();
    _writeLock = c.future;
    try { await fn(); } finally { _writeLock = null; c.complete(); }
  }

  static Future<List<AppData>> loadAll() async {
    final file = await _getFile();
    if (!file.existsSync()) return [];
    try {
      final json = jsonDecode(file.readAsStringSync()) as List;
      return json.map((e) => AppData.fromJson(e as Map<String, dynamic>)).toList()
        ..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    } catch (_) {
      return [];
    }
  }

  static Future<void> save(AppData app) => _serialized(() async {
    final apps = await loadAll();
    final idx = apps.indexWhere((a) => a.id == app.id);
    if (idx >= 0) {
      apps[idx] = app;
    } else {
      apps.insert(0, app);
    }
    await _write(apps);
  });

  static Future<void> importBatch(List<AppData> newApps) => _serialized(() async {
    final existing = await loadAll();
    final ids = existing.map((a) => a.id).toSet();
    for (final app in newApps) {
      if (ids.contains(app.id)) {
        existing[existing.indexWhere((a) => a.id == app.id)] = app;
      } else {
        existing.insert(0, app);
        ids.add(app.id);
      }
    }
    await _write(existing);
  });

  static Future<void> delete(String id) => _serialized(() async {
    final apps = await loadAll();
    apps.removeWhere((a) => a.id == id);
    await _write(apps);
  });

  static Future<void> _write(List<AppData> apps) async {
    final file = await _getFile();
    final tmp = File('${file.path}.tmp');
    try {
      tmp.writeAsStringSync(jsonEncode(apps.map((a) => a.toJson()).toList()));
      tmp.renameSync(file.path);
    } catch (e) {
      // Rename failed — try direct write as fallback
      try { tmp.deleteSync(); } catch (_) {}
      file.writeAsStringSync(jsonEncode(apps.map((a) => a.toJson()).toList()));
    }
  }
}
