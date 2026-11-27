// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/plus_colors.dart';
import 'package:fluffychat/config/setting_keys.dart';
import 'package:fluffychat/config/themes.dart';
import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/pages/chat_list/chat_list.dart';
import 'package:fluffychat/pages/chat_list/client_chooser_button.dart';
import 'package:fluffychat/utils/sync_status_localization.dart';
import 'package:fluffychat/widgets/chat_selection_bar.dart';
import 'package:fluffychat/widgets/plus_circle_button.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

import '../../widgets/matrix.dart';

class ChatListHeader extends StatelessWidget implements PreferredSizeWidget {
  final ChatListController controller;
  final bool globalSearch;

  const ChatListHeader({
    super.key,
    required this.controller,
    this.globalSearch = true,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final client = Matrix.of(context).client;
    final isColumnMode = FluffyThemes.isColumnMode(context);

    if (controller.selectMode) {
      // FluffyChat+: bulk actions for the selected chats.
      return SliverAppBar(
        floating: true,
        pinned: true,
        toolbarHeight: 72,
        scrolledUnderElevation: 0,
        backgroundColor: isColumnMode
            ? theme.colorScheme.surface.withAlpha(240)
            : theme.colorScheme.surface,
        automaticallyImplyLeading: false,
        title: ChatSelectionBar(
          rooms: controller.selectedRooms,
          onClose: controller.clearRoomSelection,
          onSelectAll: controller.selectAllVisibleRooms,
          onDone: controller.clearRoomSelection,
        ),
      );
    }

    final plus = PlusColors.of(context);
    final railHidden =
        !isColumnMode && !AppSettings.displayNavigationRail.value;

    if (!controller.isSearchMode) {
      return SliverAppBar(
        floating: true,
        pinned: isColumnMode,
        toolbarHeight: 92,
        titleSpacing: 18,
        scrolledUnderElevation: 0,
        backgroundColor: plus.background,
        automaticallyImplyLeading: false,
        title: StreamBuilder(
          stream: client.onSyncStatus.stream,
          builder: (context, snapshot) {
            final status =
                client.onSyncStatus.value ??
                const SyncStatusUpdate(SyncStatus.waitingForResponse);
            final synced =
                client.onSync.value != null &&
                status.status != SyncStatus.error &&
                client.prevBatch != null;
            return Column(
              crossAxisAlignment: .start,
              mainAxisSize: .min,
              children: [
                Row(
                  children: [
                    if (!synced) ...[
                      SizedBox.square(
                        dimension: 10,
                        child: CircularProgressIndicator(
                          strokeWidth: 1.6,
                          value: status.progress,
                          color: plus.accent,
                        ),
                      ),
                      const SizedBox(width: 6),
                    ],
                    Flexible(
                      child: Text(
                        synced
                            ? 'FLUFFYCHAT+'
                            : status.calcLocalizedString(context).toUpperCase(),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: PlusText.kicker(
                          status.status == SyncStatus.error
                              ? plus.danger
                              : plus.textFaint,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 2),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        controller.activeFilter == ActiveFilter.tag
                            ? (controller.activeTag ?? '').replaceFirst(
                                'u.',
                                '',
                              )
                            : L10n.of(context).chats,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontFamily: PlusText.displayFamily,
                          fontSize: 36,
                          fontWeight: FontWeight.w900,
                          letterSpacing: -1.1,
                          height: 1.1,
                          color: plus.text,
                        ),
                      ),
                    ),
                    if (railHidden && controller.spaces.isNotEmpty)
                      PlusCircleButton(
                        icon: Icons.menu,
                        tooltip: L10n.of(context).displayNavigationRail,
                        onPressed: controller.openNavrail,
                      ),
                    PlusCircleButton(
                      icon: Icons.search,
                      tooltip: L10n.of(context).search,
                      onPressed: controller.startSearch,
                    ),
                    if (railHidden) ClientChooserButton(controller),
                  ],
                ),
              ],
            );
          },
        ),
      );
    }

    return SliverAppBar(
      floating: true,
      toolbarHeight: 72,
      pinned: isColumnMode,
      scrolledUnderElevation: 0,
      backgroundColor: plus.background,
      automaticallyImplyLeading: false,
      title: TextField(
        controller: controller.searchController,
        focusNode: controller.searchFocusNode,
        autofocus: true,
        textInputAction: TextInputAction.search,
        onChanged: (text) =>
            controller.onSearchEnter(text, globalSearch: globalSearch),
        decoration: InputDecoration(
          filled: true,
          fillColor: plus.surface,
          enabledBorder: OutlineInputBorder(
            borderSide: BorderSide(color: plus.line),
            borderRadius: BorderRadius.circular(99),
          ),
          focusedBorder: OutlineInputBorder(
            borderSide: BorderSide(color: plus.textFaint),
            borderRadius: BorderRadius.circular(99),
          ),
          border: OutlineInputBorder(
            borderSide: BorderSide(color: plus.line),
            borderRadius: BorderRadius.circular(99),
          ),
          hintText: L10n.of(context).searchChatsRooms,
          hintStyle: TextStyle(
            color: plus.textFaint,
            fontWeight: FontWeight.normal,
          ),
          prefixIcon: IconButton(
            tooltip: L10n.of(context).cancel,
            icon: const Icon(Icons.close_outlined),
            onPressed: controller.cancelSearch,
            color: plus.text,
          ),
          suffixIcon: globalSearch
              ? controller.isSearching
                    ? const Padding(
                        padding: EdgeInsets.symmetric(
                          vertical: 10.0,
                          horizontal: 12,
                        ),
                        child: SizedBox.square(
                          dimension: 24,
                          child: CircularProgressIndicator.adaptive(
                            strokeWidth: 2,
                          ),
                        ),
                      )
                    : TextButton.icon(
                        onPressed: controller.setServer,
                        style: TextButton.styleFrom(
                          foregroundColor: plus.textMuted,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(99),
                          ),
                          textStyle: const TextStyle(fontSize: 12),
                        ),
                        icon: const Icon(Icons.edit_outlined, size: 16),
                        label: Text(
                          controller.searchServer ??
                              Matrix.of(context).client.homeserver!.host,
                          maxLines: 2,
                        ),
                      )
              : null,
        ),
      ),
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(56);
}
