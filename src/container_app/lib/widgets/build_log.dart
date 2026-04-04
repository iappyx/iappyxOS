import 'package:flutter/material.dart';

class BuildLog extends StatelessWidget {
  final List<String> log;
  const BuildLog({super.key, required this.log});

  @override
  Widget build(BuildContext context) {
    if (log.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SizedBox(height: 24),
        const Text('Build Log', style: TextStyle(fontSize: 13, color: Colors.white54)),
        const SizedBox(height: 8),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: const Color(0xFF0A0A14),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: log.map((msg) => Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Text(msg, style: TextStyle(
                fontSize: 13,
                fontFamily: 'monospace',
                color: msg.startsWith('\u274C') ? const Color(0xFFFF6B6B)
                     : msg.startsWith('\u2705') ? const Color(0xFF69F0AE)
                     : Colors.white70,
              )),
            )).toList(),
          ),
        ),
      ],
    );
  }
}
