import 'package:flutter/material.dart';
import '../../models/icon_config.dart';
import '../icon_editor_screen.dart';

/// Shared UI components used across all creation flows

Widget buildBackButton(VoidCallback onTap) => GestureDetector(
  onTap: onTap,
  child: const Row(mainAxisSize: MainAxisSize.min, children: [
    Icon(Icons.arrow_back_ios, size: 14, color: Colors.white38),
    SizedBox(width: 4),
    Text('Back', style: TextStyle(fontSize: 13, color: Colors.white38)),
  ]),
);

Widget buildActionButton({
  required String label,
  required VoidCallback? onPressed,
  bool secondary = false,
  IconData? icon,
}) => SizedBox(
  width: double.infinity,
  child: FilledButton(
    onPressed: onPressed,
    style: FilledButton.styleFrom(
      backgroundColor: secondary ? const Color(0xFF1A1A2E) : const Color(0xFF0F3460),
      padding: const EdgeInsets.symmetric(vertical: 16),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      disabledBackgroundColor: const Color(0xFF0F3460).withValues(alpha: 0.3),
    ),
    child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
      if (icon != null) ...[Icon(icon, size: 18, color: Colors.white), const SizedBox(width: 8)],
      Text(label, style: const TextStyle(fontSize: 16, color: Colors.white)),
    ]),
  ),
);

Widget buildIconEditor(IconConfig config, VoidCallback onTap) => Row(
  children: [
    GestureDetector(onTap: onTap, child: IconPreview(config: config, size: 80)),
    const SizedBox(width: 16),
    Expanded(child: GestureDetector(
      onTap: onTap,
      child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Tap to edit icon', style: TextStyle(fontSize: 13, color: Colors.white54)),
        Text('Add emojis, text, change colors', style: TextStyle(fontSize: 11, color: Colors.white24)),
      ]),
    )),
  ],
);

Widget buildModeCard({
  required IconData icon,
  required String title,
  required String subtitle,
  required VoidCallback onTap,
}) => GestureDetector(
  onTap: onTap,
  child: Container(
    width: double.infinity,
    padding: const EdgeInsets.all(20),
    decoration: BoxDecoration(
      color: const Color(0xFF1A1A2E),
      borderRadius: BorderRadius.circular(14),
    ),
    child: Row(children: [
      Container(
        width: 48, height: 48,
        decoration: BoxDecoration(
          color: const Color(0xFF0F3460),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Icon(icon, color: const Color(0xFF4FC3F7), size: 24),
      ),
      const SizedBox(width: 16),
      Expanded(child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
          const SizedBox(height: 2),
          Text(subtitle, style: const TextStyle(fontSize: 12, color: Colors.white38)),
        ],
      )),
      const Icon(Icons.chevron_right, color: Colors.white38, size: 22),
    ]),
  ),
);

/// Demo template data
/// Demo template data — (id, emoji, name, description)
/// Section headers use empty id with '---' prefix in name
const demoTemplates = [
  // ── Apps ──
  ('',           '\u2500', '── Apps',           ''),
  ('todo',       '\u2705', 'Todo List',         'Tasks with checkboxes'),
  ('notes',      '\uD83D\uDCDD', 'Notes',       'Create, edit and delete notes'),
  ('counter',    '\uD83D\uDD22', 'Counter',      'Tap counter with laps'),
  ('timer',      '\u23F1\uFE0F', 'Stopwatch',    'Stopwatch with lap times'),
  ('calculator', '\uD83E\uDDEE', 'Calculator',   'Basic calculator'),
  ('photoeditor','\uD83D\uDDBC\uFE0F', 'Photo Editor', 'Camera + filters + emoji'),
  ('dashboard',  '\uD83D\uDCCA', 'Dashboard',    'Track data & charts (Chart.js)'),
  ('runtracker', '\uD83C\uDFC3', 'Run Tracker',  'GPS tracking, steps, geofencing'),
  // ── Camera & ML ──
  ('',           '\u2500', '── Camera & ML',    ''),
  ('camera',     '\uD83D\uDCF8', 'Camera',       'Photo, video, share'),
  ('qrscanner',  '\uD83D\uDCF7', 'QR Scanner',   'Scan QR codes & barcodes'),
  ('ocrtest',    '\uD83D\uDD0D', 'Text Scanner',  'OCR — scan text from photos'),
  ('classifier', '\uD83E\uDDE0', 'Image Classify','ML object/plant/animal recognition'),
  ('bgremover',  '\u2702\uFE0F', 'BG Remover',    'Remove photo background (ML)'),
  ('qrgen',      '\uD83D\uDD33', 'QR Generator',  'Text, URL, WiFi, contact QR codes'),
  // ── Audio & Voice ──
  ('',           '\u2500', '── Audio & Voice',  ''),
  ('audiotest',  '\uD83C\uDFB5', 'Audio Player',  'Background audio playback'),
  ('voicerecorder','\uD83C\uDFA4', 'Voice Recorder','Record & play audio'),
  ('speechtest', '\uD83C\uDF99\uFE0F', 'Speech to Text','Voice recognition'),
  ('soundtools', '\uD83C\uDF9B\uFE0F', 'Sound Tools','Mic spectrum, sound effects, volume'),
  ('tts',        '\uD83D\uDDE3\uFE0F', 'Text to Speech', 'TTS engine test'),
  ('mediatest',  '\uD83C\uDFA5', 'Media Stream',  'getUserMedia camera + mic'),
  // ── Location & Sensors ──
  ('',           '\u2500', '── Location & Sensors', ''),
  ('location',   '\uD83D\uDCCD', 'Location',      'GPS location test'),
  ('sensor',     '\uD83D\uDCE1', 'Sensors',        'Accelerometer & gyroscope'),
  ('compass',    '\uD83E\uDDED', 'Compass',        'Heading, direction, bearing'),
  ('stepcounter','\uD83D\uDEB6', 'Step Counter',   'Step counter sensor'),
  // ── Device & System ──
  ('',           '\u2500', '── Device & System', ''),
  ('device',     '\uD83D\uDCF1', 'Device Info',    'Battery, model, vibration'),
  ('connectivity','\uD83D\uDCF6', 'Connectivity',  'Network status & device info'),
  ('flashlight', '\uD83D\uDD26', 'Flashlight',     'Torch, brightness, theme detection'),
  ('wallpaper',  '\uD83C\uDFA8', 'Wallpaper',      'Set device wallpaper from photo'),
  ('screentest', '\uD83D\uDCA1', 'Screen',         'Brightness, wake lock, haptic'),
  ('clipboard',  '\uD83D\uDCCB', 'Clipboard',      'Clipboard bridge test'),
  ('smartnotif', '\uD83D\uDD14', 'Smart Notifications','Actions, alarms, app shortcuts'),
  ('sharemedia', '\uD83D\uDD17', 'Share & Media',  'Share target, media session controls'),
  ('reminders',  '\u23F0', 'Reminders',         'Scheduled notifs, repeating, DND, badge'),
  ('powertools', '\uD83D\uDD27', 'Power Tools',  'Clipboard monitor, read Downloads, text select'),
  // ── Communication ──
  ('',           '\u2500', '── Communication',   ''),
  ('contactstest','\uD83D\uDC65', 'Contacts',     'Read device contacts'),
  ('smstest',    '\uD83D\uDCAC', 'Send SMS',      'Send real SMS'),
  ('calendartest','\uD83D\uDCC5', 'Calendar',     'Read and add events'),
  ('nfctest',    '\uD83D\uDCE1', 'NFC Scanner',   'Read & write NFC tags'),
  ('blescan',    '\uD83D\uDD35', 'BLE Scanner',  'Scan & connect Bluetooth LE'),
  ('biotest',    '\uD83D\uDD10', 'Biometric',     'Fingerprint / face auth'),
  // ── Data & Export ──
  ('',           '\u2500', '── Data & Export',   ''),
  ('sqlitetest', '\uD83D\uDDC4\uFE0F', 'SQLite',  'Full SQL database'),
  ('pdftest',    '\uD83D\uDCC4', 'PDF Creator',   'Create & view PDFs (pdf-lib)'),
  ('printexport','\uD83D\uDDA8\uFE0F', 'Print & Export','Print, save to Downloads, share'),
  ('filepicker', '\uD83D\uDCC1', 'File Picker',   'Pick files from storage'),
  ('mediagallery','\uD83D\uDDBC\uFE0F', 'Media Gallery','Browse photos, videos, music'),
  ('downloadmgr','\u2B07\uFE0F', 'Downloader',   'Download files with progress'),
  ('alarmtest',  '\u23F0', 'Alarm',              'Alarm that fires when closed'),
  // ── Network ──
  ('',           '\u2500', '── Network',         ''),
  ('lanshare',   '\uD83D\uDCE1', 'LAN Share',   'Share text & photos over WiFi'),
  ('wifidirect', '\uD83D\uDCF6', 'WiFi Direct', 'P2P sharing without router'),
  ('httpclient', '\uD83C\uDF10', 'HTTP Client', 'Native HTTPS + self-signed certs'),
  ('sshclient',  '\uD83D\uDDA5\uFE0F', 'SSH Client', 'Remote terminal & SFTP'),
  ('networkfiles','\uD83D\uDCC2', 'Network Files', 'Browse Windows/NAS shares (SMB)'),
  ('tcpsocket',  '\uD83D\uDD0C', 'TCP Socket',  'Persistent bidirectional connection'),
  ('udpchat',    '\uD83D\uDCE8', 'UDP Chat',    'Chat via UDP datagrams'),
  // ── Bluetooth ──
  ('',           '\u2500', '── Bluetooth',        ''),
  ('btserial',   '\uD83D\uDD35', 'BT Serial',     'Bluetooth Classic serial terminal'),
  // ── Widget & Tasks ──
  ('',           '\u2500', '── Widget & Tasks',  ''),
  ('widgetdemo', '\uD83D\uDCF2', 'Widget Dashboard', 'Home screen widget with stats'),
  ('taskdemo',   '\u23F0', 'Background Tasks',   'Scheduled tasks + widget refresh'),
];
