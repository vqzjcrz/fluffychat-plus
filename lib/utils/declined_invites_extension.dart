// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:matrix/matrix.dart';

/// Account data event with invites from bridge bots the user declined.
/// When a bridge invites them to one of these rooms again (for example
/// after re-syncing a Discord server), the app declines it on its own.
///
/// Content: `{ "rooms": ["!room1", "!room2"] }`
const String declinedInvitesAccountDataType =
    'im.fluffychat.plus.declined_invites';

extension DeclinedInvitesExtension on Client {
  Set<String> get declinedInvites {
    final rooms = accountData[declinedInvitesAccountDataType]
        ?.content['rooms'];
    return rooms is List ? rooms.whereType<String>().toSet() : <String>{};
  }

  Future<void> _setDeclinedInvites(Set<String> rooms) async {
    final userId = userID;
    if (userId == null) return;
    final content = <String, Object?>{'rooms': rooms.toList()};
    accountData[declinedInvitesAccountDataType] = BasicEvent(
      type: declinedInvitesAccountDataType,
      content: content,
    );
    await setAccountData(userId, declinedInvitesAccountDataType, content);
  }

  /// Whether a bridge bot sent the invite to [room], judged by the inviter's
  /// name (discordbot, signalbot, instagrambot, ...). Invites from people
  /// are never declined automatically.
  bool invitedByBridgeBot(Room room) {
    final userId = userID;
    if (userId == null) return false;
    final inviter = room.getState(EventTypes.RoomMember, userId)?.senderId;
    if (inviter == null || inviter == userId) return false;
    final localpart = inviter.localpart?.toLowerCase() ?? '';
    return localpart.endsWith('bot');
  }

  /// Declines the invite to [room]. If a bridge bot sent it, the app keeps
  /// declining that room if the bridge invites the user again.
  Future<void> declineInvite(Room room) async {
    final remember = invitedByBridgeBot(room);
    await room.leave();
    if (!remember) return;
    await _setDeclinedInvites(declinedInvites..add(room.id));
  }

  /// Runs on every sync: declines invites to rooms the user declined before,
  /// and forgets rooms the user has since joined on purpose.
  Future<void> handleDeclinedInvites(SyncUpdate update) async {
    final declined = declinedInvites;
    if (declined.isEmpty) return;
    final rooms = update.rooms;
    if (rooms == null) return;

    final joined = rooms.join?.keys.where(declined.contains).toList() ?? [];
    if (joined.isNotEmpty) {
      await _setDeclinedInvites(declined..removeAll(joined));
    }

    final reinvited =
        rooms.invite?.keys.where(declined.contains).toList() ?? [];
    for (final roomId in reinvited) {
      try {
        await leaveRoom(roomId);
        Logs().i('Declined repeated bridge invite to $roomId');
      } catch (e, s) {
        Logs().w('Unable to decline repeated invite to $roomId', e, s);
      }
    }
  }
}
