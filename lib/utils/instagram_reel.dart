// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:matrix/matrix.dart';

final RegExp _reelUrl = RegExp(
  r'instagram\.com/(?:[^/?#]+/)?reels?/',
  caseSensitive: false,
);
final RegExp _postUrl = RegExp(
  r'instagram\.com/(?:[^/?#]+/)?p/',
  caseSensitive: false,
);
// First line of a reel the reels hub reposted: "🎞 Sender · Chat".
final RegExp _hubHeader = RegExp(r'^\u{1F39E}️?\s*', unicode: true);
final RegExp _matrixToLink = RegExp(r'https://matrix\.to/#/[^"\s<]+');

extension InstagramReelExtension on Event {
  /// A video that came from Instagram as a reel (or a video post), either
  /// bridged straight into a chat or reposted by the reels hub.
  bool get isInstagramReel {
    if (type != EventTypes.Message || redacted) return false;
    if (messageType != MessageTypes.Video) return false;
    final url = content.tryGet<String>('external_url');
    if (url == null) return false;
    return _reelUrl.hasMatch(url) || _postUrl.hasMatch(url);
  }
}

/// What the reels UI shows for one reel.
class InstagramReel {
  /// The event as it arrived. Reactions and replies go to this one.
  final Event event;

  /// The event with its latest edit applied. The video comes from this one.
  final Event displayEvent;

  /// Who sent the reel.
  final String author;

  /// Avatar of whoever sent it, when known.
  final Uri? authorAvatar;

  /// The chat a reposted reel came from.
  final String? source;

  final String caption;

  /// The reel on instagram.com.
  final String? url;

  /// matrix.to link to the original message of a reposted reel.
  final String? originalLink;

  const InstagramReel({
    required this.event,
    required this.displayEvent,
    required this.author,
    required this.authorAvatar,
    required this.source,
    required this.caption,
    required this.url,
    required this.originalLink,
  });

  static InstagramReel? fromEvent(Event event, {Timeline? timeline}) {
    final displayEvent = timeline == null
        ? event
        : event.getDisplayEvent(timeline);
    if (!displayEvent.isInstagramReel) return null;
    final url = displayEvent.content.tryGet<String>('external_url');

    // The plain body, not the rendered HTML, so the link line can be
    // recognized and dropped.
    final body = displayEvent.content.tryGet<String>('body') ?? '';
    final filename = displayEvent.content.tryGet<String>('filename');
    final hasCaption = body.isNotEmpty && filename != null && filename != body;
    var lines = hasCaption ? body.split('\n') : <String>[];
    String author;
    String? source;
    Uri? authorAvatar;
    String? originalLink;
    if (lines.isNotEmpty && _hubHeader.hasMatch(lines.first)) {
      final header = lines.first.replaceFirst(_hubHeader, '').trim();
      final separator = header.lastIndexOf(' · ');
      if (separator > 0) {
        author = header.substring(0, separator);
        source = header.substring(separator + 3);
      } else {
        author = header;
      }
      lines = lines.sublist(1);
      final formatted = displayEvent.content.tryGet<String>('formatted_body');
      originalLink = formatted == null
          ? null
          : _matrixToLink.firstMatch(formatted)?.group(0);
    } else {
      final sender = event.senderFromMemoryOrFallback;
      author = sender.calcDisplayname();
      authorAvatar = sender.avatarUrl;
    }
    final caption = lines
        .where((line) => line.trim() != url)
        .join('\n')
        .trim();

    return InstagramReel(
      event: event,
      displayEvent: displayEvent,
      author: author,
      authorAvatar: authorAvatar,
      source: source == 'DM' ? null : source,
      caption: caption,
      url: url,
      originalLink: originalLink,
    );
  }

  /// All reels in [timeline], newest first, like a feed.
  static List<InstagramReel> fromTimeline(Timeline timeline) => timeline.events
      .where(
        (event) =>
            event.relationshipType != RelationshipTypes.edit &&
            event.type == EventTypes.Message,
      )
      .map((event) => fromEvent(event, timeline: timeline))
      .whereType<InstagramReel>()
      .toList();

  /// Whether this reel is [event], which may be the original or its edit.
  bool matches(Event other) =>
      other.eventId == event.eventId || other.eventId == displayEvent.eventId;

  /// The user's ❤️ on this reel, if they liked it.
  Event? ownLike(Timeline? timeline) {
    if (timeline == null) return null;
    final userId = event.room.client.userID;
    for (final reaction in event.aggregatedEvents(
      timeline,
      RelationshipTypes.reaction,
    )) {
      if (reaction.senderId != userId) continue;
      final key = reaction.content
          .tryGetMap<String, Object?>('m.relates_to')
          ?.tryGet<String>('key');
      if (key != null && isLikeKey(key)) return reaction;
    }
    return null;
  }

  /// How many reactions the reel has.
  int reactionCount(Timeline? timeline) => timeline == null
      ? 0
      : event.aggregatedEvents(timeline, RelationshipTypes.reaction).length;

  static const String likeKey = '❤️';

  static bool isLikeKey(String key) => key == likeKey || key == '❤';
}
