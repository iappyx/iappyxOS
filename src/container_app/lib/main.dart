import 'package:flutter/material.dart';
import 'screens/create_screen.dart';
import 'screens/my_apps_screen.dart';
import 'screens/settings_screen.dart';
import 'services/app_storage.dart';

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
      home: const AppShell(),
    );
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _tab,
        children: [
          MyAppsScreen(key: _myAppsKey, onEditApp: _editApp, onCreateTap: () => _switchTab(1)),
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
    );
  }
}
