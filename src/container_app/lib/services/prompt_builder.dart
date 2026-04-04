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
