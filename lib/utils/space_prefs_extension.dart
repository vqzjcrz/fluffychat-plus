// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:collection/collection.dart';
import 'package:fluffychat/utils/custom_order_extension.dart';
import 'package:matrix/matrix.dart';

/// Account data event that stores per-space view settings: how the chats in a
/// space are sorted and which chats are pinned to the top of it.
///
/// Content:
/// ```json
/// {
///   "sort": { "!spaceA": "recent" },
///   "pins": { "!spaceA": ["!room1", "!room2"] }
/// }
/// ```
const String spacePrefsAccountDataType = 'im.fluffychat.plus.space_prefs';

enum SpaceSortMode { recent, unread, custom }

extension SpacePrefsExtension on Client {
  Map<String, Object?> get _spacePrefsContent =>
      accountData[spacePrefsAccountDataType]?.content ?? const {};

  /// How the chats of [spaceId] are sorted. Spaces the user already arranged
  /// by hand keep that order until they pick something else.
  SpaceSortMode spaceSortMode(String spaceId) {
    final sort = _spacePrefsContent['sort'];
    final name = sort is Map ? sort[spaceId] : null;
    final mode = SpaceSortMode.values.firstWhereOrNull((m) => m.name == name);
    if (mode != null) return mode;
    return hasCustomChildOrder(spaceId)
        ? SpaceSortMode.custom
        : SpaceSortMode.recent;
  }

  Future<void> setSpaceSortMode(String spaceId, SpaceSortMode mode) =>
      _updateSpacePrefs((content) {
        final existing = content['sort'];
        final sort = existing is Map
            ? Map<String, Object?>.from(existing)
            : <String, Object?>{};
        sort[spaceId] = mode.name;
        content['sort'] = sort;
      });

  /// Room IDs pinned to the top of [spaceId], in the order they were pinned.
  List<String> pinnedChats(String spaceId) {
    final pins = _spacePrefsContent['pins'];
    if (pins is! Map) return const [];
    final list = pins[spaceId];
    return list is List ? list.whereType<String>().toList() : const [];
  }

  bool isPinnedInSpace(String spaceId, String roomId) =>
      pinnedChats(spaceId).contains(roomId);

  Future<void> setPinnedInSpace(String spaceId, String roomId, bool pinned) =>
      _updateSpacePrefs((content) {
        final existing = content['pins'];
        final pins = existing is Map
            ? Map<String, Object?>.from(existing)
            : <String, Object?>{};
        final list = List<String>.from(pinnedChats(spaceId));
        list.remove(roomId);
        if (pinned) list.add(roomId);
        if (list.isEmpty) {
          pins.remove(spaceId);
        } else {
          pins[spaceId] = list;
        }
        content['pins'] = pins;
      });

  Future<void> _updateSpacePrefs(
    void Function(Map<String, Object?> content) update,
  ) async {
    final userId = userID;
    if (userId == null) return;
    final content = Map<String, Object?>.from(_spacePrefsContent);
    update(content);
    // Apply locally right away so the UI doesn't wait for the next sync.
    accountData[spacePrefsAccountDataType] = BasicEvent(
      type: spacePrefsAccountDataType,
      content: content,
    );
    await setAccountData(userId, spacePrefsAccountDataType, content);
  }
}
