// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/utils/chat_nickname_extension.dart';
import 'package:fluffychat/utils/matrix_sdk_extensions/matrix_locals.dart';
import 'package:fluffychat/utils/rate_limit_retry.dart';
import 'package:fluffychat/utils/room_plus_extension.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_modal_action_popup.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_ok_cancel_alert_dialog.dart';
import 'package:fluffychat/widgets/future_loading_dialog.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

enum _MoreAction { markRead, mute, unmute }

/// Takes the place of the search bar while chats are selected, with actions
/// for all of them at once.
class ChatSelectionBar extends StatelessWidget {
  final List<Room> rooms;
  final VoidCallback onClose;
  final VoidCallback onSelectAll;

  /// Called after an action, so the list can leave selection mode.
  final VoidCallback onDone;

  /// The space the chats are being picked from, if any. "Move to space" takes
  /// them out of it when the user is allowed to.
  final Room? currentSpace;

  /// Shows the "Move to category" button when set (only inside a space).
  final VoidCallback? onCategory;

  const ChatSelectionBar({
    required this.rooms,
    required this.onClose,
    required this.onSelectAll,
    required this.onDone,
    this.currentSpace,
    this.onCategory,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final joined = rooms
        .where((room) => room.membership == Membership.join)
        .toList();
    final anyUnread = joined.any((room) => room.isUnread);
    final allMuted =
        joined.isNotEmpty &&
        joined.every((room) => room.pushRuleState != PushRuleState.notify);
    final color = theme.colorScheme.onTertiaryContainer;

    return Material(
      color: theme.colorScheme.tertiaryContainer,
      borderRadius: BorderRadius.circular(99),
      child: SizedBox(
        height: 56,
        child: Row(
          children: [
            IconButton(
              icon: const Icon(Icons.close),
              color: color,
              tooltip: l10n.close,
              onPressed: onClose,
            ),
            Expanded(
              child: Text(
                l10n.selectedChatsCount(rooms.length),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: color,
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.select_all),
              color: color,
              tooltip: l10n.selectAll,
              onPressed: onSelectAll,
            ),
            if (onCategory != null)
              IconButton(
                icon: const Icon(Icons.label_outline),
                color: color,
                tooltip: l10n.moveToCategory,
                onPressed: onCategory,
              ),
            IconButton(
              icon: const Icon(Icons.drive_file_move_outlined),
              color: color,
              tooltip: l10n.moveToSpace,
              onPressed: () async {
                if (await moveRoomsToSpace(
                  context,
                  rooms,
                  fromSpace: currentSpace,
                )) {
                  onDone();
                }
              },
            ),
            IconButton(
              icon: Icon(Icons.exit_to_app, color: theme.colorScheme.error),
              tooltip: l10n.leave,
              onPressed: () async {
                if (await leaveRooms(context, rooms)) onDone();
              },
            ),
            if (anyUnread || joined.isNotEmpty)
              PopupMenuButton<_MoreAction>(
                icon: Icon(Icons.more_vert, color: color),
                onSelected: (action) async {
                  switch (action) {
                    case _MoreAction.markRead:
                      await markRoomsRead(context, joined);
                    case _MoreAction.mute:
                      await setRoomsMuted(context, joined, true);
                    case _MoreAction.unmute:
                      await setRoomsMuted(context, joined, false);
                  }
                  onDone();
                },
                itemBuilder: (context) => [
                  if (anyUnread)
                    PopupMenuItem(
                      value: _MoreAction.markRead,
                      child: ListTile(
                        leading: const Icon(Icons.mark_chat_read_outlined),
                        title: Text(l10n.markAsRead),
                      ),
                    ),
                  if (joined.isNotEmpty)
                    PopupMenuItem(
                      value: allMuted ? _MoreAction.unmute : _MoreAction.mute,
                      child: ListTile(
                        leading: Icon(
                          allMuted
                              ? Icons.notifications_on_outlined
                              : Icons.notifications_off_outlined,
                        ),
                        title: Text(allMuted ? l10n.unmuteChat : l10n.muteChat),
                      ),
                    ),
                ],
              ),
            const SizedBox(width: 4),
          ],
        ),
      ),
    );
  }
}

/// Runs [action] for every room with a progress bar, waiting out the
/// server's "Too many requests" limits instead of failing. Keeps going after
/// an error and shows the first one at the end. Returns true without errors.
Future<bool> _forEachRoom(
  BuildContext context,
  List<Room> rooms,
  Future<void> Function(Room room) action,
) async {
  if (rooms.isEmpty) return true;
  final result = await showFutureLoadingDialog(
  context: context,
  futureWithProgress: (setProgress) async {
    Object? firstError;
    for (var i = 0; i < rooms.length; i++) {
      final room = rooms[i];
      try {
        await retryOnRateLimit(() => action(room));
      } catch (e, s) {
        Logs().w('Bulk action failed for ${room.id}', e, s);
        firstError ??= e;
      }
      setProgress((i + 1) / rooms.length);
    }
    if (firstError != null) throw firstError;
  },
);
  return result.error == null;
}

/// Leaves (or, for invites, declines) all [rooms] after asking once.
Future<bool> leaveRooms(BuildContext context, List<Room> rooms) async {
  if (rooms.isEmpty) return false;
  final l10n = L10n.of(context);
  final confirmed = await showOkCancelAlertDialog(
    context: context,
    title: l10n.leaveChatsTitle(rooms.length),
    message: l10n.leaveChatsDescription,
    okLabel: l10n.leave,
    cancelLabel: l10n.cancel,
    isDestructive: true,
  );
  if (confirmed != OkCancelResult.ok || !context.mounted) return false;
  return _forEachRoom(context, rooms, (room) => room.leave());
}

Future<void> markRoomsRead(BuildContext context, List<Room> rooms) =>
    _forEachRoom(
      context,
      rooms.where((room) => room.isUnread).toList(),
      (room) => room.markAllRead(),
    );

Future<void> setRoomsMuted(
  BuildContext context,
  List<Room> rooms,
  bool muted,
) => _forEachRoom(
  context,
  rooms
      .where((room) => (room.pushRuleState == PushRuleState.notify) == muted)
      .toList(),
  (room) => room.setPushRuleState(
    muted ? PushRuleState.mentionsOnly : PushRuleState.notify,
  ),
);

/// Asks which space to put [rooms] in and adds them there. When they're
/// picked from [fromSpace] and the user may edit it, they're also taken out
/// of it, so they move rather than appear in both.
Future<bool> moveRoomsToSpace(
  BuildContext context,
  List<Room> rooms, {
  Room? fromSpace,
}) async {
  if (rooms.isEmpty) return false;
  final l10n = L10n.of(context);
  final client = rooms.first.client;
  final movingIds = rooms.map((room) => room.id).toSet();
  final spaces =
      client.rooms
          .where(
            (room) =>
                room.isSpace &&
                room.membership == Membership.join &&
                room.id != fromSpace?.id &&
                !movingIds.contains(room.id) &&
                room.canChangeStateEvent(EventTypes.SpaceChild),
          )
          .toList()
        ..sort(
          (a, b) => a
              .getNicknameOrDisplayname(MatrixLocals(l10n))
              .toLowerCase()
              .compareTo(
                b.getNicknameOrDisplayname(MatrixLocals(l10n)).toLowerCase(),
              ),
        );
  if (spaces.isEmpty) {
    await showOkAlertDialog(
      context: context,
      title: l10n.moveToSpace,
      message: l10n.noSpacesToMoveTo,
      okLabel: l10n.ok,
    );
    return false;
  }
  if (!context.mounted) return false;
  final target = await showModalActionPopup<Room>(
    context: context,
    title: l10n.moveToSpace,
    cancelLabel: l10n.cancel,
    actions: [
      for (final space in spaces)
        AdaptiveModalAction(
          value: space,
          label: space.getNicknameOrDisplayname(MatrixLocals(l10n)),
          icon: const Icon(Icons.workspaces_outlined),
        ),
    ],
  );
  if (target == null || !context.mounted) return false;

  final from = fromSpace;
  final removeFromOld =
      from != null && from.canChangeStateEvent(EventTypes.SpaceChild);
  final success = await _forEachRoom(context, rooms, (room) async {
    await target.setSpaceChild(room.id);
    if (removeFromOld) await from!.removeSpaceChild(room.id);
  });
  if (!success || !context.mounted) return success;

  final targetName = target.getNicknameOrDisplayname(MatrixLocals(l10n));
  final String message;
  if (from != null && !removeFromOld) {
    message = l10n.addedChatsKeptInSpace(
      rooms.length,
      targetName,
      from.getNicknameOrDisplayname(MatrixLocals(l10n)),
    );
  } else {
    message = l10n.movedChatsToSpace(rooms.length, targetName);
  }
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  return true;
}
