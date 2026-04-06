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

  static Future<String> buildPrompt({
    required String appName,
    required String description,
    String? existingHtml,
  }) async {
    var systemPrompt = await getSystemPrompt();
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

Update this app based on the description. Preserve all existing functionality unless the description explicitly asks to change it. Return the complete updated HTML.''';
    }
    return '''$systemPrompt

---

APP REQUEST:
App name: $appName
Description: $description

Generate a complete, fully functional app matching this description. Apply all technical requirements from the instructions above.''';
  }
}
