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

/// Reads and writes user preferences (AI keys, custom prompt, flags).

import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/ai_provider.dart';

class Settings {
  static const showcaseBaseUrl = 'https://raw.githubusercontent.com/iappyx/iappyxOS-showcase/main';

  static SharedPreferences? _prefs;
  static const _secure = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  static Future<SharedPreferences> getPrefs() async {
    _prefs ??= await SharedPreferences.getInstance();
    return _prefs!;
  }

  // ── Secure key helpers ──

  static Future<String> _readKey(String id) async {
    try {
      final key = await _secure.read(key: 'apikey_$id');
      return key ?? '';
    } catch (e) {
      debugPrint('[Settings] Secure read failed for $id: $e');
      return '';
    }
  }

  static Future<void> _writeKey(String id, String apiKey) async {
    try {
      if (apiKey.isNotEmpty) {
        await _secure.write(key: 'apikey_$id', value: apiKey);
      } else {
        await _secure.delete(key: 'apikey_$id');
      }
    } catch (e) {
      debugPrint('[Settings] Secure write failed for $id: $e');
    }
  }

  // ── Active AI Provider (single, set-and-forget) ──

  static Future<AiProvider?> getActiveProvider() async {
    final prefs = await getPrefs();
    final json = prefs.getString('active_provider');
    if (json == null || json.isEmpty) return null;
    try {
      final provider = AiProvider.fromJson(jsonDecode(json) as Map<String, dynamic>);
      // Restore API key from secure storage
      final secureKey = await _readKey(provider.id);
      if (secureKey.isNotEmpty) {
        provider.apiKey = secureKey;
      }
      return provider;
    } catch (e) {
      debugPrint('[Settings] getActiveProvider failed: $e');
      return null;
    }
  }

  static Future<void> setActiveProvider(AiProvider provider) async {
    final prefs = await getPrefs();
    // Store API key in secure storage
    await _writeKey(provider.id, provider.apiKey);
    // Strip key from SharedPreferences
    final json = provider.toJson();
    json.remove('apiKey');
    await prefs.setString('active_provider', jsonEncode(json));
  }

  // ── AI Providers (legacy multi-provider, kept for migration) ──

  static Future<List<AiProvider>> getProviders() async {
    final prefs = await getPrefs();
    final json = prefs.getString('ai_providers');
    if (json == null || json.isEmpty) {
      return [AiProvider.anthropic(), AiProvider.openRouter()];
    }
    try {
      final list = jsonDecode(json) as List;
      final providers = list.map((j) => AiProvider.fromJson(j as Map<String, dynamic>)).toList();
      for (final p in providers) {
        final secureKey = await _readKey(p.id);
        if (secureKey.isNotEmpty) p.apiKey = secureKey;
      }
      return providers;
    } catch (_) {
      return [AiProvider.anthropic(), AiProvider.openRouter()];
    }
  }

  static Future<void> saveProviders(List<AiProvider> providers) async {
    final prefs = await getPrefs();
    for (final p in providers) {
      await _writeKey(p.id, p.apiKey);
    }
    final jsonList = providers.map((p) {
      final j = p.toJson();
      j.remove('apiKey');
      return j;
    }).toList();
    await prefs.setString('ai_providers', jsonEncode(jsonList));
  }

  static Future<AiProvider?> getProvider(String id) async {
    final providers = await getProviders();
    try { return providers.firstWhere((p) => p.id == id); }
    catch (_) { return null; }
  }

  static Future<void> updateProvider(AiProvider provider) async {
    final providers = await getProviders();
    final idx = providers.indexWhere((p) => p.id == provider.id);
    if (idx >= 0) providers[idx] = provider;
    else providers.add(provider);
    await saveProviders(providers);
  }

  // ── Legacy API key (migrate to providers) ──
  static Future<String> getApiKey() async {
    final prefs = await getPrefs();
    return prefs.getString('api_key') ?? '';
  }

  static Future<void> setApiKey(String key) async {
    final prefs = await getPrefs();
    await prefs.setString('api_key', key);
  }

  static Future<String> getGithubToken() async {
    try { return await _secure.read(key: 'github_token') ?? ''; }
    catch (_) { return ''; }
  }

  static Future<void> setGithubToken(String token) async {
    await _secure.write(key: 'github_token', value: token);
  }

  static Future<String> getModel() async {
    final prefs = await getPrefs();
    return prefs.getString('ai_model') ?? 'claude-sonnet-4-20250514';
  }

  static Future<void> setModel(String model) async {
    final prefs = await getPrefs();
    await prefs.setString('ai_model', model);
  }

  // ── Custom system prompt ──
  static Future<String?> getCustomPrompt() async {
    final prefs = await getPrefs();
    return prefs.getString('custom_prompt');
  }

  static Future<void> setCustomPrompt(String? prompt) async {
    final prefs = await getPrefs();
    if (prompt == null || prompt.isEmpty) {
      await prefs.remove('custom_prompt');
    } else {
      await prefs.setString('custom_prompt', prompt);
    }
  }

  static Future<bool> hasCompletedOnboarding() async {
    final prefs = await getPrefs();
    return prefs.getBool('onboarding_done') ?? false;
  }

  static Future<void> setOnboardingDone() async {
    final prefs = await getPrefs();
    await prefs.setBool('onboarding_done', true);
  }

  static Future<bool> hasCustomPrompt() async {
    final prefs = await getPrefs();
    return prefs.containsKey('custom_prompt');
  }

  static Future<String> getLastSeenPromptHash() async {
    final prefs = await getPrefs();
    return prefs.getString('last_seen_prompt_hash') ?? '';
  }

  static Future<void> setLastSeenPromptHash(String hash) async {
    final prefs = await getPrefs();
    await prefs.setString('last_seen_prompt_hash', hash);
  }

  // ── Package prefix ──
  static const int maxPrefixLength = 20;

  // Frysk: positive adjectives evoking beauty + the 11 Frisian cities
  static const _adjectives = [
    'moai','kreas','noflik','leaf','sierlik',
    'prachtich','swiid','skjin','ljocht','skoander','smuk',
  ];
  static const _nouns = [
    'ljouwert','snits','drylts','sleat','starum',
    'hylpen','warkum','boalsert','harns','frjentsjer','dokkum',
  ];

  static String _generatePrefix() {
    final r = DateTime.now().microsecondsSinceEpoch;
    final adj = _adjectives[r % _adjectives.length];
    final noun = _nouns[(r ~/ 100) % _nouns.length];
    return '$adj.$noun';
  }

  static Future<String> getPackagePrefix() async {
    final prefs = await getPrefs();
    var prefix = prefs.getString('package_prefix');
    if (prefix == null || prefix.isEmpty || prefix == 'com.iappyx.g') {
      // First launch or legacy default — generate a unique prefix
      prefix = _generatePrefix();
      await prefs.setString('package_prefix', prefix);
    }
    if (!validatePrefix(prefix)) {
      prefix = _generatePrefix();
      await prefs.setString('package_prefix', prefix);
    }
    return prefix;
  }

  static Future<void> setPackagePrefix(String prefix) async {
    final prefs = await getPrefs();
    if (validatePrefix(prefix)) {
      await prefs.setString('package_prefix', prefix);
    }
  }

  static bool validatePrefix(String prefix) {
    if (prefix.isEmpty || prefix.length > maxPrefixLength) return false;
    if (!RegExp(r'^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$').hasMatch(prefix)) return false;
    return true;
  }

  // ── CSS Style Preset ──

  static const stylePresets = ['default', 'material', 'glassmorphic', 'minimal', 'dynamic'];
  static const styleLabels = {
    'default': 'None',
    'material': 'Material',
    'glassmorphic': 'Glassmorphic',
    'minimal': 'Minimal',
    'dynamic': 'Dynamic',
  };

  static Future<String> getCssStylePreset() async {
    final prefs = await getPrefs();
    return prefs.getString('css_style_preset') ?? 'default';
  }

  static Future<void> setCssStylePreset(String preset) async {
    final prefs = await getPrefs();
    await prefs.setString('css_style_preset', preset);
  }

  static String getCssForPreset(String preset) {
    switch (preset) {
      case 'material':
        return '''
## CSS Style: Material Design
Apply these Material Design styles to ALL generated apps:
- `font-family: Roboto, 'Noto Color Emoji', sans-serif` instead of -apple-system (emoji fallback required)
- `touch-action: manipulation` on all interactive elements (eliminates 300ms tap delay)
- Minimum touch targets: 48px height for buttons and tappable elements
- Buttons: subtle elevation with `box-shadow: 0 1px 3px rgba(0,0,0,0.3), 0 1px 2px rgba(0,0,0,0.4)`, active state `box-shadow: 0 3px 6px rgba(0,0,0,0.3)` with `transform: translateY(-1px)`
- Add CSS ripple effect on buttons: use `position:relative;overflow:hidden` with `:active::after` pseudo-element (radial gradient flash, 300ms)
- Input fields: bottom border style (2px solid transparent, focus: 2px solid #4FC3F7) with smooth transition
- Cards: `box-shadow: 0 2px 4px rgba(0,0,0,0.2)` elevation
- All transitions: `transition: all 150ms cubic-bezier(0.4, 0, 0.2, 1)`
- Rounded corners: 12px for cards, 24px for action buttons (pill shape)
''';
      case 'glassmorphic':
        return '''
## CSS Style: Glassmorphic
Apply these glassmorphic styles to ALL generated apps:
- Cards/surfaces: `background: rgba(255,255,255,0.05); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); border: 1px solid rgba(255,255,255,0.08)`
- Buttons: `background: rgba(15,52,96,0.6); backdrop-filter: blur(8px); border: 1px solid rgba(79,195,247,0.2)`
- Active buttons: `background: rgba(15,52,96,0.9)` with no transform
- Input fields: `background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.1)` focus: `border-color: rgba(79,195,247,0.4)`
- Subtle glow effects: `box-shadow: 0 0 20px rgba(79,195,247,0.05)` on cards
- `font-family: -apple-system, sans-serif` (keep default)
- `touch-action: manipulation` on all interactive elements
- All transitions: `transition: all 200ms ease`
- Rounded corners: 16px for cards, 12px for buttons
''';
      case 'minimal':
        return '''
## CSS Style: Minimal
Apply these minimal styles to ALL generated apps:
- Ultra-clean design with maximum whitespace
- Cards/surfaces: `background: #1a1a2e; border: 1px solid rgba(255,255,255,0.06)` — no shadows, no blur
- Buttons: `background: transparent; border: 1px solid rgba(255,255,255,0.15); color: #eaeaea` — outline only
- Primary action button: `border-color: #4FC3F7; color: #4FC3F7`
- Active buttons: `background: rgba(255,255,255,0.05)`
- Input fields: border-bottom only, 1px solid rgba(255,255,255,0.15), no background
- `font-family: -apple-system, sans-serif`
- `touch-action: manipulation` on all interactive elements
- Font weight: use 300 (light) for body text, 500 for headings
- Generous padding: 20px inside cards, 12px between elements
- All transitions: `transition: all 100ms ease`
- Rounded corners: 8px for cards, 8px for buttons (subtle)
''';
      case 'dynamic':
        return '''
## CSS Style: Dynamic (Material You)
This app MUST use the device's dynamic theme colors from Android 12+ Material You.
In the bridge init (`onReady`), call `JSON.parse(iappyx.device.getThemeColors())` to get:
`{primary, primaryLight, primaryDark, secondary, tertiary, neutral, neutralLight, neutralDark, background, surface, isDark, dynamic}`

Set CSS variables on `:root` from these values:
```javascript
var t = JSON.parse(iappyx.device.getThemeColors());
var s = document.documentElement.style;
s.setProperty('--primary', t.primary);
s.setProperty('--primary-light', t.primaryLight);
s.setProperty('--primary-dark', t.primaryDark);
s.setProperty('--secondary', t.secondary);
s.setProperty('--bg', t.background);
s.setProperty('--surface', t.surface);
s.setProperty('--text', t.neutralLight);
s.setProperty('--text-secondary', t.neutral);
s.setProperty('--on-primary', t.onPrimary);
s.setProperty('--on-surface', t.onSurface);
s.setProperty('--on-bg', t.onBackground);
```

Then use CSS variables throughout: `background: var(--bg)`, `color: var(--text)`, buttons: `background: var(--primary-dark); color: var(--on-primary)`, accent: `var(--primary)`, cards: `background: var(--surface); color: var(--on-surface)`, body text: `color: var(--on-bg)`.
Always use `var(--on-primary)` for text on primary-colored buttons, `var(--on-surface)` for text on cards — these guarantee readable contrast.
Do NOT hardcode #0d0d1a, #1a1a2e, #0f3460, #4FC3F7 — use the CSS variables instead.
- `touch-action: manipulation` on all interactive elements
- All transitions: `transition: all 150ms ease`
- Rounded corners: 12px for cards, 12px for buttons
- Do NOT override native checkbox, radio, toggle/switch styling
''';
      default:
        return '';
    }
  }
}
