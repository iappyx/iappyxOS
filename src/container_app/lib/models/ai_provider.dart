class AiModel {
  final String id;
  final String name;
  final String? description;
  final int? contextLength;
  final double? pricePer1mTokens;

  const AiModel({required this.id, required this.name, this.description, this.contextLength, this.pricePer1mTokens});

  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'description': description, 'contextLength': contextLength, 'pricePer1mTokens': pricePer1mTokens};
  factory AiModel.fromJson(Map<String, dynamic> j) => AiModel(
    id: j['id'] as String, name: j['name'] as String,
    description: j['description'] as String?,
    contextLength: j['contextLength'] as int?,
    pricePer1mTokens: (j['pricePer1mTokens'] as num?)?.toDouble(),
  );
}

class AiProvider {
  final String id;
  final String name;
  final String baseUrl;
  String apiKey;
  String selectedModel;
  List<AiModel> models;

  AiProvider({required this.id, required this.name, required this.baseUrl, this.apiKey = '', this.selectedModel = '', List<AiModel>? models})
      : models = models ?? [];

  bool get isConfigured => apiKey.isNotEmpty;

  Map<String, dynamic> toJson() => {
    'id': id, 'name': name, 'baseUrl': baseUrl,
    'apiKey': apiKey, 'selectedModel': selectedModel,
    'models': models.map((m) => m.toJson()).toList(),
  };

  factory AiProvider.fromJson(Map<String, dynamic> j) => AiProvider(
    id: j['id'] as String, name: j['name'] as String, baseUrl: j['baseUrl'] as String,
    apiKey: j['apiKey'] as String? ?? '', selectedModel: j['selectedModel'] as String? ?? '',
    models: (j['models'] as List?)?.map((m) => AiModel.fromJson(m)).toList(),
  );

  static AiProvider anthropic() => AiProvider(
    id: 'anthropic', name: 'Anthropic', baseUrl: 'https://api.anthropic.com/v1/messages',
    models: const [
      AiModel(id: 'claude-sonnet-4-20250514', name: 'Claude Sonnet 4', description: 'Fast, recommended'),
      AiModel(id: 'claude-opus-4-6', name: 'Claude Opus 4.6', description: 'Smartest, slower'),
      AiModel(id: 'claude-haiku-4-5-20251001', name: 'Claude Haiku 4.5', description: 'Cheapest, fastest'),
    ],
    selectedModel: 'claude-sonnet-4-20250514',
  );

  static AiProvider openRouter() => AiProvider(
    id: 'openrouter', name: 'OpenRouter', baseUrl: 'https://openrouter.ai/api/v1/chat/completions',
    selectedModel: 'anthropic/claude-sonnet-4',
  );
}

class ChatMessage {
  final String role; // 'user' | 'assistant'
  final String content;
  final DateTime timestamp;

  ChatMessage({required this.role, required this.content, DateTime? timestamp})
      : timestamp = timestamp ?? DateTime.now();
}
