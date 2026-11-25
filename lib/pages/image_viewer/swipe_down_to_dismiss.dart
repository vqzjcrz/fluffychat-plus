// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:math';

import 'package:material_ui/material_ui.dart';

/// Lets the user drag [child] down to close the viewer, like in photo and
/// video apps. Let go early and it springs back.
class SwipeDownToDismiss extends StatefulWidget {
  final Widget child;
  final VoidCallback onDismiss;

  /// How far the drag has gone, from 0 to 1, for fading the background.
  final ValueChanged<double>? onProgress;

  const SwipeDownToDismiss({
    required this.child,
    required this.onDismiss,
    this.onProgress,
    super.key,
  });

  @override
  State<SwipeDownToDismiss> createState() => _SwipeDownToDismissState();
}

class _SwipeDownToDismissState extends State<SwipeDownToDismiss>
    with SingleTickerProviderStateMixin {
  double _offset = 0;
  double _releasedAt = 0;
  bool _dismissed = false;
  late final AnimationController _springBack;

  @override
  void initState() {
    super.initState();
    _springBack =
        AnimationController(
          vsync: this,
          duration: const Duration(milliseconds: 200),
        )..addListener(() {
          final t = Curves.easeOut.transform(_springBack.value);
          _setOffset(_releasedAt * (1 - t));
        });
  }

  @override
  void dispose() {
    _springBack.dispose();
    super.dispose();
  }

  double get _height => max(1.0, MediaQuery.sizeOf(context).height);

  void _setOffset(double offset) {
    setState(() => _offset = offset);
    widget.onProgress?.call((offset / (_height * 0.4)).clamp(0.0, 1.0));
  }

  void _onUpdate(DragUpdateDetails details) {
    if (_dismissed) return;
    _springBack.stop();
    _setOffset(max(0, _offset + details.delta.dy));
  }

  void _onEnd(DragEndDetails details) {
    if (_dismissed) return;
    final flungDown = (details.primaryVelocity ?? 0) > 800;
    if (_offset > _height * 0.15 || (flungDown && _offset > 0)) {
      _dismissed = true;
      widget.onDismiss();
      return;
    }
    _releasedAt = _offset;
    _springBack.forward(from: 0);
  }

  @override
  Widget build(BuildContext context) {
    final progress = (_offset / (_height * 0.4)).clamp(0.0, 1.0);
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onVerticalDragUpdate: _onUpdate,
      onVerticalDragEnd: _onEnd,
      onVerticalDragCancel: () => _onEnd(DragEndDetails()),
      child: Transform.translate(
        offset: Offset(0, _offset),
        child: Transform.scale(scale: 1 - progress * 0.15, child: widget.child),
      ),
    );
  }
}
