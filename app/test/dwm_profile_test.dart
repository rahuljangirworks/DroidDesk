import 'package:droiddesk/state/app_state.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('DWM Rahul is the only default desktop profile', () {
    final state = AppState();

    expect(state.selectedDE, 'dwm-jangir');
    expect(state.selectedDE, isNot('xfce4'));
  });
}
