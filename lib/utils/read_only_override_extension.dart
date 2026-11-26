// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:matrix/matrix.dart';

/// Account data event with the chats the user switched between read-only and
/// writable by hand. Only differences from the default are stored (Discord
/// chats default to read-only, everything else to writable).
///
/// Content:
/// ```json
/// { "rooms": { "!discordChannel": false, "!noisyGroup": true } }
/// ```
/// `true` means read-only, `false` means the user can post.
const String readOnlyOverridesAccountDataType = 'im.fluffychat.plus.read_only';

extension ReadOnlyOverrideExtension on Client {
  Map<String, Object?> get _readOnlyRooms {
    final rooms = accountData[readOnlyOverridesAccountDataType]?.content['rooms'];
    return rooms is Map ? Map<String, Object?>.from(rooms) : {};
  }

  /// The user's own read-only choice for [roomId], or null for the default.
  bool? readOnlyOverride(String roomId) {
    final value = _readOnlyRooms[roomId];
    return value is bool ? value : null;
  }

  /// Saves the user's choice for [roomId], or forgets it when [readOnly] is
  /// null.
  Future<void> setReadOnlyOverride(String roomId, bool? readOnly) async {
    final userId = userID;
    if (userId == null) return;
    final rooms = _readOnlyRooms;
    if (readOnly == null) {
      rooms.remove(roomId);
    } else {
      rooms[roomId] = readOnly;
    }
    final content = <String, Object?>{'rooms': rooms};
    // Apply locally right away so the UI doesn't wait for the next sync.
    accountData[readOnlyOverridesAccountDataType] = BasicEvent(
      type: readOnlyOverridesAccountDataType,
      content: content,
    );
    await setAccountData(userId, readOnlyOverridesAccountDataType, content);
  }
}
