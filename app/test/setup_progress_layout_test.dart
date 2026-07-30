import 'package:droiddesk/screens/setup/setup_progress.dart';
import 'package:droiddesk/state/app_state.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

class _CompletedSetupState extends AppState {
  @override
  bool get isSetupComplete => true;

  @override
  bool get hasRoot => false;

  @override
  double get extractProgress => 1;

  @override
  String get setupLog => List.filled(
    40,
    'Native DWM package installation completed successfully.',
  ).join('\n');

  @override
  Map<String, dynamic> get deviceInfo => const {'availableStorageMB': 4096};

  @override
  Future<bool> detectRootForSetup() async => false;

  @override
  Future<void> runSetup({bool? useRoot}) async {}
}

void main() {
  testWidgets(
    'setup completion keeps its action visible without landscape overflow',
    (tester) async {
      // Xiaomi Pad 5 app viewport reported by Android in landscape.
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(1137, 668);
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        ChangeNotifierProvider<AppState>.value(
          value: _CompletedSetupState(),
          child: const MaterialApp(home: SetupProgressScreen()),
        ),
      );
      await tester.pump(const Duration(seconds: 1));

      expect(tester.takeException(), isNull);

      final launchButton = find.widgetWithText(
        ElevatedButton,
        'Launch DroidDesk',
      );
      expect(launchButton, findsOneWidget);

      final buttonRect = tester.getRect(launchButton);
      expect(buttonRect.top, greaterThanOrEqualTo(0));
      expect(buttonRect.bottom, lessThanOrEqualTo(668));
    },
  );
}
