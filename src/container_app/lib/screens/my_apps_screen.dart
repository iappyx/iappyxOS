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

/// Lists installed apps with options to rebuild, edit, share, and delete.

import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/app_storage.dart';
import '../services/generator.dart';
import '../services/p2p_service.dart';
import '../widgets/build_log.dart';
import '../models/icon_config.dart';
import 'icon_editor_screen.dart';
import 'qr_transfer_screen.dart';
import '../services/error_helper.dart';
import '../services/github_service.dart';
import '../services/settings_service.dart';

class MyAppsScreen extends StatefulWidget {
  final void Function(AppData app)? onEditApp;
  final VoidCallback? onCreateTap;
  const MyAppsScreen({super.key, this.onEditApp, this.onCreateTap});

  @override
  State<MyAppsScreen> createState() => MyAppsScreenState();
}

class MyAppsScreenState extends State<MyAppsScreen> {
  List<AppData> _apps = [];
  Map<String, int> _versionCounts = {};
  bool _loading = true;
  String? _rebuildingId;
  final List<String> _log = [];

  @override
  void initState() {
    super.initState();
    refresh();
  }

  Future<void> refresh() async {
    final apps = await AppStorage.loadAll();
    final counts = <String, int>{};
    for (final app in apps) {
      final versions = await AppStorage.getVersions(app.id);
      if (versions.isNotEmpty) {
        // History is capped at 10 entries but version numbers keep incrementing.
        // Use the highest stored version, not list length. Current = latest stored + 1.
        final latest = (versions.last['version'] as int?) ?? versions.length;
        counts[app.id] = latest + 1;
      }
    }
    if (mounted) setState(() { _apps = apps; _versionCounts = counts; _loading = false; });
  }

  void _addLog(String msg) { if (mounted) setState(() => _log.add(msg)); }

  Future<void> _rebuild(AppData app) async {
    if (_rebuildingId != null) return; // already rebuilding
    if (app.packageName.isNotEmpty) {
      final ok = await Generator.handleSignatureConflict(packageName: app.packageName, context: context);
      if (!ok) return;
    }
    setState(() { _rebuildingId = app.id; _log.clear(); });
    try {
      final ic = app.iconConfig.isNotEmpty ? app.iconConfig : null;
      BuildResult result;
      if (app.templateId.isNotEmpty) {
        result = await Generator.generateFromTemplate(
          label: app.name,
          templateId: app.templateId,
          packageName: app.packageName,
          iconConfig: ic,
          onProgress: _addLog,
        );
      } else {
        result = await Generator.injectHtml(
          label: app.name,
          htmlContent: app.html,
          packageName: app.packageName,
          iconConfig: ic,
          firebaseConfig: app.firebaseConfig.isNotEmpty ? app.firebaseConfig : null,
          webOnly: app.appType == 'web' || app.description.startsWith('Web app: '),
          onProgress: _addLog,
        );
      }
      // Update stored apkPath and packageName
      await AppStorage.save(app.copyWith(
        apkPath: result.apkPath,
        packageName: result.packageName,
        updatedAt: DateTime.now(),
      ));
      refresh();
    } on PlatformException catch (e) {
      if (e.message != null && e.message!.contains('SIGNATURE_CONFLICT:')) {
        final pkg = e.message!.split('SIGNATURE_CONFLICT:').last;
        _addLog('\u26A0 App signed by another device. Uninstall the old version first.');
        await Generator.handleSignatureConflict(packageName: pkg, context: context);
      } else {
        final err = friendlyError(e.message); _addLog('\u274C ${err.message}'); if (err.hint != null) _addLog('   ${err.hint}');
      }
    } catch (e) {
      _addLog('\u274C Unexpected error: $e');
    } finally {
      if (mounted) setState(() => _rebuildingId = null);
    }
  }

  Future<void> _delete(AppData app) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        title: const Text('Delete saved app?'),
        content: Text('Remove "${app.name}" from your saved apps. This does not uninstall the app from your device.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete', style: TextStyle(color: Color(0xFFFF6B6B))),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await AppStorage.delete(app.id);
      refresh();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('"${app.name}" deleted.')),
        );
      }
    }
  }

  // Known limitation: uninstall triggers system dialog asynchronously, so the app data
  // is deleted immediately without waiting for the user to confirm the system uninstall.
  Future<void> _removeCompletely(AppData app) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        title: const Text('Remove completely?'),
        content: Text('Uninstall "${app.name}" from your device AND remove it from iappyxOS.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Remove', style: TextStyle(color: Color(0xFFFF6B6B))),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      try { await Generator.uninstallApp(packageName: app.packageName); } catch (_) {}
      await AppStorage.delete(app.id);
      refresh();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('"${app.name}" removed completely.')),
        );
      }
    }
  }

  void _showMoreMenu(AppData app) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1A1A2E),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(width: 40, height: 4, margin: const EdgeInsets.only(bottom: 12),
                decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2))),
              ListTile(
                leading: const Icon(Icons.share, color: Color(0xFF4FC3F7)),
                title: const Text('Share'),
                onTap: () { Navigator.pop(ctx); _showShareSheet(app); },
              ),
              if (app.html.isNotEmpty && app.templateId.isEmpty)
                ListTile(
                  leading: const Icon(Icons.history, color: Colors.white54),
                  title: const Text('Version History'),
                  onTap: () { Navigator.pop(ctx); _showVersionHistory(app); },
                ),
              if (app.packageName.isNotEmpty)
                ListTile(
                  leading: const Icon(Icons.delete_forever, color: Colors.white54),
                  title: const Text('Uninstall from device'),
                  onTap: () { Navigator.pop(ctx); _uninstall(app); },
                ),
              ListTile(
                leading: const Icon(Icons.delete_outline, color: Color(0xFFFF6B6B)),
                title: const Text('Delete from iappyxOS', style: TextStyle(color: Color(0xFFFF6B6B))),
                onTap: () { Navigator.pop(ctx); _delete(app); },
              ),
              if (app.packageName.isNotEmpty)
                ListTile(
                  leading: const Icon(Icons.delete_sweep, color: Color(0xFFFF6B6B)),
                  title: const Text('Remove completely', style: TextStyle(color: Color(0xFFFF6B6B))),
                  onTap: () { Navigator.pop(ctx); _removeCompletely(app); },
                ),
            ],
          ),
        ),
      ),
    );
  }

  void _showShareSheet(AppData app) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1A1A2E),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 40, height: 4, margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: Colors.white24,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              ListTile(
                leading: const Icon(Icons.android, color: Color(0xFF4FC3F7)),
                title: const Text('Share APK'),
                subtitle: const Text('Send the installable app', style: TextStyle(fontSize: 12, color: Colors.white38)),
                onTap: () async {
                  Navigator.pop(ctx);
                  final path = await _resolveApkPath(app);
                  if (path != null) Generator.shareFile(path: path);
                },
              ),
              ListTile(
                leading: const Icon(Icons.wifi_tethering, color: Color(0xFF4FC3F7)),
                title: const Text('Share Nearby'),
                subtitle: const Text('Send via WiFi Direct to another device', style: TextStyle(fontSize: 12, color: Colors.white38)),
                onTap: () {
                  Navigator.pop(ctx);
                  _showShareNearbySheet(app);
                },
              ),
              if (app.html.isNotEmpty)
                ListTile(
                  leading: const Icon(Icons.qr_code_2, color: Color(0xFF4FC3F7)),
                  title: const Text('Share via QR'),
                  subtitle: const Text('Transfer app with animated QR codes', style: TextStyle(fontSize: 12, color: Colors.white38)),
                  onTap: () {
                    Navigator.pop(ctx);
                    Navigator.push(context, MaterialPageRoute(builder: (_) => QRSendScreen(app: app)));
                  },
                ),
              if (app.html.isNotEmpty) ...[
                ListTile(
                  leading: const Icon(Icons.code, color: Color(0xFF4FC3F7)),
                  title: const Text('Share HTML'),
                  subtitle: const Text('Send the source code', style: TextStyle(fontSize: 12, color: Colors.white38)),
                  onTap: () {
                    Navigator.pop(ctx);
                    final safeName = app.name.replaceAll(RegExp(r'[^\w]'), '_').toLowerCase();
                    Generator.shareText(content: app.html, filename: '$safeName.html');
                  },
                ),
                ListTile(
                  leading: const Icon(Icons.copy, color: Colors.white54),
                  title: const Text('Copy HTML to clipboard'),
                  onTap: () {
                    Navigator.pop(ctx);
                    Clipboard.setData(ClipboardData(text: app.html));
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('HTML copied to clipboard.'), backgroundColor: Color(0xFF1A1A2E), duration: Duration(seconds: 4)),
                    );
                },
              ),
              if (app.html.isNotEmpty && !app.description.startsWith('Web app: ') && app.templateId.isEmpty)
                ListTile(
                  leading: const Icon(Icons.publish, color: Color(0xFF4FC3F7)),
                  title: const Text('Submit to Showcase'),
                  subtitle: const Text('Share with the community via GitHub PR', style: TextStyle(fontSize: 12, color: Colors.white38)),
                  onTap: () { Navigator.pop(ctx); _submitToShowcase(app); },
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _submitToShowcase(AppData app) async {
    final token = await Settings.getGithubToken();
    if (token.isEmpty) {
      if (!mounted) return;
      await showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          backgroundColor: const Color(0xFF1A1A2E),
          title: const Text('GitHub Token Required'),
          content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('To submit apps to the Showcase, add your GitHub token in Settings.',
              style: TextStyle(fontSize: 14, color: Colors.white70)),
            const SizedBox(height: 12),
            GestureDetector(
              onTap: () { try { Generator.openUrl('https://github.com/settings/tokens'); } catch (_) {} },
              child: const Text('github.com/settings/tokens',
                style: TextStyle(fontSize: 14, color: Color(0xFF4FC3F7), decoration: TextDecoration.underline)),
            ),
            const SizedBox(height: 8),
            const Text('Create a token with public_repo scope.',
              style: TextStyle(fontSize: 12, color: Colors.white38)),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('OK')),
          ],
        ),
      );
      return;
    }

    // Auto-detect bridges
    final bridgeMatches = RegExp(r'iappyx\.(\w+)\.\w+').allMatches(app.html);
    final bridges = bridgeMatches.map((m) => m.group(1)!).toSet();
    if (app.html.contains('iappyx.save(') || app.html.contains('iappyx.load(') || app.html.contains('iappyx.remove(')) bridges.add('storage');
    final bridgeList = bridges.toList()..sort();

    final nameController = TextEditingController(text: app.name);
    final descController = TextEditingController(text: app.description);

    final confirmed = await showModalBottomSheet<bool>(
      context: context,
      backgroundColor: const Color(0xFF1A1A2E),
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(24, 20, 24, MediaQuery.of(ctx).viewInsets.bottom + 32),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Center(child: Container(width: 40, height: 4, decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 20),
          const Text('Submit to Showcase', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          TextField(controller: nameController, style: const TextStyle(color: Colors.white),
            decoration: const InputDecoration(labelText: 'App name', filled: true, fillColor: Color(0xFF0D0D1A),
              border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(8)), borderSide: BorderSide.none))),
          const SizedBox(height: 12),
          TextField(controller: descController, style: const TextStyle(color: Colors.white), maxLines: 2,
            decoration: const InputDecoration(labelText: 'Short description', filled: true, fillColor: Color(0xFF0D0D1A),
              border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(8)), borderSide: BorderSide.none))),
          const SizedBox(height: 12),
          Wrap(
            spacing: 6, runSpacing: 6,
            children: bridgeList.map((b) => Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(color: const Color(0xFF0D0D1A), borderRadius: BorderRadius.circular(12)),
              child: Text(b, style: const TextStyle(fontSize: 11, color: Color(0xFF4FC3F7))),
            )).toList(),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFF4FC3F7), foregroundColor: const Color(0xFF0D0D1A),
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
              child: const Text('Submit', style: TextStyle(fontWeight: FontWeight.w600)),
            ),
          ),
        ]),
      ),
    );

    if (confirmed != true || !mounted) return;

    // Show loading
    showDialog(context: context, barrierDismissible: false,
      builder: (_) => const Center(child: CircularProgressIndicator(color: Color(0xFF4FC3F7))));

    try {
      var slug = nameController.text.trim().toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '-').replaceAll(RegExp(r'-+'), '-').replaceAll(RegExp(r'^-|-$'), '');
      if (slug.isEmpty) slug = 'app-${DateTime.now().millisecondsSinceEpoch}';
      final github = GithubService(token);
      final prUrl = await github.submitApp(
        slug: slug,
        appHtml: AppStorage.tagHtml(app.html),
        name: nameController.text.trim(),
        description: descController.text.trim(),
        author: (await github.getUsername()),
        bridges: bridgeList,
      );
      if (mounted) {
        Navigator.pop(context); // dismiss loading
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('PR created! $prUrl'), backgroundColor: const Color(0xFF1A1A2E), duration: const Duration(seconds: 6)));
      }
    } catch (e) {
      if (mounted) {
        Navigator.pop(context); // dismiss loading
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed: $e'), backgroundColor: const Color(0xFF1A1A2E)));
      }
    }
  }

  Future<void> _showVersionHistory(AppData app) async {
    final versions = await AppStorage.getVersions(app.id);
    if (versions.isEmpty) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No version history yet. History is saved when you rebuild with changes.'), backgroundColor: Color(0xFF1A1A2E)));
      return;
    }
    if (!mounted) return;
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1A1A2E),
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (ctx) => _VersionHistorySheet(
        appName: app.name,
        appId: app.id,
        versions: versions,
        onRestore: (html) {
          Navigator.pop(ctx);
          widget.onEditApp?.call(AppData(
            id: app.id, name: app.name, description: app.description, prompt: app.prompt,
            html: html, appType: app.appType, templateId: app.templateId,
            packageName: app.packageName, apkPath: app.apkPath, iconConfig: app.iconConfig,
            firebaseConfig: app.firebaseConfig,
            createdAt: app.createdAt, updatedAt: app.updatedAt,
          ));
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator(color: Color(0xFF4FC3F7)))
            : CustomScrollView(slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(24, 32, 24, 0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
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
                          const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text('iappyxOS', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
                            Text('My Apps', style: TextStyle(fontSize: 12, color: Colors.white38)),
                          ])),
                          IconButton(
                            onPressed: () => Navigator.push(context, MaterialPageRoute(
                              builder: (_) => QRReceiveScreen(onReceived: refresh))),
                            icon: const Icon(Icons.qr_code_scanner, color: Colors.white54, size: 24),
                            tooltip: 'Receive via QR',
                          ),
                          IconButton(
                            onPressed: _showReceiveNearbySheet,
                            icon: const Icon(Icons.wifi_tethering, color: Colors.white54, size: 26),
                            tooltip: 'Receive Nearby',
                          ),
                          if (widget.onCreateTap != null)
                            IconButton(
                              onPressed: widget.onCreateTap,
                              icon: const Icon(Icons.add_circle_outline, color: Color(0xFF4FC3F7), size: 28),
                              tooltip: 'Create New App',
                            ),
                        ]),
                        const SizedBox(height: 24),
                        if (_log.isNotEmpty) ...[
                          Stack(children: [
                            BuildLog(log: _log),
                            Positioned(right: 0, top: 20, child: GestureDetector(
                              onTap: () => setState(() => _log.clear()),
                              child: Container(
                                padding: const EdgeInsets.all(4),
                                decoration: BoxDecoration(color: const Color(0xFF1A1A2E), borderRadius: BorderRadius.circular(12)),
                                child: const Icon(Icons.close, size: 16, color: Colors.white38),
                              ),
                            )),
                          ]),
                          const SizedBox(height: 16),
                        ],
                      ],
                    ),
                  ),
                ),
                if (_apps.isEmpty)
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(
                      child: Padding(
                        padding: const EdgeInsets.all(40),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Text(
                              'No apps yet',
                              textAlign: TextAlign.center,
                              style: TextStyle(color: Colors.white38, fontSize: 14),
                            ),
                            const SizedBox(height: 6),
                            const Text(
                              'Build AI apps, website apps, or demos',
                              textAlign: TextAlign.center,
                              style: TextStyle(color: Colors.white24, fontSize: 12),
                            ),
                            const SizedBox(height: 20),
                            FilledButton.icon(
                              onPressed: widget.onCreateTap,
                              icon: const Icon(Icons.add, size: 18),
                              label: const Text('Create your first app'),
                              style: FilledButton.styleFrom(
                                backgroundColor: const Color(0xFF0F3460),
                                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                else
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(24, 0, 24, 40),
                    sliver: SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (_, i) => _buildAppCard(_apps[i]),
                        childCount: _apps.length,
                      ),
                    ),
                  ),
              ]),
      ),
    );
  }

  Widget _buildAppCard(AppData app) {
    final isRebuilding = _rebuildingId == app.id;
    final date = '${app.updatedAt.day}/${app.updatedAt.month}/${app.updatedAt.year}';
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A2E),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                GestureDetector(
                  onTap: app.packageName.isNotEmpty ? () => _launch(app) : null,
                  child: _buildAppIcon(app, 44),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(app.name, style: const TextStyle(
                              fontSize: 15, fontWeight: FontWeight.w600,
                            ), maxLines: 1, overflow: TextOverflow.ellipsis),
                          ),
                          if (_versionCounts.containsKey(app.id))
                            Padding(
                              padding: const EdgeInsets.only(right: 6),
                              child: Text('v${_versionCounts[app.id]}', style: const TextStyle(fontSize: 10, color: Color(0xFF4FC3F7))),
                            ),
                          Text(date, style: const TextStyle(fontSize: 11, color: Colors.white38)),
                        ],
                      ),
                      if (app.description.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(
                          app.description,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 12, color: Colors.white54),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: Color(0xFF0D0D1A)),
          Row(
            children: [
              if (app.packageName.isNotEmpty)
                _cardAction(
                  icon: Icons.launch,
                  label: 'Launch',
                  onTap: () => _launch(app),
                ),
              _cardAction(
                icon: Icons.refresh,
                label: 'Build Again',
                onTap: isRebuilding ? null : () => _rebuild(app),
                loading: isRebuilding,
              ),
              _cardAction(
                icon: Icons.edit_outlined,
                label: 'Edit',
                onTap: () => widget.onEditApp?.call(app),
              ),
              _cardAction(
                icon: Icons.more_horiz,
                label: 'More',
                onTap: () => _showMoreMenu(app),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _cardAction({
    required IconData icon,
    required String label,
    VoidCallback? onTap,
    Color? color,
    bool loading = false,
  }) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Column(
            children: [
              loading
                  ? const SizedBox(height: 18, width: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF4FC3F7)))
                  : Icon(icon, size: 18, color: color ?? Colors.white54),
              const SizedBox(height: 4),
              Text(label, style: TextStyle(fontSize: 12, color: color ?? Colors.white54)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAppIcon(AppData app, double size) {
    IconConfig parsed;
    if (app.iconConfig.isNotEmpty) {
      try { parsed = IconConfig.fromJsonString(app.iconConfig); }
      catch (_) { parsed = IconConfig.defaultFor(app.name); }
    } else {
      parsed = IconConfig.fromLegacy(
            emoji: app.emoji, emojiScale: app.emojiScale,
            emojiOffsetX: app.emojiOffsetX, emojiOffsetY: app.emojiOffsetY,
            appName: app.name,
          );
    }
    return IconPreview(config: parsed, size: size);
  }

  Future<void> _launch(AppData app) async {
    try {
      final launched = await Generator.launchApp(packageName: app.packageName);
      if (!launched && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('App not installed. Try rebuilding it.'), backgroundColor: Color(0xFF1A1A2E), duration: Duration(seconds: 4)),
        );
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not launch app: $e'), backgroundColor: const Color(0xFFFF6B6B)),
      );
    }
  }

  Future<String?> _resolveApkPath(AppData app) async {
    if (app.apkPath.isNotEmpty && File(app.apkPath).existsSync()) return app.apkPath;
    final installed = await Generator.getInstalledApkPath(app.packageName);
    if (installed != null && installed.isNotEmpty) return installed;
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('App not installed. Rebuild it first.'), backgroundColor: Color(0xFF1A1A2E)),
      );
    }
    return null;
  }

  Future<void> _uninstall(AppData app) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        title: const Text('Uninstall from device?'),
        content: Text('Uninstall "${app.name}" from your device. Your saved app data in iappyxOS will be kept.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Uninstall', style: TextStyle(color: Color(0xFFFF6B6B)))),
        ],
      ),
    );
    if (confirm == true) {
      try { await Generator.uninstallApp(packageName: app.packageName); }
      catch (e) {
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Uninstall failed: $e'), backgroundColor: const Color(0xFFFF6B6B)),
        );
      }
    }
  }

  // ── P2P Sharing ──

  Future<void> _showShareNearbySheet(AppData app) async {
    final apkPath = await _resolveApkPath(app);
    if (apkPath == null || !mounted) return;
    final size = File(apkPath).lengthSync();
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF0d0d1a),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      isDismissible: false,
      builder: (ctx) => _ShareNearbySheet(app: app, apkPath: apkPath, apkSize: size),
    );
  }

  void _showReceiveNearbySheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF0d0d1a),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      isDismissible: false,
      builder: (ctx) => _ReceiveNearbySheet(onInstalled: () { refresh(); }),
    );
  }
}

// ── Sender bottom sheet ──

class _ShareNearbySheet extends StatefulWidget {
  final AppData app;
  final String apkPath;
  final int apkSize;
  const _ShareNearbySheet({required this.app, required this.apkPath, required this.apkSize});

  @override
  State<_ShareNearbySheet> createState() => _ShareNearbySheetState();
}

class _ShareNearbySheetState extends State<_ShareNearbySheet> {
  String _status = 'choosing'; // choosing, starting, waiting, transferring, done, error
  bool _includeSource = false;

  void _startWithChoice() {
    setState(() => _status = 'starting');
    P2PService.onStatus = (status) {
      if (mounted) setState(() => _status = status);
    };
    _startSharing();
  }

  Future<void> _startSharing() async {
    final ok = await P2PService.startSharing(
      apkPath: widget.apkPath,
      appName: widget.app.name,
      appSize: widget.apkSize,
      metadata: _includeSource ? _buildMetadata() : null,
    );
    if (!ok && mounted) setState(() => _status = 'error');
  }

  Map<String, String> _buildMetadata() {
    return {
      'hasSource': 'true',
      'name': widget.app.name,
      'description': widget.app.description,
      'prompt': widget.app.prompt,
      'html': widget.app.html,
      'appType': widget.app.appType,
      'templateId': widget.app.templateId,
      'iconConfig': widget.app.iconConfig,
      'packageName': widget.app.packageName,
    };
  }

  @override
  void dispose() {
    P2PService.stopSharing();
    P2PService.onStatus = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Container(width: 40, height: 4, margin: const EdgeInsets.only(bottom: 20),
            decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2))),
          if (_status == 'choosing') ...[
            const Icon(Icons.wifi_tethering, color: Color(0xFF4FC3F7), size: 48),
            const SizedBox(height: 16),
            Text('Share "${widget.app.name}"', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
            const SizedBox(height: 16),
            _choiceTile(
              icon: Icons.android,
              title: 'APK only',
              subtitle: 'Receiver can install and use the app',
              selected: !_includeSource,
              onTap: () => setState(() => _includeSource = false),
            ),
            const SizedBox(height: 8),
            _choiceTile(
              icon: Icons.code,
              title: 'APK + source',
              subtitle: 'Receiver can also edit and rebuild',
              selected: _includeSource,
              onTap: () => setState(() => _includeSource = true),
            ),
            const SizedBox(height: 20),
            SizedBox(width: double.infinity, child: FilledButton(
              onPressed: _startWithChoice,
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFF0F3460),
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              child: const Text('Start Sharing', style: TextStyle(color: Colors.white)),
            )),
            const SizedBox(height: 8),
            SizedBox(width: double.infinity, child: FilledButton(
              onPressed: () => Navigator.pop(context),
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFF1A1A2E),
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              child: const Text('Cancel', style: TextStyle(color: Colors.white)),
            )),
          ] else ...[
            Icon(
              _status == 'done' ? Icons.check_circle : Icons.wifi_tethering,
              color: _status == 'error' ? const Color(0xFFFF6B6B) : const Color(0xFF4FC3F7),
              size: 48,
            ),
            const SizedBox(height: 16),
            Text(
              _status == 'starting' ? 'Starting...'
                : _status == 'waiting' ? 'Sharing "${widget.app.name}"'
                : _status == 'transferring' ? 'Sending...'
                : _status == 'done' ? 'Sent!'
                : 'WiFi Direct error',
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            Text(
              _status == 'waiting' ? 'Waiting for another device to connect...'
                : _status == 'transferring' ? 'Transferring APK...'
                : _status == 'done' ? 'The app was sent successfully'
                : _status == 'error' ? 'Could not start WiFi Direct sharing'
                : 'Setting up WiFi Direct...',
              style: const TextStyle(fontSize: 13, color: Colors.white38),
            ),
            if (_status == 'waiting') ...[
              const SizedBox(height: 16),
              const LinearProgressIndicator(color: Color(0xFF4FC3F7), backgroundColor: Color(0xFF1A1A2E)),
            ],
            const SizedBox(height: 24),
            SizedBox(width: double.infinity, child: FilledButton(
              onPressed: () => Navigator.pop(context),
              style: FilledButton.styleFrom(
                backgroundColor: _status == 'done' ? const Color(0xFF0F3460) : const Color(0xFF1A1A2E),
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              child: Text(_status == 'done' ? 'Done' : 'Cancel', style: const TextStyle(color: Colors.white)),
            )),
          ],
        ]),
      ),
    );
  }

  Widget _choiceTile({required IconData icon, required String title, required String subtitle, required bool selected, required VoidCallback onTap}) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: selected ? const Color(0xFF0F3460) : const Color(0xFF1A1A2E),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: selected ? const Color(0xFF4FC3F7) : Colors.transparent, width: 1.5),
        ),
        child: Row(children: [
          Icon(icon, color: selected ? const Color(0xFF4FC3F7) : Colors.white38, size: 24),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: selected ? Colors.white : Colors.white54)),
            Text(subtitle, style: const TextStyle(fontSize: 12, color: Colors.white38)),
          ])),
          if (selected) const Icon(Icons.check_circle, color: Color(0xFF4FC3F7), size: 20),
        ]),
      ),
    );
  }
}

// ── Receiver bottom sheet ──

class _ReceiveNearbySheet extends StatefulWidget {
  final VoidCallback onInstalled;
  const _ReceiveNearbySheet({required this.onInstalled});

  @override
  State<_ReceiveNearbySheet> createState() => _ReceiveNearbySheetState();
}

class _ReceiveNearbySheetState extends State<_ReceiveNearbySheet> {
  String _status = 'scanning'; // scanning, connecting, downloading, installing, done, error
  List<Map<String, String>> _peers = [];
  int _progress = 0;
  String? _errorMsg;
  String? _downloadedPath;

  @override
  void initState() {
    super.initState();
    P2PService.onPeers = (peers) {
      if (mounted) setState(() => _peers = peers);
    };
    P2PService.onConnected = (info) {
      if (mounted && info['groupOwnerAddress'] != null) {
        setState(() => _status = 'downloading');
        _startDownload(info['groupOwnerAddress'] as String);
      }
    };
    P2PService.onProgress = (pct, downloaded, total) {
      if (mounted) setState(() => _progress = pct);
    };
    P2PService.onDownloadDone = (ok, path, error, infoJson) {
      if (mounted) {
        if (ok && path != null) {
          setState(() { _status = 'installing'; _downloadedPath = path; });
          _installApk(path, infoJson);
        } else {
          setState(() { _status = 'error'; _errorMsg = error ?? 'Download failed'; });
        }
      }
    };
    _startDiscovery();
  }

  Future<void> _startDiscovery() async {
    final ok = await P2PService.discoverPeers();
    if (!ok && mounted) setState(() { _status = 'error'; _errorMsg = 'Could not start WiFi Direct discovery'; });
  }

  Future<void> _startDownload(String hostIp) async {
    await P2PService.downloadApk(hostIp);
  }

  Future<void> _installApk(String path, String? infoJson) async {
    try {
      // Disconnect WiFi Direct before install — restores normal WiFi,
      // prevents PackageInstaller from failing due to network disruption
      await P2PService.disconnect();

      // Check for signature conflict (app signed by a different device's key)
      String? pkgName;
      if (infoJson != null) {
        try {
          final info = Map<String, dynamic>.from(jsonDecode(infoJson) as Map);
          pkgName = info['packageName'] as String?;
        } catch (_) {}
      }
      if (pkgName != null && pkgName.isNotEmpty) {
        final sigStatus = await Generator.checkSignature(packageName: pkgName);
        if (sigStatus == 'different_signer' && mounted) {
          final proceed = await _showSignatureConflictDialog(pkgName);
          if (proceed != true) {
            if (mounted) setState(() { _status = 'error'; _errorMsg = 'Install cancelled'; });
            return;
          }
          // Poll until the app is uninstalled or timeout (user may take time on the dialog)
          var uninstalled = false;
          for (var i = 0; i < 30; i++) {
            await Future.delayed(const Duration(milliseconds: 500));
            final recheck = await Generator.checkSignature(packageName: pkgName);
            if (recheck != 'different_signer') { uninstalled = true; break; }
          }
          if (!uninstalled) {
            if (mounted) setState(() { _status = 'error'; _errorMsg = 'Existing app was not uninstalled'; });
            return;
          }
        }
      }

      await P2PService.installApk(path);
      // Save AppData if metadata was included
      if (infoJson != null) {
        try {
          final info = Map<String, dynamic>.from(jsonDecode(infoJson) as Map);
          if (info['hasSource'] == true || info['hasSource'] == 'true') {
            final now = DateTime.now();
            final appId = '${now.millisecondsSinceEpoch}_p2p';
            await AppStorage.save(AppData(
              id: appId,
              name: (info['name'] as String?) ?? 'Received App',
              description: (info['description'] as String?) ?? '',
              prompt: (info['prompt'] as String?) ?? '',
              html: (info['html'] as String?) ?? '',
              appType: (info['appType'] as String?) ?? 'ai',
              templateId: (info['templateId'] as String?) ?? '',
              packageName: (info['packageName'] as String?) ?? '',
              apkPath: path,
              iconConfig: (info['iconConfig'] as String?) ?? '',
              createdAt: now,
              updatedAt: now,
            ));
          }
        } catch (_) {}
      }
      if (mounted) setState(() => _status = 'done');
      widget.onInstalled();
    } catch (e) {
      if (mounted) setState(() { _status = 'error'; _errorMsg = 'Install failed'; });
    }
  }

  /// Shows a dialog explaining that the app was signed by a different device.
  /// Returns true if the user chose to uninstall and the uninstall intent was sent.
  Future<bool?> _showSignatureConflictDialog(String packageName) async {
    final result = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Signature mismatch'),
        content: const Text(
          'This app was built on a different device and has a different signature. '
          'To install this version, the existing app must be uninstalled first.\n\n'
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
              await Generator.uninstallApp(packageName: packageName);
              if (ctx.mounted) Navigator.pop(ctx, true);
            },
            child: const Text('Uninstall & replace', style: TextStyle(color: Color(0xFFEA5455))),
          ),
        ],
      ),
    );
    return result;
  }

  @override
  void dispose() {
    P2PService.stopDiscovery();
    P2PService.disconnect();
    P2PService.onPeers = null;
    P2PService.onConnected = null;
    P2PService.onProgress = null;
    P2PService.onDownloadDone = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Container(width: 40, height: 4, margin: const EdgeInsets.only(bottom: 20),
            decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2))),
          if (_status == 'scanning') ...[
            const Icon(Icons.radar, color: Color(0xFF4FC3F7), size: 48),
            const SizedBox(height: 16),
            const Text('Looking for nearby devices...', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            const Text('Make sure the other device is sharing an app', style: TextStyle(fontSize: 13, color: Colors.white38)),
            const SizedBox(height: 16),
            if (_peers.isEmpty)
              const Padding(padding: EdgeInsets.symmetric(vertical: 16),
                child: LinearProgressIndicator(color: Color(0xFF4FC3F7), backgroundColor: Color(0xFF1A1A2E)))
            else
              ..._peers.map((p) => ListTile(
                leading: const Icon(Icons.phone_android, color: Color(0xFF4FC3F7)),
                title: Text(p['name'] ?? 'Unknown'),
                subtitle: Text(p['status'] ?? '', style: const TextStyle(fontSize: 11, color: Colors.white38)),
                onTap: p['status'] == 'available' ? () {
                  setState(() => _status = 'connecting');
                  P2PService.connectToPeer(p['address'] ?? '');
                  // Timeout — if no connection within 15s, the sender is probably gone
                  Future.delayed(const Duration(seconds: 15), () {
                    if (mounted && _status == 'connecting') {
                      setState(() { _status = 'error'; _errorMsg = 'Connection timed out — sender may have stopped sharing'; });
                      P2PService.disconnect();
                    }
                  });
                } : null,
              )),
          ],
          if (_status == 'connecting') ...[
            const Icon(Icons.sync, color: Color(0xFF4FC3F7), size: 48),
            const SizedBox(height: 16),
            const Text('Connecting...', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
            const SizedBox(height: 16),
            const LinearProgressIndicator(color: Color(0xFF4FC3F7), backgroundColor: Color(0xFF1A1A2E)),
          ],
          if (_status == 'downloading') ...[
            const Icon(Icons.downloading, color: Color(0xFF4FC3F7), size: 48),
            const SizedBox(height: 16),
            Text('Downloading... $_progress%', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
            const SizedBox(height: 16),
            LinearProgressIndicator(value: _progress / 100, color: const Color(0xFF4FC3F7), backgroundColor: const Color(0xFF1A1A2E)),
          ],
          if (_status == 'installing') ...[
            const Icon(Icons.install_mobile, color: Color(0xFF4FC3F7), size: 48),
            const SizedBox(height: 16),
            const Text('Installing...', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
          ],
          if (_status == 'done') ...[
            const Icon(Icons.check_circle, color: Color(0xFF69F0AE), size: 48),
            const SizedBox(height: 16),
            const Text('App received!', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            const Text('The app has been installed on your device', style: TextStyle(fontSize: 13, color: Colors.white38)),
          ],
          if (_status == 'error') ...[
            const Icon(Icons.error_outline, color: Color(0xFFFF6B6B), size: 48),
            const SizedBox(height: 16),
            Text(_errorMsg ?? 'Something went wrong', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
          ],
          const SizedBox(height: 24),
          SizedBox(width: double.infinity, child: FilledButton(
            onPressed: () => Navigator.pop(context),
            style: FilledButton.styleFrom(
              backgroundColor: _status == 'done' ? const Color(0xFF0F3460) : const Color(0xFF1A1A2E),
              padding: const EdgeInsets.symmetric(vertical: 16),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: Text(_status == 'done' ? 'Done' : 'Cancel', style: const TextStyle(color: Colors.white)),
          )),
        ]),
      ),
    );
  }
}

class _VersionHistorySheet extends StatefulWidget {
  final String appName;
  final String appId;
  final List<Map<String, dynamic>> versions;
  final void Function(String html) onRestore;
  const _VersionHistorySheet({required this.appName, required this.appId, required this.versions, required this.onRestore});
  @override
  State<_VersionHistorySheet> createState() => _VersionHistorySheetState();
}

class _VersionHistorySheetState extends State<_VersionHistorySheet> {
  late List<Map<String, dynamic>> _versions;

  @override
  void initState() {
    super.initState();
    _versions = List.from(widget.versions);
  }

  String _formatDate(String iso) {
    try {
      final dt = DateTime.parse(iso);
      return '${dt.day}/${dt.month}/${dt.year} ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
    } catch (_) { return iso; }
  }

  String _formatSize(String html) {
    final kb = (html.length / 1024).toStringAsFixed(1);
    return '${kb}KB';
  }

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      initialChildSize: 0.5,
      maxChildSize: 0.85,
      minChildSize: 0.3,
      expand: false,
      builder: (_, scrollController) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 20, 24, 0),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Center(child: Container(width: 40, height: 4, decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 16),
          Text('Version History — ${widget.appName}', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          Text('${_versions.length} version${_versions.length == 1 ? '' : 's'}', style: const TextStyle(fontSize: 12, color: Colors.white38)),
          const SizedBox(height: 16),
          Expanded(
            child: ListView.separated(
              controller: scrollController,
              itemCount: _versions.length,
              separatorBuilder: (_, __) => const SizedBox(height: 8),
              itemBuilder: (_, i) {
                final v = _versions[_versions.length - 1 - i]; // newest first
                final html = v['html'] as String? ?? '';
                return Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: const Color(0xFF0D0D1A),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(children: [
                    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text('v${v['version']}', style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 2),
                      Text('${_formatDate(v['timestamp'] as String? ?? '')}  •  ${_formatSize(html)}',
                        style: const TextStyle(fontSize: 11, color: Colors.white38)),
                    ])),
                    TextButton(
                      onPressed: () => widget.onRestore(html),
                      child: const Text('Restore', style: TextStyle(color: Color(0xFF4FC3F7), fontSize: 13)),
                    ),
                  ]),
                );
              },
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Center(
              child: TextButton(
                onPressed: () async {
                  final confirm = await showDialog<bool>(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      backgroundColor: const Color(0xFF1A1A2E),
                      title: const Text('Clear History'),
                      content: const Text('Delete all saved versions? This cannot be undone.', style: TextStyle(color: Colors.white70)),
                      actions: [
                        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
                        TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Clear', style: TextStyle(color: Color(0xFFFF6B6B)))),
                      ],
                    ),
                  );
                  if (confirm == true) {
                    await AppStorage.clearVersions(widget.appId);
                    if (mounted) { Navigator.pop(context); }
                  }
                },
                child: const Text('Clear History', style: TextStyle(color: Colors.white38, fontSize: 12)),
              ),
            ),
          ),
        ]),
      ),
    );
  }
}
