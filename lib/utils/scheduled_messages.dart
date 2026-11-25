// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:convert';

import 'package:fluffychat/config/setting_keys.dart';
import 'package:matrix/matrix.dart';

/// Scheduled messages use the homeserver's delayed events (MSC4140): the
/// server holds the message and sends it at the chosen time, so the phone
/// can be off. Synapse only offers this when `max_event_delay_duration` is
/// set in homeserver.yaml.
const String _delayParam = 'org.matrix.msc4140.delay';
const String _feature = 'org.matrix.msc4140';
const String _delayedEventsPath =
    '/client/unstable/org.matrix.msc4140/delayed_events';

/// The homeserver doesn't have delayed events turned on.
class ScheduledMessagesUnsupported implements Exception {
  const ScheduledMessagesUnsupported();
}

/// The time is further out than the homeserver allows.
class ScheduledMessageTooFar implements Exception {
  final Duration maxDelay;

  const ScheduledMessageTooFar(this.maxDelay);
}

class ScheduledMessage {
  final String delayId;
  final String roomId;
  final String body;
  final DateTime sendAt;

  const ScheduledMessage({
    required this.delayId,
    required this.roomId,
    required this.body,
    required this.sendAt,
  });

  Map<String, Object?> toJson() => {
    'delay_id': delayId,
    'room_id': roomId,
    'body': body,
    'send_at': sendAt.millisecondsSinceEpoch,
  };

  static ScheduledMessage? fromJson(Object? json) {
    if (json is! Map) return null;
    final delayId = json['delay_id'];
    final roomId = json['room_id'];
    final body = json['body'];
    final sendAt = json['send_at'];
    if (delayId is! String || roomId is! String || sendAt is! num) {
      return null;
    }
    return ScheduledMessage(
      delayId: delayId,
      roomId: roomId,
      body: body is String ? body : '',
      sendAt: DateTime.fromMillisecondsSinceEpoch(sendAt.toInt()),
    );
  }
}

extension ScheduledMessagesExtension on Client {
  // The server keeps encrypted messages as ciphertext, so the readable text
  // is kept on this device.
  String get _storeKey => 'im.fluffychat.plus.scheduled.$clientName';

  List<ScheduledMessage> _loadScheduled() {
    try {
      final raw = AppSettings.store.getString(_storeKey);
      if (raw == null) return [];
      final list = jsonDecode(raw);
      if (list is! List) return [];
      return list.map(ScheduledMessage.fromJson).whereType<ScheduledMessage>().toList();
    } catch (_) {
      return [];
    }
  }

  Future<void> _saveScheduled(List<ScheduledMessage> messages) =>
      AppSettings.store.setString(
        _storeKey,
        jsonEncode(messages.map((m) => m.toJson()).toList()),
      );

  /// Messages waiting to be sent, soonest first. Ones whose time has passed
  /// are left out: the server sent them.
  List<ScheduledMessage> scheduledMessages([String? roomId]) {
    final cutoff = DateTime.now().subtract(const Duration(minutes: 1));
    return _loadScheduled()
        .where(
          (m) => (roomId == null || m.roomId == roomId) && m.sendAt.isAfter(cutoff),
        )
        .toList()
      ..sort((a, b) => a.sendAt.compareTo(b.sendAt));
  }

  Future<bool> supportsScheduledMessages() async {
    final versions = await getVersions(
      cacheLifetime: const Duration(minutes: 1),
    );
    return versions.unstableFeatures?[_feature] == true;
  }

  /// Hands [text] to the homeserver to send in [room] at [sendAt].
  Future<ScheduledMessage> scheduleMessage(
    Room room,
    String text,
    DateTime sendAt,
  ) async {
    // Without delayed events turned on, the server would ignore the delay
    // and send the message right away.
    if (!await supportsScheduledMessages()) {
      throw const ScheduledMessagesUnsupported();
    }
    final delay = sendAt.difference(DateTime.now());
    final content = <String, Object?>{'msgtype': MessageTypes.Text, 'body': text};
    final mustEncrypt = room.encrypted && encryptionEnabled;
    final payload = mustEncrypt
        ? await encryption!.encryptGroupMessagePayload(room.id, content)
        : content;
    final type = payload.containsKey('ciphertext')
        ? EventTypes.Encrypted
        : EventTypes.Message;
    final txnId = generateUniqueTransactionId();

    final Map<String, Object?> response;
    try {
      response = await request(
        RequestType.PUT,
        '/client/v3/rooms/${Uri.encodeComponent(room.id)}/send/${Uri.encodeComponent(type)}/${Uri.encodeComponent(txnId)}',
        data: payload,
        query: {_delayParam: '${delay.inMilliseconds.clamp(0, 1 << 52)}'},
      );
    } on MatrixException catch (e) {
      if (e.errcode == 'M_MAX_DELAY_EXCEEDED') {
        final max = e.raw['max_delay'];
        throw ScheduledMessageTooFar(
          Duration(milliseconds: max is num ? max.toInt() : 0),
        );
      }
      rethrow;
    }
    final delayId = response['delay_id'];
    if (delayId is! String) {
      // The server sent it straight away after all.
      throw const ScheduledMessagesUnsupported();
    }
    final message = ScheduledMessage(
      delayId: delayId,
      roomId: room.id,
      body: text,
      sendAt: sendAt,
    );
    await _saveScheduled([..._loadScheduled(), message]);
    return message;
  }

  /// Sends a scheduled message now, or cancels it.
  Future<void> updateScheduledMessage(
    ScheduledMessage message, {
    required bool sendNow,
  }) async {
    final action = sendNow ? 'send' : 'cancel';
    final path = '$_delayedEventsPath/${Uri.encodeComponent(message.delayId)}';
    try {
      await request(RequestType.POST, path, data: {'action': action});
    } on MatrixException catch (e) {
      if (e.errcode == 'M_NOT_FOUND') {
        // Already sent or cancelled somewhere else.
      } else if (e.errcode == 'M_UNRECOGNIZED') {
        // Newer servers use a path per action instead.
        await request(RequestType.POST, '$path/$action', data: {});
      } else {
        rethrow;
      }
    }
    await _saveScheduled(
      _loadScheduled().where((m) => m.delayId != message.delayId).toList(),
    );
  }

  /// Drops scheduled messages the server no longer holds.
  Future<List<ScheduledMessage>> refreshScheduledMessages([
    String? roomId,
  ]) async {
    try {
      final response = await request(RequestType.GET, _delayedEventsPath);
      final events = response['delayed_events'];
      if (events is List) {
        final onServer = events
            .whereType<Map>()
            .map((e) => e['delay_id'])
            .whereType<String>()
            .toSet();
        await _saveScheduled(
          _loadScheduled().where((m) => onServer.contains(m.delayId)).toList(),
        );
      }
    } catch (e, s) {
      Logs().w('Unable to refresh scheduled messages', e, s);
    }
    return scheduledMessages(roomId);
  }
}
