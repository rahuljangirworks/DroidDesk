import 'dart:ffi';
import 'dart:io';

void main() {
  final stdlib = DynamicLibrary.process();
  final malloc = stdlib
      .lookupFunction<
        Pointer<Void> Function(IntPtr),
        Pointer<Void> Function(int)
      >('malloc');
  final free = stdlib
      .lookupFunction<
        Void Function(Pointer<Void>),
        void Function(Pointer<Void>)
      >('free');

  final ptr = malloc(1024);
  stdout.writeln('Allocated at: ${ptr.address}');
  free(ptr);
  stdout.writeln('Freed successfully');
}
