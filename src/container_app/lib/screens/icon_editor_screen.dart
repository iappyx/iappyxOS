import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:emoji_picker_flutter/emoji_picker_flutter.dart';
import '../models/icon_config.dart';

class IconEditorScreen extends StatefulWidget {
  final IconConfig config;
  const IconEditorScreen({super.key, required this.config});

  @override
  State<IconEditorScreen> createState() => _IconEditorScreenState();
}

class _IconEditorScreenState extends State<IconEditorScreen> {
  late IconConfig _config;
  int _selectedIndex = -1;

  @override
  void dispose() {
    _emojiInputController.dispose();
    super.dispose();
  }

  static const _presetColors = [
    0xFF1565C0, 0xFF6A1B9A, 0xFF00838F, 0xFFC62828, 0xFF2E7D32,
    0xFFEF6C00, 0xFF283593, 0xFF4E342E, 0xFF00695C, 0xFFAD1457,
    0xFF0D0D1A, 0xFF1A1A2E, 0xFF37474F, 0xFF455A64, 0xFF546E7A,
    0xFF7B1FA2, 0xFFC2185B, 0xFF00796B, 0xFF1976D2, 0xFFE65100,
  ];

  static const _presetGradients = [
    [0xFF0D0D1A, 0xFF0F3460],
    [0xFF16213E, 0xFFE94560],
    [0xFF1A1A2E, 0xFF4FC3F7],
    [0xFF0F0C29, 0xFF302B63],
    [0xFF2D4059, 0xFFEA5455],
    [0xFF1B262C, 0xFF3282B8],
    [0xFF0D0D1A, 0xFF69F0AE],
    [0xFF1A1A2E, 0xFFF72585],
    [0xFF6A1B9A, 0xFF4FC3F7],
    [0xFFC62828, 0xFFEF6C00],
  ];

  static const _defaultEmojis = [
    '📝', '✅', '💪', '🎯', '📊', '💰', '🛒', '🍳',
    '🎵', '📸', '🗺️', '⏱️', '🧮', '📅', '💡', '🔒',
    '🎮', '📚', '🏋️', '🌤️', '❤️', '🚀', '🔧', '🎨',
    '🏠', '🎬', '🍕', '⚽', '🎸', '💻', '🌍', '🔔',
  ];

  @override
  void initState() {
    super.initState();
    _config = IconConfig(
      bgColor: widget.config.bgColor,
      bgGradient: widget.config.bgGradient != null ? List<int>.from(widget.config.bgGradient!) : null,
      elements: widget.config.elements.map((e) => e.copy()).toList(),
    );
    if (_config.elements.isNotEmpty) _selectedIndex = 0;
  }

  IconElement? get _selected =>
      _selectedIndex >= 0 && _selectedIndex < _config.elements.length
          ? _config.elements[_selectedIndex] : null;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0D0D1A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0D0D1A),
        title: const Text('Edit Icon', style: TextStyle(fontSize: 16)),
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () async {
            if (_config.toJsonString() != widget.config.toJsonString()) {
              final discard = await showDialog<bool>(context: context, builder: (ctx) => AlertDialog(
                backgroundColor: const Color(0xFF1A1A2E),
                title: const Text('Discard changes?'),
                content: const Text('You have unsaved icon changes.'),
                actions: [
                  TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Keep editing')),
                  TextButton(onPressed: () => Navigator.pop(ctx, true),
                    child: const Text('Discard', style: TextStyle(color: Color(0xFFFF6B6B)))),
                ],
              ));
              if (discard != true) return;
            }
            if (context.mounted) Navigator.pop(context);
          },
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, _config),
            child: const Text('Save Icon', style: TextStyle(color: Color(0xFF4FC3F7), fontSize: 16)),
          ),
        ],
      ),
      body: Column(
        children: [
          const SizedBox(height: 16),
          Center(child: _buildPreview(200)),
          const SizedBox(height: 16),
          if (_selected != null) _buildElementControls(),
          // Action buttons
          _buildActionBar(),
          const SizedBox(height: 8),
          // Layers list (reorderable)
          Expanded(child: _buildLayerList()),
        ],
      ),
    );
  }

  Widget _buildPreview(double size) {
    return GestureDetector(
      onPanUpdate: (d) {
        if (_selected == null) return;
        setState(() {
          _selected!.offsetX = (_selected!.offsetX + d.delta.dx / size).clamp(-0.5, 0.5);
          _selected!.offsetY = (_selected!.offsetY + d.delta.dy / size).clamp(-0.5, 0.5);
        });
      },
      child: Container(
        width: size, height: size,
        decoration: BoxDecoration(
          color: _config.bgGradient == null ? Color(_config.bgColor) : null,
          gradient: _config.bgGradient != null ? LinearGradient(
            begin: Alignment.topLeft, end: Alignment.bottomRight,
            colors: [Color(_config.bgGradient![0]), Color(_config.bgGradient![1])],
          ) : null,
          borderRadius: BorderRadius.circular(size / 5),
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          clipBehavior: Clip.hardEdge,
          children: [
            for (int i = 0; i < _config.elements.length; i++)
              _buildElementOnCanvas(_config.elements[i], size, i == _selectedIndex),
          ],
        ),
      ),
    );
  }

  Widget _buildElementOnCanvas(IconElement el, double size, bool selected) {
    Widget child;
    if (el.isImage) {
      final imgSize = size * 0.6 * el.scale;
      child = Image.memory(
        _cachedDecode(el.content),
        width: imgSize, height: imgSize,
        fit: BoxFit.cover,
      );
    } else {
      final fontSize = el.isText ? size * 0.25 * el.scale : size * 0.55 * el.scale;
      child = Text(
        el.content,
        style: TextStyle(
          fontSize: fontSize,
          color: el.isText ? Color(el.color) : null,
          fontWeight: el.isText ? FontWeight.bold : null,
          height: 1.1,
        ),
        textAlign: TextAlign.center,
      );
    }
    final canvasSize = size * 3;
    return Positioned(
      left: size / 2 + el.offsetX * size - canvasSize / 2,
      top: size / 2 + el.offsetY * size - canvasSize / 2,
      width: canvasSize,
      height: canvasSize,
      child: Transform.rotate(
        angle: el.rotation * pi / 180,
        child: Center(
          child: Opacity(
            opacity: el.opacity,
            child: Container(
              decoration: BoxDecoration(
                boxShadow: _resolveShadow(el) != null ? [_resolveShadow(el)!] : null,
                border: selected ? Border.all(color: Colors.white.withValues(alpha: 0.4), width: 1) : null,
                borderRadius: selected ? BorderRadius.circular(4) : null,
              ),
              padding: selected ? const EdgeInsets.all(2) : null,
              child: el.filter != 'none' ? ColorFiltered(
                colorFilter: ColorFilter.matrix(_colorMatrixFor(el.filter)),
                child: child,
              ) : child,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLayerList() {
    if (_config.elements.isEmpty) {
      return const Center(
        child: Text('No layers yet. Add an emoji, text, or image.',
            style: TextStyle(fontSize: 12, color: Colors.white24)),
      );
    }
    return ReorderableListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      itemCount: _config.elements.length,
      onReorder: (oldIndex, newIndex) {
        setState(() {
          if (newIndex > oldIndex) newIndex--;
          final el = _config.elements.removeAt(oldIndex);
          _config.elements.insert(newIndex, el);
          // Track selection
          if (_selectedIndex == oldIndex) {
            _selectedIndex = newIndex;
          } else if (_selectedIndex > oldIndex && _selectedIndex <= newIndex) {
            _selectedIndex--;
          } else if (_selectedIndex < oldIndex && _selectedIndex >= newIndex) {
            _selectedIndex++;
          }
        });
      },
      itemBuilder: (context, i) {
        final el = _config.elements[i];
        final selected = i == _selectedIndex;
        return Container(
          key: ValueKey('layer_$i\_${el.content.hashCode}'),
          margin: const EdgeInsets.only(bottom: 4),
          decoration: BoxDecoration(
            color: selected ? const Color(0xFF0F3460) : const Color(0xFF1A1A2E),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: selected ? const Color(0xFF4FC3F7) : Colors.transparent,
              width: 1,
            ),
          ),
          child: ListTile(
            dense: true,
            contentPadding: const EdgeInsets.symmetric(horizontal: 12),
            leading: el.isImage
                ? const Icon(Icons.image, size: 18, color: Color(0xFF4FC3F7))
                : el.isText
                    ? const Icon(Icons.text_fields, size: 18, color: Colors.white54)
                    : Text(el.content, style: const TextStyle(fontSize: 20)),
            title: Text(
              el.isImage ? 'Image' : (el.content.length > 15 ? '${el.content.substring(0, 15)}...' : el.content),
              style: TextStyle(fontSize: 13, color: selected ? Colors.white : Colors.white70),
            ),
            subtitle: Text(
              'Scale ${el.scale.toStringAsFixed(1)}x${el.rotation > 0 ? ' · ${el.rotation.toStringAsFixed(0)}°' : ''}',
              style: const TextStyle(fontSize: 10, color: Colors.white24),
            ),
            trailing: const Icon(Icons.drag_handle, size: 18, color: Colors.white24),
            onTap: () => setState(() => _selectedIndex = i),
          ),
        );
      },
    );
  }

  Widget _buildElementControls() {
    final el = _selected!;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        children: [
          Row(
            children: [
              const Text('Size', style: TextStyle(fontSize: 12, color: Colors.white54)),
              Expanded(
                child: Slider(
                  value: el.scale,
                  min: 0.3, max: 5.0,
                  activeColor: const Color(0xFF4FC3F7),
                  onChanged: (v) => setState(() => el.scale = v),
                ),
              ),
            ],
          ),
          Row(
            children: [
              const Text('Rotate', style: TextStyle(fontSize: 12, color: Colors.white54)),
              Expanded(
                child: Slider(
                  value: el.rotation,
                  min: 0, max: 360,
                  activeColor: const Color(0xFF4FC3F7),
                  onChanged: (v) => setState(() => el.rotation = v),
                ),
              ),
            ],
          ),
          Row(
            children: [
              const Text('Opacity', style: TextStyle(fontSize: 12, color: Colors.white54)),
              Expanded(
                child: Slider(
                  value: el.opacity,
                  min: 0.0, max: 1.0,
                  activeColor: const Color(0xFF4FC3F7),
                  onChanged: (v) => setState(() => el.opacity = v),
                ),
              ),
            ],
          ),
          const Text('Shadow', style: TextStyle(fontSize: 12, color: Colors.white54)),
          const SizedBox(height: 4),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: ['none', 'subtle', 'drop', 'hard', 'glow', 'neon', 'custom'].map(
                (s) => Padding(
                  padding: const EdgeInsets.only(right: 4),
                  child: GestureDetector(
                    onTap: () => setState(() => el.shadowPreset = s),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: el.shadowPreset == s ? const Color(0xFF0F3460) : const Color(0xFF1A1A2E),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: el.shadowPreset == s ? const Color(0xFF4FC3F7) : Colors.transparent),
                      ),
                      child: Text(s[0].toUpperCase() + s.substring(1),
                        style: TextStyle(fontSize: 10, color: el.shadowPreset == s ? const Color(0xFF4FC3F7) : Colors.white38)),
                    ),
                  ),
                ),
              ).toList(),
            ),
          ),
          if (el.shadowPreset == 'custom') ...[
            const SizedBox(height: 4),
            Row(children: [const Text('Blur', style: TextStyle(fontSize: 10, color: Colors.white38)), Expanded(child: Slider(value: el.shadowBlur, min: 0, max: 20, activeColor: const Color(0xFF4FC3F7), onChanged: (v) => setState(() => el.shadowBlur = v)))]),
            Row(children: [const Text('X', style: TextStyle(fontSize: 10, color: Colors.white38)), Expanded(child: Slider(value: el.shadowOffsetX, min: -10, max: 10, activeColor: const Color(0xFF4FC3F7), onChanged: (v) => setState(() => el.shadowOffsetX = v)))]),
            Row(children: [const Text('Y', style: TextStyle(fontSize: 10, color: Colors.white38)), Expanded(child: Slider(value: el.shadowOffsetY, min: -10, max: 10, activeColor: const Color(0xFF4FC3F7), onChanged: (v) => setState(() => el.shadowOffsetY = v)))]),
            Row(children: [const Text('Opacity', style: TextStyle(fontSize: 10, color: Colors.white38)), Expanded(child: Slider(value: el.shadowOpacity, min: 0, max: 1, activeColor: const Color(0xFF4FC3F7), onChanged: (v) => setState(() => el.shadowOpacity = v)))]),
            Row(children: [const Text('Spread', style: TextStyle(fontSize: 10, color: Colors.white38)), Expanded(child: Slider(value: el.shadowSpread, min: 0, max: 10, activeColor: const Color(0xFF4FC3F7), onChanged: (v) => setState(() => el.shadowSpread = v)))]),
          ],
          const SizedBox(height: 8),
          const Text('Filter', style: TextStyle(fontSize: 12, color: Colors.white54)),
          const SizedBox(height: 4),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: _filterNames.map(
                (f) => Padding(
                  padding: const EdgeInsets.only(right: 4),
                  child: GestureDetector(
                    onTap: () => setState(() => el.filter = f),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: el.filter == f ? const Color(0xFF0F3460) : const Color(0xFF1A1A2E),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: el.filter == f ? const Color(0xFF4FC3F7) : Colors.transparent),
                      ),
                      child: Text(_filterLabel(f),
                        style: TextStyle(fontSize: 10, color: el.filter == f ? const Color(0xFF4FC3F7) : Colors.white38)),
                    ),
                  ),
                ),
              ).toList(),
            ),
          ),
          const SizedBox(height: 4),
          if (el.isText)
            Row(
              children: [
                const Text('Color', style: TextStyle(fontSize: 12, color: Colors.white54)),
                const SizedBox(width: 12),
                ...[ 0xFFFFFFFF, 0xFFEAEAEA, 0xFF000000, 0xFF4FC3F7, 0xFF69F0AE, 0xFFFF6B6B, 0xFFFFD54F ].map(
                  (c) => GestureDetector(
                    onTap: () => setState(() => el.color = c),
                    child: Container(
                      width: 28, height: 28,
                      margin: const EdgeInsets.only(right: 6),
                      decoration: BoxDecoration(
                        color: Color(c),
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: el.color == c ? const Color(0xFF4FC3F7) : Colors.transparent,
                          width: 2,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }

  Widget _buildActionBar() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _actionButton(Icons.palette, 'Background', _pickBgColor),
          _actionButton(Icons.emoji_emotions, 'Emoji', _addEmoji),
          _actionButton(Icons.image, 'Image', _addImage),
          _actionButton(Icons.text_fields, 'Text', _addText),
          if (_selected != null)
            _actionButton(Icons.delete_outline, 'Delete', _deleteSelected, color: const Color(0xFFFF6B6B)),
        ],
      ),
    );
  }

  Widget _actionButton(IconData icon, String label, VoidCallback onTap, {Color? color}) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color ?? Colors.white54, size: 22),
          const SizedBox(height: 4),
          Text(label, style: TextStyle(fontSize: 10, color: color ?? Colors.white38)),
        ],
      ),
    );
  }

  void _pickBgColor() {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1A1A2E),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Background', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 12),
            const Text('Solid Colors', style: TextStyle(fontSize: 12, color: Colors.white54)),
            const SizedBox(height: 8),
            Wrap(
              spacing: 10, runSpacing: 10,
              children: _presetColors.map((c) => GestureDetector(
                onTap: () { setState(() { _config.bgColor = c; _config.bgGradient = null; }); Navigator.pop(ctx); },
                child: Container(
                  width: 44, height: 44,
                  decoration: BoxDecoration(
                    color: Color(c),
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: _config.bgGradient == null && _config.bgColor == c ? const Color(0xFF4FC3F7) : Colors.transparent,
                      width: 3,
                    ),
                  ),
                ),
              )).toList(),
            ),
            const SizedBox(height: 16),
            const Text('Gradients', style: TextStyle(fontSize: 12, color: Colors.white54)),
            const SizedBox(height: 8),
            Wrap(
              spacing: 10, runSpacing: 10,
              children: _presetGradients.map((g) => GestureDetector(
                onTap: () { setState(() { _config.bgGradient = g; }); Navigator.pop(ctx); },
                child: Container(
                  width: 44, height: 44,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [Color(g[0]), Color(g[1])]),
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: _config.bgGradient != null && _config.bgGradient![0] == g[0] && _config.bgGradient![1] == g[1] ? const Color(0xFF4FC3F7) : Colors.transparent,
                      width: 3,
                    ),
                  ),
                ),
              )).toList(),
            ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }

  final _emojiInputController = TextEditingController();

  void _addEmoji() {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1A1A2E),
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (ctx) {
        return SizedBox(
          height: MediaQuery.of(ctx).size.height * 0.55,
          child: Column(children: [
            Container(width: 40, height: 4, margin: const EdgeInsets.only(top: 12, bottom: 8),
              decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2))),
            const Text('Add Emoji', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            Expanded(
              child: EmojiPicker(
                onEmojiSelected: (category, emoji) {
                  setState(() {
                    _config.elements.add(IconElement(content: emoji.emoji));
                    _selectedIndex = _config.elements.length - 1;
                  });
                  Navigator.pop(ctx);
                },
                config: Config(
                  emojiViewConfig: EmojiViewConfig(
                    columns: 8,
                    emojiSizeMax: 28,
                    backgroundColor: const Color(0xFF1A1A2E),
                  ),
                  skinToneConfig: const SkinToneConfig(
                    dialogBackgroundColor: Color(0xFF0D0D1A),
                    indicatorColor: Color(0xFF4FC3F7),
                    enabled: true,
                  ),
                  categoryViewConfig: const CategoryViewConfig(
                    backgroundColor: Color(0xFF1A1A2E),
                    indicatorColor: Color(0xFF4FC3F7),
                    iconColorSelected: Color(0xFF4FC3F7),
                    iconColor: Colors.white38,
                  ),
                  searchViewConfig: const SearchViewConfig(
                    backgroundColor: Color(0xFF1A1A2E),
                    buttonIconColor: Colors.white38,
                    hintText: 'Search emoji...',
                  ),
                  bottomActionBarConfig: const BottomActionBarConfig(
                    backgroundColor: Color(0xFF1A1A2E),
                    buttonIconColor: Colors.white38,
                    buttonColor: Color(0xFF0F3460),
                  ),
                ),
              ),
            ),
          ]),
        );
      },
    );
  }

  Future<void> _addImage() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.image,
    );
    if (result == null || result.files.single.path == null) return;
    try {
      final file = File(result.files.single.path!);
      final bytes = await file.readAsBytes();
      // Decode, resize to max 256px, re-encode as JPEG
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      final img = frame.image;
      final maxDim = img.width > img.height ? img.width : img.height;
      final targetSize = maxDim > 256 ? 256.0 : maxDim.toDouble();
      final imgScale = targetSize / maxDim;
      final w = (img.width * imgScale).round();
      final h = (img.height * imgScale).round();

      final recorder = ui.PictureRecorder();
      final canvas = Canvas(recorder);
      canvas.drawImageRect(
        img,
        Rect.fromLTWH(0, 0, img.width.toDouble(), img.height.toDouble()),
        Rect.fromLTWH(0, 0, w.toDouble(), h.toDouble()),
        Paint(),
      );
      final picture = recorder.endRecording();
      final resized = await picture.toImage(w, h);
      final byteData = await resized.toByteData(format: ui.ImageByteFormat.png);
      if (byteData == null) return;

      final b64 = base64Encode(byteData.buffer.asUint8List());
      setState(() {
        _config.elements.add(IconElement(content: b64, isImage: true));
        _selectedIndex = _config.elements.length - 1;
      });
    } catch (e) {
      // Fallback: just base64 the raw file if resize fails
      final bytes = await File(result.files.single.path!).readAsBytes();
      final b64 = base64Encode(bytes);
      setState(() {
        _config.elements.add(IconElement(content: b64, isImage: true));
        _selectedIndex = _config.elements.length - 1;
      });
    }
  }

  void _addText() {
    final textController = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A2E),
        title: const Text('Add Text'),
        content: TextField(
          controller: textController,
          style: const TextStyle(color: Colors.white),
          decoration: const InputDecoration(hintText: 'Enter text...'),
          autofocus: true,
          textCapitalization: TextCapitalization.words,
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          TextButton(
            onPressed: () {
              if (textController.text.isNotEmpty) {
                setState(() {
                  _config.elements.add(IconElement(
                    content: textController.text,
                    isText: true,
                    color: 0xFFFFFFFF,
                  ));
                  _selectedIndex = _config.elements.length - 1;
                });
              }
              Navigator.pop(ctx);
            },
            child: const Text('Add', style: TextStyle(color: Color(0xFF4FC3F7))),
          ),
        ],
      ),
    ).then((_) => textController.dispose());
  }

  void _deleteSelected() {
    if (_selectedIndex < 0 || _selectedIndex >= _config.elements.length) return;
    setState(() {
      _config.elements.removeAt(_selectedIndex);
      if (_config.elements.isEmpty) {
        _selectedIndex = -1;
      } else {
        _selectedIndex = _selectedIndex.clamp(0, _config.elements.length - 1);
      }
    });
  }
}

/// Reusable widget to render an IconConfig preview at any size
// Cache decoded image bytes to avoid re-decoding base64 on every frame
final Map<int, Uint8List> _imageCache = {};
final Uint8List _emptyPixel = Uint8List.fromList([0]); // fallback for corrupt data
Uint8List _cachedDecode(String b64) {
  final key = b64.hashCode;
  return _imageCache.putIfAbsent(key, () {
    try { return base64Decode(b64); }
    catch (_) { return _emptyPixel; }
  });
}

const _filterNames = ['none', 'grayscale', 'sepia', 'invert', 'vibrant', 'warm', 'cool', 'contrast', 'fade', 'duotone', 'nightshift', 'posterize'];

String _filterLabel(String f) {
  switch (f) {
    case 'none': return 'None';
    case 'grayscale': return 'B&W';
    case 'nightshift': return 'Night';
    default: return f[0].toUpperCase() + f.substring(1);
  }
}

List<double> _colorMatrixFor(String filter) {
  switch (filter) {
    case 'grayscale': return [0.2126,0.7152,0.0722,0,0, 0.2126,0.7152,0.0722,0,0, 0.2126,0.7152,0.0722,0,0, 0,0,0,1,0];
    case 'sepia': return [0.393,0.769,0.189,0,0, 0.349,0.686,0.168,0,0, 0.272,0.534,0.131,0,0, 0,0,0,1,0];
    case 'invert': return [-1,0,0,0,255, 0,-1,0,0,255, 0,0,-1,0,255, 0,0,0,1,0];
    case 'vibrant': return [1.5,-0.25,-0.25,0,0, -0.25,1.5,-0.25,0,0, -0.25,-0.25,1.5,0,0, 0,0,0,1,0];
    case 'warm': return [1.2,0.1,0,0,10, 0,1.05,0,0,5, 0,0,0.8,0,-10, 0,0,0,1,0];
    case 'cool': return [0.85,0,0,0,-10, 0,1.0,0.1,0,0, 0,0.1,1.2,0,15, 0,0,0,1,0];
    case 'contrast': return [1.5,0,0,0,-40, 0,1.5,0,0,-40, 0,0,1.5,0,-40, 0,0,0,1,0];
    case 'fade': return [0.8,0.1,0.1,0,30, 0.1,0.8,0.1,0,30, 0.1,0.1,0.8,0,30, 0,0,0,1,0];
    case 'duotone': return [0.3,0.6,0.1,0,20, 0.1,0.3,0.6,0,10, 0.5,0.3,0.2,0,40, 0,0,0,1,0];
    case 'nightshift': return [1.1,0.1,0,0,20, 0,0.9,0.1,0,10, 0,0,0.6,0,-20, 0,0,0,1,0];
    case 'posterize': return [2.0,-0.5,-0.5,0,-20, -0.5,2.0,-0.5,0,-20, -0.5,-0.5,2.0,0,-20, 0,0,0,1,0];
    default: return [1,0,0,0,0, 0,1,0,0,0, 0,0,1,0,0, 0,0,0,1,0];
  }
}

/// Resolve shadow preset to BoxShadow, or null for 'none'
BoxShadow? _resolveShadow(IconElement el) {
  switch (el.shadowPreset) {
    case 'subtle': return const BoxShadow(color: Color(0x4D000000), blurRadius: 4, offset: Offset(1, 1));
    case 'drop': return const BoxShadow(color: Color(0x80000000), blurRadius: 8, offset: Offset(2, 3));
    case 'hard': return const BoxShadow(color: Color(0xB3000000), blurRadius: 1, offset: Offset(3, 3));
    case 'glow': return BoxShadow(color: Color(el.isText ? el.color : 0xFF4FC3F7).withValues(alpha: 0.6), blurRadius: 12, spreadRadius: 4);
    case 'neon': return const BoxShadow(color: Color(0xCC4FC3F7), blurRadius: 16, spreadRadius: 6);
    case 'custom': return BoxShadow(
      color: Color(el.shadowColor).withValues(alpha: el.shadowOpacity),
      blurRadius: el.shadowBlur,
      offset: Offset(el.shadowOffsetX, el.shadowOffsetY),
      spreadRadius: el.shadowSpread,
    );
    default: return null;
  }
}

class IconPreview extends StatelessWidget {
  final IconConfig config;
  final double size;
  const IconPreview({super.key, required this.config, required this.size});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size, height: size,
      decoration: BoxDecoration(
        color: config.bgGradient == null ? Color(config.bgColor) : null,
        gradient: config.bgGradient != null ? LinearGradient(
          begin: Alignment.topLeft, end: Alignment.bottomRight,
          colors: [Color(config.bgGradient![0]), Color(config.bgGradient![1])],
        ) : null,
        borderRadius: BorderRadius.circular(size / 5),
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        clipBehavior: Clip.hardEdge,
        children: [
          for (final el in config.elements)
            _buildPreviewElement(el, size),
        ],
      ),
    );
  }

  static Widget _buildPreviewElement(IconElement el, double size) {
    final cs = size * 3;
    Widget child;
    if (el.isImage) {
      child = Image.memory(
        _cachedDecode(el.content),
        width: size * 0.6 * el.scale,
        height: size * 0.6 * el.scale,
        fit: BoxFit.cover,
      );
    } else {
      child = Text(
        el.content,
        style: TextStyle(
          fontSize: (el.isText ? size * 0.25 : size * 0.55) * el.scale,
          color: el.isText ? Color(el.color) : null,
          fontWeight: el.isText ? FontWeight.bold : null,
          height: 1.1,
        ),
        textAlign: TextAlign.center,
      );
    }
    return Positioned(
      left: size / 2 + el.offsetX * size - cs / 2,
      top: size / 2 + el.offsetY * size - cs / 2,
      width: cs,
      height: cs,
      child: Transform.rotate(
        angle: el.rotation * pi / 180,
        child: Center(
          child: Opacity(
            opacity: el.opacity,
            child: Container(
              decoration: _resolveShadow(el) != null ? BoxDecoration(
                boxShadow: [_resolveShadow(el)!],
              ) : null,
              child: el.filter != 'none' ? ColorFiltered(
                colorFilter: ColorFilter.matrix(_colorMatrixFor(el.filter)),
                child: child,
              ) : child,
            ),
          ),
        ),
      ),
    );
  }
}
