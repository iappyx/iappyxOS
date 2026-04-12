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

/// Data model for multi-layer app icons (emoji, text, images) serialized as JSON.

import 'dart:convert';

class IconElement {
  String content;
  bool isText;
  bool isImage; // content = base64 JPEG
  double scale;
  double offsetX;
  double offsetY;
  int color; // ARGB
  double rotation; // degrees
  double opacity;  // 0.0–1.0
  String shadowPreset; // none, subtle, drop, hard, glow, neon, custom
  double shadowBlur;
  double shadowOffsetX;
  double shadowOffsetY;
  int shadowColor;
  double shadowOpacity;
  double shadowSpread;
  String filter;   // none, grayscale, sepia, invert, vibrant, warm, cool, contrast, fade, duotone, nightshift, posterize

  IconElement({
    required this.content,
    this.isText = false,
    this.isImage = false,
    this.scale = 1.0,
    this.offsetX = 0.0,
    this.offsetY = 0.0,
    this.color = 0xFFEAEAEA,
    this.rotation = 0.0,
    this.opacity = 1.0,
    this.shadowPreset = 'none',
    this.shadowBlur = 8.0,
    this.shadowOffsetX = 2.0,
    this.shadowOffsetY = 3.0,
    this.shadowColor = 0xFF000000,
    this.shadowOpacity = 0.5,
    this.shadowSpread = 0.0,
    this.filter = 'none',
  });

  Map<String, dynamic> toJson() => {
    'content': content,
    'isText': isText,
    'isImage': isImage,
    'scale': scale,
    'offsetX': offsetX,
    'offsetY': offsetY,
    'color': color,
    'rotation': rotation,
    'opacity': opacity,
    'shadowPreset': shadowPreset,
    'shadowBlur': shadowBlur,
    'shadowOffsetX': shadowOffsetX,
    'shadowOffsetY': shadowOffsetY,
    'shadowColor': shadowColor,
    'shadowOpacity': shadowOpacity,
    'shadowSpread': shadowSpread,
    'filter': filter,
  };

  factory IconElement.fromJson(Map<String, dynamic> j) => IconElement(
    content: j['content'] as String? ?? '',
    isText: j['isText'] as bool? ?? false,
    isImage: j['isImage'] as bool? ?? false,
    scale: (j['scale'] as num?)?.toDouble() ?? 1.0,
    offsetX: (j['offsetX'] as num?)?.toDouble() ?? 0.0,
    offsetY: (j['offsetY'] as num?)?.toDouble() ?? 0.0,
    color: (j['color'] as num?)?.toInt() ?? 0xFFEAEAEA,
    rotation: (j['rotation'] as num?)?.toDouble() ?? 0.0,
    opacity: (j['opacity'] as num?)?.toDouble() ?? 1.0,
    // Backward compat: old shadow: true/false → preset
    shadowPreset: j.containsKey('shadow') && j['shadow'] is bool
        ? (j['shadow'] == true ? 'drop' : 'none')
        : (j['shadowPreset'] as String? ?? 'none'),
    shadowBlur: (j['shadowBlur'] as num?)?.toDouble() ?? 8.0,
    shadowOffsetX: (j['shadowOffsetX'] as num?)?.toDouble() ?? 2.0,
    shadowOffsetY: (j['shadowOffsetY'] as num?)?.toDouble() ?? 3.0,
    shadowColor: (j['shadowColor'] as num?)?.toInt() ?? 0xFF000000,
    shadowOpacity: (j['shadowOpacity'] as num?)?.toDouble() ?? 0.5,
    shadowSpread: (j['shadowSpread'] as num?)?.toDouble() ?? 0.0,
    filter: j['filter'] as String? ?? 'none',
  );

  IconElement copy() => IconElement(
    content: content,
    isText: isText,
    isImage: isImage,
    scale: scale,
    offsetX: offsetX,
    offsetY: offsetY,
    color: color,
    rotation: rotation,
    opacity: opacity,
    shadowPreset: shadowPreset,
    shadowBlur: shadowBlur,
    shadowOffsetX: shadowOffsetX,
    shadowOffsetY: shadowOffsetY,
    shadowColor: shadowColor,
    shadowOpacity: shadowOpacity,
    shadowSpread: shadowSpread,
    filter: filter,
  );
}

class IconConfig {
  int bgColor;
  List<int>? bgGradient; // null = solid, [color1, color2] = gradient
  List<IconElement> elements;

  IconConfig({
    this.bgColor = 0xFF1565C0,
    this.bgGradient,
    List<IconElement>? elements,
  }) : elements = elements ?? [];

  String toJsonString() => jsonEncode({
    'bgColor': bgColor,
    if (bgGradient != null) 'bgGradient': bgGradient,
    'elements': elements.map((e) => e.toJson()).toList(),
  });

  factory IconConfig.fromJsonString(String json) {
    try {
      final j = jsonDecode(json) as Map<String, dynamic>;
      final rawElements = j['elements'] as List? ?? [];
      final elements = <IconElement>[];
      for (final e in rawElements) {
        try {
          elements.add(IconElement.fromJson(e as Map<String, dynamic>));
        } catch (_) {
          // Skip malformed element instead of losing all
        }
      }
      final rawGradient = j['bgGradient'] as List?;
      return IconConfig(
        bgColor: (j['bgColor'] as num?)?.toInt() ?? 0xFF1565C0,
        bgGradient: rawGradient?.map((e) => (e as num).toInt()).toList(),
        elements: elements,
      );
    } catch (_) {
      return IconConfig();
    }
  }

  /// Create from legacy fields (backward compat)
  factory IconConfig.fromLegacy({
    String emoji = '',
    double emojiScale = 1.0,
    double emojiOffsetX = 0.0,
    double emojiOffsetY = 0.0,
    String appName = '',
  }) {
    final display = emoji.isNotEmpty ? emoji : (appName.isNotEmpty ? appName[0].toUpperCase() : 'A');
    final bgColor = colorForString(display);
    return IconConfig(
      bgColor: bgColor,
      elements: [
        IconElement(
          content: display,
          isText: emoji.isEmpty,
          scale: emojiScale,
          offsetX: emojiOffsetX,
          offsetY: emojiOffsetY,
        ),
      ],
    );
  }

  /// Default icon for an app name
  factory IconConfig.defaultFor(String appName) {
    final display = appName.isNotEmpty ? appName[0].toUpperCase() : 'A';
    return IconConfig(
      bgColor: colorForString(display),
      elements: [IconElement(content: display)],
    );
  }

  static const _colors = [
    0xFF1565C0, 0xFF6A1B9A, 0xFF00838F, 0xFFC62828, 0xFF2E7D32,
    0xFFEF6C00, 0xFF283593, 0xFF4E342E, 0xFF00695C, 0xFFAD1457,
  ];

  static int colorForString(String s) => _colors[s.hashCode.abs() % _colors.length];
}
