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

/// Browse and install community-submitted apps from the showcase repository.

import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../services/settings_service.dart';
import '../services/bundle_storage.dart';

class ShowcaseApp {
  final String slug;
  final String name;
  final String description;
  final String author;
  final List<String> bridges;
  final int resourceCount;
  final int resourceSize;

  ShowcaseApp({required this.slug, required this.name, required this.description, required this.author, required this.bridges, this.resourceCount = 0, this.resourceSize = 0});

  factory ShowcaseApp.fromJson(Map<String, dynamic> json) => ShowcaseApp(
    slug: json['slug'] ?? '',
    name: json['name'] ?? '',
    description: json['description'] ?? '',
    author: json['author'] ?? '',
    bridges: (json['bridges'] as List?)?.map((e) => e.toString()).toList() ?? [],
    resourceCount: json['resourceCount'] ?? 0,
    resourceSize: json['resourceSize'] ?? 0,
  );

  String get resourceSizeStr {
    if (resourceSize < 1024) return '$resourceSize B';
    if (resourceSize < 1024 * 1024) return '${(resourceSize / 1024).toStringAsFixed(0)} KB';
    return '${(resourceSize / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}

class ShowcaseScreen extends StatefulWidget {
  final void Function(String name, String html, {String? bundleAppId}) onLoadApp;
  final VoidCallback? onBack;
  const ShowcaseScreen({super.key, required this.onLoadApp, this.onBack});
  @override
  State<ShowcaseScreen> createState() => _ShowcaseScreenState();
}

class _ShowcaseScreenState extends State<ShowcaseScreen> {
  List<ShowcaseApp> _apps = [];
  bool _loading = true;
  String? _error;
  String _search = '';
  String _baseUrl = '';

  @override
  void initState() {
    super.initState();
    _fetchShowcase();
  }

  Future<void> _fetchShowcase() async {
    try {
      final url = '${Settings.showcaseBaseUrl}/showcase.json';
      final resp = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 10));
      if (resp.statusCode != 200) throw Exception('HTTP ${resp.statusCode}');
      final data = jsonDecode(resp.body);
      _baseUrl = data['base_url'] ?? Settings.showcaseBaseUrl;
      final apps = (data['apps'] as List).map((e) => ShowcaseApp.fromJson(e)).toList();
      if (mounted) setState(() { _apps = apps; _loading = false; });
    } catch (e) {
      if (mounted) setState(() { _error = 'Could not load showcase'; _loading = false; });
    }
  }

  Future<void> _loadApp(ShowcaseApp app) async {
    // If the app has resources, confirm download first
    if (app.resourceCount > 0) {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          backgroundColor: const Color(0xFF1A1A2E),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: Text('Download resources', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
          content: Text(
            '${app.name} includes ${app.resourceCount} resource file${app.resourceCount == 1 ? '' : 's'} (${app.resourceSizeStr}).\n\nDownload now?',
            style: const TextStyle(fontSize: 14, color: Colors.white70),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel', style: TextStyle(color: Colors.white38))),
            TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Download', style: TextStyle(color: Color(0xFF4FC3F7)))),
          ],
        ),
      );
      if (confirmed != true || !mounted) return;
    }

    // Show progress dialog
    String progressText = 'Downloading app…';
    final progressKey = GlobalKey<_ProgressDialogState>();
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => _ProgressDialog(key: progressKey, initialText: progressText),
    );

    try {
      // Download HTML
      final url = '$_baseUrl/${app.slug}/app.html';
      final resp = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 15));
      if (!mounted) return;
      if (resp.statusCode != 200) throw Exception('HTTP ${resp.statusCode}');
      final html = resp.body;

      // Download resources if any
      if (app.resourceCount > 0) {
        progressKey.currentState?.update('Fetching resource list…');
        // Fetch per-app showcase.json for resource filenames
        final metaUrl = '$_baseUrl/${app.slug}/showcase.json';
        final metaResp = await http.get(Uri.parse(metaUrl)).timeout(const Duration(seconds: 10));
        if (metaResp.statusCode != 200) throw Exception('Could not fetch resource list');
        final meta = jsonDecode(metaResp.body);
        final resources = (meta['resources'] as List?) ?? [];

        // Create a temporary app ID for bundle storage (will be assigned properly on build)
        final tempAppId = 'showcase_${app.slug}';
        await BundleStorage.clearBundle(tempAppId);

        for (int i = 0; i < resources.length; i++) {
          final res = resources[i];
          final name = res['name'] as String;
          progressKey.currentState?.update('Downloading $name (${i + 1}/${resources.length})…');
          final resUrl = '$_baseUrl/${app.slug}/resources/$name';
          final resResp = await http.get(Uri.parse(resUrl)).timeout(const Duration(seconds: 60));
          if (resResp.statusCode != 200) throw Exception('Failed to download $name: HTTP ${resResp.statusCode}');
          await BundleStorage.addFile(tempAppId, name, Uint8List.fromList(resResp.bodyBytes));
        }

        // Store the temp app ID so create_screen can pick up the bundle
        _pendingBundleAppId = tempAppId;
      }

      if (!mounted) return;
      Navigator.pop(context); // dismiss progress
      widget.onLoadApp(app.name, html, bundleAppId: _pendingBundleAppId);
    } catch (e) {
      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to load: $e'), backgroundColor: const Color(0xFF1A1A2E)),
        );
      }
    }
  }

  String? _pendingBundleAppId;

  void _showDetail(ShowcaseApp app) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1A1A2E),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 20, 24, 32),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Center(child: Container(width: 40, height: 4, decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 20),
          Text(app.name, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          Text('by ${app.author}', style: const TextStyle(fontSize: 12, color: Colors.white38)),
          const SizedBox(height: 12),
          Text(app.description, style: const TextStyle(fontSize: 14, color: Colors.white70)),
          if (app.resourceCount > 0) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(color: const Color(0xFF0D0D1A), borderRadius: BorderRadius.circular(8)),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                const Text('📦 ', style: TextStyle(fontSize: 12)),
                Text('${app.resourceCount} resource file${app.resourceCount == 1 ? '' : 's'} (${app.resourceSizeStr})',
                  style: const TextStyle(fontSize: 12, color: Colors.white54)),
              ]),
            ),
          ],
          const SizedBox(height: 16),
          Wrap(
            spacing: 6, runSpacing: 6,
            children: app.bridges.map((b) => Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(color: const Color(0xFF0F3460), borderRadius: BorderRadius.circular(12)),
              child: Text(b, style: const TextStyle(fontSize: 11, color: Color(0xFF4FC3F7))),
            )).toList(),
          ),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () { Navigator.pop(ctx); _loadApp(app); },
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFF4FC3F7),
                foregroundColor: const Color(0xFF0D0D1A),
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              child: const Text('Load into editor', style: TextStyle(fontWeight: FontWeight.w600)),
            ),
          ),
        ]),
      ),
    );
  }

  List<ShowcaseApp> get _filtered {
    if (_search.isEmpty) return _apps;
    final q = _search.toLowerCase();
    return _apps.where((a) =>
      a.name.toLowerCase().contains(q) ||
      a.description.toLowerCase().contains(q) ||
      a.author.toLowerCase().contains(q) ||
      a.bridges.any((b) => b.toLowerCase().contains(q))
    ).toList();
  }

  @override
  Widget build(BuildContext context) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      // Back + title
      Row(children: [
        GestureDetector(
          onTap: () => widget.onBack?.call(),
          child: Container(
            width: 38, height: 38,
            decoration: BoxDecoration(color: const Color(0xFF1A1A2E), borderRadius: BorderRadius.circular(10)),
            child: const Icon(Icons.arrow_back, size: 18, color: Colors.white54),
          ),
        ),
        const SizedBox(width: 12),
        const Expanded(child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Showcase', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            Text('Community apps built with iappyxOS', style: TextStyle(fontSize: 12, color: Colors.white38)),
          ],
        )),
      ]),
      const SizedBox(height: 20),

      // Search
      TextField(
        style: const TextStyle(color: Colors.white, fontSize: 14),
        decoration: InputDecoration(
          hintText: 'Search apps or bridges...',
          hintStyle: const TextStyle(color: Colors.white24, fontSize: 14),
          prefixIcon: const Icon(Icons.search, color: Colors.white24, size: 20),
          filled: true, fillColor: const Color(0xFF1A1A2E),
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        ),
        onChanged: (v) => setState(() => _search = v),
      ),
      const SizedBox(height: 16),

      // Content
      Expanded(child: _buildContent()),
    ]);
  }

  Widget _buildContent() {
    if (_loading) return const Center(child: CircularProgressIndicator(color: Color(0xFF4FC3F7)));
    if (_error != null) return Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
      const Icon(Icons.cloud_off, color: Colors.white24, size: 40),
      const SizedBox(height: 12),
      Text(_error!, style: const TextStyle(color: Colors.white38)),
      const SizedBox(height: 12),
      TextButton(onPressed: () { setState(() { _loading = true; _error = null; }); _fetchShowcase(); },
        child: const Text('Retry', style: TextStyle(color: Color(0xFF4FC3F7)))),
    ]));

    final apps = _filtered;
    if (apps.isEmpty) return const Center(child: Text('No matching apps', style: TextStyle(color: Colors.white38)));

    return ListView.separated(
      itemCount: apps.length,
      separatorBuilder: (_, __) => const SizedBox(height: 10),
      itemBuilder: (_, i) {
        final app = apps[i];
        return GestureDetector(
          onTap: () => _showDetail(app),
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF1A1A2E),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: const Color(0xFF2A2A3E), width: 1),
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Expanded(child: Text(app.name, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600))),
                if (app.resourceCount > 0) ...[
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    margin: const EdgeInsets.only(right: 8),
                    decoration: BoxDecoration(color: const Color(0xFF0D0D1A), borderRadius: BorderRadius.circular(6)),
                    child: Text('📦 ${app.resourceSizeStr}', style: const TextStyle(fontSize: 9, color: Colors.white38)),
                  ),
                ],
                Text(app.author, style: const TextStyle(fontSize: 11, color: Colors.white38)),
              ]),
              const SizedBox(height: 6),
              Text(app.description, maxLines: 2, overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 13, color: Colors.white54)),
              const SizedBox(height: 10),
              Wrap(
                spacing: 6, runSpacing: 4,
                children: app.bridges.take(5).map((b) => Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(color: const Color(0xFF0D0D1A), borderRadius: BorderRadius.circular(8)),
                  child: Text(b, style: const TextStyle(fontSize: 10, color: Color(0xFF4FC3F7))),
                )).toList()
                ..addAll(app.bridges.length > 5 ? [Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(color: const Color(0xFF0D0D1A), borderRadius: BorderRadius.circular(8)),
                  child: Text('+${app.bridges.length - 5}', style: const TextStyle(fontSize: 10, color: Colors.white38)),
                )] : []),
              ),
            ]),
          ),
        );
      },
    );
  }
}

class _ProgressDialog extends StatefulWidget {
  final String initialText;
  const _ProgressDialog({super.key, required this.initialText});
  @override
  State<_ProgressDialog> createState() => _ProgressDialogState();
}

class _ProgressDialogState extends State<_ProgressDialog> {
  late String _text;
  @override
  void initState() { super.initState(); _text = widget.initialText; }
  void update(String text) { if (mounted) setState(() => _text = text); }
  @override
  Widget build(BuildContext context) {
    return Center(child: Container(
      margin: const EdgeInsets.symmetric(horizontal: 40),
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(color: const Color(0xFF1A1A2E), borderRadius: BorderRadius.circular(16)),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const CircularProgressIndicator(color: Color(0xFF4FC3F7)),
        const SizedBox(height: 16),
        Text(_text, style: const TextStyle(fontSize: 13, color: Colors.white54, decoration: TextDecoration.none, fontWeight: FontWeight.normal), textAlign: TextAlign.center),
      ]),
    ));
  }
}
