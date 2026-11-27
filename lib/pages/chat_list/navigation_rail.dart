// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/plus_colors.dart';
import 'package:fluffychat/config/themes.dart';
import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/pages/chat_list/navi_rail_item.dart';
import 'package:fluffychat/pages/chat_list/start_chat_fab.dart';
import 'package:fluffychat/utils/chat_nickname_extension.dart';
import 'package:fluffychat/utils/custom_order_extension.dart';
import 'package:fluffychat/utils/matrix_sdk_extensions/matrix_locals.dart';
import 'package:fluffychat/utils/stream_extension.dart';
import 'package:fluffychat/widgets/avatar.dart';
import 'package:fluffychat/widgets/matrix.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

class SpacesNavigationRail extends StatefulWidget {
  final String? activeSpaceId;
  final void Function() onGoToChats;
  final void Function(String) onGoToSpaceId;

  /// Shown at the bottom of the rail: your avatar, which opens the account
  /// and settings menu. Without one, the avatar opens the settings.
  final Widget? accountButton;

  const SpacesNavigationRail({
    required this.activeSpaceId,
    required this.onGoToChats,
    required this.onGoToSpaceId,
    this.accountButton,
    super.key,
  });

  @override
  State<SpacesNavigationRail> createState() => _SpacesNavigationRailState();
}

class _SpacesNavigationRailState extends State<SpacesNavigationRail> {
  Future<void> _onReorder(
    Client client,
    List<Room> spaces,
    int oldIndex,
    int newIndex,
  ) async {
    final newOrder = moveItem(
      spaces,
      oldIndex,
      newIndex,
    ).map((space) => space.id).toList();
    final saving = client.setCustomSpaceOrder(newOrder);
    // The new order is already applied locally, rebuild right away.
    setState(() {});
    try {
      await saving;
    } catch (e, s) {
      Logs().w('Unable to save space order', e, s);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  @override
  Widget build(BuildContext context) {
    final client = Matrix.of(context).client;
    final plus = PlusColors.of(context);
    final columnMode = FluffyThemes.isColumnMode(context);
    return Material(
      color: plus.background,
      child: SafeArea(
        child: StreamBuilder(
          key: ValueKey(client.userID.toString()),
          stream: client.onSync.stream
              .where(
                (s) =>
                    s.hasRoomUpdate ||
                    (s.accountData?.any(
                          (e) => e.type == customOrderAccountDataType,
                        ) ??
                        false),
              )
              .rateLimit(const Duration(seconds: 1)),
          builder: (context, _) {
            final allSpaces = sortByCustomOrder(
              client.rooms
                  .where((room) => room.isSpace && room.membership == .join)
                  .toList(),
              client.customSpaceOrder,
              (room) => room.id,
            );

            return SizedBox(
              width: columnMode ? FluffyThemes.navRailWidth : 68,
              child: Column(
                children: [
                  Expanded(
                    child: ReorderableListView.builder(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      scrollDirection: Axis.vertical,
                      buildDefaultDragHandles: false,
                      header: NaviRailItem(
                        isSelected: widget.activeSpaceId == null,
                        onTap: widget.onGoToChats,
                        icon: const RailCircle(
                          child: Icon(Icons.forum_outlined),
                        ),
                        selectedIcon: const RailCircle(
                          selected: true,
                          child: Icon(Icons.forum),
                        ),
                        toolTip: L10n.of(context).chats,
                        unreadBadgeFilter: (room) => true,
                      ),
                      footer: NaviRailItem(
                        isSelected: false,
                        onTap: () => context.go('/rooms/newspace'),
                        icon: const RailCircle(
                          dashed: true,
                          child: Icon(Icons.add),
                        ),
                        toolTip: L10n.of(context).createNewSpace,
                      ),
                      proxyDecorator: (child, index, animation) =>
                          Material(color: Colors.transparent, child: child),
                      onReorder: (oldIndex, newIndex) =>
                          _onReorder(client, allSpaces, oldIndex, newIndex),
                      itemCount: allSpaces.length,
                      itemBuilder: (context, i) {
                        final space = allSpaces[i];
                        final displayname = space.getNicknameOrDisplayname(
                          MatrixLocals(L10n.of(context)),
                        );
                        final spaceChildrenIds = space.spaceChildren
                            .map((c) => c.roomId)
                            .toSet();
                        final selected = widget.activeSpaceId == space.id;
                        // Long-press (or click and hold) a space to drag it.
                        return ReorderableDelayedDragStartListener(
                          key: ValueKey(space.id),
                          index: i,
                          child: NaviRailItem(
                            toolTip: displayname,
                            tooltipOnLongPress: false,
                            isSelected: selected,
                            onTap: () => widget.onGoToSpaceId(space.id),
                            unreadBadgeFilter: (room) =>
                                spaceChildrenIds.contains(room.id),
                            icon: RailSpaceAvatar(
                              selected: selected,
                              avatar: Avatar(
                                mxContent: space.avatar,
                                name: displayname,
                                size: 40,
                              ),
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                  if (columnMode)
                    Padding(
                      padding: const EdgeInsets.only(top: 8, bottom: 4),
                      child: StartChatFab(),
                    ),
                  Padding(
                    padding: const EdgeInsets.only(top: 8, bottom: 16),
                    child: widget.accountButton ?? const RailSettingsAvatar(),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

/// Your avatar with a small gear on it, at the bottom of the rail.
class RailAvatar extends StatelessWidget {
  final Uri? avatarUrl;
  final String? name;

  const RailAvatar({this.avatarUrl, this.name, super.key});

  @override
  Widget build(BuildContext context) {
    final plus = PlusColors.of(context);
    return SizedBox.square(
      dimension: 48,
      child: Stack(
        clipBehavior: Clip.none,
        alignment: Alignment.center,
        children: [
          Avatar(mxContent: avatarUrl, name: name, size: 42),
          Positioned(
            right: -1,
            bottom: -1,
            child: Container(
              width: 20,
              height: 20,
              decoration: BoxDecoration(
                color: plus.raised,
                shape: BoxShape.circle,
                border: Border.all(color: plus.background, width: 2),
              ),
              child: Icon(Icons.settings, size: 11, color: plus.text),
            ),
          ),
        ],
      ),
    );
  }
}

/// [RailAvatar] that opens the settings, for rails without the account menu.
class RailSettingsAvatar extends StatelessWidget {
  const RailSettingsAvatar({super.key});

  @override
  Widget build(BuildContext context) {
    final client = Matrix.of(context).client;
    return FutureBuilder<Profile>(
      future: client.isLogged() ? client.fetchOwnProfile() : null,
      builder: (context, snapshot) => Tooltip(
        message: L10n.of(context).settings,
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: () => context.go('/rooms/settings'),
          child: RailAvatar(
            avatarUrl: snapshot.data?.avatarUrl,
            name: snapshot.data?.displayName ?? client.userID?.localpart,
          ),
        ),
      ),
    );
  }
}
