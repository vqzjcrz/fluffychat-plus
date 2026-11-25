// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:async';
import 'dart:convert';

import 'package:collection/collection.dart';
import 'package:fluffychat/config/app_config.dart';
import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/pages/chat_list/active_call_indicator.dart';
import 'package:fluffychat/pages/chat_list/unread_bubble.dart';
import 'package:fluffychat/utils/custom_order_extension.dart';
import 'package:fluffychat/utils/date_time_extension.dart';
import 'package:fluffychat/utils/localized_exception_extension.dart';
import 'package:fluffychat/utils/matrix_live_kit_calls/matrix_live_kit_call.dart';
import 'package:fluffychat/utils/matrix_sdk_extensions/matrix_locals.dart';
import 'package:fluffychat/utils/room_plus_extension.dart';
import 'package:fluffychat/utils/room_status_extension.dart';
import 'package:fluffychat/utils/snooze_extension.dart';
import 'package:fluffychat/utils/space_prefs_extension.dart';
import 'package:fluffychat/utils/stream_extension.dart';
import 'package:fluffychat/utils/string_color.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_ok_cancel_alert_dialog.dart';
import 'package:fluffychat/widgets/avatar.dart';
import 'package:fluffychat/widgets/future_loading_dialog.dart';
import 'package:fluffychat/widgets/hover_builder.dart';
import 'package:fluffychat/widgets/matrix.dart';
import 'package:fluffychat/widgets/snooze_dialog.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

enum SpaceChildAction {
  pin,
  unpin,
  snooze,
  mute,
  unmute,
  markAsUnread,
  markAsRead,
  removeFromSpace,
  leave,
}

enum SpaceActions {
  addChild,
  settings,
  sortRecent,
  sortUnread,
  sortCustom,
  reorder,
  resetOrder,
  markAllRead,
  muteAll,
  unmuteAll,
  leave,
}

/// One row of the space list: a section header or a chat.
sealed class _SpaceListEntry {
  const _SpaceListEntry();
}

class _SectionHeader extends _SpaceListEntry {
  final String label;
  final IconData icon;
  final bool? expanded;
  final VoidCallback? onTap;

  const _SectionHeader(this.label, this.icon, {this.expanded, this.onTap});
}

class _ChildEntry extends _SpaceListEntry {
  final SpaceRoomsChunk$2 item;

  const _ChildEntry(this.item);
}

class SpaceView extends StatefulWidget {
  final String spaceId;
  final void Function() onBack;
  final void Function(Room room) onChatTab;
  final String? activeChat;

  const SpaceView({
    required this.spaceId,
    required this.onBack,
    required this.onChatTab,
    required this.activeChat,
    super.key,
  });

  @override
  State<SpaceView> createState() => _SpaceViewState();
}

class _SpaceViewState extends State<SpaceView> {
  final List<SpaceRoomsChunk$2> _discoveredChildren = [];
  final TextEditingController _filterController = TextEditingController();
  String? _nextBatch;
  bool _noMoreRooms = false;
  bool _isLoading = false;
  bool _reorderMode = false;
  bool _mutedExpanded = false;

  StreamSubscription? _childStateSub;

  @override
  void initState() {
    _loadHierarchy();
    _childStateSub = Matrix.of(context).client.onSync.stream
        .where(
          (syncUpdate) =>
              syncUpdate.rooms?.join?[widget.spaceId]?.timeline?.events?.any(
                (event) => event.type == EventTypes.SpaceChild,
              ) ??
              false,
        )
        .listen(_loadHierarchy);
    super.initState();
  }

  @override
  void dispose() {
    _childStateSub?.cancel();
    _filterController.dispose();
    super.dispose();
  }

  Future<void> _loadHierarchy([_]) async {
    final matrix = Matrix.of(context);
    final room = matrix.client.getRoomById(widget.spaceId);
    if (room == null) return;

    final cacheKey = 'spaces_history_cache${room.id}';
    if (_discoveredChildren.isEmpty) {
      final cachedChildren = matrix.store.getStringList(cacheKey);
      if (cachedChildren != null) {
        try {
          _discoveredChildren.addAll(
            cachedChildren.map(
              (jsonString) =>
                  SpaceRoomsChunk$2.fromJson(jsonDecode(jsonString)),
            ),
          );
        } catch (e, s) {
          Logs().e('Unable to json decode spaces hierarchy cache!', e, s);
          matrix.store.remove(cacheKey);
        }
      }
    }

    setState(() {
      _isLoading = true;
    });

    try {
      final hierarchy = await room.client.getSpaceHierarchy(
        widget.spaceId,
        suggestedOnly: false,
        maxDepth: 2,
        from: _nextBatch,
      );
      if (!mounted) return;
      setState(() {
        if (_nextBatch == null) _discoveredChildren.clear();
        _nextBatch = hierarchy.nextBatch;
        if (hierarchy.nextBatch == null) {
          _noMoreRooms = true;
        }
        _discoveredChildren.addAll(
          hierarchy.rooms.where((room) => room.roomId != widget.spaceId),
        );
        _isLoading = false;
      });

      if (_nextBatch == null) {
        matrix.store.setStringList(
          cacheKey,
          _discoveredChildren
              .map((child) => jsonEncode(child.toJson()))
              .toList(),
        );
      }
    } catch (e, s) {
      Logs().w('Unable to load hierarchy', e, s);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(e.toLocalizedString(context))));
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _joinChildRoom(SpaceRoomsChunk$2 item) async {
    final client = Matrix.of(context).client;
    final space = client.getRoomById(widget.spaceId);
    final via = space?.spaceChildren
        .firstWhereOrNull((child) => child.roomId == item.roomId)
        ?.via;
    final roomResult = await showFutureLoadingDialog(
      context: context,
      future: () async {
        final waitForRoom = client.waitForRoomInSync(item.roomId, join: true);
        await client.joinRoom(item.roomId, via: via);
        await waitForRoom;
        return client.getRoomById(item.roomId)!;
      },
    );
    final room = roomResult.result;
    if (room != null) widget.onChatTab(room);
  }

  /// The room for [item] if the user is in it or invited to it.
  Room? _roomFor(Client client, SpaceRoomsChunk$2 item) {
    final room = client.getRoomById(item.roomId);
    if (room == null || room.membership == Membership.leave) return null;
    return room;
  }

  /// Joined chats of this space (not sub-spaces), for the bulk actions.
  List<Room> _joinedChildRooms(Client client) => _discoveredChildren
      .map((item) => client.getRoomById(item.roomId))
      .whereType<Room>()
      .where((room) => room.membership == Membership.join && !room.isSpace)
      .toList();

  String _displaynameFor(
    BuildContext context,
    SpaceRoomsChunk$2 item,
    Room? joinedRoom,
  ) =>
      item.name ??
      item.canonicalAlias ??
      joinedRoom?.getLocalizedDisplayname() ??
      L10n.of(context).emptyChat;

  List<SpaceRoomsChunk$2> _customSortedChildren(Client client) =>
      sortByCustomOrder(
        _discoveredChildren,
        client.customChildOrder(widget.spaceId),
        (child) => child.roomId,
      );

  List<SpaceRoomsChunk$2> _sortedChildren(Client client, SpaceSortMode mode) {
    if (mode == SpaceSortMode.custom) return _customSortedChildren(client);

    int unreadRank(Room? room) {
      if (room == null || room.membership != Membership.join) return 3;
      if (room.hasUnreadMentions) return 0;
      if (room.isUnread) return 1;
      return 2;
    }

    final indexed = _discoveredChildren
        .mapIndexed(
          (index, item) => (index: index, item: item, room: _roomFor(client, item)),
        )
        .toList();
    indexed.sort((a, b) {
      if (mode == SpaceSortMode.unread) {
        final byUnread = unreadRank(a.room).compareTo(unreadRank(b.room));
        if (byUnread != 0) return byUnread;
      }
      final roomA = a.room;
      final roomB = b.room;
      if (roomA == null || roomB == null) {
        if (roomA != null) return -1;
        if (roomB != null) return 1;
        return a.index.compareTo(b.index);
      }
      final byTime = roomB.latestEventReceivedTime.compareTo(
        roomA.latestEventReceivedTime,
      );
      if (byTime != 0) return byTime;
      return a.index.compareTo(b.index);
    });
    return indexed.map((entry) => entry.item).toList();
  }

  Future<void> _setSortMode(SpaceSortMode mode) async {
    final client = Matrix.of(context).client;
    final saving = client.setSpaceSortMode(widget.spaceId, mode);
    setState(() {});
    try {
      await saving;
    } catch (e, s) {
      Logs().w('Unable to save sort mode', e, s);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(e.toLocalizedString(context))));
    }
  }

  Future<void> _setPinned(String roomId, bool pinned) async {
    final client = Matrix.of(context).client;
    final saving = client.setPinnedInSpace(widget.spaceId, roomId, pinned);
    setState(() {});
    try {
      await saving;
    } catch (e, s) {
      Logs().w('Unable to save pinned chats', e, s);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(e.toLocalizedString(context))));
    }
  }

  Future<void> _toggleRead(Room room) async {
    try {
      if (room.isUnread) {
        await room.markAllRead();
      } else {
        await room.markUnread(true);
      }
    } catch (e, s) {
      Logs().w('Unable to change read state', e, s);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(e.toLocalizedString(context))));
    }
  }

  Future<void> _onSpaceAction(SpaceActions action) async {
    final client = Matrix.of(context).client;
    final space = client.getRoomById(widget.spaceId);

    switch (action) {
      case SpaceActions.settings:
        await space?.postLoad();
        if (!mounted) return;
        context.push('/rooms/${widget.spaceId}/details');
        break;
      case SpaceActions.addChild:
        context.go('/rooms/newgroup?space_id=${widget.spaceId}');
        break;
      case SpaceActions.sortRecent:
        await _setSortMode(SpaceSortMode.recent);
        break;
      case SpaceActions.sortUnread:
        await _setSortMode(SpaceSortMode.unread);
        break;
      case SpaceActions.sortCustom:
        await _setSortMode(SpaceSortMode.custom);
        break;
      case SpaceActions.reorder:
        _filterController.clear();
        setState(() => _reorderMode = true);
        if (client.spaceSortMode(widget.spaceId) != SpaceSortMode.custom) {
          await _setSortMode(SpaceSortMode.custom);
        }
        break;
      case SpaceActions.resetOrder:
        await showFutureLoadingDialog(
          context: context,
          future: () => client.setCustomChildOrder(widget.spaceId, null),
        );
        if (!mounted) return;
        setState(() {});
        break;
      case SpaceActions.markAllRead:
        final unread = _joinedChildRooms(
          client,
        ).where((room) => room.isUnread).toList();
        await showFutureLoadingDialog(
          context: context,
          future: () async {
            for (final room in unread) {
              await room.markAllRead();
            }
          },
        );
        break;
      case SpaceActions.muteAll:
      case SpaceActions.unmuteAll:
        final newState = action == SpaceActions.muteAll
            ? PushRuleState.mentionsOnly
            : PushRuleState.notify;
        final rooms = _joinedChildRooms(client)
            .where(
              (room) =>
                  (room.pushRuleState == PushRuleState.notify) !=
                  (newState == PushRuleState.notify),
            )
            .toList();
        await showFutureLoadingDialog(
          context: context,
          future: () async {
            for (final room in rooms) {
              await room.setPushRuleState(newState);
            }
          },
        );
        break;
      case SpaceActions.leave:
        final confirmed = await showOkCancelAlertDialog(
          context: context,
          title: L10n.of(context).areYouSure,
          message: L10n.of(context).archiveRoomDescription,
          okLabel: L10n.of(context).leave,
          cancelLabel: L10n.of(context).cancel,
          isDestructive: true,
        );
        if (!mounted) return;
        if (confirmed != OkCancelResult.ok) return;

        final success = await showFutureLoadingDialog(
          context: context,
          future: () async => await space?.leave(),
        );
        if (!mounted) return;
        if (success.error != null) return;
        widget.onBack();
    }
  }

  Future<void> _showSpaceChildEditMenu(
    BuildContext posContext,
    String roomId,
  ) async {
    final client = Matrix.of(context).client;
    final space = client.getRoomById(widget.spaceId);
    final room = client.getRoomById(roomId);
    if (space == null) return;
    final overlay =
        Overlay.of(posContext).context.findRenderObject() as RenderBox;

    final button = posContext.findRenderObject() as RenderBox;

    final position = RelativeRect.fromRect(
      Rect.fromPoints(
        button.localToGlobal(const Offset(0, -65), ancestor: overlay),
        button.localToGlobal(
          button.size.bottomRight(Offset.zero) + const Offset(-50, 0),
          ancestor: overlay,
        ),
      ),
      Offset.zero & overlay.size,
    );

    final isPinned = client.isPinnedInSpace(widget.spaceId, roomId);
    final snoozedUntil = client.snoozedUntil(roomId);

    final action = await showMenu<SpaceChildAction>(
      context: posContext,
      position: position,
      items: [
        if (room != null && room.membership == Membership.join) ...[
          PopupMenuItem(
            value: isPinned ? SpaceChildAction.unpin : SpaceChildAction.pin,
            child: Row(
              mainAxisSize: .min,
              children: [
                Icon(isPinned ? Icons.push_pin : Icons.push_pin_outlined),
                const SizedBox(width: 12),
                Text(isPinned ? L10n.of(context).unpin : L10n.of(context).pin),
              ],
            ),
          ),
          PopupMenuItem(
            value: room.isUnread
                ? SpaceChildAction.markAsRead
                : SpaceChildAction.markAsUnread,
            child: Row(
              mainAxisSize: .min,
              children: [
                Icon(
                  room.isUnread
                      ? Icons.mark_chat_read_outlined
                      : Icons.mark_as_unread_outlined,
                ),
                const SizedBox(width: 12),
                Text(
                  room.isUnread
                      ? L10n.of(context).markAsRead
                      : L10n.of(context).markAsUnread,
                ),
              ],
            ),
          ),
          PopupMenuItem(
            value: SpaceChildAction.snooze,
            child: Row(
              mainAxisSize: .min,
              children: [
                Icon(
                  snoozedUntil == null ? Icons.snooze_outlined : Icons.snooze,
                ),
                const SizedBox(width: 12),
                Text(
                  snoozedUntil == null
                      ? L10n.of(context).snoozeChat
                      : L10n.of(
                          context,
                        ).snoozedUntil(formatSnoozeEnd(context, snoozedUntil)),
                ),
              ],
            ),
          ),
          PopupMenuItem(
            value: room.pushRuleState == PushRuleState.notify
                ? SpaceChildAction.mute
                : SpaceChildAction.unmute,
            child: Row(
              mainAxisSize: .min,
              children: [
                Icon(
                  room.pushRuleState == PushRuleState.notify
                      ? Icons.notifications_off_outlined
                      : Icons.notifications_on_outlined,
                ),
                const SizedBox(width: 12),
                Text(
                  room.pushRuleState == PushRuleState.notify
                      ? L10n.of(context).muteChat
                      : L10n.of(context).unmuteChat,
                ),
              ],
            ),
          ),
          PopupMenuItem(
            value: SpaceChildAction.leave,
            child: Row(
              mainAxisSize: .min,
              children: [
                Icon(
                  Icons.delete_outlined,
                  color: Theme.of(context).colorScheme.onErrorContainer,
                ),
                const SizedBox(width: 12),
                Text(
                  L10n.of(context).leave,
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onErrorContainer,
                  ),
                ),
              ],
            ),
          ),
        ],
        if (space.canChangeStateEvent(EventTypes.SpaceChild) == true)
          PopupMenuItem(
            value: SpaceChildAction.removeFromSpace,
            child: Row(
              mainAxisSize: .min,
              children: [
                Icon(
                  Icons.remove,
                  color: Theme.of(context).colorScheme.onErrorContainer,
                ),
                const SizedBox(width: 12),
                Text(
                  L10n.of(context).removeFromSpace,
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onErrorContainer,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
    if (action == null) return;
    if (!mounted) return;
    switch (action) {
      case SpaceChildAction.removeFromSpace:
        final consent = await showOkCancelAlertDialog(
          context: context,
          title: L10n.of(context).removeFromSpace,
          message: L10n.of(context).removeFromSpaceDescription,
        );
        if (consent != OkCancelResult.ok) return;
        if (!mounted) return;
        final result = await showFutureLoadingDialog(
          context: context,
          future: () => space.removeSpaceChild(roomId),
        );
        if (result.isError) return;
        if (!mounted) return;
        _nextBatch = null;
        return;
      case SpaceChildAction.pin:
        await _setPinned(roomId, true);
      case SpaceChildAction.unpin:
        await _setPinned(roomId, false);
      case SpaceChildAction.snooze:
        await showSnoozeDialog(context, room!);
        if (!mounted) return;
        setState(() {});
      case SpaceChildAction.mute:
        await showFutureLoadingDialog(
          context: context,
          future: () => room!.setPushRuleState(PushRuleState.mentionsOnly),
        );
      case SpaceChildAction.unmute:
        await showFutureLoadingDialog(
          context: context,
          future: () => room!.setPushRuleState(PushRuleState.notify),
        );
      case SpaceChildAction.markAsUnread:
        await showFutureLoadingDialog(
          context: context,
          future: () => room!.markUnread(true),
        );
      case SpaceChildAction.markAsRead:
        await showFutureLoadingDialog(
          context: context,
          future: () => room!.markAllRead(),
        );
      case SpaceChildAction.leave:
        await showFutureLoadingDialog(
          context: context,
          future: () => room!.leave(),
        );
    }
  }

  Future<void> _onReorderChildren(
    List<SpaceRoomsChunk$2> children,
    int oldIndex,
    int newIndex,
  ) async {
    final client = Matrix.of(context).client;
    final newOrder = moveItem(
      children,
      oldIndex,
      newIndex,
    ).map((child) => child.roomId).toList();
    final saving = client.setCustomChildOrder(widget.spaceId, newOrder);
    // The new order is already applied locally, rebuild right away.
    setState(() {});
    try {
      await saving;
    } catch (e, s) {
      Logs().w('Unable to save chat order', e, s);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(e.toLocalizedString(context))));
    }
  }

  String _relativeTime(BuildContext context, DateTime time) {
    final l10n = L10n.of(context);
    final diff = DateTime.now().difference(time);
    if (diff.inMinutes < 1) return l10n.justNow;
    if (diff.inHours < 1) return l10n.minutesAgo(diff.inMinutes);
    if (diff.inDays < 1) return l10n.hoursAgo(diff.inHours);
    if (diff.inDays < 7) return l10n.daysAgo(diff.inDays);
    return time.localizedTimeShort(context);
  }

  String? _lastMessagePreview(BuildContext context, Room room) {
    final typingText = room.getLocalizedTypingText(context);
    if (typingText.isNotEmpty) return typingText;
    final lastEvent = room.lastEvent;
    if (lastEvent == null) return null;
    final l10n = L10n.of(context);
    var body = lastEvent.calcLocalizedBody(
      MatrixLocals(l10n),
      hideReply: true,
      hideEdit: true,
      plaintextBody: true,
      removeMarkdown: true,
    );
    if (!room.isDirectChat || room.directChatMatrixID != lastEvent.senderId) {
      body = l10n.senderMessage(
        lastEvent.senderId == room.client.userID
            ? l10n.you
            : lastEvent.senderFromMemoryOrFallback.calcDisplayname(),
        body,
      );
    }
    return body;
  }

  Widget _buildSectionHeader(BuildContext context, _SectionHeader header) {
    final theme = Theme.of(context);
    final expanded = header.expanded;
    final color = theme.colorScheme.onSurfaceVariant;
    return InkWell(
      onTap: header.onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 16, 4),
        child: Row(
          children: [
            Icon(header.icon, size: 14, color: color),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                header.label,
                style: theme.textTheme.labelMedium?.copyWith(
                  color: color,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
            if (expanded != null)
              Icon(
                expanded ? Icons.expand_less : Icons.expand_more,
                size: 18,
                color: color,
              ),
          ],
        ),
      ),
    );
  }

  Widget _swipeBackground(
    BuildContext context, {
    required IconData icon,
    required String label,
    required bool alignStart,
  }) {
    final theme = Theme.of(context);
    return Container(
      color: theme.colorScheme.tertiaryContainer,
      padding: const EdgeInsets.symmetric(horizontal: 20),
      alignment: alignStart ? Alignment.centerLeft : Alignment.centerRight,
      child: Row(
        mainAxisSize: .min,
        children: [
          Icon(icon, color: theme.colorScheme.onTertiaryContainer),
          const SizedBox(width: 8),
          Text(
            label,
            style: TextStyle(color: theme.colorScheme.onTertiaryContainer),
          ),
        ],
      ),
    );
  }

  Widget _buildChildTile(
    BuildContext context,
    Room space,
    SpaceRoomsChunk$2 item, {
    int? reorderIndex,
  }) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final client = space.client;
    const avatarSize = Avatar.defaultSize / 1.5;
    final isAdmin = space.canChangeStateEvent(EventTypes.SpaceChild);
    final reorder = reorderIndex != null;

    final joinedRoom = _roomFor(client, item);
    final displayname = _displaynameFor(context, item, joinedRoom);
    final avatarUrl = item.avatarUrl ?? joinedRoom?.avatar;
    final memberRoom =
        joinedRoom != null && joinedRoom.membership == Membership.join
        ? joinedRoom
        : null;
    final isMuted =
        memberRoom != null &&
        memberRoom.pushRuleState != PushRuleState.notify;
    final hasMentions = memberRoom != null && memberRoom.hasUnreadMentions;
    final isUnread = memberRoom != null && memberRoom.isUnread;
    final isActive = joinedRoom != null && widget.activeChat == joinedRoom.id;
    final readOnly = memberRoom != null && memberRoom.isReadOnlyForMe;
    final snoozed = memberRoom != null && client.isSnoozed(memberRoom.id);
    final isPinned = client.isPinnedInSpace(widget.spaceId, item.roomId);
    final previewRoom = memberRoom != null && !memberRoom.isSpace && !reorder
        ? memberRoom
        : null;
    final preview = previewRoom == null
        ? null
        : _lastMessagePreview(context, previewRoom);

    final Color backgroundColor;
    if (isActive) {
      backgroundColor = theme.colorScheme.secondaryContainer;
    } else if (hasMentions) {
      backgroundColor = theme.colorScheme.primary.withAlpha(84);
    } else if (isUnread && !isMuted) {
      backgroundColor = theme.colorScheme.primary.withAlpha(40);
    } else {
      backgroundColor = Colors.transparent;
    }

    Widget tile = Material(
      borderRadius: BorderRadius.circular(AppConfig.borderRadius),
      clipBehavior: Clip.hardEdge,
      color: backgroundColor,
      child: Container(
        decoration: hasMentions && !isActive
            ? BoxDecoration(
                border: Border(
                  left: BorderSide(color: theme.colorScheme.primary, width: 4),
                ),
              )
            : null,
        child: HoverBuilder(
          builder: (context, hovered) => ListTile(
            visualDensity: const VisualDensity(vertical: -0.5),
            contentPadding: EdgeInsets.only(
              left: hasMentions && !isActive ? 4 : 8,
              right: joinedRoom == null ? 0 : 8,
            ),
            onTap: !reorder && joinedRoom != null
                ? () => widget.onChatTab(joinedRoom)
                : null,
            onLongPress: !reorder && joinedRoom != null
                ? () => _showSpaceChildEditMenu(context, item.roomId)
                : null,
            leading: hovered && !reorder && (isAdmin || joinedRoom != null)
                ? SizedBox.square(
                    dimension: avatarSize,
                    child: IconButton(
                      splashRadius: avatarSize,
                      iconSize: 14,
                      style: IconButton.styleFrom(
                        foregroundColor: theme.colorScheme.onTertiaryContainer,
                        backgroundColor: theme.colorScheme.tertiaryContainer,
                      ),
                      onPressed: () =>
                          _showSpaceChildEditMenu(context, item.roomId),
                      icon: const Icon(Icons.edit_outlined),
                    ),
                  )
                : Avatar(
                    size: avatarSize,
                    mxContent: avatarUrl,
                    name: '#',
                    backgroundColor: theme.colorScheme.surfaceContainer,
                    textColor:
                        item.name?.colorScheme.primary ??
                        theme.colorScheme.onSurface,
                    shapeBorder: item.roomType == 'm.space'
                        ? RoundedSuperellipseBorder(
                            side: BorderSide(
                              color: theme.colorScheme.surfaceContainerHighest,
                            ),
                            borderRadius: BorderRadius.circular(
                              AppConfig.borderRadius / 4,
                            ),
                          )
                        : null,
                    borderRadius: item.roomType == 'm.space'
                        ? BorderRadius.circular(AppConfig.borderRadius / 4)
                        : null,
                  ),
            title: Row(
              children: [
                if (joinedRoom != null &&
                    joinedRoom.hasActiveMatrixRtcCall == true)
                  Padding(
                    padding: const EdgeInsets.only(right: 4.0),
                    child: ActiveCallIndicator(room: joinedRoom),
                  ),
                Expanded(
                  child: Opacity(
                    opacity: joinedRoom == null ? 0.5 : 1,
                    child: Text(
                      displayname,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: (isUnread && !isMuted) || hasMentions
                          ? const TextStyle(fontWeight: FontWeight.bold)
                          : null,
                    ),
                  ),
                ),
                if (readOnly)
                  Tooltip(
                    message: l10n.readOnlyChat,
                    child: const Padding(
                      padding: EdgeInsets.only(left: 4.0),
                      child: Icon(Icons.lock_outline, size: 16),
                    ),
                  ),
                if (snoozed)
                  const Padding(
                    padding: EdgeInsets.only(left: 4.0),
                    child: Icon(Icons.snooze, size: 16),
                  )
                else if (isMuted)
                  const Padding(
                    padding: EdgeInsets.only(left: 4.0),
                    child: Icon(Icons.notifications_off_outlined, size: 16),
                  ),
                if (isPinned && reorder)
                  const Padding(
                    padding: EdgeInsets.only(left: 4.0),
                    child: Icon(Icons.push_pin_outlined, size: 16),
                  ),
                if (joinedRoom != null)
                  UnreadBubble(room: joinedRoom)
                else
                  TextButton(
                    onPressed: () => _joinChildRoom(item),
                    child: Text(l10n.join),
                  ),
                if (reorderIndex != null)
                  ReorderableDragStartListener(
                    index: reorderIndex,
                    child: const Padding(
                      padding: EdgeInsets.only(left: 8.0),
                      child: Icon(Icons.drag_handle),
                    ),
                  ),
              ],
            ),
            subtitle: previewRoom != null
                ? Row(
                    children: [
                      Expanded(
                        child: Text(
                          preview ?? l10n.noMessagesYet,
                          maxLines: 1,
                          softWrap: false,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 13,
                            color: theme.colorScheme.onSurfaceVariant,
                            decoration: previewRoom.lastEvent?.redacted == true
                                ? TextDecoration.lineThrough
                                : null,
                          ),
                        ),
                      ),
                      if (previewRoom.lastEvent != null)
                        Padding(
                          padding: const EdgeInsets.only(left: 8.0),
                          child: Text(
                            _relativeTime(
                              context,
                              previewRoom.latestEventReceivedTime,
                            ),
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: hasMentions || isUnread
                                  ? FontWeight.bold
                                  : null,
                              color: hasMentions
                                  ? theme.colorScheme.primary
                                  : theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                    ],
                  )
                : null,
          ),
        ),
      ),
    );

    if (memberRoom != null && !reorder) {
      tile = ClipRRect(
        borderRadius: BorderRadius.circular(AppConfig.borderRadius),
        child: Dismissible(
          key: ValueKey('swipe_${item.roomId}'),
          direction: DismissDirection.horizontal,
          dismissThresholds: const {
            DismissDirection.startToEnd: 0.3,
            DismissDirection.endToStart: 0.3,
          },
          confirmDismiss: (direction) async {
            if (direction == DismissDirection.startToEnd) {
              unawaited(_toggleRead(memberRoom));
            } else {
              unawaited(_setPinned(item.roomId, !isPinned));
            }
            return false;
          },
          background: _swipeBackground(
            context,
            icon: isUnread
                ? Icons.mark_chat_read_outlined
                : Icons.mark_as_unread_outlined,
            label: isUnread ? l10n.markAsRead : l10n.markAsUnread,
            alignStart: true,
          ),
          secondaryBackground: _swipeBackground(
            context,
            icon: isPinned ? Icons.push_pin : Icons.push_pin_outlined,
            label: isPinned ? l10n.unpin : l10n.pin,
            alignStart: false,
          ),
          child: tile,
        ),
      );
    }

    return Padding(
      key: ValueKey(item.roomId),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 1),
      child: tile,
    );
  }

  PopupMenuItem<SpaceActions> _sortMenuItem(
    BuildContext context, {
    required SpaceActions value,
    required IconData icon,
    required String label,
    required bool selected,
  }) => PopupMenuItem(
    value: value,
    child: ListTile(
      leading: Icon(icon),
      title: Text(label),
      trailing: selected ? const Icon(Icons.check) : null,
    ),
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);

    final room = Matrix.of(context).client.getRoomById(widget.spaceId);
    final displayname = room?.getLocalizedDisplayname() ?? l10n.nothingFound;
    const avatarSize = Avatar.defaultSize / 1.5;
    final isAdmin = room?.canChangeStateEvent(EventTypes.SpaceChild) == true;
    return Scaffold(
      body: room == null
          ? const Center(child: Icon(Icons.search_outlined, size: 80))
          : StreamBuilder(
              stream: room.client.onSync.stream
                  .where((s) => s.hasRoomUpdate)
                  .rateLimit(const Duration(seconds: 1)),
              builder: (context, snapshot) {
                final client = room.client;
                final filter = _filterController.text.trim().toLowerCase();
                final sortMode = client.spaceSortMode(widget.spaceId);
                final joinedChildRooms = _joinedChildRooms(client);
                final allMuted =
                    joinedChildRooms.isNotEmpty &&
                    joinedChildRooms.every(
                      (child) => child.pushRuleState != PushRuleState.notify,
                    );
                final anyUnread = joinedChildRooms.any(
                  (child) => child.isUnread,
                );

                bool matchesFilter(SpaceRoomsChunk$2 item) =>
                    filter.isEmpty ||
                    _displaynameFor(
                      context,
                      item,
                      _roomFor(client, item),
                    ).toLowerCase().contains(filter);

                // Reorder mode shows one flat list in the custom order.
                final reorderChildren = _reorderMode
                    ? _customSortedChildren(client)
                    : const <SpaceRoomsChunk$2>[];

                final entries = <_SpaceListEntry>[];
                if (!_reorderMode) {
                  final pinnedIds = client.pinnedChats(widget.spaceId);
                  final pinned = <SpaceRoomsChunk$2>[];
                  final normal = <SpaceRoomsChunk$2>[];
                  final muted = <SpaceRoomsChunk$2>[];
                  for (final item in _sortedChildren(client, sortMode)) {
                    if (!matchesFilter(item)) continue;
                    final childRoom = _roomFor(client, item);
                    if (pinnedIds.contains(item.roomId)) {
                      pinned.add(item);
                    } else if (childRoom != null &&
                        childRoom.membership == Membership.join &&
                        !childRoom.isSpace &&
                        childRoom.pushRuleState != PushRuleState.notify) {
                      muted.add(item);
                    } else {
                      normal.add(item);
                    }
                  }
                  if (pinned.isNotEmpty) {
                    entries.add(
                      _SectionHeader(l10n.pinned, Icons.push_pin_outlined),
                    );
                    entries.addAll(pinned.map(_ChildEntry.new));
                    if (normal.isNotEmpty) {
                      entries.add(
                        _SectionHeader(l10n.chats, Icons.forum_outlined),
                      );
                    }
                  }
                  entries.addAll(normal.map(_ChildEntry.new));
                  if (muted.isNotEmpty) {
                    final expanded = _mutedExpanded || filter.isNotEmpty;
                    entries.add(
                      _SectionHeader(
                        '${l10n.muted} · ${muted.length}',
                        Icons.notifications_off_outlined,
                        expanded: expanded,
                        onTap: filter.isNotEmpty
                            ? null
                            : () => setState(
                                () => _mutedExpanded = !_mutedExpanded,
                              ),
                      ),
                    );
                    if (expanded) entries.addAll(muted.map(_ChildEntry.new));
                  }
                }

                return CustomScrollView(
                  slivers: [
                    SliverAppBar(
                      toolbarHeight: 72,
                      floating: true,
                      scrolledUnderElevation: 0,
                      backgroundColor: Colors.transparent,
                      automaticallyImplyLeading: false,
                      title: TextField(
                        controller: _filterController,
                        onChanged: (_) => setState(() {}),
                        textInputAction: TextInputAction.search,
                        decoration: InputDecoration(
                          filled: true,
                          fillColor: theme.colorScheme.secondaryContainer,
                          border: OutlineInputBorder(
                            borderSide: BorderSide.none,
                            borderRadius: BorderRadius.circular(99),
                          ),
                          contentPadding: EdgeInsets.zero,
                          hintText: l10n.search,
                          hintStyle: TextStyle(
                            color: theme.colorScheme.onPrimaryContainer,
                            fontWeight: FontWeight.normal,
                          ),
                          floatingLabelBehavior: FloatingLabelBehavior.never,
                          prefixIcon: IconButton(
                            onPressed: () {},
                            icon: Icon(
                              Icons.search_outlined,
                              color: theme.colorScheme.onPrimaryContainer,
                            ),
                          ),
                          suffixIcon: PopupMenuButton<SpaceActions>(
                            icon: Avatar(
                              size: avatarSize,
                              mxContent: room.avatar,
                              name: displayname,
                              shapeBorder: RoundedSuperellipseBorder(
                                side: BorderSide(
                                  width: 1,
                                  color: theme.dividerColor,
                                ),
                                borderRadius: BorderRadius.circular(
                                  AppConfig.spaceBorderRadius,
                                ),
                              ),
                              borderRadius: BorderRadius.circular(
                                AppConfig.spaceBorderRadius,
                              ),
                            ),
                            useRootNavigator: true,
                            onSelected: _onSpaceAction,
                            itemBuilder: (context) => [
                              if (isAdmin)
                                PopupMenuItem(
                                  value: SpaceActions.addChild,
                                  child: ListTile(
                                    leading: const Icon(Icons.edit_square),
                                    title: Text(l10n.addChatOrSubSpace),
                                  ),
                                ),
                              PopupMenuItem(
                                value: SpaceActions.settings,
                                child: ListTile(
                                  leading: const Icon(Icons.settings_outlined),
                                  title: Text(l10n.settings),
                                ),
                              ),
                              if (anyUnread)
                                PopupMenuItem(
                                  value: SpaceActions.markAllRead,
                                  child: ListTile(
                                    leading: const Icon(
                                      Icons.mark_chat_read_outlined,
                                    ),
                                    title: Text(l10n.markAllAsRead),
                                  ),
                                ),
                              if (joinedChildRooms.isNotEmpty)
                                PopupMenuItem(
                                  value: allMuted
                                      ? SpaceActions.unmuteAll
                                      : SpaceActions.muteAll,
                                  child: ListTile(
                                    leading: Icon(
                                      allMuted
                                          ? Icons.notifications_on_outlined
                                          : Icons.notifications_off_outlined,
                                    ),
                                    title: Text(
                                      allMuted
                                          ? l10n.unmuteAllChats
                                          : l10n.muteAllChats,
                                    ),
                                  ),
                                ),
                              const PopupMenuDivider(),
                              _sortMenuItem(
                                context,
                                value: SpaceActions.sortRecent,
                                icon: Icons.schedule_outlined,
                                label: l10n.sortRecentFirst,
                                selected: sortMode == SpaceSortMode.recent,
                              ),
                              _sortMenuItem(
                                context,
                                value: SpaceActions.sortUnread,
                                icon: Icons.mark_unread_chat_alt_outlined,
                                label: l10n.sortUnreadFirst,
                                selected: sortMode == SpaceSortMode.unread,
                              ),
                              _sortMenuItem(
                                context,
                                value: SpaceActions.sortCustom,
                                icon: Icons.low_priority,
                                label: l10n.sortCustomOrder,
                                selected: sortMode == SpaceSortMode.custom,
                              ),
                              PopupMenuItem(
                                value: SpaceActions.reorder,
                                child: ListTile(
                                  leading: const Icon(Icons.swap_vert),
                                  title: Text(l10n.reorderChats),
                                ),
                              ),
                              if (client.hasCustomChildOrder(widget.spaceId))
                                PopupMenuItem(
                                  value: SpaceActions.resetOrder,
                                  child: ListTile(
                                    leading: const Icon(Icons.restart_alt),
                                    title: Text(l10n.resetChatOrder),
                                  ),
                                ),
                              const PopupMenuDivider(),
                              PopupMenuItem(
                                value: SpaceActions.leave,
                                child: ListTile(
                                  leading: const Icon(Icons.delete_outlined),
                                  title: Text(l10n.leave),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    if (_reorderMode) ...[
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(16, 0, 8, 8),
                          child: Row(
                            children: [
                              Expanded(
                                child: Text(
                                  l10n.dragToReorderChats,
                                  style: theme.textTheme.bodySmall,
                                ),
                              ),
                              FilledButton(
                                onPressed: () =>
                                    setState(() => _reorderMode = false),
                                child: Text(l10n.doneReordering),
                              ),
                            ],
                          ),
                        ),
                      ),
                      SliverReorderableList(
                        itemCount: reorderChildren.length,
                        onReorder: (oldIndex, newIndex) => _onReorderChildren(
                          reorderChildren,
                          oldIndex,
                          newIndex,
                        ),
                        proxyDecorator: (child, index, animation) => Material(
                          color: theme.colorScheme.surfaceContainerHigh,
                          elevation: 2,
                          borderRadius: BorderRadius.circular(
                            AppConfig.borderRadius,
                          ),
                          child: child,
                        ),
                        itemBuilder: (context, i) => _buildChildTile(
                          context,
                          room,
                          reorderChildren[i],
                          reorderIndex: i,
                        ),
                      ),
                    ] else
                      SliverList.builder(
                        itemCount: entries.length,
                        itemBuilder: (context, i) => switch (entries[i]) {
                          final _SectionHeader header => _buildSectionHeader(
                            context,
                            header,
                          ),
                          final _ChildEntry entry => _buildChildTile(
                            context,
                            room,
                            entry.item,
                          ),
                        },
                      ),
                    if (!_noMoreRooms)
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12.0,
                            vertical: 2.0,
                          ),
                          child: TextButton(
                            onPressed: _isLoading ? null : _loadHierarchy,
                            child: _isLoading
                                ? const CircularProgressIndicator.adaptive()
                                : Text(l10n.loadMore),
                          ),
                        ),
                      ),
                    const SliverPadding(padding: EdgeInsets.only(top: 32)),
                  ],
                );
              },
            ),
    );
  }
}
