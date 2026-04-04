import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../services/app_storage.dart';

const _screenChannel = MethodChannel('com.iappyx.container/generator');

const _prefix = 'IXYQR';
const _chunkSize = 900; // bytes per QR frame — smaller = easier to scan

// ── Send Screen ──

class QRSendScreen extends StatefulWidget {
  final AppData app;
  const QRSendScreen({super.key, required this.app});

  @override
  State<QRSendScreen> createState() => _QRSendScreenState();
}

class _QRSendScreenState extends State<QRSendScreen> {
  late List<String> _frames;
  int _currentFrame = 0;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _screenChannel.invokeMethod('keepScreenOn');
    _prepareFrames();
  }

  void _prepareFrames() {
    // Serialize app data
    final payload = jsonEncode({
      'name': widget.app.name,
      'description': widget.app.description,
      'appType': widget.app.appType,
      'templateId': widget.app.templateId,
      'iconConfig': widget.app.iconConfig,
      'html': widget.app.html,
      'prompt': widget.app.prompt,
    });

    // Compress with gzip
    final compressed = gzip.encode(utf8.encode(payload));
    final b64 = base64Encode(compressed);

    // Split into chunks
    _frames = [];
    final total = (b64.length / _chunkSize).ceil();
    for (var i = 0; i < total; i++) {
      final start = i * _chunkSize;
      final end = (start + _chunkSize).clamp(0, b64.length);
      final chunk = b64.substring(start, end);
      _frames.add('$_prefix|$i|$total|$chunk');
    }

    // Start cycling — 250ms per frame
    _timer = Timer.periodic(const Duration(milliseconds: 250), (_) {
      if (mounted) setState(() => _currentFrame = (_currentFrame + 1) % _frames.length);
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _screenChannel.invokeMethod('releaseScreenOn');
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0D0D1A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0D0D1A),
        title: const Text('Share via QR', style: TextStyle(fontSize: 18)),
        leading: IconButton(icon: const Icon(Icons.close), onPressed: () => Navigator.pop(context)),
      ),
      body: Center(
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          Text(widget.app.name, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: Colors.white)),
          const SizedBox(height: 8),
          Text('${_frames.length} frames • ${(_frames.length * 0.25).toStringAsFixed(0)}s per cycle',
            style: const TextStyle(fontSize: 13, color: Colors.white38)),
          const SizedBox(height: 24),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
              ),
              child: LayoutBuilder(builder: (ctx, constraints) {
                final size = constraints.maxWidth - 24;
                return QrImageView(
                  data: _frames.isNotEmpty ? _frames[_currentFrame] : '',
                  version: QrVersions.auto,
                  size: size,
                  errorCorrectionLevel: QrErrorCorrectLevel.L,
                );
              }),
            ),
          ),
          const SizedBox(height: 16),
          // Frame counter
          Text('Frame ${_currentFrame + 1} / ${_frames.length}',
            style: const TextStyle(fontSize: 14, color: Color(0xFF4FC3F7), fontFeatures: [FontFeature.tabularFigures()])),
          const SizedBox(height: 8),
          // Progress dots
          SizedBox(
            height: 8,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(_frames.length.clamp(0, 30), (i) => Container(
                width: 8, height: 8,
                margin: const EdgeInsets.symmetric(horizontal: 2),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: i == _currentFrame ? const Color(0xFF4FC3F7) : const Color(0xFF1A1A2E),
                ),
              )),
            ),
          ),
          const SizedBox(height: 24),
          const Text('Hold near the receiving device', style: TextStyle(fontSize: 13, color: Colors.white38)),
        ]),
      ),
    );
  }
}

// ── Receive Screen ──

class QRReceiveScreen extends StatefulWidget {
  final VoidCallback? onReceived;
  const QRReceiveScreen({super.key, this.onReceived});

  @override
  State<QRReceiveScreen> createState() => _QRReceiveScreenState();
}

class _QRReceiveScreenState extends State<QRReceiveScreen> {
  final MobileScannerController _controller = MobileScannerController(
    detectionSpeed: DetectionSpeed.normal,
    facing: CameraFacing.back,
  );

  @override
  void initState() {
    super.initState();
    _screenChannel.invokeMethod('keepScreenOn');
  }

  int _totalChunks = 0;
  final Map<int, String> _received = {};
  bool _complete = false;
  String? _error;
  String? _receivedAppName;

  @override
  void dispose() {
    _controller.dispose();
    _screenChannel.invokeMethod('releaseScreenOn');
    super.dispose();
  }

  void _onDetect(BarcodeCapture capture) {
    if (_complete) return;
    for (final barcode in capture.barcodes) {
      final raw = barcode.rawValue;
      if (raw == null || !raw.startsWith('$_prefix|')) continue;

      final parts = raw.split('|');
      if (parts.length < 4) continue;

      final seq = int.tryParse(parts[1]);
      final total = int.tryParse(parts[2]);
      final data = parts.sublist(3).join('|'); // rejoin in case chunk contains |

      if (seq == null || total == null || total <= 0) continue;

      if (_totalChunks == 0) {
        setState(() => _totalChunks = total);
      }

      if (!_received.containsKey(seq)) {
        _received[seq] = data;
        HapticFeedback.lightImpact();
        setState(() {});

        // Check if complete
        if (_received.length == _totalChunks) {
          _assemble();
        }
      }
    }
  }

  Future<void> _assemble() async {
    setState(() => _complete = true);
    _controller.stop();
    HapticFeedback.heavyImpact();

    try {
      // Reassemble base64 string
      final b64 = List.generate(_totalChunks, (i) => _received[i] ?? '').join();

      // Decompress
      final compressed = base64Decode(b64);
      final jsonStr = utf8.decode(gzip.decode(compressed));
      final data = jsonDecode(jsonStr) as Map<String, dynamic>;

      // Save as AppData
      final now = DateTime.now();
      final appId = '${now.millisecondsSinceEpoch}_qr';
      final app = AppData(
        id: appId,
        name: (data['name'] as String?) ?? 'Received App',
        description: (data['description'] as String?) ?? '',
        prompt: (data['prompt'] as String?) ?? '',
        html: (data['html'] as String?) ?? '',
        appType: (data['appType'] as String?) ?? 'ai',
        templateId: (data['templateId'] as String?) ?? '',
        packageName: '',
        apkPath: '',
        iconConfig: (data['iconConfig'] as String?) ?? '',
        createdAt: now,
        updatedAt: now,
      );
      await AppStorage.save(app);

      setState(() => _receivedAppName = app.name);
      widget.onReceived?.call();
    } catch (e) {
      final msg = e.toString(); setState(() => _error = 'Failed to decode: ${msg.length > 100 ? msg.substring(0, 100) : msg}');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0D0D1A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0D0D1A),
        title: const Text('Receive via QR', style: TextStyle(fontSize: 18)),
        leading: IconButton(icon: const Icon(Icons.close), onPressed: () => Navigator.pop(context)),
      ),
      body: _complete ? _buildComplete() : _buildScanning(),
    );
  }

  Widget _buildScanning() {
    return Column(children: [
      Expanded(
        child: ClipRRect(
          borderRadius: BorderRadius.circular(16),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: MobileScanner(
                controller: _controller,
                onDetect: _onDetect,
              ),
            ),
          ),
        ),
      ),
      Padding(
        padding: const EdgeInsets.all(16),
        child: Column(children: [
          if (_totalChunks > 0) ...[
            // Progress bar
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: _received.length / _totalChunks,
                backgroundColor: const Color(0xFF1A1A2E),
                color: const Color(0xFF4FC3F7),
                minHeight: 8,
              ),
            ),
            const SizedBox(height: 8),
            Text('${_received.length} / $_totalChunks chunks',
              style: const TextStyle(fontSize: 14, color: Color(0xFF4FC3F7))),
            const SizedBox(height: 4),
            // Chunk grid
            Wrap(
              spacing: 4, runSpacing: 4,
              children: List.generate(_totalChunks.clamp(0, 50), (i) => Container(
                width: 10, height: 10,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(2),
                  color: _received.containsKey(i) ? const Color(0xFF69F0AE) : const Color(0xFF1A1A2E),
                ),
              )),
            ),
          ] else ...[
            const Text('Point camera at the QR code', style: TextStyle(fontSize: 14, color: Colors.white38)),
          ],
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(_error!, style: const TextStyle(fontSize: 12, color: Color(0xFFFF6B6B))),
            ),
          const SizedBox(height: 16),
        ]),
      ),
    ]);
  }

  Widget _buildComplete() {
    return Center(
      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        const Icon(Icons.check_circle, color: Color(0xFF69F0AE), size: 64),
        const SizedBox(height: 16),
        Text(_receivedAppName ?? 'App received!',
          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: Colors.white)),
        const SizedBox(height: 8),
        const Text('Saved to My Apps', style: TextStyle(fontSize: 14, color: Colors.white38)),
        const SizedBox(height: 32),
        FilledButton(
          onPressed: () => Navigator.pop(context),
          style: FilledButton.styleFrom(
            backgroundColor: const Color(0xFF0F3460),
            padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 14),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          child: const Text('Done', style: TextStyle(fontSize: 16, color: Colors.white)),
        ),
      ]),
    );
  }
}
