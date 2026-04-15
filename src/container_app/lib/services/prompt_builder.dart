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

/// Assembles the system prompt sent to the AI, merging default and custom parts.

import 'package:flutter/services.dart' show rootBundle;
import 'settings_service.dart';

class PromptBuilder {
  static String? _defaultPrompt;

  static Future<String> getSystemPrompt() async {
    // Check for custom prompt first
    final custom = await Settings.getCustomPrompt();
    if (custom != null && custom.isNotEmpty) return custom;
    // Fall back to bundled default
    _defaultPrompt ??= await rootBundle.loadString('assets/system_prompt.md');
    return _defaultPrompt!;
  }

  static Future<String> getDefaultPrompt() async {
    _defaultPrompt ??= await rootBundle.loadString('assets/system_prompt.md');
    return _defaultPrompt!;
  }

  /// Returns true if user has a custom prompt AND the bundled prompt has changed since they last reset.
  static Future<bool> isPromptOutdated() async {
    final hasCustom = await Settings.hasCustomPrompt();
    if (!hasCustom) return false;
    final bundled = await getDefaultPrompt();
    final hash = '${bundled.length}_${bundled.codeUnits.fold<int>(0, (a, b) => a + b)}';
    final lastSeen = await Settings.getLastSeenPromptHash();
    return lastSeen.isNotEmpty && lastSeen != hash;
  }

  /// Call when user resets to default or on first launch to store the current bundled hash.
  static Future<void> markPromptAsSeen() async {
    final bundled = await getDefaultPrompt();
    final hash = '${bundled.length}_${bundled.codeUnits.fold<int>(0, (a, b) => a + b)}';
    await Settings.setLastSeenPromptHash(hash);
  }

  /// Returns today's date as an ISO version tag (e.g. "v2026-04-15"). Computed
  /// fresh per call so the prompt always carries the current date — this is a
  /// deliberate signal to the AI: "this is recent, ignore older training data".
  static String _todayVersionTag() {
    final now = DateTime.now();
    String pad(int n) => n < 10 ? '0$n' : '$n';
    return 'v${now.year}-${pad(now.month)}-${pad(now.day)}';
  }

  static Future<String> buildPrompt({
    required String appName,
    required String description,
    String? existingHtml,
  }) async {
    var systemPrompt = await getSystemPrompt();
    final versionTag = _todayVersionTag();
    systemPrompt = systemPrompt.replaceAll('{{VERSION_TAG}}', versionTag);
    final stylePreset = await Settings.getCssStylePreset();
    final styleCss = Settings.getCssForPreset(stylePreset);
    if (styleCss.isNotEmpty) systemPrompt += '\n\n$styleCss';
    if (existingHtml != null && existingHtml.trim().startsWith('<')) {
      return '''$systemPrompt

---

APP UPDATE REQUEST:
App name: $appName
Description: $description

Here is the current app code:
```html
$existingHtml
```

Update this app based on the description. Preserve all existing functionality unless the description explicitly asks to change it. Return the complete updated HTML.

REMINDER (iappyxOS system prompt $versionTag): the code above may contain `iappyx.*` calls that were hallucinated by an earlier AI generation and silently fail at runtime. Before returning the updated HTML, re-verify EVERY `iappyx.*` call — both the ones already in the code and any new ones you add — against the Bridge reference at the top of this prompt. If a call does not match a method in the reference, replace it with the correct method or remove the feature. Do not preserve a wrong call just because it is already in the file.''';
    }
    return '''$systemPrompt

---

APP REQUEST:
App name: $appName
Description: $description

Generate a complete, fully functional app matching this description. Apply all technical requirements from the instructions above.

REMINDER (iappyxOS system prompt $versionTag): every `iappyx.*` method you use must be verified against the Bridge reference above. If you are uncertain whether a method exists, it does not — pick one that IS listed.''';
  }
}
