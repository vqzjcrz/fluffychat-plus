// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:math';

import 'package:fluffychat/config/setting_keys.dart';
import 'package:fluffychat/pages/chat_list/chat_list_item.dart';
import 'package:fluffychat/pages/chat_list/navigation_rail.dart';
import 'package:fluffychat/pages/chat_list/space_view.dart';
import 'package:fluffychat/widgets/matrix.dart';
import 'package:flutter/gestures.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

/// Discord-style panel inside a chat: the spaces bar and the chats of the
/// selected space, so you can jump to another chat without going back.
class SpacesPanel extends StatefulWidget {
  final String currentRoomId;

  const SpacesPanel({required this.currentRoomId, super.key});

  @override
  State<SpacesPanel> createState() => _SpacesPanelState();
}

class _SpacesPanelState extends State<SpacesPanel> {
  String? _spaceId;

  @override
  void initState() {
    super.initState();
    _spaceId = _initialSpace();
  }

  /// The space that was open last if this chat is in it, else any space this
  /// chat is in, else the last space.
  String? _initialSpace() {
    final client = Matrix.of(context).client;
    bool isJoinedSpace(String id) {
      final room = client.getRoomById(id);
      return room != null &&
          room.isSpace &&
          room.membership == Membership.join;
    }

    bool contains(Room space) => space.spaceChildren.any(
      (child) => child.roomId == widget.currentRoomId,
    );

    final last = AppSettings.lastSpace.value;
    if (last.isNotEmpty && isJoinedSpace(last)) {
      if (contains(client.getRoomById(last)!)) return last;
    }
    for (final room in client.rooms) {
      if (room.isSpace && room.membership == Membership.join && contains(room)) {
        return room.id;
      }
    }
    return last.isNotEmpty && isJoinedSpace(last) ? last : null;
  }

  void _openRoom(Room room) {
    final router = GoRouter.of(context);
    Navigator.of(context).pop();
    if (room.id == widget.currentRoomId) return;
    router.go('/rooms/${room.id}');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final spaceId = _spaceId;
    return Drawer(
      width: min(MediaQuery.sizeOf(context).width * 0.9, 440),
      backgroundColor: theme.colorScheme.surface,
      child: SafeArea(
        bottom: false,
        child: Row(
          children: [
            Material(
              color: theme.colorScheme.surface,
              child: SpacesNavigationRail(
                activeSpaceId: spaceId,
                onGoToChats: () => setState(() => _spaceId = null),
                onGoToSpaceId: (id) => setState(() => _spaceId = id),
              ),
            ),
            Expanded(
              child: spaceId == null
                  ? _AllChats(
                      currentRoomId: widget.currentRoomId,
                      onTap: _openRoom,
                    )
                  : SpaceView(
                      key: ValueKey(spaceId),
                      spaceId: spaceId,
                      onBack: () => setState(() => _spaceId = null),
                      onChatTab: _openRoom,
                      activeChat: widget.currentRoomId,
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AllChats extends StatelessWidget {
  final String currentRoomId;
  final void Function(Room room) onTap;

  const _AllChats({required this.currentRoomId, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final client = Matrix.of(context).client;
    final rooms =
        client.rooms
            .where(
              (room) => !room.isSpace && room.membership != Membership.leave,
            )
            .toList()
          ..sort(
            (a, b) =>
                b.latestEventReceivedTime.compareTo(a.latestEventReceivedTime),
          );
    return ListView.builder(
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: rooms.length,
      itemBuilder: (context, i) => ChatListItem(
        rooms[i],
        key: ValueKey(rooms[i].id),
        activeChat: rooms[i].id == currentRoomId,
        onTap: () => onTap(rooms[i]),
      ),
    );
  }
}

/// Opens the panel on a swipe from left to right anywhere in the chat, like
/// Discord. Watches raw pointer movement so it doesn't fight the messages'
/// own gestures (swipe left to reply keeps working).
class SwipeRightToOpen extends StatefulWidget {
  final Widget child;
  final VoidCallback onOpen;
  final bool enabled;

  const SwipeRightToOpen({
    required this.child,
    required this.onOpen,
    this.enabled = true,
    super.key,
  });

  @override
  State<SwipeRightToOpen> createState() => _SwipeRightToOpenState();
}

class _SwipeRightToOpenState extends State<SwipeRightToOpen> {
  static const double _distance = 72;

  int? _pointer;
  Offset _start = Offset.zero;
  bool _done = false;

  void _onDown(PointerDownEvent event) {
    if (!widget.enabled || event.kind != PointerDeviceKind.touch) return;
    if (_pointer != null) {
      // A second finger: not a swipe.
      _pointer = null;
      return;
    }
    _pointer = event.pointer;
    _start = event.position;
    _done = false;
  }

  void _onMove(PointerMoveEvent event) {
    if (event.pointer != _pointer || _done) return;
    final delta = event.position - _start;
    if (delta.dx < -12 || delta.dy.abs() > 48) {
      // Going left or scrolling: leave it alone.
      _done = true;
      return;
    }
    if (delta.dx > _distance && delta.dx > delta.dy.abs() * 2) {
      _done = true;
      widget.onOpen();
    }
  }

  void _onEnd(PointerEvent event) {
    if (event.pointer == _pointer) _pointer = null;
  }

  @override
  Widget build(BuildContext context) => Listener(
    behavior: HitTestBehavior.translucent,
    onPointerDown: _onDown,
    onPointerMove: _onMove,
    onPointerUp: _onEnd,
    onPointerCancel: _onEnd,
    child: widget.child,
  );
}
