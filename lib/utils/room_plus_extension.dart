// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/setting_keys.dart';
import 'package:matrix/matrix.dart';

/// State event types bridges use to describe what they bridge.
const List<String> bridgeStateEventTypes = ['m.bridge', 'uk.half-shot.bridge'];

extension RoomPlusExtension on Room {
  /// Whether a bridge marks this room as a Discord portal.
  bool get isDiscordBridged {
    for (final type in bridgeStateEventTypes) {
      final events = states[type]?.values;
      if (events == null) continue;
      for (final event in events) {
        final protocol = event.content.tryGetMap<String, Object?>('protocol');
        final id = protocol?.tryGet<String>('id')?.toLowerCase() ?? '';
        final name =
            protocol?.tryGet<String>('displayname')?.toLowerCase() ?? '';
        if (id.contains('discord') || name.contains('discord')) return true;
      }
    }
    return false;
  }

  /// Whether the user is in this chat but can't post in it: the power levels
  /// don't let them send messages (for example a Signal group where only
  /// admins can post), or it's a read-only Discord portal.
  bool get isReadOnlyForMe {
    if (membership != Membership.join || isSpace) return false;
    return !canSendDefaultMessages || isDiscordBridged;
  }

  /// Whether someone mentioned or replied to the user in unread messages.
  bool get hasUnreadMentions => highlightCount > 0;

  /// Marks everything in this chat as read, including a manual unread mark.
  Future<void> markAllRead() async {
    if (markedUnread) await markUnread(false);
    if (notificationCount == 0 && !hasNewMessages) return;
    final eventId = lastEvent?.eventId;
    if (eventId == null) return;
    await setReadMarker(
      eventId,
      mRead: eventId,
      public: AppSettings.sendPublicReadReceipts.value,
    );
  }
}
