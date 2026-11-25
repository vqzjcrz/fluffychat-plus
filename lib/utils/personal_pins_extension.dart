// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:matrix/matrix.dart';

/// Account data event with messages the user pinned for themselves, for
/// chats where they can't pin for everyone (like Discord announcement
/// channels).
///
/// Content: `{ "rooms": { "!room1": ["$event1", "$event2"] } }`
const String personalPinsAccountDataType = 'im.fluffychat.plus.personal_pins';

extension PersonalPinsExtension on Client {
  Map<String, Object?> get _personalPinsRooms {
    final rooms = accountData[personalPinsAccountDataType]?.content['rooms'];
    return rooms is Map ? Map<String, Object?>.from(rooms) : {};
  }

  /// Messages pinned for the user in [roomId], oldest pin first.
  List<String> personalPins(String roomId) {
    final list = _personalPinsRooms[roomId];
    return list is List ? list.whereType<String>().toList() : const [];
  }

  bool isPersonallyPinned(String roomId, String eventId) =>
      personalPins(roomId).contains(eventId);

  Future<void> setPersonallyPinned(
    String roomId,
    String eventId,
    bool pinned,
  ) async {
    final userId = userID;
    if (userId == null) return;
    final rooms = _personalPinsRooms;
    final list = List<String>.from(personalPins(roomId))..remove(eventId);
    if (pinned) list.add(eventId);
    if (list.isEmpty) {
      rooms.remove(roomId);
    } else {
      rooms[roomId] = list;
    }
    final content = <String, Object?>{'rooms': rooms};
    accountData[personalPinsAccountDataType] = BasicEvent(
      type: personalPinsAccountDataType,
      content: content,
    );
    await setAccountData(userId, personalPinsAccountDataType, content);
  }
}
