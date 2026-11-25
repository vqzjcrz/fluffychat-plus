// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:convert';

import 'package:fluffychat/config/setting_keys.dart';
import 'package:matrix/matrix.dart';

/// Account data event with names the user gave chats for themselves only.
///
/// Content: `{ "rooms": { "!room1": "Squad chat" } }`
const String chatNicknamesAccountDataType = 'im.fluffychat.plus.nicknames';

String _nicknamesStoreKey(String clientName) =>
    'im.fluffychat.plus.nicknames.$clientName';

Map<String, String> _parseNicknames(Object? rooms) {
  if (rooms is! Map) return {};
  final result = <String, String>{};
  for (final entry in rooms.entries) {
    final key = entry.key;
    final value = entry.value;
    if (key is String && value is String && value.trim().isNotEmpty) {
      result[key] = value.trim();
    }
  }
  return result;
}

extension ChatNicknameClientExtension on Client {
  Map<String, String> get chatNicknames {
    final event = accountData[chatNicknamesAccountDataType];
    if (event != null) return _parseNicknames(event.content['rooms']);
    // The push handler can run before account data is loaded.
    try {
      final raw = AppSettings.store.getString(_nicknamesStoreKey(clientName));
      if (raw != null) return _parseNicknames(jsonDecode(raw));
    } catch (_) {}
    return const {};
  }

  /// Gives [roomId] a name only this user sees, or removes it when [name] is
  /// empty or null.
  Future<void> setChatNickname(String roomId, String? name) async {
    final userId = userID;
    if (userId == null) return;
    final rooms = Map<String, String>.from(chatNicknames);
    final trimmed = name?.trim() ?? '';
    if (trimmed.isEmpty) {
      rooms.remove(roomId);
    } else {
      rooms[roomId] = trimmed;
    }
    final content = <String, Object?>{'rooms': rooms};
    accountData[chatNicknamesAccountDataType] = BasicEvent(
      type: chatNicknamesAccountDataType,
      content: content,
    );
    await mirrorChatNicknamesToStore();
    await setAccountData(userId, chatNicknamesAccountDataType, content);
  }

  Future<void> mirrorChatNicknamesToStore() async {
    final event = accountData[chatNicknamesAccountDataType];
    if (event == null) return;
    try {
      await AppSettings.store.setString(
        _nicknamesStoreKey(clientName),
        jsonEncode(_parseNicknames(event.content['rooms'])),
      );
    } catch (_) {}
  }
}

extension ChatNicknameRoomExtension on Room {
  /// The name the user gave this chat for themselves, if any.
  String? get nickname => client.chatNicknames[id];

  /// The user's own name for this chat if they set one, else its real name.
  String getNicknameOrDisplayname([
    MatrixLocalizations i18n = const MatrixDefaultLocalizations(),
  ]) => nickname ?? getLocalizedDisplayname(i18n);
}
