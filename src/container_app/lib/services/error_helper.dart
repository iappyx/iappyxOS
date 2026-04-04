class AppError {
  final String message;
  final String? hint;

  const AppError(this.message, [this.hint]);

  @override
  String toString() => hint != null ? '$message\n$hint' : message;
}

AppError friendlyError(String? raw) {
  if (raw == null || raw.isEmpty) return const AppError('Something went wrong');
  final e = raw.toLowerCase();

  // Install errors
  if (e.contains('install_failed_update_incompatible'))
    return const AppError('App was signed by another device', 'Uninstall it first, then rebuild');
  if (e.contains('install_failed_insufficient_storage'))
    return const AppError('Not enough storage', 'Free up space and try again');
  if (e.contains('install_failed_older_sdk'))
    return const AppError('Android version too old', 'This app requires a newer Android version');
  if (e.contains('install_failed_duplicate_permission'))
    return const AppError('Permission conflict with another app', 'Uninstall the conflicting app first');
  if (e.contains('install_failed_conflicting_provider'))
    return const AppError('Provider conflict with another app', 'Uninstall the other version first');
  if (e.contains('install_failed'))
    return AppError('Install failed', _shorten(raw));

  // Build errors
  if (e.contains('a build is already in progress'))
    return const AppError('Build in progress', 'Wait for the current build to finish');
  if (e.contains('label required'))
    return const AppError('App name is required');
  if (e.contains('html required'))
    return const AppError('HTML content is required');

  // API / network errors
  if (e.contains('api error 401') || e.contains('unauthorized') || e.contains('invalid.*api.*key'))
    return const AppError('Invalid API key', 'Check your API key in Settings');
  if (e.contains('api error 429') || e.contains('rate limit'))
    return const AppError('Rate limited', 'Wait a moment and try again');
  if (e.contains('api error 5') || e.contains('internal server error'))
    return const AppError('AI server error', 'Try again in a few seconds');
  if (e.contains('empty response'))
    return const AppError('AI returned an empty response', 'Try rephrasing your description');
  if (e.contains('request failed after'))
    return const AppError('Connection failed after retries', 'Check your internet connection');
  if (e.contains('socketexception') || e.contains('no address associated'))
    return const AppError('No internet connection', 'Check your WiFi or mobile data');
  if (e.contains('connection refused'))
    return const AppError('Server unreachable', 'Check the address and try again');
  if (e.contains('timeout') || e.contains('timed out'))
    return const AppError('Request timed out', 'Try again or check your connection');
  if (e.contains('handshake') || e.contains('ssl') || e.contains('certificate'))
    return const AppError('Secure connection failed', 'The server\'s certificate may be invalid');

  // Permission errors
  if (e.contains('permission denied') || e.contains('permission'))
    return const AppError('Permission required', 'Grant the permission and try again');

  // Fallback: show shortened raw error
  return AppError('Something went wrong', _shorten(raw));
}

String _shorten(String s) {
  final clean = s.replaceAll(RegExp(r'Exception:|PlatformException\([^,]*,\s*'), '').trim();
  return clean.length > 120 ? '${clean.substring(0, 120)}...' : clean;
}
