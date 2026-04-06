import 'dart:io';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:file_picker/file_picker.dart';
import '../services/generator.dart';
import '../services/prompt_builder.dart';
import '../services/app_storage.dart';
import '../services/ai_service.dart';
import '../services/settings_service.dart';
import '../widgets/build_log.dart';
import '../models/icon_config.dart';
import '../models/ai_provider.dart';
import 'preview_screen.dart';
import 'icon_editor_screen.dart';
import '../services/error_helper.dart';
import 'flows/create_helpers.dart' as h;

class CreateScreen extends StatefulWidget {
  final VoidCallback? onAppSaved;
  final VoidCallback? onViewMyApps;
  const CreateScreen({super.key, this.onAppSaved, this.onViewMyApps});
  @override
  State<CreateScreen> createState() => CreateScreenState();
}

class CreateScreenState extends State<CreateScreen> {
  String? _mode;
  bool _isBuilding = false;
  bool _showBuildLog = false;
  final List<String> _log = [];

  final _nameController = TextEditingController();
  IconConfig _iconConfig = IconConfig();
  String? _editingId;
  String? _existingPackageName;
  String _firebaseConfig = '';

  final _urlController = TextEditingController();
  String? _urlError;
  final _descController = TextEditingController();
  final _htmlController = TextEditingController();
  String _generatedPrompt = '';
  bool _descExpanded = true;
  bool _promptVisible = false;
  bool _promptExpanded = false;
  bool _promptCopied = false;
  bool _htmlExpanded = false;

  String? _selectedDemoId;

  // AI generation
  String _genMethod = ''; // 'api', 'manual'
  AiProvider? _activeProvider;
  bool _loadingProvider = false;
  List<ChatMessage> _conversation = [];
  bool _aiGenerating = false;
  DateTime? _genStartTime;
  int _genId = 0;
  final _followUpController = TextEditingController();
  String? _selectedHtml; // HTML selected via "Use This"
  final _formScrollController = ScrollController();

  @override
  void dispose() {
    _nameController.dispose();
    _urlController.dispose();
    _descController.dispose();
    _htmlController.dispose();
    _followUpController.dispose();
    _formScrollController.dispose();
    super.dispose();
  }

  void _addLog(String msg) { if (mounted) setState(() => _log.add(msg)); }

  void loadApp(AppData app) {
    _nameController.text = app.name;
    _descController.text = app.description;
    _htmlController.text = app.html;
    _generatedPrompt = app.prompt;
    _editingId = app.id;
    _existingPackageName = app.packageName;
    _firebaseConfig = app.firebaseConfig;
    _selectedDemoId = null;
    _isBuilding = false;
    _showBuildLog = false;
    _log.clear();
    _genMethod = 'manual';
    _descExpanded = true;
    _htmlExpanded = true;
    _conversation.clear();
    _aiGenerating = false;
    _selectedHtml = null;
    _followUpController.clear();

    if (app.iconConfig.isNotEmpty) {
      _iconConfig = IconConfig.fromJsonString(app.iconConfig);
    } else {
      _iconConfig = IconConfig.fromLegacy(
        emoji: app.emoji, emojiScale: app.emojiScale,
        emojiOffsetX: app.emojiOffsetX, emojiOffsetY: app.emojiOffsetY,
        appName: app.name,
      );
    }

    final type = app.appType.isNotEmpty ? app.appType
        : app.templateId.isNotEmpty ? 'demo'
        : app.description.startsWith('Web app: ') ? 'web' : 'ai';

    if (type == 'web') {
      _urlController.text = app.description.replaceFirst('Web app: ', '');
      _urlError = null;
      setState(() => _mode = 'web');
    } else if (type == 'demo') {
      _selectedDemoId = app.templateId;
      setState(() => _mode = 'demo');
    } else {
      _promptVisible = app.prompt.isNotEmpty;
      _htmlExpanded = true;
      _descExpanded = true;
      setState(() => _mode = 'ai');
    }
  }

  void _reset() {
    setState(() {
      _mode = null;
      _isBuilding = false;
      _showBuildLog = false;
      _nameController.clear();
      _urlController.clear(); _urlError = null;
      _descController.clear();
      _htmlController.clear();
      _generatedPrompt = '';
      _editingId = null;
      _existingPackageName = null;
      _firebaseConfig = '';
      _selectedDemoId = null;
      _iconConfig = IconConfig();
      _descExpanded = true;
      _promptVisible = false;
      _promptExpanded = false;
      _promptCopied = false;
      _htmlExpanded = false;
      _genMethod = '';
      _conversation.clear();
      _aiGenerating = false;
      _selectedHtml = null;
      _followUpController.clear();
      _log.clear();
    });
  }

  IconConfig _ensureIconConfig(String label) {
    if (_iconConfig.elements.isEmpty) _iconConfig = IconConfig.defaultFor(label);
    return _iconConfig;
  }

  Future<void> _openIconEditor() async {
    _ensureIconConfig(_nameController.text.trim());
    final result = await Navigator.push<IconConfig>(
      context, MaterialPageRoute(builder: (_) => IconEditorScreen(config: _iconConfig)),
    );
    if (result != null) setState(() => _iconConfig = result);
  }

  String _stripMarkdownFences(String text) {
    var s = text.trim();
    final m = RegExp(r'^```(?:html)?\s*\n?([\s\S]*?)\n?\s*```$').firstMatch(s);
    if (m != null) s = m.group(1)!.trim();
    final i = s.indexOf('<');
    if (i > 0) s = s.substring(i);
    return s;
  }

  Future<DateTime?> _getExistingCreatedAt(String id) async {
    try { return (await AppStorage.loadAll()).firstWhere((a) => a.id == id).createdAt; }
    catch (_) { return null; }
  }

  Future<void> _loadActiveProvider() async {
    _loadingProvider = true;
    _activeProvider = await Settings.getActiveProvider();
    _loadingProvider = false;
    if (mounted) setState(() {});
  }

  /// Called when the Create tab becomes visible (e.g. after Settings change).
  void refreshProvider() => _loadActiveProvider();

  /// When editing an existing app via API, seed the conversation with the current code
  /// so the user can ask for changes directly without re-generating from scratch.
  void _startEditConversation() {
    final existingHtml = _htmlController.text.trim();
    if (existingHtml.isEmpty || !existingHtml.startsWith('<')) return;
    setState(() {
      _genMethod = 'api';
      _conversation = [
        ChatMessage(role: 'assistant', content: existingHtml),
      ];
      _selectedHtml = existingHtml;
    });
  }

  Future<void> _generateWithAI(AiProvider provider) async {
    final name = _nameController.text.trim();
    final desc = _descController.text.trim();
    if (name.isEmpty || desc.isEmpty) { _snack('Fill in name and description.'); return; }

    final thisGenId = ++_genId;
    setState(() {
      _genMethod = 'api';
      _aiGenerating = true; _genStartTime = DateTime.now();
      _conversation = [ChatMessage(role: 'user', content: 'App name: $name\nDescription: $desc')];
    });

    try {
      final systemPrompt = await PromptBuilder.getSystemPrompt();
      final response = await AiService.generate(
        provider: provider,
        systemPrompt: systemPrompt,
        messages: _conversation,
      );
      if (!mounted || thisGenId != _genId) return;
      final cleaned = _stripMarkdownFences(response);
      setState(() {
        _conversation.add(ChatMessage(role: 'assistant', content: cleaned));
        _aiGenerating = false;
      });
      if (cleaned.trim().startsWith('<')) _useThisHtml(cleaned);
    } catch (e) {
      if (!mounted || thisGenId != _genId) return;
      setState(() => _aiGenerating = false);
      _snack(friendlyError(e.toString()).toString());
    }
  }

  Future<void> _sendFollowUp() async {
    final msg = _followUpController.text.trim();
    if (msg.isEmpty || _activeProvider == null) return;

    final thisGenId = ++_genId;
    final provider = _activeProvider!;
    setState(() {
      _conversation.add(ChatMessage(role: 'user', content: msg));
      _followUpController.clear();
      _aiGenerating = true; _genStartTime = DateTime.now();
    });

    try {
      final systemPrompt = await PromptBuilder.getSystemPrompt();
      final response = await AiService.generate(
        provider: provider,
        systemPrompt: systemPrompt,
        messages: _conversation,
      );
      if (!mounted || thisGenId != _genId) return;
      final cleaned = _stripMarkdownFences(response);
      setState(() {
        _conversation.add(ChatMessage(role: 'assistant', content: cleaned));
        _aiGenerating = false;
      });
      if (cleaned.trim().startsWith('<')) _useThisHtml(cleaned);
    } catch (e) {
      if (!mounted || thisGenId != _genId) return;
      setState(() => _aiGenerating = false);
      _snack(friendlyError(e.toString()).toString());
    }
  }

  bool get _hasUnsavedChanges {
    if (_selectedHtml == null) return false;
    // If editing and the HTML matches what's saved, no unsaved changes
    if (_editingId != null && _selectedHtml == _htmlController.text.trim()) return false;
    // If conversation produced HTML that hasn't been built
    return _conversation.any((m) => m.role == 'assistant' && m.content.trim().startsWith('<'));
  }

  Future<void> _confirmExit(VoidCallback onConfirm) async {
    if (!_hasUnsavedChanges) { onConfirm(); return; }
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        title: const Text('Unsaved changes'),
        content: const Text('You have AI-generated code that hasn\'t been saved. Leave anyway?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Stay')),
          TextButton(onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Leave', style: TextStyle(color: Color(0xFFFF6B6B)))),
        ],
      ),
    );
    if (result == true) onConfirm();
  }

  void _useThisHtml(String html) {
    setState(() {
      _selectedHtml = html;
      _htmlController.text = html;
      _htmlExpanded = false;
    });
    _snack('HTML loaded. Preview or build below.');
    Future.delayed(const Duration(milliseconds: 300), () {
      if (_formScrollController.hasClients) {
        _formScrollController.animateTo(
          _formScrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 400),
          curve: Curves.easeOut,
        );
      }
    });
  }

  void _snack(String msg) => ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(msg, style: const TextStyle(color: Colors.white)),
      backgroundColor: const Color(0xFF1A1A2E), duration: const Duration(seconds: 4)));

  // ── Builds ──

  Future<void> _buildWebApp() async {
    final label = _nameController.text.trim();
    final url = _urlController.text.trim();
    if (label.isEmpty || url.isEmpty) { _snack('Please fill in name and URL.'); return; }
    if (label.length > 37 || label.codeUnits.length > 37) { _snack('Name too long (max 37 characters).'); return; }
    if (!url.startsWith('http://') && !url.startsWith('https://')) { _snack('URL must start with http:// or https://'); return; }
    final safeLabel = label.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
    final safeUrl = url.replaceAll('\\', '\\\\').replaceAll('"', '\\"').replaceAll('<', '\\x3c');
    final html = '<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>$safeLabel</title><style>*{margin:0;padding:0}body{background:#0d0d1a;display:flex;align-items:center;justify-content:center;height:100vh;font-family:-apple-system,sans-serif}.loader{color:#4FC3F7;font-size:14px}.spinner{width:24px;height:24px;border:3px solid #1a1a2e;border-top-color:#4FC3F7;border-radius:50%;animation:spin .8s linear infinite;margin:0 auto 12px}@keyframes spin{to{transform:rotate(360deg)}}</style></head><body><div class="loader"><div class="spinner"></div>Loading...</div><script>window.location.href="$safeUrl";</script></body></html>';
    await _doBuild(label, html, 'web', description: 'Web app: $url');
  }

  Future<void> _buildAiApp() async {
    final label = _nameController.text.trim();
    final html = _htmlController.text.trim();
    if (label.isEmpty || html.isEmpty) { _snack('Fill in name and paste HTML.'); return; }
    if (label.length > 37 || label.codeUnits.length > 37) { _snack('Name too long (max 37 characters).'); return; }
    if (!html.startsWith('<')) { _snack('HTML must start with < (e.g., <!DOCTYPE html>).'); return; }
    await _doBuild(label, html, 'ai', description: _descController.text.trim(), prompt: _generatedPrompt);
  }

  Future<void> _buildDemo() async {
    if (_selectedDemoId == null) return;
    final t = h.demoTemplates.firstWhere((t) => t.$1 == _selectedDemoId);
    final label = _nameController.text.trim().isNotEmpty ? _nameController.text.trim() : t.$3;
    if (label.length > 37 || label.codeUnits.length > 37) { _snack('Name too long (max 37 characters).'); return; }
    if (_iconConfig.elements.isEmpty) {
      _iconConfig = IconConfig(bgColor: IconConfig.colorForString(t.$2), elements: [IconElement(content: t.$2)]);
    }
    if (_existingPackageName != null && _existingPackageName!.isNotEmpty) {
      final ok = await Generator.handleSignatureConflict(packageName: _existingPackageName!, context: context);
      if (!ok) return;
    }
    setState(() { _isBuilding = true; _showBuildLog = true; _log.clear(); });
    try {
      final result = await Generator.generateFromTemplate(label: label, templateId: t.$1, packageName: _existingPackageName, iconConfig: _iconConfig.toJsonString(), onProgress: _addLog);
      final now = DateTime.now();
      final appId = _editingId ?? '${now.millisecondsSinceEpoch}_${Random().nextInt(9999)}';
      await AppStorage.save(AppData(
        id: appId, name: label,
        description: 'Demo: ${t.$4}', prompt: '', html: '', appType: 'demo', templateId: t.$1,
        packageName: result.packageName, apkPath: result.apkPath, iconConfig: _iconConfig.toJsonString(),
        createdAt: _editingId != null ? (await _getExistingCreatedAt(_editingId!) ?? now) : now, updatedAt: now,
      ));
      _editingId = appId;
      _existingPackageName = result.packageName;
      _addLog('\u2705 Build complete! App saved.');
      widget.onAppSaved?.call();
    } on PlatformException catch (e) { final err = friendlyError(e.message); _addLog('\u274C ${err.message}'); if (err.hint != null) _addLog('   ${err.hint}'); }
    finally { setState(() => _isBuilding = false); }
  }

  Future<void> _doBuild(String label, String html, String appType, {String description = '', String prompt = ''}) async {
    if (_existingPackageName != null && _existingPackageName!.isNotEmpty) {
      final ok = await Generator.handleSignatureConflict(packageName: _existingPackageName!, context: context);
      if (!ok) return;
    }
    setState(() { _isBuilding = true; _showBuildLog = true; _log.clear(); });
    try {
      final ic = _ensureIconConfig(label);
      final result = await Generator.injectHtml(label: label, htmlContent: html, packageName: _existingPackageName, iconConfig: ic.toJsonString(), firebaseConfig: _firebaseConfig.isNotEmpty ? _firebaseConfig : null, onProgress: _addLog, webOnly: appType == 'web');
      final now = DateTime.now();
      final appId = _editingId ?? '${now.millisecondsSinceEpoch}_${Random().nextInt(9999)}';
      await AppStorage.save(AppData(
        id: appId, name: label,
        description: description, prompt: prompt, html: html, appType: appType,
        packageName: result.packageName, apkPath: result.apkPath, iconConfig: ic.toJsonString(), firebaseConfig: _firebaseConfig,
        createdAt: _editingId != null ? (await _getExistingCreatedAt(_editingId!) ?? now) : now, updatedAt: now,
      ));
      _editingId = appId;
      _existingPackageName = result.packageName;
      _addLog('\u2705 Build complete! App saved.');
      widget.onAppSaved?.call();
    } on PlatformException catch (e) { final err = friendlyError(e.message); _addLog('\u274C ${err.message}'); if (err.hint != null) _addLog('   ${err.hint}'); }
    finally { setState(() => _isBuilding = false); }
  }

  Future<void> _generatePrompt() async {
    final name = _nameController.text.trim();
    final desc = _descController.text.trim();
    if (name.isEmpty || desc.isEmpty) { _snack('Fill in name and description.'); return; }
    try {
      final existingHtml = _editingId != null ? _htmlController.text.trim() : null;
      _generatedPrompt = await PromptBuilder.buildPrompt(appName: name, description: desc, existingHtml: existingHtml);
      setState(() { _promptVisible = true; _promptExpanded = true; _descExpanded = false; _htmlExpanded = true; });
    } catch (e) { _snack('Prompt generation failed.'); }
  }

  // ── UI ──

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: _showBuildLog ? _buildingView() : _mode == null ? _modeSelection() : _formView(),
      ),
    );
  }

  Widget _advancedSettings() {
    if (_existingPackageName == null || _existingPackageName!.isEmpty) return const SizedBox.shrink();
    return ExpansionTile(
      title: const Text('Advanced Settings', style: TextStyle(fontSize: 13, color: Colors.white54)),
      tilePadding: EdgeInsets.zero,
      childrenPadding: const EdgeInsets.only(bottom: 12),
      iconColor: Colors.white38,
      collapsedIconColor: Colors.white38,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: const Color(0xFF0D0D1A),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Package Name', style: TextStyle(fontSize: 11, color: Colors.white38)),
            const SizedBox(height: 4),
            SelectableText(_existingPackageName!, style: const TextStyle(fontSize: 13, fontFamily: 'monospace', color: Color(0xFF4FC3F7))),
            const SizedBox(height: 4),
            const Text('Use this when adding an Android app in Firebase Console.', style: TextStyle(fontSize: 10, color: Colors.white24)),
            const SizedBox(height: 16),
            const Text('Firebase Config (optional)', style: TextStyle(fontSize: 11, color: Colors.white38)),
            const SizedBox(height: 4),
            const Text('Enables push notifications. Create a Firebase project, add an Android app with the package name above, download google-services.json.',
              style: TextStyle(fontSize: 10, color: Colors.white24)),
            const SizedBox(height: 8),
            Row(children: [
              GestureDetector(
                onTap: () async {
                  final result = await FilePicker.platform.pickFiles(type: FileType.any);
                  if (result != null && result.files.single.path != null) {
                    final file = File(result.files.single.path!);
                    final content = await file.readAsString();
                    if (content.contains('project_id') && content.contains('client')) {
                      setState(() => _firebaseConfig = content);
                    } else {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Invalid google-services.json')),
                      );
                    }
                  }
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: const Color(0xFF0F3460),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    _firebaseConfig.isEmpty ? 'Pick google-services.json' : 'Replace config',
                    style: const TextStyle(fontSize: 12, color: Color(0xFF4FC3F7)),
                  ),
                ),
              ),
              if (_firebaseConfig.isNotEmpty) ...[
                const SizedBox(width: 8),
                const Icon(Icons.check_circle, color: Color(0xFF69F0AE), size: 16),
                const SizedBox(width: 4),
                const Text('Configured', style: TextStyle(fontSize: 11, color: Color(0xFF69F0AE))),
                const SizedBox(width: 8),
                GestureDetector(
                  onTap: () => setState(() => _firebaseConfig = ''),
                  child: const Icon(Icons.close, color: Colors.white38, size: 16),
                ),
              ],
            ]),
          ]),
        ),
      ],
    );
  }

  Widget _modeSelection() => SingleChildScrollView(
    padding: const EdgeInsets.fromLTRB(24, 32, 24, 40),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _appHeader('Create App'),
      const SizedBox(height: 32),
      const Text('What do you want to create?', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
      const SizedBox(height: 20),
      h.buildModeCard(icon: Icons.auto_awesome, title: 'AI-generated App', subtitle: 'Describe what you want, generate with AI', onTap: () => setState(() => _mode = 'ai')),
      const SizedBox(height: 12),
      h.buildModeCard(icon: Icons.language, title: 'Website as App', subtitle: 'Turn any website into a standalone app', onTap: () => setState(() => _mode = 'web')),
      const SizedBox(height: 12),
      h.buildModeCard(icon: Icons.science_outlined, title: 'Demo App', subtitle: 'Pre-built apps to test bridge features', onTap: () => setState(() => _mode = 'demo')),
    ]),
  );

  Widget _formView() {
    final isEdit = _editingId != null;
    final label = _mode == 'web' ? 'Website as App' : _mode == 'ai' ? 'AI App' : 'Demo App';
    return SingleChildScrollView(
      controller: _formScrollController,
      padding: const EdgeInsets.fromLTRB(24, 32, 24, 40),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Back + title
        Row(children: [
          GestureDetector(
            onTap: () => _confirmExit(isEdit ? () => widget.onViewMyApps?.call() : _reset),
            child: Container(
              width: 38, height: 38,
              decoration: BoxDecoration(color: const Color(0xFF1A1A2E), borderRadius: BorderRadius.circular(10)),
              child: const Icon(Icons.arrow_back, size: 18, color: Colors.white54),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(isEdit ? 'Editing' : 'New $label',
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              if (isEdit && _nameController.text.isNotEmpty)
                Text(_nameController.text, style: const TextStyle(fontSize: 12, color: Colors.white38)),
            ],
          )),
        ]),
        const SizedBox(height: 24),

        // Icon + Name
        Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
          Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: _openIconEditor,
              borderRadius: BorderRadius.circular(14),
              child: Stack(children: [
                IconPreview(config: _iconConfig.elements.isEmpty ? _ensureIconConfig(_nameController.text.trim()) : _iconConfig, size: 60),
                Positioned(right: -2, bottom: -2, child: Container(
                  width: 24, height: 24,
                  decoration: BoxDecoration(color: const Color(0xFF4FC3F7), borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFF0D0D1A), width: 2)),
                  child: const Icon(Icons.edit, size: 12, color: Color(0xFF0D0D1A)),
                )),
              ]),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(child: TextField(
            controller: _nameController,
            style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w600),
            decoration: const InputDecoration(hintText: 'App Name', contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 14)),
            textCapitalization: TextCapitalization.words,
          )),
        ]),
        const SizedBox(height: 20),

        if (_mode == 'web') _webFields(),
        if (_mode == 'ai') _aiFields(),
        if (_mode == 'demo') _demoFields(),
      ]),
    );
  }

  // ── Web ──
  void _validateUrl() {
    final url = _urlController.text.trim();
    setState(() {
      if (url.isEmpty) { _urlError = null; }
      else if (!url.startsWith('http://') && !url.startsWith('https://')) { _urlError = 'Must start with http:// or https://'; }
      else if (url == 'http://' || url == 'https://') { _urlError = 'Enter a complete URL'; }
      else if (!url.contains('.')) { _urlError = 'Enter a valid URL'; }
      else { _urlError = null; }
    });
  }

  Widget _webFields() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const Text('URL', style: TextStyle(fontSize: 13, color: Colors.white54)),
    const SizedBox(height: 8),
    TextField(controller: _urlController, style: const TextStyle(color: Colors.white, fontSize: 14),
      decoration: InputDecoration(hintText: 'https://example.com', errorText: _urlError),
      keyboardType: TextInputType.url,
      onChanged: (_) { if (_urlError != null) _validateUrl(); },
      onEditingComplete: _validateUrl),
    _advancedSettings(),
    const SizedBox(height: 24),
    h.buildActionButton(label: 'Build App', onPressed: _isBuilding ? null : _buildWebApp, icon: Icons.rocket_launch),
  ]);

  // ── AI ──
  Widget _aiFields() {
    if (_activeProvider == null && !_loadingProvider) _loadActiveProvider();
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      // Section 1: Describe
      _section('Describe your app', _descExpanded, () => setState(() => _descExpanded = !_descExpanded),
        done: _genMethod.isNotEmpty || _generatedPrompt.isNotEmpty,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const SizedBox(height: 8),
          TextField(controller: _descController, style: const TextStyle(color: Colors.white), maxLines: 4,
            decoration: const InputDecoration(hintText: 'What should the app do?'), textCapitalization: TextCapitalization.sentences),
          const SizedBox(height: 16),
          const Text('Choose how to generate the code for your app.',
              style: TextStyle(fontSize: 12, color: Colors.white38)),
          const SizedBox(height: 10),
          Row(children: [
            Expanded(child: _genButton(
              icon: Icons.content_paste,
              label: 'AI Manual',
              sublabel: 'Copy-paste',
              selected: _genMethod == 'manual',
              onTap: () => setState(() => _genMethod = 'manual'),
            )),
            const SizedBox(width: 10),
            Expanded(child: _genButton(
              icon: Icons.auto_awesome,
              label: 'AI API',
              sublabel: _activeProvider != null && _activeProvider!.isConfigured
                  ? _activeProvider!.selectedModel.split('/').last.split('-').take(3).join('-')
                  : 'Tap to set up',
              selected: _genMethod == 'api',
              onTap: () async {
                if (_activeProvider == null || !_activeProvider!.isConfigured) {
                  final provider = _activeProvider ?? AiProvider.anthropic();
                  final result = await Navigator.push<AiProvider>(
                    context,
                    MaterialPageRoute(builder: (_) => ProviderSetupPage(provider: provider)),
                  );
                  if (result != null && result.isConfigured) {
                    await Settings.setActiveProvider(result);
                    _activeProvider = result;
                  } else {
                    await _loadActiveProvider();
                  }
                  if (_activeProvider == null || !_activeProvider!.isConfigured) return;
                }
                setState(() => _genMethod = 'api');
              },
            )),
          ]),
          if (_genMethod.isNotEmpty) ...[
            const SizedBox(height: 10),
            if (_genMethod == 'manual')
              const Padding(padding: EdgeInsets.only(bottom: 10),
                child: Text('This will generate a detailed prompt containing all technical instructions. Copy the prompt to any AI assistant, and paste the HTML code it returns back here.',
                  style: TextStyle(fontSize: 11, color: Colors.white38)))
            else if (_genMethod == 'api')
              Padding(padding: const EdgeInsets.only(bottom: 10),
                child: Text('Your app description will be sent to ${_activeProvider?.selectedModel.split('/').last ?? "the AI"} along with the technical documentation. The response appears below.',
                  style: const TextStyle(fontSize: 11, color: Colors.white38))),
            if (_genMethod == 'api' && _editingId != null && _htmlController.text.trim().startsWith('<') && _conversation.isEmpty)
              h.buildActionButton(
                label: 'Edit with AI',
                icon: Icons.edit,
                onPressed: _startEditConversation,
              )
            else
              h.buildActionButton(
                label: _genMethod == 'api' ? 'Generate' : (_editingId != null ? 'Create Update Prompt' : 'Create Prompt for AI'),
                icon: _genMethod == 'api' ? Icons.auto_awesome : Icons.content_paste,
                onPressed: _aiGenerating ? null : () {
                  if (_genMethod == 'manual') {
                    _generatePrompt();
                  } else if (_activeProvider != null && _activeProvider!.isConfigured) {
                    _generateWithAI(_activeProvider!);
                  }
                },
              ),
          ],
        ]),
      ),

      // Section 2a: AI Conversation (when using API)
      if (_genMethod == 'api' && _conversation.isNotEmpty) ...[
        const SizedBox(height: 10),
        _section('AI Conversation', true, () {},
          done: _selectedHtml != null,
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const SizedBox(height: 8),
            const Text('Use the text field below to ask for changes. Each message includes the full conversation history.',
                style: TextStyle(fontSize: 11, color: Colors.white38)),
            const SizedBox(height: 8),
            // Messages
            ..._conversation.map((msg) => _chatBubble(msg)),
            if (_aiGenerating)
              _generatingIndicator(),
            if (!_aiGenerating && _conversation.isNotEmpty) ...[
              const SizedBox(height: 10),
              Row(children: [
                Expanded(child: TextField(
                  controller: _followUpController,
                  style: const TextStyle(color: Colors.white, fontSize: 13),
                  decoration: InputDecoration(hintText: _editingId != null ? 'What should change?' : 'Ask for changes...', contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12)),
                  onSubmitted: (_) => _sendFollowUp(),
                )),
                const SizedBox(width: 8),
                GestureDetector(
                  onTap: _sendFollowUp,
                  child: Container(
                    width: 44, height: 44,
                    decoration: BoxDecoration(color: const Color(0xFF0F3460), borderRadius: BorderRadius.circular(12)),
                    child: const Icon(Icons.send, size: 18, color: Color(0xFF4FC3F7)),
                  ),
                ),
              ]),
            ],
          ]),
        ),
      ],

      // Section 2b: Manual copy-paste (when using manual)
      if (_genMethod == 'manual') ...[
        if (_promptVisible) ...[
          const SizedBox(height: 10),
          _section('Copy prompt to AI', _promptExpanded, () => setState(() => _promptExpanded = !_promptExpanded), done: _promptCopied,
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const SizedBox(height: 8),
              const Text('Send this prompt to an AI assistant. The prompt contains the bridge documentation and your app description.',
                  style: TextStyle(fontSize: 11, color: Colors.white38)),
              const SizedBox(height: 8),
              Container(
                width: double.infinity, constraints: const BoxConstraints(maxHeight: 180),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(color: const Color(0xFF0A0A14), borderRadius: BorderRadius.circular(10)),
                child: SingleChildScrollView(child: Text(_generatedPrompt, style: const TextStyle(fontSize: 10, fontFamily: 'monospace', color: Colors.white38))),
              ),
              const SizedBox(height: 10),
              Row(children: [
                Expanded(child: h.buildActionButton(label: 'Copy', icon: Icons.copy, onPressed: () {
                  Clipboard.setData(ClipboardData(text: _generatedPrompt));
                  setState(() { _promptCopied = true; _promptExpanded = false; });
                  _snack('Copied! Paste into your AI assistant.');
                })),
                const SizedBox(width: 10),
                Expanded(child: h.buildActionButton(label: 'Share', icon: Icons.share, secondary: true, onPressed: () {
                  final name = _nameController.text.trim().replaceAll(RegExp(r'[^\w]'), '_').toLowerCase();
                  Generator.shareText(content: _generatedPrompt, filename: '${name.isEmpty ? "prompt" : name}_prompt.txt');
                  setState(() { _promptCopied = true; });
                })),
              ]),
              const SizedBox(height: 8),
              h.buildActionButton(label: 'Save to Downloads', icon: Icons.download, secondary: true, onPressed: () async {
                try {
                  final name = _nameController.text.trim().replaceAll(RegExp(r'[^\w]'), '_').toLowerCase();
                  final filename = '${name.isEmpty ? "prompt" : name}_prompt.txt';
                  final dir = Directory('/storage/emulated/0/Download');
                  if (!dir.existsSync()) dir.createSync(recursive: true);
                  final file = File('${dir.path}/$filename');
                  file.writeAsStringSync(_generatedPrompt);
                  setState(() { _promptCopied = true; });
                  _snack('Saved to Downloads/$filename');
                } catch (e) {
                  _snack('Could not save: $e');
                }
              }),
            ]),
          ),
        ],
        const SizedBox(height: 10),
        _section('Paste the AI response', _htmlExpanded, () => setState(() => _htmlExpanded = !_htmlExpanded),
          done: _htmlController.text.trim().startsWith('<'),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const SizedBox(height: 8),
            const Text('Paste the HTML code from the AI response. The code should be a complete HTML file starting with <!DOCTYPE html>.',
                style: TextStyle(fontSize: 11, color: Colors.white38)),
            const SizedBox(height: 8),
            TextField(controller: _htmlController, style: const TextStyle(fontSize: 11, fontFamily: 'monospace', color: Colors.white),
              maxLines: 8, decoration: const InputDecoration(hintText: 'Paste HTML here...', hintStyle: TextStyle(fontFamily: 'monospace'))),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(child: OutlinedButton.icon(
                onPressed: () async {
                  final d = await Clipboard.getData(Clipboard.kTextPlain);
                  if (!mounted) return;
                  if (d?.text != null) { _htmlController.text = _stripMarkdownFences(d!.text!); setState(() {}); }
                },
                icon: const Icon(Icons.paste, size: 16), label: const Text('Paste'),
                style: OutlinedButton.styleFrom(foregroundColor: const Color(0xFF4FC3F7), side: const BorderSide(color: Color(0xFF4FC3F7)),
                  padding: const EdgeInsets.symmetric(vertical: 12), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10))),
              )),
              const SizedBox(width: 10),
              Expanded(child: OutlinedButton.icon(
                onPressed: () async {
                  final r = await FilePicker.platform.pickFiles(type: FileType.custom, allowedExtensions: ['html', 'htm', 'txt']);
                  if (!mounted) return;
                  if (r != null && r.files.single.path != null) { _htmlController.text = _stripMarkdownFences(await File(r.files.single.path!).readAsString()); if (mounted) setState(() {}); }
                },
                icon: const Icon(Icons.file_open, size: 16), label: const Text('File'),
                style: OutlinedButton.styleFrom(foregroundColor: const Color(0xFF4FC3F7), side: const BorderSide(color: Color(0xFF4FC3F7)),
                  padding: const EdgeInsets.symmetric(vertical: 12), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10))),
              )),
            ]),
          ]),
        ),
      ],

      _advancedSettings(),

      // Section: Preview & Build (both flows)
      if (_htmlController.text.trim().startsWith('<') || _selectedHtml != null) ...[
        const SizedBox(height: 10),
        _section('Preview & Build', true, () {},
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const SizedBox(height: 8),
            const Text('Preview runs the app in a test environment. Save & Build creates an installable APK on your device.',
                style: TextStyle(fontSize: 11, color: Colors.white38)),
            const SizedBox(height: 8),
            h.buildActionButton(label: 'Preview', icon: Icons.visibility, onPressed: _htmlController.text.trim().isEmpty ? null : () {
              Navigator.push(context, MaterialPageRoute(builder: (_) => PreviewScreen(htmlContent: _htmlController.text.trim())));
            }),
            const SizedBox(height: 10),
            h.buildActionButton(label: 'Save & Build APK', icon: Icons.build, onPressed: _isBuilding ? null : _buildAiApp, secondary: true),
          ]),
        ),
      ],
    ]);
  }

  Widget _genButton({required IconData icon, required String label, required String sublabel, required bool selected, required VoidCallback onTap}) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: selected ? const Color(0xFF0F3460) : const Color(0xFF1A1A2E),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: selected ? const Color(0xFF4FC3F7) : Colors.white12),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(icon, size: 16, color: selected ? const Color(0xFF4FC3F7) : Colors.white38),
            const SizedBox(width: 8),
            Text(label, style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600,
                color: selected ? Colors.white : Colors.white54)),
          ]),
          const SizedBox(height: 3),
          Text(sublabel, style: TextStyle(fontSize: 10, color: selected ? Colors.white38 : Colors.white24)),
        ]),
      ),
    );
  }

  Widget _generatingIndicator() {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: StreamBuilder(
        stream: Stream.periodic(const Duration(seconds: 1)),
        builder: (context, _) {
          final elapsed = _genStartTime != null
              ? DateTime.now().difference(_genStartTime!).inSeconds
              : 0;
          return Row(children: [
            const SizedBox(width: 16, height: 16,
                child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF4FC3F7))),
            const SizedBox(width: 10),
            Text('Generating... ${elapsed}s',
                style: const TextStyle(fontSize: 12, color: Colors.white38)),
            const Spacer(),
            GestureDetector(
              onTap: () => setState(() {
                _genId++;
                _aiGenerating = false;
                _genStartTime = null;
              }),
              child: const Text('Cancel', style: TextStyle(fontSize: 12, color: Color(0xFFFF6B6B))),
            ),
          ]);
        },
      ),
    );
  }

  Widget _chatBubble(ChatMessage msg) {
    final isUser = msg.role == 'user';
    final isHtml = !isUser && msg.content.trim().startsWith('<');
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isUser ? const Color(0xFF0F3460) : const Color(0xFF0A0A14),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Text(isUser ? 'You' : 'AI', style: TextStyle(fontSize: 10, fontWeight: FontWeight.w600,
              color: isUser ? const Color(0xFF4FC3F7) : const Color(0xFF69F0AE))),
          const Spacer(),
          if (isHtml)
            GestureDetector(
              onTap: () => _useThisHtml(msg.content),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: _selectedHtml == msg.content ? const Color(0xFF1B5E20) : const Color(0xFF0F3460),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  if (_selectedHtml == msg.content) const Icon(Icons.check, size: 12, color: Color(0xFF69F0AE)),
                  if (_selectedHtml == msg.content) const SizedBox(width: 4),
                  Text(_selectedHtml == msg.content ? 'Selected' : 'Use This',
                    style: TextStyle(fontSize: 11, color: _selectedHtml == msg.content ? const Color(0xFF69F0AE) : const Color(0xFF4FC3F7))),
                ]),
              ),
            ),
        ]),
        const SizedBox(height: 6),
        if (isHtml)
          Text('HTML response (${(msg.content.length / 1024).toStringAsFixed(1)} KB)',
            style: const TextStyle(fontSize: 11, color: Colors.white24))
        else
          Text(msg.content, style: const TextStyle(fontSize: 13, color: Colors.white70)),
      ]),
    );
  }


  // ── Demo ──
  Widget _demoCard((String, String, String, String) t) {
    final sel = _selectedDemoId == t.$1;
    return GestureDetector(
      onTap: () => setState(() {
        _selectedDemoId = t.$1;
        if (_nameController.text.isEmpty || h.demoTemplates.any((x) => x.$3 == _nameController.text)) _nameController.text = t.$3;
        _iconConfig = IconConfig(bgColor: IconConfig.colorForString(t.$2), elements: [IconElement(content: t.$2)]);
      }),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        decoration: BoxDecoration(
          color: sel ? const Color(0xFF0F3460) : const Color(0xFF1A1A2E),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: sel ? const Color(0xFF4FC3F7) : Colors.transparent, width: 1.5),
        ),
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          Text(t.$2, style: const TextStyle(fontSize: 20)),
          Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(t.$3, style: TextStyle(fontWeight: FontWeight.w600, fontSize: 11, color: sel ? Colors.white : Colors.white70)),
            Text(t.$4, style: const TextStyle(fontSize: 9, color: Colors.white38)),
          ]),
        ]),
      ),
    );
  }

  Widget _demoFields() {
    final widgets = <Widget>[
      const Text('Select a demo', style: TextStyle(fontSize: 13, color: Colors.white54)),
      const SizedBox(height: 12),
    ];
    final items = h.demoTemplates;
    int i = 0;
    while (i < items.length) {
      final t = items[i];
      if (t.$1.isEmpty) {
        // Section header
        widgets.add(Padding(
          padding: EdgeInsets.only(top: i == 0 ? 0 : 16, bottom: 8),
          child: Text(t.$3, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.white38)),
        ));
        i++;
      } else if (i + 1 < items.length && items[i + 1].$1.isNotEmpty) {
        // Two items side by side
        widgets.add(Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Row(children: [
            Expanded(child: SizedBox(height: 80, child: _demoCard(t))),
            const SizedBox(width: 8),
            Expanded(child: SizedBox(height: 80, child: _demoCard(items[i + 1]))),
          ]),
        ));
        i += 2;
      } else {
        // Single item (odd one at end of section)
        widgets.add(Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Row(children: [
            Expanded(child: SizedBox(height: 80, child: _demoCard(t))),
            const SizedBox(width: 8),
            const Expanded(child: SizedBox()),
          ]),
        ));
        i++;
      }
    }
    if (_selectedDemoId != null) {
      widgets.add(const SizedBox(height: 12));
      widgets.add(h.buildActionButton(
        label: 'Build ${h.demoTemplates.firstWhere((t) => t.$1 == _selectedDemoId).$3}',
        onPressed: _isBuilding ? null : _buildDemo, icon: Icons.rocket_launch));
    }
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: widgets);
  }

  // ── Building ──
  Widget _buildingView() => SingleChildScrollView(
    padding: const EdgeInsets.fromLTRB(24, 32, 24, 40),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _appHeader('Building...'),
      const SizedBox(height: 24),
      if (_log.isEmpty) const LinearProgressIndicator(color: Color(0xFF4FC3F7), backgroundColor: Color(0xFF1A1A2E)),
      BuildLog(log: _log),
      if (!_isBuilding && _log.isNotEmpty) ...[
        const SizedBox(height: 24),
        h.buildActionButton(label: 'View in My Apps', onPressed: widget.onViewMyApps, icon: Icons.folder_outlined),
        const SizedBox(height: 12),
        h.buildActionButton(label: 'Keep Editing', onPressed: () => setState(() { _showBuildLog = false; _log.clear(); }), icon: Icons.edit, secondary: true),
        const SizedBox(height: 12),
        h.buildActionButton(label: 'Create New App', onPressed: _reset, secondary: true),
      ],
    ]),
  );

  // ── Shared widgets ──
  Widget _appHeader(String sub) => Row(children: [
    Container(width: 44, height: 44,
      decoration: BoxDecoration(color: const Color(0xFF0F3460), borderRadius: BorderRadius.circular(12)),
      child: ClipRRect(borderRadius: BorderRadius.circular(10), child: Image.asset('assets/ic_launcher.png', width: 44, height: 44))),
    const SizedBox(width: 12),
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('iappyxOS', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
      Text(sub, style: const TextStyle(fontSize: 12, color: Colors.white38)),
    ]),
  ]);

  Widget _section(String title, bool expanded, VoidCallback onToggle, {required Widget child, bool done = false}) {
    return Container(
      decoration: BoxDecoration(color: const Color(0xFF1A1A2E), borderRadius: BorderRadius.circular(12)),
      child: Column(children: [
        InkWell(
          onTap: onToggle,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(12)),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
            child: Row(children: [
              done ? const Icon(Icons.check_circle, size: 18, color: Color(0xFF69F0AE))
                   : Icon(expanded ? Icons.expand_less : Icons.expand_more, size: 18, color: Colors.white38),
              const SizedBox(width: 10),
              Expanded(child: Text(title, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600))),
            ]),
          ),
        ),
        if (expanded) Padding(padding: const EdgeInsets.fromLTRB(14, 0, 14, 14), child: child),
      ]),
    );
  }
}

// ── Provider Setup Page ──

class ProviderSetupPage extends StatefulWidget {
  final AiProvider provider;
  const ProviderSetupPage({required this.provider});
  @override
  State<ProviderSetupPage> createState() => ProviderSetupPageState();
}

class ProviderSetupPageState extends State<ProviderSetupPage> {
  late final TextEditingController _keyController;
  final _searchController = TextEditingController();
  late AiProvider _provider;
  List<AiModel> _models = [];
  String _searchQuery = '';
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _provider = AiProvider(
      id: widget.provider.id, name: widget.provider.name, baseUrl: widget.provider.baseUrl,
      apiKey: widget.provider.apiKey, selectedModel: widget.provider.selectedModel,
      models: List.from(widget.provider.models),
    );
    _keyController = TextEditingController(text: _provider.apiKey);
    _models = List.from(_provider.models);
    // Auto-fetch OpenRouter models (public endpoint)
    if (_provider.id == 'openrouter' && _models.isEmpty) _fetchModels();
  }

  @override
  void dispose() {
    _keyController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _fetchModels() async {
    // OpenRouter models are public; Anthropic requires a key
    if (_provider.id != 'openrouter' && _keyController.text.trim().isEmpty) return;
    final apiKey = _keyController.text.trim();
    setState(() { _loading = true; _error = null; });
    try {
      if (_provider.id == 'anthropic') {
        _models = await AiService.fetchAnthropicModels(apiKey);
      } else if (_provider.id == 'openrouter') {
        _models = await AiService.fetchOpenRouterModels(apiKey);
      }
      setState(() => _loading = false);
    } catch (e) {
      setState(() { _loading = false; _error = 'Failed to fetch models: $e'; });
    }
  }

  Future<void> _autoSave() async {
    _provider.apiKey = _keyController.text.trim();
    await Settings.updateProvider(_provider);
    await Settings.setActiveProvider(_provider);
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) await _autoSave();
      },
      child: Scaffold(
      backgroundColor: const Color(0xFF0D0D1A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0D0D1A),
        title: Text(_provider.name, style: const TextStyle(fontSize: 16)),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () async { await _autoSave(); Navigator.pop(context, _provider); },
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('API Key', style: TextStyle(fontSize: 13, color: Colors.white54)),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: TextField(
              controller: _keyController,
              style: const TextStyle(color: Colors.white, fontSize: 13, fontFamily: 'monospace'),
              obscureText: true,
              decoration: InputDecoration(
                hintText: _provider.id == 'anthropic' ? 'sk-ant-...' : 'sk-or-...',
                filled: true, fillColor: const Color(0xFF1A1A2E),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              ),
              onEditingComplete: () { _autoSave(); _fetchModels(); },
            )),
            ...[
              const SizedBox(width: 8),
              GestureDetector(
                onTap: _loading ? null : _fetchModels,
                child: Container(
                  width: 44, height: 44,
                  decoration: BoxDecoration(color: const Color(0xFF0F3460), borderRadius: BorderRadius.circular(10)),
                  child: _loading
                      ? const Padding(padding: EdgeInsets.all(12), child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF4FC3F7)))
                      : const Icon(Icons.refresh, size: 18, color: Color(0xFF4FC3F7)),
                ),
              ),
            ],
          ]),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: const TextStyle(fontSize: 11, color: Color(0xFFFF6B6B))),
          ],

          const SizedBox(height: 20),
          const Text('Model', style: TextStyle(fontSize: 13, color: Colors.white54)),
          const SizedBox(height: 8),

          if (_models.length > 10)
            TextField(
              controller: _searchController,
              style: const TextStyle(color: Colors.white, fontSize: 13),
              decoration: InputDecoration(
                hintText: 'Search models...',
                prefixIcon: const Icon(Icons.search, size: 18, color: Colors.white24),
                filled: true, fillColor: const Color(0xFF1A1A2E),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              ),
              onChanged: (v) => setState(() => _searchQuery = v.toLowerCase()),
            ),
          if (_models.length > 10) const SizedBox(height: 8),

          if (_models.isEmpty)
            GestureDetector(
              onTap: _fetchModels,
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: const Color(0xFF1A1A2E), borderRadius: BorderRadius.circular(10)),
                child: const Text('Enter API key and tap refresh to load models',
                    style: TextStyle(fontSize: 12, color: Colors.white38), textAlign: TextAlign.center),
              ),
            )
          else
            ...(_models.where((m) => _searchQuery.isEmpty || m.name.toLowerCase().contains(_searchQuery) || m.id.toLowerCase().contains(_searchQuery)).map((m) {
              final selected = _provider.selectedModel == m.id;
              return GestureDetector(
                onTap: () { setState(() => _provider.selectedModel = m.id); _autoSave(); },
                child: Container(
                  width: double.infinity,
                  margin: const EdgeInsets.only(bottom: 6),
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  decoration: BoxDecoration(
                    color: selected ? const Color(0xFF0F3460) : const Color(0xFF1A1A2E),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: selected ? const Color(0xFF4FC3F7) : Colors.transparent),
                  ),
                  child: Row(children: [
                    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(m.name, style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600,
                          color: selected ? Colors.white : Colors.white70)),
                      if (m.description != null)
                        Text(m.description!, style: const TextStyle(fontSize: 10, color: Colors.white38)),
                      if (m.contextLength != null || m.pricePer1mTokens != null)
                        Text(
                          [
                            if (m.contextLength != null) '${(m.contextLength! / 1000).round()}K ctx',
                            if (m.pricePer1mTokens != null) '\$${m.pricePer1mTokens!.toStringAsFixed(2)}/1M tokens',
                          ].join(' · '),
                          style: const TextStyle(fontSize: 9, color: Colors.white24),
                        ),
                    ])),
                    if (selected)
                      const Icon(Icons.check_circle, size: 18, color: Color(0xFF4FC3F7)),
                  ]),
                ),
              );
            })),

          const SizedBox(height: 40),
        ]),
      ),
    ),
    );
  }
}

