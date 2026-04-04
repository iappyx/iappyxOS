import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/ai_provider.dart';

class AiService {
  /// Generate a response from the AI provider.
  /// Returns the assistant's message content.
  static Future<String> generate({
    required AiProvider provider,
    required String systemPrompt,
    required List<ChatMessage> messages,
  }) async {
    return _withRetry(() {
      if (provider.id == 'anthropic') {
        return _callAnthropic(provider, systemPrompt, messages);
      } else if (provider.id == 'openrouter') {
        return _callOpenRouter(provider, systemPrompt, messages);
      }
      throw Exception('Unknown provider: ${provider.id}');
    });
  }

  /// Retry on transient connection errors (reset by peer, timeout).
  static Future<String> _withRetry(Future<String> Function() call, {int maxRetries = 2}) async {
    for (var attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return await call();
      } on SocketException catch (e) {
        if (attempt == maxRetries) rethrow;
        debugPrint('[AI] Connection error, retrying (${attempt + 1}/$maxRetries): $e');
        await Future.delayed(Duration(seconds: 2 * (attempt + 1)));
      } on HttpException catch (e) {
        if (attempt == maxRetries) rethrow;
        debugPrint('[AI] HTTP error, retrying (${attempt + 1}/$maxRetries): $e');
        await Future.delayed(Duration(seconds: 2 * (attempt + 1)));
      }
    }
    throw Exception('Request failed after $maxRetries retries');
  }

  /// Test the provider connection with a simple prompt.
  static Future<bool> testConnection(AiProvider provider) async {
    try {
      final result = await generate(
        provider: provider,
        systemPrompt: 'Respond with exactly: OK',
        messages: [ChatMessage(role: 'user', content: 'Test')],
      );
      return result.trim().isNotEmpty;
    } catch (_) {
      return false;
    }
  }

  /// Fetch available models from Anthropic.
  static Future<List<AiModel>> fetchAnthropicModels(String apiKey) async {
    final response = await http.get(
      Uri.parse('https://api.anthropic.com/v1/models?limit=1000'),
      headers: {
        'x-api-key': apiKey,
        'anthropic-version': '2023-06-01',
      },
    ).timeout(const Duration(seconds: 15));

    if (response.statusCode != 200) throw Exception('Failed to fetch models: ${response.statusCode}');

    final data = jsonDecode(response.body);
    final models = (data['data'] as List).map((m) {
      return AiModel(
        id: m['id'] as String,
        name: m['display_name'] as String? ?? m['id'] as String,
        contextLength: m['max_input_tokens'] as int?,
      );
    }).toList();

    // Sort: newest families first (opus > sonnet > haiku), then by id descending for version
    models.sort((a, b) {
      int family(AiModel m) {
        final id = m.id.toLowerCase();
        if (id.contains('opus')) return 0;
        if (id.contains('sonnet')) return 1;
        if (id.contains('haiku')) return 2;
        return 3;
      }
      final fc = family(a).compareTo(family(b));
      if (fc != 0) return fc;
      return b.id.compareTo(a.id); // newer versions first within same family
    });

    return models;
  }

  /// Fetch available models from OpenRouter (public endpoint, no key needed).
  static Future<List<AiModel>> fetchOpenRouterModels(String apiKey) async {
    final response = await http.get(
      Uri.parse('https://openrouter.ai/api/v1/models'),
    ).timeout(const Duration(seconds: 15));

    if (response.statusCode != 200) throw Exception('Failed to fetch models: ${response.statusCode}');

    final data = jsonDecode(response.body);
    final models = (data['data'] as List).map((m) {
      final pricing = m['pricing'] as Map?;
      final prompt = pricing != null ? double.tryParse(pricing['prompt']?.toString() ?? '') : null;
      return AiModel(
        id: m['id'] as String,
        name: m['name'] as String? ?? m['id'] as String,
        contextLength: m['context_length'] as int?,
        pricePer1mTokens: prompt != null ? prompt * 1000000 : null,
      );
    }).toList();

    // Sort: Claude first, then GPT, then others
    models.sort((a, b) {
      int score(AiModel m) {
        if (m.id.contains('claude')) return 0;
        if (m.id.contains('gpt')) return 1;
        if (m.id.contains('llama')) return 2;
        if (m.id.contains('mistral')) return 3;
        return 4;
      }
      return score(a).compareTo(score(b));
    });

    return models;
  }

  // ── Anthropic ──

  static Future<String> _callAnthropic(AiProvider provider, String systemPrompt, List<ChatMessage> messages) async {
    final body = jsonEncode({
      'model': provider.selectedModel,
      'max_tokens': 16000,
      'system': systemPrompt,
      'messages': messages.map((m) => {'role': m.role, 'content': m.content}).toList(),
    });

    final response = await http.post(
      Uri.parse(provider.baseUrl),
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': provider.apiKey,
        'anthropic-version': '2023-06-01',
      },
      body: body,
    ).timeout(const Duration(seconds: 300));

    if (response.statusCode != 200) {
      final error = jsonDecode(response.body);
      throw Exception(error['error']?['message'] ?? 'API error ${response.statusCode}');
    }

    final data = jsonDecode(response.body);
    final content = data['content'] as List?;
    if (content == null || content.isEmpty) throw Exception('Empty response from API');
    return content.first['text'] as String;
  }

  // ── OpenRouter ──

  static Future<String> _callOpenRouter(AiProvider provider, String systemPrompt, List<ChatMessage> messages) async {
    final allMessages = [
      {'role': 'system', 'content': systemPrompt},
      ...messages.map((m) => {'role': m.role, 'content': m.content}),
    ];

    final body = jsonEncode({
      'model': provider.selectedModel,
      'max_tokens': 16000,
      'messages': allMessages,
    });

    final response = await http.post(
      Uri.parse(provider.baseUrl),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ${provider.apiKey}',
        'HTTP-Referer': 'https://iappyx.com',
        'X-Title': 'iappyxOS',
      },
      body: body,
    ).timeout(const Duration(seconds: 300));

    if (response.statusCode != 200) {
      final error = jsonDecode(response.body);
      throw Exception(error['error']?['message'] ?? 'API error ${response.statusCode}');
    }

    final data = jsonDecode(response.body);
    final choices = data['choices'] as List?;
    if (choices == null || choices.isEmpty) throw Exception('Empty response from API');
    return choices[0]['message']['content'] as String;
  }
}
