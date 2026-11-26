// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/utils/room_plus_extension.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_ok_cancel_alert_dialog.dart';
import 'package:fluffychat/widgets/future_loading_dialog.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

/// Takes the place of the search bar while chats are selected, with actions
/// for all of them at once.
class ChatSelectionBar extends StatelessWidget {
  final List<Room> rooms;
  final VoidCallback onClose;
  final VoidCallback onSelectAll;

  /// Called after an action, so the list can leave selection mode.
  final VoidCallback onDone;

  const ChatSelectionBar({
    required this.rooms,
    required this.onClose,
    required this.onSelectAll,
    required this.onDone,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final joined = rooms
        .where((room) => room.membership == Membership.join)
        .toList();
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
            if (joined.any((room) => room.isUnread))
              IconButton(
                icon: const Icon(Icons.mark_chat_read_outlined),
                color: color,
                tooltip: l10n.markAsRead,
                onPressed: () async {
                  await markRoomsRead(context, joined);
                  onDone();
                },
              ),
            if (joined.isNotEmpty)
              IconButton(
                icon: Icon(
                  allMuted
                      ? Icons.notifications_on_outlined
                      : Icons.notifications_off_outlined,
                ),
                color: color,
                tooltip: allMuted ? l10n.unmuteChat : l10n.muteChat,
                onPressed: () async {
                  await setRoomsMuted(context, joined, !allMuted);
                  onDone();
                },
              ),
            IconButton(
              icon: Icon(
                Icons.exit_to_app,
                color: theme.colorScheme.error,
              ),
              tooltip: l10n.leave,
              onPressed: () async {
                if (await leaveRooms(context, rooms)) onDone();
              },
            ),
            const SizedBox(width: 4),
          ],
        ),
      ),
    );
  }
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
  final result = await showFutureLoadingDialog(
    context: context,
    future: () async {
      Object? firstError;
      for (final room in rooms) {
        try {
          await room.leave();
        } catch (e, s) {
          Logs().w('Unable to leave ${room.id}', e, s);
          firstError ??= e;
        }
      }
      if (firstError != null) throw firstError;
    },
  );
  return result.error == null;
}

Future<void> markRoomsRead(BuildContext context, List<Room> rooms) =>
    showFutureLoadingDialog(
      context: context,
      future: () async {
        for (final room in rooms) {
          if (room.isUnread) await room.markAllRead();
        }
      },
    );

Future<void> setRoomsMuted(
  BuildContext context,
  List<Room> rooms,
  bool muted,
) => showFutureLoadingDialog(
  context: context,
  future: () async {
    final state = muted ? PushRuleState.mentionsOnly : PushRuleState.notify;
    for (final room in rooms) {
      if ((room.pushRuleState == PushRuleState.notify) == muted) {
        await room.setPushRuleState(state);
      }
    }
  },
);
