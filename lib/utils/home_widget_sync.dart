// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:async';
import 'dart:convert';

import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/utils/matrix_sdk_extensions/matrix_locals.dart';
import 'package:fluffychat/utils/platform_infos.dart';
import 'package:flutter/services.dart';
import 'package:matrix/matrix.dart';

/// Feeds the FluffyChat+ chat widgets on the Android home screen
/// (`ChatWidget.kt`). Each widget shows the recent messages of one chat the
/// user picks there. This side sends the chat list for the picker and the
/// messages of every chat that has a widget.
abstract class HomeWidgetSync {
  static const MethodChannel _channel = MethodChannel(
    'im.fluffychat.plus/widgets',
  );
  static const int _maxMessages = 20;
  static const Set<String> _messageTypes = {
    EventTypes.Message,
    EventTypes.Encrypted,
    EventTypes.Sticker,
  };

  static bool get _supported => PlatformInfos.isAndroid;

  static final Set<String> _pendingRooms = {};
  static bool _fullRefresh = true;
  static bool _roomListChanged = true;
  static DateTime _lastRoomList = DateTime(0);
  static Timer? _timer;
  static List<Client> _clients = const [];
  static L10n? _l10n;

  /// Call for every sync. Batches the changes and updates the widgets of the
  /// chats that changed.
  static void onSync(SyncUpdate update, List<Client> clients, L10n l10n) {
    if (!_supported) return;
    final rooms = update.rooms;
    final join = rooms?.join;
    if (join != null && join.isNotEmpty) {
      _pendingRooms.addAll(join.keys);
      _roomListChanged = true;
    }
    if ((rooms?.leave?.isNotEmpty ?? false) ||
        (rooms?.invite?.isNotEmpty ?? false)) {
      _roomListChanged = true;
    }
    if (_pendingRooms.isEmpty && !_roomListChanged && !_fullRefresh) return;
    _schedule(clients, l10n);
  }

  /// Updates the chat list and every widget, for example when the app comes
  /// back to the foreground.
  static void requestFullRefresh(List<Client> clients, L10n l10n) {
    if (!_supported) return;
    _fullRefresh = true;
    _roomListChanged = true;
    _schedule(clients, l10n);
  }

  /// Updates everything right away, for example before the app goes to the
  /// background and stops syncing.
  static Future<void> refreshNow(List<Client> clients, L10n l10n) async {
    if (!_supported) return;
    _fullRefresh = true;
    _roomListChanged = true;
    _clients = clients;
    _l10n = l10n;
    _timer?.cancel();
    await _flush();
  }

  static void _schedule(List<Client> clients, L10n l10n) {
    _clients = clients;
    _l10n = l10n;
    _timer ??= Timer(const Duration(seconds: 2), _flush);
  }

  static Future<void> _flush() async {
    _timer = null;
    final l10n = _l10n;
    if (l10n == null) return;
    final pending = Set<String>.from(_pendingRooms);
    _pendingRooms.clear();
    final full = _fullRefresh;
    _fullRefresh = false;
    final now = DateTime.now();
    final pushList =
        full ||
        (_roomListChanged &&
            now.difference(_lastRoomList) > const Duration(seconds: 30));
    try {
      if (pushList) {
        _roomListChanged = false;
        _lastRoomList = now;
        await _pushRoomList(_clients, l10n);
      }
      final configured = await _configuredRooms();
      final toUpdate = full
          ? configured
          : configured.where(pending.contains).toList();
      for (final roomId in toUpdate) {
        final room = _findRoom(_clients, roomId);
        if (room == null) continue;
        await _setRoomData(roomId, await _roomData(room, l10n));
      }
      if (pushList || toUpdate.isNotEmpty) {
        await _channel.invokeMethod('refresh');
      }
    } catch (e, s) {
      Logs().w('Unable to update home screen widgets', e, s);
    }
  }

  /// Adds a message that arrived by push to its chat's widget, if it has one.
  /// The app isn't syncing then, so this is how widgets stay current.
  static Future<void> onPushEvent(Event event, L10n l10n) async {
    if (!_supported) return;
    if (!_messageTypes.contains(event.type)) return;
    if (event.relationshipType == RelationshipTypes.edit) return;
    try {
      final room = event.room;
      final configured = await _configuredRooms();
      if (!configured.contains(room.id)) return;

      final data = await _storedRoomData(room.id) ??
          <String, Object?>{
            'name': room.getLocalizedDisplayname(MatrixLocals(l10n)),
            'unread': 0,
            'highlight': 0,
            'messages': <Object?>[],
          };
      final messages = List<Object?>.from(
        data['messages'] is List ? data['messages'] as List : const [],
      );
      if (messages.any(
        (message) => message is Map && message['id'] == event.eventId,
      )) {
        return;
      }
      messages.add(_message(event, l10n));
      while (messages.length > _maxMessages) {
        messages.removeAt(0);
      }
      data['messages'] = messages;

      if (event.senderId == room.client.userID) {
        // Sending a message marks the chat as read.
        data['unread'] = 0;
        data['highlight'] = 0;
      } else {
        data['unread'] = _intOf(data['unread']) + 1;
        if (room.client.pushruleEvaluator.match(event).highlight) {
          data['highlight'] = _intOf(data['highlight']) + 1;
        }
      }
      await _setRoomData(room.id, data);
      await _channel.invokeMethod('refresh');
    } catch (e, s) {
      Logs().w('Unable to update home screen widget from push', e, s);
    }
  }

  /// A push said nothing is unread anymore, so clear every widget's badge.
  static Future<void> onAllRead() async {
    if (!_supported) return;
    try {
      final configured = await _configuredRooms();
      var changed = false;
      for (final roomId in configured) {
        final data = await _storedRoomData(roomId);
        if (data == null) continue;
        if (_intOf(data['unread']) == 0 && _intOf(data['highlight']) == 0) {
          continue;
        }
        data['unread'] = 0;
        data['highlight'] = 0;
        await _setRoomData(roomId, data);
        changed = true;
      }
      if (changed) await _channel.invokeMethod('refresh');
    } catch (e, s) {
      Logs().w('Unable to clear home screen widget badges', e, s);
    }
  }

  static int _intOf(Object? value) => value is num ? value.toInt() : 0;

  static Future<List<String>> _configuredRooms() async =>
      await _channel.invokeListMethod<String>('configuredRooms') ?? const [];

  static Future<Map<String, Object?>?> _storedRoomData(String roomId) async {
    final raw = await _channel.invokeMethod<String>('getRoomData', roomId);
    if (raw == null) return null;
    try {
      final decoded = jsonDecode(raw);
      return decoded is Map ? Map<String, Object?>.from(decoded) : null;
    } catch (_) {
      return null;
    }
  }

  static Future<void> _setRoomData(String roomId, Map<String, Object?> data) =>
      _channel.invokeMethod('setRoomData', {
        'roomId': roomId,
        'data': jsonEncode(data),
      });

  static Room? _findRoom(List<Client> clients, String roomId) {
    for (final client in clients) {
      final room = client.getRoomById(roomId);
      if (room != null && room.membership == Membership.join) return room;
    }
    return null;
  }

  static Map<String, Object?> _message(
    Event event,
    L10n l10n, {
    Timeline? timeline,
  }) {
    final displayEvent = timeline == null
        ? event
        : event.getDisplayEvent(timeline);
    return {
      'id': event.eventId,
      'sender': event.senderId == event.room.client.userID
          ? l10n.you
          : event.senderFromMemoryOrFallback.calcDisplayname(),
      'body': displayEvent.calcLocalizedBody(
        MatrixLocals(l10n),
        hideReply: true,
        hideEdit: true,
        plaintextBody: true,
        removeMarkdown: true,
      ),
      'time': event.originServerTs.millisecondsSinceEpoch,
    };
  }

  static Future<Map<String, Object?>> _roomData(Room room, L10n l10n) async {
    final messages = <Map<String, Object?>>[];
    Timeline? timeline;
    try {
      timeline = await room.getTimeline(limit: 40);
      // Newest first.
      for (final event in timeline.events) {
        if (!_messageTypes.contains(event.type)) continue;
        if (event.redacted) continue;
        if (event.relationshipType == RelationshipTypes.edit) continue;
        messages.add(_message(event, l10n, timeline: timeline));
        if (messages.length >= _maxMessages) break;
      }
    } catch (e, s) {
      Logs().w('Unable to load messages for home screen widget', e, s);
    } finally {
      timeline?.cancelSubscriptions();
    }
    return {
      'name': room.getLocalizedDisplayname(MatrixLocals(l10n)),
      'unread': room.notificationCount,
      'highlight': room.highlightCount,
      'messages': messages.reversed.toList(),
    };
  }

  static Future<void> _pushRoomList(List<Client> clients, L10n l10n) async {
    final locals = MatrixLocals(l10n);
    final rooms = <Room>[];
    final spaceNames = <String, List<String>>{};
    for (final client in clients) {
      for (final room in client.rooms) {
        if (room.membership != Membership.join) continue;
        if (!room.isSpace) {
          rooms.add(room);
          continue;
        }
        final name = room.getLocalizedDisplayname(locals);
        for (final child in room.spaceChildren) {
          final childId = child.roomId;
          if (childId == null) continue;
          (spaceNames[childId] ??= []).add(name);
        }
      }
    }
    rooms.sort(
      (a, b) => b.latestEventReceivedTime.compareTo(a.latestEventReceivedTime),
    );
    final entries = <Map<String, Object?>>[];
    for (final room in rooms) {
      final spaces = spaceNames[room.id];
      final lastEvent = room.lastEvent;
      entries.add({
        'id': room.id,
        'name': room.getLocalizedDisplayname(locals),
        'sub': spaces != null
            ? spaces.join(', ')
            : room.isDirectChat
            ? l10n.directChat
            : '',
        'unread': room.notificationCount,
        'highlight': room.highlightCount,
        if (lastEvent != null) 'last': _message(lastEvent, l10n),
      });
    }
    await _channel.invokeMethod('setRooms', jsonEncode(entries));
  }
}
