import 'package:flutter/material.dart';

class OnboardingScreen extends StatelessWidget {
  final VoidCallback onDone;
  const OnboardingScreen({super.key, required this.onDone});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0D0D1A),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Column(
            children: [
              const Spacer(flex: 2),
              const Text('iappyxOS', style: TextStyle(
                fontSize: 32, fontWeight: FontWeight.bold, color: Color(0xFF4FC3F7),
                letterSpacing: -0.5,
              )),
              const SizedBox(height: 8),
              const Text('Apps from words', style: TextStyle(
                fontSize: 15, color: Colors.white38,
              )),
              const Spacer(flex: 2),
              _step(Icons.chat_outlined, 'Describe', 'Tell any AI what app you want — or connect your own API key'),
              const SizedBox(height: 28),
              _step(Icons.build_outlined, 'Build', 'It gets built and signed on your phone'),
              const SizedBox(height: 28),
              _step(Icons.rocket_launch_outlined, 'Use', 'A real app appears in your launcher'),
              const Spacer(flex: 3),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: onDone,
                  style: FilledButton.styleFrom(
                    backgroundColor: const Color(0xFF4FC3F7),
                    foregroundColor: const Color(0xFF0D0D1A),
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  child: const Text('Get started', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                ),
              ),
              const SizedBox(height: 48),
            ],
          ),
        ),
      ),
    );
  }

  Widget _step(IconData icon, String title, String subtitle) {
    return Row(
      children: [
        Container(
          width: 48, height: 48,
          decoration: BoxDecoration(
            color: const Color(0xFF1A1A2E),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Icon(icon, color: const Color(0xFF4FC3F7), size: 24),
        ),
        const SizedBox(width: 16),
        Expanded(child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 2),
            Text(subtitle, style: const TextStyle(fontSize: 13, color: Colors.white54)),
          ],
        )),
      ],
    );
  }
}
