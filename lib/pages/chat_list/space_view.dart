// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:async';
import 'dart:convert';

import 'package:collection/collection.dart';
import 'package:fluffychat/config/app_config.dart';
import 'package:fluffychat/config/plus_colors.dart';
import 'package:fluffychat/config/setting_keys.dart';
import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/pages/chat_list/active_call_indicator.dart';
import 'package:fluffychat/pages/chat_list/space_media_gallery.dart';
import 'package:fluffychat/pages/chat_list/unread_bubble.dart';
import 'package:fluffychat/utils/chat_nickname_extension.dart';
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
import 'package:fluffychat/widgets/adaptive_dialogs/show_modal_action_popup.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_ok_cancel_alert_dialog.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_text_input_dialog.dart';
import 'package:fluffychat/widgets/avatar.dart';
import 'package:fluffychat/widgets/chat_selection_bar.dart';
import 'package:fluffychat/widgets/future_loading_dialog.dart';
import 'package:fluffychat/widgets/hover_builder.dart';
import 'package:fluffychat/widgets/matrix.dart';
import 'package:fluffychat/widgets/plus_circle_button.dart';
import 'package:fluffychat/widgets/plus_tab.dart';
import 'package:fluffychat/widgets/rename_for_me_dialog.dart';
import 'package:fluffychat/widgets/snooze_dialog.dart';
import 'package:fluffychat/widgets/space_accent_theme.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

enum SpaceChildAction {
  select,
  category,
  moveToSpace,
  readOnly,
  pin,
  unpin,
  rename,
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
  newCategory,
  settings,
  sortRecent,
  sortUnread,
  sortCustom,
  reorder,
  resetOrder,
  markAllRead,
  muteAll,
  unmuteAll,
  toggleFavorite,
  color,
  media,
  leave,
}

enum _CategoryAction { rename, moveUp, moveDown, delete }

/// One row of the space list: a section header or a chat.
sealed class _SpaceListEntry {
  const _SpaceListEntry();
}

class _SectionHeader extends _SpaceListEntry {
  final String label;
  final IconData icon;
  final bool? expanded;
  final VoidCallback? onTap;

  /// Opens the menu of a category header (long-press or the ⋯ button).
  final VoidCallback? onMenu;

  const _SectionHeader(
    this.label,
    this.icon, {
    this.expanded,
    this.onTap,
    this.onMenu,
  });
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

  /// The filter field replaces the title while it's open.
  bool _searchOpen = false;

  /// Chats picked for a bulk action; selection mode is on while not empty.
  final Set<String> _selectedRoomIds = {};

  /// Chats the list showed last, for "select all".
  List<String> _visibleRoomIds = const [];

  bool get _selectMode => _selectedRoomIds.isNotEmpty;

  void _toggleSelection(String roomId) => setState(() {
    if (!_selectedRoomIds.remove(roomId)) _selectedRoomIds.add(roomId);
  });

  void _clearSelection() => setState(_selectedRoomIds.clear);

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
      Matrix.of(context).client.chatNicknames[item.roomId] ??
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
      case SpaceActions.newCategory:
        await _createCategory();
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
      case SpaceActions.color:
        final picked = await showSpaceColorPicker(
          context,
          client.spaceColor(widget.spaceId),
        );
        if (picked == null || !mounted) return;
        await showFutureLoadingDialog(
          context: context,
          future: () =>
              client.setSpaceColor(widget.spaceId, picked < 0 ? null : picked),
        );
        if (!mounted) return;
        setState(() {});
        break;
      case SpaceActions.media:
        await Navigator.of(context, rootNavigator: true).push(
          MaterialPageRoute(
            builder: (_) => SpaceMediaGallery(spaceId: widget.spaceId),
          ),
        );
        break;
      case SpaceActions.toggleFavorite:
        final isFavorite = AppSettings.favoriteSpace.value == widget.spaceId;
        await AppSettings.favoriteSpace.setItem(
          isFavorite ? '' : widget.spaceId,
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
        if (room != null && room.membership != Membership.leave)
          PopupMenuItem(
            value: SpaceChildAction.select,
            child: Row(
              mainAxisSize: .min,
              children: [
                const Icon(Icons.check_circle_outline),
                const SizedBox(width: 12),
                Text(L10n.of(context).select),
              ],
            ),
          ),
        PopupMenuItem(
          value: SpaceChildAction.category,
          child: Row(
            mainAxisSize: .min,
            children: [
              const Icon(Icons.label_outline),
              const SizedBox(width: 12),
              Text(L10n.of(context).moveToCategory),
            ],
          ),
        ),
        if (room != null && room.membership != Membership.leave)
          PopupMenuItem(
            value: SpaceChildAction.moveToSpace,
            child: Row(
              mainAxisSize: .min,
              children: [
                const Icon(Icons.drive_file_move_outlined),
                const SizedBox(width: 12),
                Text(L10n.of(context).moveToSpace),
              ],
            ),
          ),
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
            value: SpaceChildAction.rename,
            child: Row(
              mainAxisSize: .min,
              children: [
                const Icon(Icons.drive_file_rename_outline),
                const SizedBox(width: 12),
                Text(L10n.of(context).renameForMe),
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
          if (room.canToggleReadOnly)
            PopupMenuItem(
              value: SpaceChildAction.readOnly,
              child: Row(
                mainAxisSize: .min,
                children: [
                  Icon(
                    room.isReadOnlyForMe
                        ? Icons.lock_open_outlined
                        : Icons.lock_outline,
                  ),
                  const SizedBox(width: 12),
                  Text(
                    room.isReadOnlyForMe
                        ? L10n.of(context).unlockChat
                        : L10n.of(context).makeReadOnly,
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
      case SpaceChildAction.select:
        _toggleSelection(roomId);
      case SpaceChildAction.category:
        await _pickCategoryFor([roomId]);
      case SpaceChildAction.moveToSpace:
        await moveRoomsToSpace(context, [room!], fromSpace: space);
      case SpaceChildAction.readOnly:
        final chat = room!;
        await showFutureLoadingDialog(
          context: context,
          future: () => chat.setReadOnlyForMe(!chat.isReadOnlyForMe),
        );
        if (!mounted) return;
        setState(() {});
      case SpaceChildAction.pin:
        await _setPinned(roomId, true);
      case SpaceChildAction.unpin:
        await _setPinned(roomId, false);
      case SpaceChildAction.rename:
        await showRenameForMeDialog(context, room!);
        if (!mounted) return;
        setState(() {});
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
    final plus = PlusColors.of(context);
    final expanded = header.expanded;
    final color = plus.textFaint;
    return InkWell(
      onTap: header.onTap,
      onLongPress: header.onMenu,
      child: Padding(
        padding: EdgeInsets.fromLTRB(18, 18, header.onMenu == null ? 16 : 4, 6),
        child: Row(
          children: [
            Icon(
              expanded == null
                  ? header.icon
                  : expanded
                  ? Icons.keyboard_arrow_down
                  : Icons.keyboard_arrow_right,
              size: 15,
              color: color,
            ),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                header.label.toUpperCase(),
                style: PlusText.kicker(color),
              ),
            ),
            if (header.onMenu != null)
              IconButton(
                icon: const Icon(Icons.more_horiz),
                iconSize: 18,
                color: color,
                visualDensity: VisualDensity.compact,
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 32, minHeight: 24),
                onPressed: header.onMenu,
              ),
          ],
        ),
      ),
    );
  }

  /// Asks which category to put [roomIds] in (or none, or a new one).
  Future<void> _pickCategoryFor(List<String> roomIds) async {
    if (roomIds.isEmpty) return;
    final l10n = L10n.of(context);
    final client = Matrix.of(context).client;
    final categories = client.spaceCategories(widget.spaceId);
    const noCategory = '';
    const newCategory = '\u0000new';
    final current = roomIds
        .map((id) => client.spaceCategoryOf(widget.spaceId, id))
        .toSet();
    final picked = await showModalActionPopup<String>(
      context: context,
      title: l10n.moveToCategory,
      cancelLabel: l10n.cancel,
      actions: [
        for (final category in categories)
          AdaptiveModalAction(
            value: category.id,
            label: category.name,
            icon: Icon(
              current.length == 1 && current.first == category.id
                  ? Icons.check
                  : Icons.label_outline,
            ),
          ),
        AdaptiveModalAction(
          value: newCategory,
          label: l10n.newCategory,
          icon: const Icon(Icons.add),
        ),
        if (current.any((id) => id != null))
          AdaptiveModalAction(
            value: noCategory,
            label: l10n.noCategory,
            icon: const Icon(Icons.label_off_outlined),
          ),
      ],
    );
    if (picked == null || !mounted) return;
    String? categoryId = picked == noCategory ? null : picked;
    if (picked == newCategory) {
      categoryId = await _createCategory();
      if (categoryId == null || !mounted) return;
    }
    await showFutureLoadingDialog(
      context: context,
      future: () =>
          client.setSpaceCategoryOf(widget.spaceId, roomIds, categoryId),
    );
    if (!mounted) return;
    setState(_selectedRoomIds.clear);
  }

  /// Asks for a name and adds a category; returns its ID.
  Future<String?> _createCategory() async {
    final l10n = L10n.of(context);
    final name = await showTextInputDialog(
      context: context,
      title: l10n.newCategory,
      hintText: l10n.categoryName,
      okLabel: l10n.create,
      cancelLabel: l10n.cancel,
      maxLines: 1,
    );
    final trimmed = name?.trim() ?? '';
    if (trimmed.isEmpty || !mounted) return null;
    final client = Matrix.of(context).client;
    final result = await showFutureLoadingDialog(
      context: context,
      future: () => client.addSpaceCategory(widget.spaceId, trimmed),
    );
    if (mounted) setState(() {});
    return result.result;
  }

  Future<void> _showCategoryMenu(SpaceCategory category, int index, int count) async {
    final l10n = L10n.of(context);
    final client = Matrix.of(context).client;
    final action = await showModalActionPopup<_CategoryAction>(
      context: context,
      title: category.name,
      cancelLabel: l10n.cancel,
      actions: [
        AdaptiveModalAction(
          value: _CategoryAction.rename,
          label: l10n.renameCategory,
          icon: const Icon(Icons.edit_outlined),
        ),
        if (index > 0)
          AdaptiveModalAction(
            value: _CategoryAction.moveUp,
            label: l10n.moveUp,
            icon: const Icon(Icons.arrow_upward),
          ),
        if (index < count - 1)
          AdaptiveModalAction(
            value: _CategoryAction.moveDown,
            label: l10n.moveDown,
            icon: const Icon(Icons.arrow_downward),
          ),
        AdaptiveModalAction(
          value: _CategoryAction.delete,
          label: l10n.deleteCategory,
          icon: const Icon(Icons.delete_outlined),
          isDestructive: true,
        ),
      ],
    );
    if (action == null || !mounted) return;
    switch (action) {
      case _CategoryAction.rename:
        final name = await showTextInputDialog(
          context: context,
          title: l10n.renameCategory,
          initialText: category.name,
          hintText: l10n.categoryName,
          okLabel: l10n.ok,
          cancelLabel: l10n.cancel,
          maxLines: 1,
        );
        final trimmed = name?.trim() ?? '';
        if (trimmed.isEmpty || !mounted) return;
        await showFutureLoadingDialog(
          context: context,
          future: () => client.renameSpaceCategory(
            widget.spaceId,
            category.id,
            trimmed,
          ),
        );
      case _CategoryAction.moveUp:
      case _CategoryAction.moveDown:
        await showFutureLoadingDialog(
          context: context,
          future: () => client.moveSpaceCategory(
            widget.spaceId,
            category.id,
            action == _CategoryAction.moveUp ? -1 : 1,
          ),
        );
      case _CategoryAction.delete:
        final consent = await showOkCancelAlertDialog(
          context: context,
          title: l10n.deleteCategory,
          message: l10n.deleteCategoryDescription(category.name),
          okLabel: l10n.delete,
          cancelLabel: l10n.cancel,
          isDestructive: true,
        );
        if (consent != OkCancelResult.ok || !mounted) return;
        await showFutureLoadingDialog(
          context: context,
          future: () =>
              client.deleteSpaceCategory(widget.spaceId, category.id),
        );
    }
    if (mounted) setState(() {});
  }

  Widget _swipeBackground(
    BuildContext context, {
    required IconData icon,
    required String label,
    required bool alignStart,
  }) {
    final plus = PlusColors.of(context);
    return Container(
      color: plus.accent,
      padding: const EdgeInsets.symmetric(horizontal: 20),
      alignment: alignStart ? Alignment.centerLeft : Alignment.centerRight,
      child: Row(
        mainAxisSize: .min,
        children: [
          Icon(icon, color: plus.onAccent),
          const SizedBox(width: 8),
          Text(
            label,
            style: TextStyle(
              color: plus.onAccent,
              fontWeight: FontWeight.w700,
            ),
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

    final plus = PlusColors.of(context);
    final selected = _selectedRoomIds.contains(item.roomId);
    final emphasized = (isUnread && !isMuted) || hasMentions;
    // Discord channels show as "# name" (or a lock when read-only), like on
    // Discord; other chats keep their avatar and a preview line.
    final channelStyle =
        joinedRoom != null && !joinedRoom.isSpace && joinedRoom.isDiscordBridged;
    final Color backgroundColor;
    if (selected || isActive) {
      backgroundColor = plus.raised;
    } else if (emphasized) {
      backgroundColor = plus.surface;
    } else {
      backgroundColor = Colors.transparent;
    }

    Widget tile = Material(
      borderRadius: BorderRadius.circular(12),
      clipBehavior: Clip.hardEdge,
      color: backgroundColor,
      child: Container(
        decoration: hasMentions && !isActive
            ? BoxDecoration(
                border: Border(left: BorderSide(color: plus.danger, width: 3)),
              )
            : null,
        child: HoverBuilder(
          builder: (context, hovered) => ListTile(
            visualDensity: VisualDensity(vertical: channelStyle ? -2 : -0.5),
            horizontalTitleGap: channelStyle ? 10 : 12,
            contentPadding: EdgeInsets.only(
              left: hasMentions && !isActive ? 7 : 10,
              right: joinedRoom == null ? 0 : 10,
            ),
            onTap: reorder || joinedRoom == null
                ? null
                : _selectMode
                ? () => _toggleSelection(item.roomId)
                : () => widget.onChatTab(joinedRoom),
            onLongPress: reorder || joinedRoom == null
                ? null
                : _selectMode
                ? () => _toggleSelection(item.roomId)
                : () => _showSpaceChildEditMenu(context, item.roomId),
            leading: selected
                ? SizedBox.square(
                    dimension: avatarSize,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: plus.accent,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(Icons.check, size: 18, color: plus.onAccent),
                    ),
                  )
                : hovered && !reorder && (isAdmin || joinedRoom != null)
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
                : channelStyle
                ? SizedBox(
                    width: 24,
                    child: Icon(
                      readOnly ? Icons.lock_outline : Icons.tag,
                      size: 20,
                      color: emphasized ? plus.text : plus.textFaint,
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
                      channelStyle && displayname.startsWith('#')
                          ? displayname.substring(1)
                          : displayname,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: emphasized
                            ? FontWeight.w700
                            : FontWeight.w500,
                        color: emphasized ? plus.text : plus.textFaint,
                      ),
                    ),
                  ),
                ),
                if (readOnly && !channelStyle)
                  Tooltip(
                    message: l10n.readOnlyChat,
                    child: Padding(
                      padding: const EdgeInsets.only(left: 4.0),
                      child: Icon(
                        Icons.lock_outline,
                        size: 14,
                        color: plus.textFaint,
                      ),
                    ),
                  ),
                if (snoozed)
                  Padding(
                    padding: const EdgeInsets.only(left: 4.0),
                    child: Icon(Icons.snooze, size: 14, color: plus.textFaint),
                  )
                else if (isMuted)
                  Padding(
                    padding: const EdgeInsets.only(left: 4.0),
                    child: Icon(
                      Icons.notifications_off_outlined,
                      size: 14,
                      color: plus.textFaint,
                    ),
                  ),
                const SizedBox(width: 6),
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
            subtitle: previewRoom != null && !channelStyle
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
                            color: emphasized ? plus.textMuted : plus.textFaint,
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
                              fontWeight: FontWeight.w600,
                              color: plus.textFaint,
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

    if (memberRoom != null && !reorder && !_selectMode) {
      tile = ClipRRect(
        borderRadius: BorderRadius.circular(12),
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
  Widget build(BuildContext context) => SpaceAccentTheme(
    spaceId: widget.spaceId,
    child: Builder(builder: _buildView),
  );

  Widget _buildView(BuildContext context) {
    final theme = Theme.of(context);
    final plus = PlusColors.of(context);
    final l10n = L10n.of(context);

    final room = Matrix.of(context).client.getRoomById(widget.spaceId);
    final displayname = room?.getLocalizedDisplayname() ?? l10n.nothingFound;
    const avatarSize = Avatar.defaultSize / 1.5;
    final isAdmin = room?.canChangeStateEvent(EventTypes.SpaceChild) == true;
    final isFavorite = AppSettings.favoriteSpace.value == widget.spaceId;
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
                  final categories = client.spaceCategories(widget.spaceId);
                  final inCategory = <String, List<SpaceRoomsChunk$2>>{
                    for (final category in categories) category.id: [],
                  };
                  final pinned = <SpaceRoomsChunk$2>[];
                  final normal = <SpaceRoomsChunk$2>[];
                  final muted = <SpaceRoomsChunk$2>[];
                  for (final item in _sortedChildren(client, sortMode)) {
                    if (!matchesFilter(item)) continue;
                    final childRoom = _roomFor(client, item);
                    final categoryId = client.spaceCategoryOf(
                      widget.spaceId,
                      item.roomId,
                    );
                    if (pinnedIds.contains(item.roomId)) {
                      pinned.add(item);
                    } else if (categoryId != null &&
                        inCategory.containsKey(categoryId)) {
                      // A chat put in a category stays there, muted or not.
                      inCategory[categoryId]!.add(item);
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
                  for (var i = 0; i < categories.length; i++) {
                    final category = categories[i];
                    final items = inCategory[category.id]!;
                    if (filter.isNotEmpty && items.isEmpty) continue;
                    final expanded = filter.isNotEmpty || !category.collapsed;
                    entries.add(
                      _SectionHeader(
                        '${category.name.toUpperCase()} · ${items.length}',
                        Icons.label_outline,
                        expanded: expanded,
                        onTap: filter.isNotEmpty
                            ? null
                            : () async {
                                await client.setSpaceCategoryCollapsed(
                                  widget.spaceId,
                                  category.id,
                                  !category.collapsed,
                                );
                                if (mounted) setState(() {});
                              },
                        onMenu: () =>
                            _showCategoryMenu(category, i, categories.length),
                      ),
                    );
                    if (expanded) entries.addAll(items.map(_ChildEntry.new));
                  }
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
                _visibleRoomIds = entries
                    .whereType<_ChildEntry>()
                    .where((entry) => _roomFor(client, entry.item) != null)
                    .map((entry) => entry.item.roomId)
                    .toList();
                final selectedRooms = _selectedRoomIds
                    .map(client.getRoomById)
                    .whereType<Room>()
                    .toList();

                final spaceMenu = PopupMenuButton<SpaceActions>(
                            tooltip: l10n.moreOptions,
                            child: Padding(
                              padding: const EdgeInsets.only(left: 6),
                              child: Container(
                                width: 38,
                                height: 38,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  border: Border.all(color: plus.line),
                                ),
                                child: Icon(
                                  Icons.more_horiz,
                                  size: 19,
                                  color: plus.text,
                                ),
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
                              PopupMenuItem(
                                value: SpaceActions.media,
                                child: ListTile(
                                  leading: const Icon(
                                    Icons.photo_library_outlined,
                                  ),
                                  title: Text(l10n.spaceMedia),
                                ),
                              ),
                              PopupMenuItem(
                                value: SpaceActions.color,
                                child: ListTile(
                                  leading: Icon(
                                    Icons.palette_outlined,
                                    color: client.spaceColor(widget.spaceId) ==
                                            null
                                        ? null
                                        : Color(
                                            client.spaceColor(widget.spaceId)!,
                                          ),
                                  ),
                                  title: Text(l10n.spaceColor),
                                ),
                              ),
                              PopupMenuItem(
                                value: SpaceActions.toggleFavorite,
                                child: ListTile(
                                  leading: Icon(
                                    isFavorite
                                        ? Icons.star
                                        : Icons.star_border,
                                  ),
                                  title: Text(l10n.openSpaceOnStart),
                                  trailing: isFavorite
                                      ? const Icon(Icons.check)
                                      : null,
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
                              PopupMenuItem(
                                value: SpaceActions.newCategory,
                                child: ListTile(
                                  leading: const Icon(Icons.new_label_outlined),
                                  title: Text(l10n.newCategory),
                                ),
                              ),
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
                          );
                return CustomScrollView(
                  slivers: [
                    SliverAppBar(
                      toolbarHeight: _selectMode || _searchOpen ? 72 : 92,
                      titleSpacing: 18,
                      floating: true,
                      scrolledUnderElevation: 0,
                      backgroundColor: plus.background,
                      automaticallyImplyLeading: false,
                      pinned: _selectMode,
                      title: _selectMode
                          ? ChatSelectionBar(
                              rooms: selectedRooms,
                              currentSpace: room,
                              onCategory: () =>
                                  _pickCategoryFor(_selectedRoomIds.toList()),
                              onClose: _clearSelection,
                              onSelectAll: () => setState(
                                () => _selectedRoomIds.addAll(_visibleRoomIds),
                              ),
                              onDone: _clearSelection,
                            )
                          : _searchOpen
                          ? TextField(
                              controller: _filterController,
                              autofocus: true,
                              onChanged: (_) => setState(() {}),
                              textInputAction: TextInputAction.search,
                              decoration: InputDecoration(
                                filled: true,
                                fillColor: plus.surface,
                                enabledBorder: OutlineInputBorder(
                                  borderSide: BorderSide(color: plus.line),
                                  borderRadius: BorderRadius.circular(99),
                                ),
                                focusedBorder: OutlineInputBorder(
                                  borderSide: BorderSide(
                                    color: plus.textFaint,
                                  ),
                                  borderRadius: BorderRadius.circular(99),
                                ),
                                contentPadding: EdgeInsets.zero,
                                hintText: l10n.search,
                                hintStyle: TextStyle(
                                  color: plus.textFaint,
                                  fontWeight: FontWeight.normal,
                                ),
                                prefixIcon: IconButton(
                                  tooltip: l10n.cancel,
                                  onPressed: () => setState(() {
                                    _searchOpen = false;
                                    _filterController.clear();
                                  }),
                                  icon: Icon(
                                    Icons.close_outlined,
                                    color: plus.text,
                                  ),
                                ),
                              ),
                            )
                          : Column(
                              crossAxisAlignment: .start,
                              mainAxisSize: .min,
                              children: [
                                Text(
                                  [
                                    ?room.bridgeNetworkName,
                                    l10n.countParticipants(
                                      room.summary.mJoinedMemberCount ?? 0,
                                    ),
                                  ].join(' · ').toUpperCase(),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: PlusText.kicker(plus.textFaint),
                                ),
                                const SizedBox(height: 2),
                                Row(
                                  children: [
                                    Expanded(
                                      child: Text(
                                        displayname,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: TextStyle(
                                          fontFamily: PlusText.displayFamily,
                                          fontSize: 30,
                                          fontWeight: FontWeight.w900,
                                          letterSpacing: -0.9,
                                          height: 1.15,
                                          color: plus.text,
                                        ),
                                      ),
                                    ),
                                    PlusCircleButton(
                                      icon: Icons.search,
                                      tooltip: l10n.search,
                                      onPressed: () =>
                                          setState(() => _searchOpen = true),
                                    ),
                                    spaceMenu,
                                  ],
                                ),
                              ],
                            ),
                      bottom: _selectMode || _searchOpen
                          ? null
                          : PreferredSize(
                              preferredSize: const Size.fromHeight(38),
                              child: DecoratedBox(
                                decoration: BoxDecoration(
                                  border: Border(
                                    bottom: BorderSide(color: plus.line),
                                  ),
                                ),
                                child: SizedBox(
                                  height: 38,
                                  child: Row(
                                    children: [
                                      const SizedBox(width: 10),
                                      PlusTab(
                                        label: l10n.chats,
                                        selected: true,
                                        onTap: () {},
                                      ),
                                      PlusTab(
                                        label: l10n.spaceMedia,
                                        selected: false,
                                        onTap: () =>
                                            _onSpaceAction(SpaceActions.media),
                                      ),
                                      PlusTab(
                                        label: l10n.spaceMembers,
                                        selected: false,
                                        onTap: () => context.push(
                                          '/rooms/${widget.spaceId}/details/members',
                                        ),
                                      ),
                                    ],
                                  ),
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
