// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:convert';

import 'package:matrix/matrix.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Account data event that stores snoozed chats and when each snooze ends.
///
/// Content:
/// ```json
/// { "rooms": { "!room1": 1764000000000 } }
/// ```
///
/// Snoozing doesn't touch the server's push rules. Notifications for a
/// snoozed chat are dropped on the device, so the snooze ends on its own even
/// if the app isn't opened again.
const String snoozeAccountDataType = 'im.fluffychat.plus.snooze';

String _snoozeStoreKey(String clientName) =>
    'im.fluffychat.plus.snooze.$clientName';

Map<String, int> _parseSnoozeMap(Object? rooms) {
  if (rooms is! Map) return {};
  final result = <String, int>{};
  for (final entry in rooms.entries) {
    final key = entry.key;
    final value = entry.value;
    if (key is String && value is num) result[key] = value.toInt();
  }
  return result;
}

extension SnoozeExtension on Client {
  Map<String, int> get _snoozeMap =>
      _parseSnoozeMap(accountData[snoozeAccountDataType]?.content['rooms']);

  /// When the snooze of [roomId] ends, or null if it isn't snoozed.
  DateTime? snoozedUntil(String roomId) {
    final until = _snoozeMap[roomId];
    if (until == null) return null;
    final time = DateTime.fromMillisecondsSinceEpoch(until);
    return time.isAfter(DateTime.now()) ? time : null;
  }

  bool isSnoozed(String roomId) => snoozedUntil(roomId) != null;

  /// Snoozes [roomId] until [until], or ends the snooze when [until] is null.
  Future<void> snoozeRoom(String roomId, DateTime? until) async {
    final userId = userID;
    if (userId == null) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    final rooms = Map<String, int>.from(_snoozeMap)
      ..removeWhere((_, end) => end <= now);
    if (until == null) {
      rooms.remove(roomId);
    } else {
      rooms[roomId] = until.millisecondsSinceEpoch;
    }
    final content = <String, Object?>{'rooms': rooms};
    // Apply locally right away so the UI doesn't wait for the next sync.
    accountData[snoozeAccountDataType] = BasicEvent(
      type: snoozeAccountDataType,
      content: content,
    );
    await mirrorSnoozeToStore();
    await setAccountData(userId, snoozeAccountDataType, content);
  }

  /// Keeps a local copy for the push handler, which can run before the
  /// client has loaded its account data.
  Future<void> mirrorSnoozeToStore() async {
    // Nothing loaded yet: keep the last copy instead of wiping it.
    if (accountData[snoozeAccountDataType] == null) return;
    final store = await SharedPreferences.getInstance();
    await store.setString(
      _snoozeStoreKey(clientName),
      jsonEncode(_snoozeMap),
    );
  }
}

/// Whether a push notification for [roomId] should be dropped.
Future<bool> isRoomSnoozedForPush(
  Client client,
  String roomId,
  SharedPreferences store,
) async {
  if (client.isSnoozed(roomId)) return true;
  try {
    await store.reload();
  } catch (_) {}
  final raw = store.getString(_snoozeStoreKey(client.clientName));
  if (raw == null) return false;
  try {
    final until = _parseSnoozeMap(jsonDecode(raw))[roomId];
    if (until == null) return false;
    return DateTime.fromMillisecondsSinceEpoch(until).isAfter(DateTime.now());
  } catch (_) {
    return false;
  }
}

/// 8:00 the next morning in local time.
DateTime tomorrowMorning() {
  final now = DateTime.now();
  return DateTime(now.year, now.month, now.day + 1, 8);
}
