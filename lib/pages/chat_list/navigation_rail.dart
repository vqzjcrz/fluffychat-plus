// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/app_config.dart';
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

  const SpacesNavigationRail({
    required this.activeSpaceId,
    required this.onGoToChats,
    required this.onGoToSpaceId,
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
    final coloredMode = !FluffyThemes.isColumnMode(context);
    final theme = Theme.of(context);
    return Material(
      color: coloredMode ? theme.colorScheme.surfaceContainer : null,
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
              width: FluffyThemes.isColumnMode(context)
                  ? FluffyThemes.navRailWidth
                  : FluffyThemes.navRailWidth - 8,
              child: Column(
                children: [
                  Expanded(
                    child: ReorderableListView.builder(
                      padding: EdgeInsets.symmetric(vertical: 4),
                      scrollDirection: Axis.vertical,
                      buildDefaultDragHandles: false,
                      header: NaviRailItem(
                        isSelected: widget.activeSpaceId == null,
                        onTap: widget.onGoToChats,
                        icon: const Padding(
                          padding: EdgeInsets.all(12.0),
                          child: Icon(Icons.forum_outlined),
                        ),
                        selectedIcon: const Padding(
                          padding: EdgeInsets.all(12.0),
                          child: Icon(Icons.forum),
                        ),
                        toolTip: L10n.of(context).chats,
                        unreadBadgeFilter: (room) => true,
                      ),
                      footer: NaviRailItem(
                        isSelected: false,
                        onTap: () => context.go('/rooms/newspace'),
                        icon: const Padding(
                          padding: EdgeInsets.all(12.0),
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
                        // Long-press (or click and hold) a space to drag it.
                        return ReorderableDelayedDragStartListener(
                          key: ValueKey(space.id),
                          index: i,
                          child: NaviRailItem(
                            toolTip: displayname,
                            tooltipOnLongPress: false,
                            isSelected: widget.activeSpaceId == space.id,
                            onTap: () => widget.onGoToSpaceId(space.id),
                            unreadBadgeFilter: (room) =>
                                spaceChildrenIds.contains(room.id),
                            icon: Avatar(
                              mxContent: space.avatar,
                              name: displayname,
                              //size: 36,
                              shapeBorder: RoundedSuperellipseBorder(
                                side: BorderSide(
                                  width: 1,
                                  color: Theme.of(context).dividerColor,
                                ),
                                borderRadius: BorderRadius.circular(
                                  AppConfig.spaceBorderRadius,
                                ),
                              ),
                              borderRadius: BorderRadius.circular(
                                AppConfig.spaceBorderRadius,
                              ),
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                  if (FluffyThemes.isColumnMode(context))
                    Padding(
                      padding: const EdgeInsets.all(12.0),
                      child: StartChatFab(),
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
