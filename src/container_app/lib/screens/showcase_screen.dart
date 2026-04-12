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
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../services/settings_service.dart';

class ShowcaseApp {
  final String slug;
  final String name;
  final String description;
  final String author;
  final List<String> bridges;

  ShowcaseApp({required this.slug, required this.name, required this.description, required this.author, required this.bridges});

  factory ShowcaseApp.fromJson(Map<String, dynamic> json) => ShowcaseApp(
    slug: json['slug'] ?? '',
    name: json['name'] ?? '',
    description: json['description'] ?? '',
    author: json['author'] ?? '',
    bridges: (json['bridges'] as List?)?.map((e) => e.toString()).toList() ?? [],
  );
}

class ShowcaseScreen extends StatefulWidget {
  final void Function(String name, String html) onLoadApp;
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
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(child: CircularProgressIndicator(color: Color(0xFF4FC3F7))),
    );
    try {
      final url = '$_baseUrl/${app.slug}/app.html';
      final resp = await http.get(Uri.parse(url)).timeout(const Duration(seconds: 15));
      if (!mounted) return;
      Navigator.pop(context); // dismiss loading
      if (resp.statusCode != 200) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to load: HTTP ${resp.statusCode}'), backgroundColor: const Color(0xFF1A1A2E)),
        );
        return;
      }
      widget.onLoadApp(app.name, resp.body);
    } catch (e) {
      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to load: $e'), backgroundColor: const Color(0xFF1A1A2E)),
        );
      }
    }
  }

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
