// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/plus_colors.dart';
import 'package:fluffychat/config/themes.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

class UnreadBubble extends StatelessWidget {
  final Room room;
  const UnreadBubble({required this.room, super.key});

  @override
  Widget build(BuildContext context) {
    final plus = PlusColors.of(context);
    final unread = room.isUnreadOrInvited || room.hasNewMessages;
    final count = room.notificationCount;
    final mention = room.highlightCount > 0;
    final showCount = count > 0 || room.membership == .invite;
    final Widget child;
    if (!unread && !showCount) {
      child = const SizedBox.shrink();
    } else if (!showCount) {
      // New messages that don't notify: just a dot.
      child = Container(
        width: 9,
        height: 9,
        decoration: BoxDecoration(color: plus.accent, shape: BoxShape.circle),
      );
    } else {
      child = Container(
        constraints: const BoxConstraints(minWidth: 20),
        height: 20,
        padding: const EdgeInsets.symmetric(horizontal: 6),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: mention ? plus.danger : plus.accent,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(
          room.membership == .invite
              ? '!'
              : '${mention ? '@' : ''}$count',
          style: TextStyle(
            color: mention ? plus.onDanger : plus.onAccent,
            fontSize: 11,
            fontWeight: FontWeight.w900,
            height: 1,
          ),
          textAlign: .center,
        ),
      );
    }
    return AnimatedSize(
      duration: FluffyThemes.animationDuration,
      curve: FluffyThemes.animationCurve,
      child: child,
    );
  }
}
