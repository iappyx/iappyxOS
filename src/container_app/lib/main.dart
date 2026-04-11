import 'package:flutter/material.dart';
import 'screens/create_screen.dart';
import 'screens/my_apps_screen.dart';
import 'screens/settings_screen.dart';
import 'screens/onboarding_screen.dart';
import 'services/app_storage.dart';
import 'services/settings_service.dart';

void main() => runApp(const IappyxOSApp());

class IappyxOSApp extends StatelessWidget {
  const IappyxOSApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'iappyxOS',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(useMaterial3: true).copyWith(
        scaffoldBackgroundColor: const Color(0xFF0D0D1A),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF4FC3F7),
          surface: Color(0xFF1A1A2E),
        ),
        snackBarTheme: const SnackBarThemeData(
          backgroundColor: Color(0xFF1A1A2E),
          contentTextStyle: TextStyle(color: Colors.white, fontSize: 14),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(10))),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFF1A1A2E),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide.none,
          ),
          hintStyle: const TextStyle(color: Colors.white24),
        ),
      ),
      home: const AppRoot(),
    );
  }
}

class AppRoot extends StatefulWidget {
  const AppRoot({super.key});
  @override
  State<AppRoot> createState() => _AppRootState();
}

class _AppRootState extends State<AppRoot> {
  bool _loading = true;
  bool _showOnboarding = false;

  @override
  void initState() {
    super.initState();
    _checkOnboarding();
  }

  Future<void> _checkOnboarding() async {
    final done = await Settings.hasCompletedOnboarding();
    if (mounted) setState(() { _showOnboarding = !done; _loading = false; });
  }

  void _finishOnboarding() async {
    await Settings.setOnboardingDone();
    if (mounted) setState(() => _showOnboarding = false);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Scaffold(backgroundColor: Color(0xFF0D0D1A));
    if (_showOnboarding) return OnboardingScreen(onDone: _finishOnboarding);
    return const AppShell();
  }
}

class AppShell extends StatefulWidget {
  const AppShell({super.key});
  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int _tab = 0;
  final _createKey = GlobalKey<CreateScreenState>();
  final _myAppsKey = GlobalKey<MyAppsScreenState>();

  void _editApp(AppData app) {
    _createKey.currentState?.loadApp(app);
    setState(() => _tab = 1);
  }

  void _onAppSaved() {
    _myAppsKey.currentState?.refresh();
  }

  void _onAppsImported() {
    _myAppsKey.currentState?.refresh();
  }

  void _switchTab(int tab) {
    if (tab == 1) _createKey.currentState?.refreshProvider();
    setState(() => _tab = tab);
  }

  Future<bool> _onBackPressed() async {
    if (_tab == 1) {
      final cs = _createKey.currentState;
      if (cs != null) {
        if (cs.isShowingBuildLog) { cs.dismissBuildLog(); return false; }
        if (cs.isEditing || cs.hasMode) { cs.handleCreateTap(); return false; }
      }
      setState(() => _tab = 0);
      return false;
    }
    if (_tab == 2) { setState(() => _tab = 0); return false; }
    return true; // My Apps tab — let system handle (exit/minimize)
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final shouldPop = await _onBackPressed();
        if (shouldPop && context.mounted) Navigator.of(context).maybePop();
      },
      child: Scaffold(
      body: IndexedStack(
        index: _tab,
        children: [
          MyAppsScreen(key: _myAppsKey, onEditApp: _editApp, onCreateTap: () {
            final cs = _createKey.currentState;
            if (cs != null && cs.isEditing) {
              cs.handleCreateTap();
              setState(() => _tab = 1);
            } else {
              _switchTab(1);
            }
          }),
          CreateScreen(key: _createKey, onAppSaved: _onAppSaved, onViewMyApps: () => _switchTab(0)),
          SettingsScreen(onAppsImported: _onAppsImported),
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _tab,
        onTap: _switchTab,
        backgroundColor: const Color(0xFF0D0D1A),
        selectedItemColor: const Color(0xFF4FC3F7),
        unselectedItemColor: Colors.white38,
        type: BottomNavigationBarType.fixed,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.folder_outlined),
            label: 'My Apps',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.add_circle_outline),
            label: 'Create',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.settings),
            label: 'Settings',
          ),
        ],
      ),
    ));
  }
}
