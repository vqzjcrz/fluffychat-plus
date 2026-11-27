// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/setting_keys.dart';
import 'package:fluffychat/utils/read_only_override_extension.dart';
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

  /// The name of the network a bridge connects this room to, like
  /// "Discord", if any.
  String? get bridgeNetworkName {
    for (final type in bridgeStateEventTypes) {
      final events = states[type]?.values;
      if (events == null) continue;
      for (final event in events) {
        final protocol = event.content.tryGetMap<String, Object?>('protocol');
        final name =
            protocol?.tryGet<String>('displayname') ??
            protocol?.tryGet<String>('id');
        if (name != null && name.isNotEmpty) return name;
      }
    }
    return null;
  }

  /// Whether the user is in this chat but can't post in it: the power levels
  /// don't let them send messages (for example a Signal group where only
  /// admins can post), or it's read-only in this app: Discord portals by
  /// default, or any chat the user switched to read-only.
  bool get isReadOnlyForMe {
    if (membership != Membership.join || isSpace) return false;
    if (!canSendDefaultMessages) return true;
    return client.readOnlyOverride(id) ?? isDiscordBridged;
  }

  /// Whether the user can switch this chat between read-only and writable:
  /// only where the server lets them post at all.
  bool get canToggleReadOnly =>
      membership == Membership.join && !isSpace && canSendDefaultMessages;

  /// Makes this chat read-only or writable for the user. Choosing the default
  /// for the chat removes the saved choice.
  Future<void> setReadOnlyForMe(bool readOnly) => client.setReadOnlyOverride(
    id,
    readOnly == isDiscordBridged ? null : readOnly,
  );

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
