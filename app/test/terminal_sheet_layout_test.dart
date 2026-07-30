import 'package:droiddesk/screens/home_screen.dart';
import 'package:droiddesk/state/app_state.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

void main() {
  testWidgets('terminal sheet stays above a landscape software keyboard', (
    tester,
  ) async {
    // Xiaomi Pad 5 app viewport reported by Android in landscape.
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(1137, 668);
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      ChangeNotifierProvider<AppState>(
        create: (_) => AppState(),
        child: const MaterialApp(home: HomeScreen()),
      ),
    );

    final terminalAction = find.text('Terminal');
    await tester.ensureVisible(terminalAction);
    await tester.tap(terminalAction);
    await tester.pumpAndSettle();

    // Approximate the keyboard height observed on the physical tablet.
    tester.view.viewInsets = const FakeViewPadding(bottom: 320);
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(TextField), findsOneWidget);

    final inputRect = tester.getRect(find.byType(TextField));
    expect(inputRect.bottom, lessThanOrEqualTo(668 - 320));
  });
}
