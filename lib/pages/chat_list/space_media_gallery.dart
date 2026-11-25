// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:convert';

import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/pages/image_viewer/image_viewer.dart';
import 'package:fluffychat/utils/chat_nickname_extension.dart';
import 'package:fluffychat/utils/instagram_reel.dart';
import 'package:fluffychat/utils/localized_exception_extension.dart';
import 'package:fluffychat/utils/matrix_sdk_extensions/matrix_locals.dart';
import 'package:fluffychat/utils/platform_infos.dart';
import 'package:fluffychat/widgets/matrix.dart';
import 'package:fluffychat/widgets/mxc_image.dart';
import 'package:fluffychat/widgets/space_accent_theme.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

/// Every photo, video and reel shared in the chats of a space, newest first.
class SpaceMediaGallery extends StatefulWidget {
  final String spaceId;

  const SpaceMediaGallery({required this.spaceId, super.key});

  @override
  State<SpaceMediaGallery> createState() => _SpaceMediaGalleryState();
}

class _SpaceMediaGalleryState extends State<SpaceMediaGallery> {
  static const int _pageSize = 40;
  static const Set<String> _mediaTypes = {
    MessageTypes.Image,
    MessageTypes.Sticker,
    MessageTypes.Video,
  };

  final List<Event> _items = [];
  final Set<String> _seen = {};
  final Map<String, String?> _nextTokens = {};
  final Set<String> _finishedRooms = {};
  bool _loading = false;
  Object? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadMore());
  }

  /// Joined chats of the space and of its sub-spaces.
  List<Room> _childRooms(Client client) {
    final result = <String, Room>{};
    void visit(Room space, int depth) {
      for (final child in space.spaceChildren) {
        final id = child.roomId;
        if (id == null) continue;
        final room = client.getRoomById(id);
        if (room == null || room.membership != Membership.join) continue;
        if (room.isSpace) {
          if (depth < 2) visit(room, depth + 1);
        } else {
          result[room.id] = room;
        }
      }
    }

    final space = client.getRoomById(widget.spaceId);
    if (space != null) visit(space, 0);
    return result.values.toList();
  }

  bool get _hasMore {
    final client = Matrix.of(context).client;
    return _childRooms(
      client,
    ).any((room) => !_finishedRooms.contains(room.id));
  }

  Future<void> _loadMore() async {
    if (_loading || !mounted) return;
    final client = Matrix.of(context).client;
    final rooms = _childRooms(
      client,
    ).where((room) => !_finishedRooms.contains(room.id)).toList();
    if (rooms.isEmpty) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      // Pages can come back without media (encrypted chats, text-only
      // chats), so keep going a few rounds until something shows up.
      for (var round = 0; round < 5; round++) {
        final pending = rooms
            .where((room) => !_finishedRooms.contains(room.id))
            .toList();
        if (pending.isEmpty) break;
        final batches = await Future.wait(pending.map(_loadRoom));
        if (!mounted) return;
        var added = 0;
        for (final batch in batches) {
          for (final event in batch) {
            if (_seen.add(event.eventId)) {
              _items.add(event);
              added++;
            }
          }
        }
        if (added > 0) break;
      }
      _items.sort((a, b) => b.originServerTs.compareTo(a.originServerTs));
    } catch (e, s) {
      Logs().w('Unable to load space media', e, s);
      _error = e;
    }
    if (!mounted) return;
    setState(() => _loading = false);
  }

  /// One page of media from [room], going back in time.
  Future<List<Event>> _loadRoom(Room room) async {
    final client = room.client;
    // Encrypted media can't be filtered on the server, so encrypted chats
    // fetch everything and filter after decrypting.
    final filter = room.encrypted
        ? {
            'types': [EventTypes.Encrypted, EventTypes.Message],
            'lazy_load_members': true,
          }
        : {
            'types': [EventTypes.Message, EventTypes.Sticker],
            'contains_url': true,
            'lazy_load_members': true,
          };
    try {
      final response = await client.getRoomEvents(
        room.id,
        Direction.b,
        from: _nextTokens[room.id],
        limit: _pageSize,
        filter: jsonEncode(filter),
      );
      _nextTokens[room.id] = response.end;
      if (response.end == null || response.chunk.isEmpty) {
        _finishedRooms.add(room.id);
      }
      final result = <Event>[];
      for (final matrixEvent in response.chunk) {
        var event = Event.fromMatrixEvent(matrixEvent, room);
        final encryption = client.encryption;
        if (event.type == EventTypes.Encrypted && encryption != null) {
          try {
            event = await encryption.decryptRoomEvent(event);
          } catch (_) {
            continue;
          }
        }
        if (event.redacted) continue;
        if (event.relationshipType == RelationshipTypes.edit) continue;
        if (!_mediaTypes.contains(event.messageType)) continue;
        result.add(event);
      }
      return result;
    } catch (e, s) {
      Logs().w('Unable to load media of ${room.id}', e, s);
      _finishedRooms.add(room.id);
      return const [];
    }
  }

  void _open(Event event) {
    final playable = _items
        .where(
          (item) =>
              item.messageType != MessageTypes.Video ||
              PlatformInfos.supportsVideoPlayer,
        )
        .toList()
        .reversed
        .toList();
    showDialog(
      context: context,
      builder: (_) => ImageViewer(
        event,
        events: playable,
        outerContext: context,
      ),
    );
  }

  void _goToMessage(Event event) {
    final router = GoRouter.of(context);
    Navigator.of(context).pop();
    router.go(
      '/rooms/${event.room.id}?event=${Uri.encodeQueryComponent(event.eventId)}',
    );
  }

  @override
  Widget build(BuildContext context) => SpaceAccentTheme(
    spaceId: widget.spaceId,
    child: Builder(builder: _buildView),
  );

  Widget _buildView(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final client = Matrix.of(context).client;
    final space = client.getRoomById(widget.spaceId);
    final hasMore = _hasMore;

    return Scaffold(
      appBar: AppBar(
        title: Text(
          space == null
              ? l10n.spaceMedia
              : '${l10n.spaceMedia} · ${space.getNicknameOrDisplayname(MatrixLocals(l10n))}',
        ),
      ),
      body: _items.isEmpty
          ? Center(
              child: _loading
                  ? const CircularProgressIndicator.adaptive()
                  : Padding(
                      padding: const EdgeInsets.all(32),
                      child: Column(
                        mainAxisSize: .min,
                        children: [
                          Icon(
                            Icons.photo_library_outlined,
                            size: 64,
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                          const SizedBox(height: 12),
                          Text(
                            _error == null
                                ? l10n.noSpaceMedia
                                : _error!.toLocalizedString(context),
                            textAlign: TextAlign.center,
                          ),
                          if (_error != null || hasMore)
                            TextButton(
                              onPressed: _loadMore,
                              child: Text(l10n.loadMore),
                            ),
                        ],
                      ),
                    ),
            )
          : GridView.builder(
              padding: const EdgeInsets.all(2),
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 160,
                mainAxisSpacing: 2,
                crossAxisSpacing: 2,
              ),
              itemCount: _items.length + (hasMore ? 1 : 0),
              itemBuilder: (context, i) {
                if (i >= _items.length) {
                  if (!_loading) {
                    WidgetsBinding.instance.addPostFrameCallback(
                      (_) => _loadMore(),
                    );
                  }
                  return const Center(
                    child: CircularProgressIndicator.adaptive(),
                  );
                }
                final event = _items[i];
                return _MediaTile(
                  event: event,
                  onTap: () => _open(event),
                  onLongPress: () => _goToMessage(event),
                );
              },
            ),
    );
  }
}

class _MediaTile extends StatelessWidget {
  final Event event;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  const _MediaTile({
    required this.event,
    required this.onTap,
    required this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isVideo = event.messageType == MessageTypes.Video;
    final showImage = !isVideo || event.hasThumbnail;
    return Material(
      color: theme.colorScheme.surfaceContainerHigh,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (showImage)
              MxcImage(
                key: ValueKey(event.eventId),
                event: event,
                isThumbnail: true,
                fit: BoxFit.cover,
                cacheKey: event.eventId,
                cacheName: event.room.id,
              ),
            if (isVideo)
              Positioned(
                top: 6,
                right: 6,
                child: Icon(
                  event.isInstagramReel
                      ? Icons.movie_outlined
                      : Icons.play_circle_outline,
                  color: Colors.white,
                  size: 20,
                  shadows: const [Shadow(blurRadius: 6)],
                ),
              ),
          ],
        ),
      ),
    );
  }
}
