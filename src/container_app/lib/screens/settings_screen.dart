// MIT License
//
// Copyright (c) 2026 iappyx
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

/// Settings screen for AI provider config, custom prompts, and app management.

import 'dart:io';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:file_picker/file_picker.dart';
import '../services/settings_service.dart';
import '../services/prompt_builder.dart';
import '../services/app_storage.dart';
import '../services/generator.dart';
import '../models/ai_provider.dart';
import 'create_screen.dart' show ProviderSetupPage;

class SettingsScreen extends StatefulWidget {
  final VoidCallback? onAppsImported;
  const SettingsScreen({super.key, this.onAppsImported});
  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  AiProvider? _activeProvider;
  bool _hasCustomPrompt = false;
  bool _promptOutdated = false;
  String _packagePrefix = '';
  final _prefixController = TextEditingController();
  String? _prefixError;
  int _appCount = 0;
  Map<String, dynamic> _keyInfo = {};
  String _stylePreset = 'default';
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _prefixController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final activeProvider = await Settings.getActiveProvider();
    final hasCustom = await Settings.hasCustomPrompt();
    final promptOutdated = await PromptBuilder.isPromptOutdated();
    if (!hasCustom) await PromptBuilder.markPromptAsSeen(); // track hash on first launch / after reset
    final prefix = await Settings.getPackagePrefix();
    final apps = await AppStorage.loadAll();
    Map<String, dynamic> keyInfo = {};
    try { keyInfo = await Generator.getKeyInfo(); } catch (_) {}
    final style = await Settings.getCssStylePreset();
    if (mounted) {
      setState(() {
        _activeProvider = activeProvider;
        _hasCustomPrompt = hasCustom;
        _promptOutdated = promptOutdated;
        _packagePrefix = prefix;
        _prefixController.text = prefix;
        _appCount = apps.length;
        _keyInfo = keyInfo;
        _stylePreset = style;
        _loaded = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: _loaded ? SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 32, 24, 40),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(children: [
                Container(
                  width: 44, height: 44,
                  decoration: BoxDecoration(
                    color: const Color(0xFF0F3460),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: Image.asset('assets/ic_launcher.png', width: 44, height: 44),
                  ),
                ),
                const SizedBox(width: 12),
                const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('iappyxOS', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
                  Text('Settings', style: TextStyle(fontSize: 12, color: Colors.white38)),
                ]),
              ]),

              const SizedBox(height: 24),

              // Support
              _card(children: [
                InkWell(
                  onTap: () { try { Generator.openUrl('https://ko-fi.com/iappyx'); } catch (_) {} },
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
                    child: Row(
                      children: [
                        Container(
                          width: 36, height: 36,
                          decoration: BoxDecoration(
                            color: const Color(0xFFFF5E5B),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Center(child: Text('\u2764', style: TextStyle(fontSize: 18))),
                        ),
                        const SizedBox(width: 12),
                        const Expanded(child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Support iappyxOS', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                            SizedBox(height: 2),
                            Text('Buy me a coffee on Ko-fi', style: TextStyle(fontSize: 11, color: Colors.white38)),
                          ],
                        )),
                        const Icon(Icons.open_in_new, size: 16, color: Colors.white24),
                      ],
                    ),
                  ),
                ),
              ]),

              const SizedBox(height: 24),

              // AI Prompt
              _sectionTitle('AI System Prompt'),
              _card(children: [
                _row('Status', _hasCustomPrompt ? (_promptOutdated ? 'Custom (outdated)' : 'Custom') : 'Default'),
                if (_promptOutdated) ...[
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                    child: Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: const Color(0xFF3E2723),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Row(children: [
                        Icon(Icons.warning_amber, color: Color(0xFFFFB74D), size: 18),
                        SizedBox(width: 8),
                        Expanded(child: Text('New bridges have been added. Reset to default to get them.',
                          style: TextStyle(fontSize: 11, color: Color(0xFFFFB74D)))),
                      ]),
                    ),
                  ),
                ],
                const Divider(height: 1, color: Color(0xFF0D0D1A)),
                _actionRow('View / Edit Prompt', Icons.edit_outlined, _editPrompt),
                if (_hasCustomPrompt) ...[
                  const Divider(height: 1, color: Color(0xFF0D0D1A)),
                  _actionRow('Reset to Default', Icons.restore, _resetPrompt, color: const Color(0xFFFF6B6B)),
                ],
              ]),

              const SizedBox(height: 24),

              // App Style
              _sectionTitle('App Style'),
              _card(children: [
                Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Text('CSS style applied to all generated apps', style: TextStyle(fontSize: 11, color: Colors.white38)),
                    const SizedBox(height: 12),
                    ..._styleOptions.map((opt) {
                      final selected = _stylePreset == opt.id;
                      return GestureDetector(
                        onTap: () {
                          setState(() => _stylePreset = opt.id);
                          Settings.setCssStylePreset(opt.id);
                        },
                        child: Container(
                          margin: const EdgeInsets.only(bottom: 10),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: selected ? const Color(0xFF4FC3F7) : Colors.transparent, width: 1.5),
                          ),
                          child: Column(children: [
                            // Preview
                            if (opt.id != 'default')
                              ClipRRect(
                                borderRadius: const BorderRadius.vertical(top: Radius.circular(11)),
                                child: Container(
                                  width: double.infinity,
                                  padding: const EdgeInsets.all(12),
                                  color: const Color(0xFF0D0D1A),
                                  child: opt.preview,
                                ),
                              ),
                            // Label bar
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                              decoration: BoxDecoration(
                                color: selected ? const Color(0xFF0F3460) : const Color(0xFF1A1A2E),
                                borderRadius: BorderRadius.vertical(
                                  top: opt.id == 'default' ? const Radius.circular(11) : Radius.zero,
                                  bottom: const Radius.circular(11),
                                ),
                              ),
                              child: Row(children: [
                                Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                  Text(opt.title, style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: selected ? Colors.white : Colors.white70)),
                                  Text(opt.subtitle, style: const TextStyle(fontSize: 10, color: Colors.white38)),
                                ])),
                                if (selected) const Icon(Icons.check_circle, color: Color(0xFF4FC3F7), size: 18),
                              ]),
                            ),
                          ]),
                        ),
                      );
                    }),
                  ]),
                ),
              ]),

              const SizedBox(height: 24),

              // AI Configuration
              _sectionTitle('AI Configuration'),
              _card(children: [
                Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Text('Provider', style: TextStyle(fontSize: 11, color: Colors.white38)),
                    const SizedBox(height: 6),
                    Row(children: [
                      _providerButton('anthropic', 'Anthropic', Icons.auto_awesome),
                      const SizedBox(width: 8),
                      _providerButton('openrouter', 'OpenRouter', Icons.shuffle),
                    ]),
                    const SizedBox(height: 14),
                    _actionRow('Configure API key & model', Icons.settings, () async {
                      final provider = _activeProvider ?? AiProvider.anthropic();
                      final result = await Navigator.push<AiProvider>(
                        context,
                        MaterialPageRoute(builder: (_) => ProviderSetupPage(provider: provider)),
                      );
                      if (result != null) {
                        await Settings.setActiveProvider(result);
                        _load();
                      }
                    }),
                    if (_activeProvider != null && _activeProvider!.isConfigured) ...[
                      const Divider(height: 16, color: Color(0xFF0D0D1A)),
                      Row(children: [
                        const Icon(Icons.check_circle, size: 14, color: Color(0xFF69F0AE)),
                        const SizedBox(width: 8),
                        Expanded(child: Text(
                          '${_activeProvider!.name} · ${_activeProvider!.selectedModel.split('/').last}',
                          style: const TextStyle(fontSize: 12, color: Colors.white54),
                          maxLines: 1, overflow: TextOverflow.ellipsis,
                        )),
                      ]),
                    ] else ...[
                      const Divider(height: 16, color: Color(0xFF0D0D1A)),
                      const Text('Not configured — AI API flow unavailable',
                          style: TextStyle(fontSize: 11, color: Colors.white24)),
                    ],
                  ]),
                ),
              ]),

              const SizedBox(height: 24),

              // App ID Prefix
              _sectionTitle('App ID Prefix'),
              _card(children: [
                Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('All your apps get a unique ID starting with this prefix (e.g., myprefix.appname12345).',
                          style: TextStyle(fontSize: 11, color: Colors.white38)),
                      const SizedBox(height: 10),
                      TextField(
                        controller: _prefixController,
                        style: const TextStyle(color: Colors.white, fontSize: 13, fontFamily: 'monospace'),
                        decoration: InputDecoration(
                          filled: true, fillColor: const Color(0xFF0D0D1A),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none),
                          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                          errorText: _prefixError,
                          suffixText: '${_prefixController.text.length}/${Settings.maxPrefixLength}',
                          suffixStyle: const TextStyle(fontSize: 10, color: Colors.white24),
                        ),
                        onChanged: (v) {
                          final val = v.toLowerCase();
                          if (val != v) {
                            _prefixController.value = TextEditingValue(
                              text: val,
                              selection: TextSelection.collapsed(offset: val.length),
                            );
                          }
                          setState(() {
                            if (val.isEmpty) {
                              _prefixError = null;
                            } else if (!Settings.validatePrefix(val)) {
                              _prefixError = 'Lowercase letters, digits, dots. Start with letter. Max ${Settings.maxPrefixLength} chars.';
                            } else {
                              _prefixError = null;
                            }
                          });
                        },
                        onSubmitted: (v) => _savePrefix(),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Preview: ${_prefixController.text.isNotEmpty && Settings.validatePrefix(_prefixController.text) ? _prefixController.text : _packagePrefix}.myapp12345678',
                        style: const TextStyle(fontSize: 11, fontFamily: 'monospace', color: Colors.white24),
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Expanded(
                            child: FilledButton(
                              onPressed: _prefixError == null ? _savePrefix : null,
                              style: FilledButton.styleFrom(
                                backgroundColor: const Color(0xFF0F3460),
                                padding: const EdgeInsets.symmetric(vertical: 10),
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                              ),
                              child: const Text('Save', style: TextStyle(fontSize: 13)),
                            ),
                          ),
                          const SizedBox(width: 8),
                          TextButton(
                            onPressed: () async {
                              final prefs = await Settings.getPrefs();
                              await prefs.remove('package_prefix');
                              final fresh = await Settings.getPackagePrefix();
                              _prefixController.text = fresh;
                              setState(() { _packagePrefix = fresh; _prefixError = null; });
                              if (mounted) ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: Text('Prefix reset to $fresh')),
                              );
                            },
                            child: const Text('Randomize', style: TextStyle(fontSize: 12, color: Colors.white38)),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ]),

              const SizedBox(height: 24),

              // Data Management
              _sectionTitle('Data Management'),
              _card(children: [
                _row('Saved Apps', '$_appCount'),
                const Divider(height: 1, color: Color(0xFF0D0D1A)),
                _actionRow('Export Apps', Icons.upload, _exportApps),
                const Divider(height: 1, color: Color(0xFF0D0D1A)),
                _actionRow('Import Apps', Icons.download, _importApps),
                const Divider(height: 1, color: Color(0xFF0D0D1A)),
                _actionRow('Clear All Apps', Icons.delete_forever, _clearApps, color: const Color(0xFFFF6B6B)),
              ]),

              const SizedBox(height: 24),

              // Signing Key
              _sectionTitle('Signing Key'),
              _card(children: [
                _row('Status', _keyInfo['exists'] == true ? 'Active' : 'Not found'),
                if (_keyInfo['fingerprint'] != null) ...[
                  const Divider(height: 1, color: Color(0xFF0D0D1A)),
                  Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Fingerprint', style: TextStyle(fontSize: 11, color: Colors.white38)),
                        const SizedBox(height: 4),
                        GestureDetector(
                          onTap: () {
                            Clipboard.setData(ClipboardData(text: _keyInfo['fingerprint']));
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Fingerprint copied.')),
                            );
                          },
                          child: Row(children: [
                            Expanded(child: Text(
                              _keyInfo['fingerprint'],
                              style: const TextStyle(fontSize: 11, fontFamily: 'monospace', color: Colors.white54),
                            )),
                            const SizedBox(width: 8),
                            const Icon(Icons.copy, size: 14, color: Colors.white24),
                          ]),
                        ),
                      ],
                    ),
                  ),
                ],
              ]),

              const SizedBox(height: 24),

              // Showcase
              _sectionTitle('Showcase'),
              _card(children: [
                _actionRow('GitHub Token', Icons.key, () async {
                  final current = await Settings.getGithubToken();
                  final controller = TextEditingController(text: current);
                  final String? result;
                  try {
                    result = await showDialog<String>(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        backgroundColor: const Color(0xFF1A1A2E),
                        title: const Text('GitHub Token'),
                        content: Column(mainAxisSize: MainAxisSize.min, children: [
                          const Text('Needed for Showcase submissions. Create at github.com/settings/tokens with public_repo scope.',
                            style: TextStyle(fontSize: 12, color: Colors.white54)),
                          const SizedBox(height: 12),
                          TextField(controller: controller, style: const TextStyle(color: Colors.white, fontSize: 13),
                            obscureText: true,
                            decoration: const InputDecoration(hintText: 'ghp_...', filled: true, fillColor: Color(0xFF0D0D1A),
                              border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(8)), borderSide: BorderSide.none))),
                        ]),
                        actions: [
                          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
                          TextButton(onPressed: () => Navigator.pop(ctx, controller.text.trim()), child: const Text('Save')),
                        ],
                      ),
                    );
                  } finally {
                    controller.dispose();
                  }
                  if (result != null) {
                    await Settings.setGithubToken(result);
                    if (mounted) ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(result.isEmpty ? 'GitHub token removed' : 'GitHub token saved')));
                  }
                }),
              ]),

              const SizedBox(height: 24),

              // About
              _sectionTitle('About'),
              _card(children: [
                _row('Version', '0.1.0'),
                const Divider(height: 1, color: Color(0xFF0D0D1A)),
                _row('Demo Templates', '23'),
                const Divider(height: 1, color: Color(0xFF0D0D1A)),
                _row('Platform', 'Android (on-device)'),
              ]),

              const SizedBox(height: 40),
            ],
          ),
        ) : const Center(child: CircularProgressIndicator(color: Color(0xFF4FC3F7))),
      ),
    );
  }

  // ── UI helpers ──

  Widget _providerButton(String id, String name, IconData icon) {
    final selected = _activeProvider?.id == id;
    return Expanded(child: GestureDetector(
      onTap: () async {
        if (_activeProvider?.id == id) return; // already selected
        final provider = id == 'anthropic' ? AiProvider.anthropic() : AiProvider.openRouter();
        // Restore previously saved key if switching back
        final saved = await Settings.getProvider(id);
        if (saved != null && saved.apiKey.isNotEmpty) {
          provider.apiKey = saved.apiKey;
          provider.selectedModel = saved.selectedModel;
          provider.models = saved.models;
        }
        await Settings.setActiveProvider(provider);
        _load();
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: selected ? const Color(0xFF0F3460) : const Color(0xFF0D0D1A),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: selected ? const Color(0xFF4FC3F7) : Colors.white12),
        ),
        child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          Icon(icon, size: 16, color: selected ? const Color(0xFF4FC3F7) : Colors.white38),
          const SizedBox(width: 8),
          Text(name, style: TextStyle(fontSize: 13, color: selected ? Colors.white : Colors.white54)),
        ]),
      ),
    ));
  }

  Widget _sectionTitle(String title) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Text(title, style: const TextStyle(fontSize: 13, color: Colors.white54, fontWeight: FontWeight.w600)),
  );

  Widget _card({required List<Widget> children}) => Container(
    decoration: BoxDecoration(
      color: const Color(0xFF1A1A2E),
      borderRadius: BorderRadius.circular(14),
    ),
    clipBehavior: Clip.antiAlias,
    child: Column(children: children),
  );

  Widget _row(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(fontSize: 14)),
        Text(value, style: const TextStyle(fontSize: 13, color: Colors.white54)),
      ],
    ),
  );

  Widget _actionRow(String label, IconData icon, VoidCallback onTap, {Color? color}) => InkWell(
    onTap: onTap,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(
        children: [
          Icon(icon, size: 18, color: color ?? const Color(0xFF4FC3F7)),
          const SizedBox(width: 10),
          Expanded(child: Text(label, style: TextStyle(fontSize: 14, color: color))),
          Icon(Icons.chevron_right, size: 18, color: color ?? Colors.white24),
        ],
      ),
    ),
  );

  // ── Actions ──

  Future<void> _savePrefix() async {
    final val = _prefixController.text.trim();
    if (val.isEmpty || !Settings.validatePrefix(val)) return;
    await Settings.setPackagePrefix(val);
    if (!mounted) return;
    setState(() { _packagePrefix = val; });
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('App ID prefix saved.'), backgroundColor: Color(0xFF1A1A2E), duration: Duration(seconds: 4)),
    );
  }

  Future<void> _editPrompt() async {
    final current = await PromptBuilder.getSystemPrompt();
    final controller = TextEditingController(text: current);
    if (!mounted) { controller.dispose(); return; }
    final result = await Navigator.push<String>(context, MaterialPageRoute(
      builder: (_) => _PromptEditorPage(controller: controller),
    ));
    controller.dispose();
    if (result != null) {
      final defaultPrompt = await PromptBuilder.getDefaultPrompt();
      if (result == defaultPrompt) {
        await Settings.setCustomPrompt(null);
      } else {
        await Settings.setCustomPrompt(result);
      }
      _load();
    }
  }

  Future<void> _resetPrompt() async {
    await Settings.setCustomPrompt(null);
    await PromptBuilder.markPromptAsSeen();
    _load();
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Prompt reset to default.'), backgroundColor: Color(0xFF1A1A2E), duration: Duration(seconds: 4)),
    );
  }

  Future<void> _exportApps() async {
    final apps = await AppStorage.loadAll();
    if (apps.isEmpty) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No apps to export.'), backgroundColor: Color(0xFF1A1A2E), duration: Duration(seconds: 4)),
      );
      return;
    }
    final json = jsonEncode(apps.map((a) => a.toJson()).toList());
    try {
      await Generator.shareText(content: json, filename: 'iappyxos_apps.json');
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Export failed: $e'), backgroundColor: const Color(0xFFFF6B6B)),
      );
    }
  }

  Future<void> _importApps() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['json'],
    );
    if (result == null || result.files.single.path == null) return;
    try {
      final content = await File(result.files.single.path!).readAsString();
      final list = jsonDecode(content) as List;
      final newApps = list.map((item) => AppData.fromJson(item as Map<String, dynamic>)).toList();
      await AppStorage.importBatch(newApps);
      final imported = newApps.length;
      widget.onAppsImported?.call();
      _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Imported $imported apps.')),
      );
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('Invalid file. Expected iappyxOS JSON export.', style: TextStyle(color: Colors.white)), backgroundColor: const Color(0xFFFF6B6B), duration: const Duration(seconds: 4)),
      );
    }
  }

  Future<void> _clearApps() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        title: const Text('Clear all saved apps?'),
        content: const Text('This removes all app data from iappyxOS. Installed apps on your device are not affected.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Clear All', style: TextStyle(color: Color(0xFFFF6B6B))),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      try {
        final apps = await AppStorage.loadAll();
        for (final app in apps) {
          await AppStorage.delete(app.id);
        }
      } catch (_) {}
      widget.onAppsImported?.call();
      _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('All apps cleared.'), backgroundColor: Color(0xFF1A1A2E), duration: Duration(seconds: 4)),
      );
    }
  }
}

// ── Prompt editor page ──

class _PromptEditorPage extends StatelessWidget {
  final TextEditingController controller;
  const _PromptEditorPage({required this.controller});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0D0D1A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0D0D1A),
        title: const Text('System Prompt', style: TextStyle(fontSize: 16)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, controller.text),
            child: const Text('Save', style: TextStyle(color: Color(0xFF4FC3F7), fontSize: 16)),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: TextField(
          controller: controller,
          maxLines: null,
          expands: true,
          style: const TextStyle(fontSize: 12, fontFamily: 'monospace', color: Colors.white70),
          decoration: const InputDecoration(
            border: InputBorder.none,
            filled: true,
            fillColor: Color(0xFF0A0A14),
          ),
          textAlignVertical: TextAlignVertical.top,
        ),
      ),
    );
  }
}

class _StyleOption {
  final String id, title, subtitle;
  final Widget preview;
  const _StyleOption({required this.id, required this.title, required this.subtitle, required this.preview});
}

final _styleOptions = [
  _StyleOption(
    id: 'default',
    title: 'None',
    subtitle: 'Let the AI decide the styling',
    preview: const SizedBox.shrink(),
  ),
  _StyleOption(
    id: 'material',
    title: 'Material',
    subtitle: 'Ripple effects, elevation, Roboto font',
    preview: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0xFF1A1A2E),
          borderRadius: BorderRadius.circular(12),
          boxShadow: const [BoxShadow(color: Color(0x4D000000), blurRadius: 4, offset: Offset(0, 2))],
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('Sample Card', style: TextStyle(fontFamily: 'Roboto', fontSize: 13, fontWeight: FontWeight.w600, color: Colors.white)),
          const SizedBox(height: 8),
          Container(
            width: double.infinity, height: 36,
            decoration: BoxDecoration(border: Border(bottom: BorderSide(color: Colors.white24, width: 1))),
            alignment: Alignment.centerLeft,
            child: const Text('Text input...', style: TextStyle(fontFamily: 'Roboto', fontSize: 12, color: Colors.white24)),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
            decoration: BoxDecoration(
              color: const Color(0xFF0F3460),
              borderRadius: BorderRadius.circular(24),
              boxShadow: const [BoxShadow(color: Color(0x4D000000), blurRadius: 3, offset: Offset(0, 1))],
            ),
            child: const Text('Action', style: TextStyle(fontFamily: 'Roboto', fontSize: 12, color: Colors.white)),
          ),
        ]),
      ),
    ]),
  ),
  _StyleOption(
    id: 'glassmorphic',
    title: 'Glassmorphic',
    subtitle: 'Frosted glass, blur, subtle borders',
    preview: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0x0DFFFFFF),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0x14FFFFFF)),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('Sample Card', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Colors.white)),
          const SizedBox(height: 8),
          Container(
            width: double.infinity, height: 36, padding: const EdgeInsets.symmetric(horizontal: 10),
            decoration: BoxDecoration(
              color: const Color(0x4D000000),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0x1AFFFFFF)),
            ),
            alignment: Alignment.centerLeft,
            child: const Text('Text input...', style: TextStyle(fontSize: 12, color: Colors.white24)),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
            decoration: BoxDecoration(
              color: const Color(0x990F3460),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0x334FC3F7)),
            ),
            child: const Text('Action', style: TextStyle(fontSize: 12, color: Colors.white)),
          ),
        ]),
      ),
    ]),
  ),
  _StyleOption(
    id: 'dynamic',
    title: 'Dynamic',
    subtitle: 'Uses your device\'s Material You wallpaper colors (Android 12+)',
    preview: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0xFF1C1B1F),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Container(width: 12, height: 12, decoration: const BoxDecoration(color: Color(0xFFD0BCFF), shape: BoxShape.circle)),
            const SizedBox(width: 6),
            Container(width: 12, height: 12, decoration: const BoxDecoration(color: Color(0xFFCCC2DC), shape: BoxShape.circle)),
            const SizedBox(width: 6),
            Container(width: 12, height: 12, decoration: const BoxDecoration(color: Color(0xFFEFB8C8), shape: BoxShape.circle)),
            const SizedBox(width: 8),
            const Text('Your wallpaper colors', style: TextStyle(fontSize: 10, color: Colors.white38)),
          ]),
          const SizedBox(height: 10),
          Container(
            width: double.infinity, height: 36, padding: const EdgeInsets.symmetric(horizontal: 10),
            decoration: BoxDecoration(
              color: const Color(0xFF2B2930),
              borderRadius: BorderRadius.circular(8),
            ),
            alignment: Alignment.centerLeft,
            child: const Text('Text input...', style: TextStyle(fontSize: 12, color: Colors.white24)),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
            decoration: BoxDecoration(
              color: const Color(0xFF4F378B),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Text('Action', style: TextStyle(fontSize: 12, color: Colors.white)),
          ),
        ]),
      ),
    ]),
  ),
  _StyleOption(
    id: 'minimal',
    title: 'Minimal',
    subtitle: 'Ultra-clean, outline buttons, light fonts',
    preview: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: const Color(0xFF1A1A2E),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: const Color(0x0FFFFFFF)),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('Sample Card', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w300, color: Colors.white)),
          const SizedBox(height: 10),
          Container(
            width: double.infinity,
            decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Color(0x26FFFFFF), width: 1))),
            padding: const EdgeInsets.only(bottom: 8),
            child: const Text('Text input...', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w300, color: Colors.white24)),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0xFF4FC3F7)),
            ),
            child: const Text('Action', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w400, color: Color(0xFF4FC3F7))),
          ),
        ]),
      ),
    ]),
  ),
];
