// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:collection/collection.dart';
import 'package:fluffychat/utils/custom_order_extension.dart';
import 'package:matrix/matrix.dart';

/// Account data event that stores per-space view settings: how the chats in a
/// space are sorted, which chats are pinned to the top of it, its accent
/// color and the user's own categories for grouping its chats.
///
/// Content:
/// ```json
/// {
///   "sort": { "!spaceA": "recent" },
///   "pins": { "!spaceA": ["!room1", "!room2"] },
///   "colors": { "!spaceA": 4283215696 },
///   "categories": { "!spaceA": [{ "id": "c1", "name": "Work", "collapsed": false }] },
///   "category_of": { "!spaceA": { "!room1": "c1" } },
///   "chats_pos": { "!spaceA": 1 }
/// }
/// ```
///
/// Categories only exist in this client: they need no permissions in the
/// space and nobody else sees them.
const String spacePrefsAccountDataType = 'im.fluffychat.plus.space_prefs';

enum SpaceSortMode { recent, unread, custom }

/// A heading in a space that groups some of its chats.
class SpaceCategory {
  final String id;
  final String name;
  final bool collapsed;

  const SpaceCategory({
    required this.id,
    required this.name,
    this.collapsed = false,
  });

  Map<String, Object?> toJson() => {
    'id': id,
    'name': name,
    if (collapsed) 'collapsed': true,
  };

  static SpaceCategory? fromJson(Object? json) {
    if (json is! Map) return null;
    final id = json['id'];
    final name = json['name'];
    if (id is! String || name is! String) return null;
    return SpaceCategory(id: id, name: name, collapsed: json['collapsed'] == true);
  }

  SpaceCategory copyWith({String? name, bool? collapsed}) => SpaceCategory(
    id: id,
    name: name ?? this.name,
    collapsed: collapsed ?? this.collapsed,
  );
}

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

  /// The accent color chosen for [spaceId] as an ARGB value, if any.
  int? spaceColor(String spaceId) {
    final colors = _spacePrefsContent['colors'];
    if (colors is! Map) return null;
    final value = colors[spaceId];
    return value is num ? value.toInt() : null;
  }

  /// Sets the accent color of [spaceId], or removes it when [argb] is null.
  Future<void> setSpaceColor(String spaceId, int? argb) =>
      _updateSpacePrefs((content) {
        final existing = content['colors'];
        final colors = existing is Map
            ? Map<String, Object?>.from(existing)
            : <String, Object?>{};
        if (argb == null) {
          colors.remove(spaceId);
        } else {
          colors[spaceId] = argb;
        }
        content['colors'] = colors;
      });

  /// The categories of [spaceId], in display order.
  List<SpaceCategory> spaceCategories(String spaceId) {
    final all = _spacePrefsContent['categories'];
    final list = all is Map ? all[spaceId] : null;
    if (list is! List) return const [];
    return list.map(SpaceCategory.fromJson).whereType<SpaceCategory>().toList();
  }

  /// Which category of [spaceId] the chat [roomId] is in, if any.
  String? spaceCategoryOf(String spaceId, String roomId) {
    final all = _spacePrefsContent['category_of'];
    final map = all is Map ? all[spaceId] : null;
    if (map is! Map) return null;
    final id = map[roomId];
    return id is String ? id : null;
  }

  Future<void> _setCategories(String spaceId, List<SpaceCategory> categories) =>
      _updateSpacePrefs((content) {
        final existing = content['categories'];
        final all = existing is Map
            ? Map<String, Object?>.from(existing)
            : <String, Object?>{};
        if (categories.isEmpty) {
          all.remove(spaceId);
        } else {
          all[spaceId] = categories.map((c) => c.toJson()).toList();
        }
        content['categories'] = all;
      });

  /// Adds a category at the end of [spaceId] and returns its ID.
  Future<String> addSpaceCategory(String spaceId, String name) async {
    final id = 'c${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}';
    await _setCategories(spaceId, [
      ...spaceCategories(spaceId),
      SpaceCategory(id: id, name: name),
    ]);
    return id;
  }

  Future<void> renameSpaceCategory(String spaceId, String id, String name) =>
      _setCategories(spaceId, [
        for (final c in spaceCategories(spaceId))
          c.id == id ? c.copyWith(name: name) : c,
      ]);

  Future<void> setSpaceCategoryCollapsed(
    String spaceId,
    String id,
    bool collapsed,
  ) => _setCategories(spaceId, [
    for (final c in spaceCategories(spaceId))
      c.id == id ? c.copyWith(collapsed: collapsed) : c,
  ]);

  /// Moves a category [delta] places up (negative) or down (positive).
  /// Where the chats without a category go among the categories of
  /// [spaceId]: 0 is above all of them, which is the default.
  int spaceChatsPosition(String spaceId) {
    final all = _spacePrefsContent['chats_pos'];
    final value = all is Map ? all[spaceId] : null;
    final count = spaceCategories(spaceId).length;
    if (value is! num) return 0;
    final position = value.toInt();
    return position < 0 ? 0 : (position > count ? count : position);
  }

  /// The order of the blocks in [spaceId]: category IDs, with null where
  /// the chats without a category go.
  List<String?> spaceBlockOrder(String spaceId) {
    final order = <String?>[
      for (final category in spaceCategories(spaceId)) category.id,
    ];
    order.insert(spaceChatsPosition(spaceId), null);
    return order;
  }

  /// Moves a category [delta] places up (negative) or down (positive),
  /// counting the chats without a category as one place.
  Future<void> moveSpaceCategory(String spaceId, String id, int delta) {
    final order = spaceBlockOrder(spaceId);
    final from = order.indexOf(id);
    if (from < 0) return Future.value();
    final to = from + delta;
    if (to < 0 || to >= order.length) return Future.value();
    order.insert(to, order.removeAt(from));
    final byId = {for (final c in spaceCategories(spaceId)) c.id: c};
    final categories = [
      for (final blockId in order)
        if (blockId != null) byId[blockId]!,
    ];
    final chatsPosition = order.indexOf(null);
    return _updateSpacePrefs((content) {
      final existing = content['categories'];
      final all = existing is Map
          ? Map<String, Object?>.from(existing)
          : <String, Object?>{};
      all[spaceId] = categories.map((c) => c.toJson()).toList();
      content['categories'] = all;
      final existingPos = content['chats_pos'];
      final positions = existingPos is Map
          ? Map<String, Object?>.from(existingPos)
          : <String, Object?>{};
      if (chatsPosition == 0) {
        positions.remove(spaceId);
      } else {
        positions[spaceId] = chatsPosition;
      }
      content['chats_pos'] = positions;
    });
  }

  /// Removes a category; its chats go back to having no category.
  Future<void> deleteSpaceCategory(String spaceId, String id) =>
      _updateSpacePrefs((content) {
        final existing = content['categories'];
        final all = existing is Map
            ? Map<String, Object?>.from(existing)
            : <String, Object?>{};
        final remaining = spaceCategories(
          spaceId,
        ).where((c) => c.id != id).map((c) => c.toJson()).toList();
        if (remaining.isEmpty) {
          all.remove(spaceId);
        } else {
          all[spaceId] = remaining;
        }
        content['categories'] = all;
        content['category_of'] = _withCategoryAssignments(
          content,
          spaceId,
          (map) => map.removeWhere((_, value) => value == id),
        );
      });

  /// Puts [roomIds] into category [categoryId] of [spaceId], or takes them out
  /// of any category when [categoryId] is null.
  Future<void> setSpaceCategoryOf(
    String spaceId,
    Iterable<String> roomIds,
    String? categoryId,
  ) => _updateSpacePrefs((content) {
    content['category_of'] = _withCategoryAssignments(content, spaceId, (map) {
      for (final roomId in roomIds) {
        if (categoryId == null) {
          map.remove(roomId);
        } else {
          map[roomId] = categoryId;
        }
      }
    });
  });

  Map<String, Object?> _withCategoryAssignments(
    Map<String, Object?> content,
    String spaceId,
    void Function(Map<String, Object?> map) update,
  ) {
    final existing = content['category_of'];
    final all = existing is Map
        ? Map<String, Object?>.from(existing)
        : <String, Object?>{};
    final current = all[spaceId];
    final map = current is Map
        ? Map<String, Object?>.from(current)
        : <String, Object?>{};
    update(map);
    if (map.isEmpty) {
      all.remove(spaceId);
    } else {
      all[spaceId] = map;
    }
    return all;
  }

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
