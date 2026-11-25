// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:matrix/matrix.dart';

/// Account data event that stores the user's own ordering of spaces and of
/// the chats inside each space. Kept in account data so it follows the
/// account across devices and reinstalls.
///
/// Content:
/// ```json
/// {
///   "spaces": ["!spaceA", "!spaceB"],
///   "space_children": { "!spaceA": ["!room1", "!room2"] }
/// }
/// ```
const String customOrderAccountDataType = 'im.fluffychat.plus.custom_order';

extension CustomOrderExtension on Client {
  Map<String, Object?> get _customOrderContent =>
      accountData[customOrderAccountDataType]?.content ?? const {};

  /// Room IDs of spaces in the order the user arranged them.
  List<String> get customSpaceOrder =>
      _stringList(_customOrderContent['spaces']);

  /// Room IDs of the children of [spaceId] in the order the user arranged them.
  List<String> customChildOrder(String spaceId) {
    final children = _customOrderContent['space_children'];
    if (children is! Map) return const [];
    return _stringList(children[spaceId]);
  }

  /// Whether the user has arranged the children of [spaceId].
  bool hasCustomChildOrder(String spaceId) =>
      customChildOrder(spaceId).isNotEmpty;

  Future<void> setCustomSpaceOrder(List<String> order) =>
      _updateCustomOrder((content) => content['spaces'] = order);

  Future<void> setCustomChildOrder(String spaceId, List<String>? order) =>
      _updateCustomOrder((content) {
        final existing = content['space_children'];
        final children = existing is Map
            ? Map<String, Object?>.from(existing)
            : <String, Object?>{};
        if (order == null || order.isEmpty) {
          children.remove(spaceId);
        } else {
          children[spaceId] = order;
        }
        content['space_children'] = children;
      });

  Future<void> _updateCustomOrder(
    void Function(Map<String, Object?> content) update,
  ) async {
    final userId = userID;
    if (userId == null) return;
    final content = Map<String, Object?>.from(_customOrderContent);
    update(content);
    // Apply locally right away so the UI doesn't wait for the next sync.
    accountData[customOrderAccountDataType] = BasicEvent(
      type: customOrderAccountDataType,
      content: content,
    );
    await setAccountData(userId, customOrderAccountDataType, content);
  }
}

List<String> _stringList(Object? value) =>
    value is List ? value.whereType<String>().toList() : const [];

/// Sorts [items] by their position in [order]. Items missing from [order]
/// keep their original relative order and go after the arranged ones.
List<T> sortByCustomOrder<T>(
  List<T> items,
  List<String> order,
  String Function(T item) idOf,
) {
  if (order.isEmpty || items.length < 2) return items;
  final position = <String, int>{
    for (var i = 0; i < order.length; i++) order[i]: i,
  };
  final indexed = items.asMap().entries.toList();
  indexed.sort((a, b) {
    final pa = position[idOf(a.value)];
    final pb = position[idOf(b.value)];
    if (pa != null && pb != null) return pa.compareTo(pb);
    if (pa != null) return -1;
    if (pb != null) return 1;
    return a.key.compareTo(b.key);
  });
  return indexed.map((e) => e.value).toList();
}

/// Moves the item at [oldIndex] to [newIndex] the way
/// `ReorderableListView.onReorder` reports it.
List<T> moveItem<T>(List<T> items, int oldIndex, int newIndex) {
  final result = List<T>.from(items);
  if (newIndex > oldIndex) newIndex -= 1;
  final item = result.removeAt(oldIndex);
  result.insert(newIndex, item);
  return result;
}
