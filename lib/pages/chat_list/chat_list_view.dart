// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/plus_colors.dart';
import 'package:fluffychat/config/setting_keys.dart';
import 'package:fluffychat/config/themes.dart';
import 'package:fluffychat/pages/chat_list/chat_list.dart';
import 'package:fluffychat/pages/chat_list/client_chooser_button.dart';
import 'package:fluffychat/pages/chat_list/navigation_rail.dart';
import 'package:fluffychat/pages/chat_list/start_chat_fab.dart';
import 'package:material_ui/material_ui.dart';

import 'chat_list_body.dart';

class ChatListView extends StatelessWidget {
  final ChatListController controller;

  const ChatListView(this.controller, {super.key});

  @override
  Widget build(BuildContext context) {
    final plus = PlusColors.of(context);
    final showRail =
        FluffyThemes.isColumnMode(context) ||
        AppSettings.displayNavigationRail.value;
    return PopScope(
      canPop:
          !controller.isSearchMode &&
          controller.activeSpaceId == null &&
          !controller.selectMode,
      onPopInvokedWithResult: (pop, _) {
        if (pop) return;
        if (controller.selectMode) {
          controller.clearRoomSelection();
          return;
        }
        if (controller.activeSpaceId != null) {
          controller.clearActiveSpace();
          return;
        }
        if (controller.isSearchMode) {
          controller.cancelSearch();
          return;
        }
      },
      child: Row(
        children: [
          Material(
            color: plus.background,
            child: AnimatedSize(
              duration: FluffyThemes.animationDuration,
              curve: FluffyThemes.animationCurve,
              child: showRail
                  ? SpacesNavigationRail(
                      activeSpaceId: controller.activeSpaceId,
                      onGoToChats: controller.clearActiveSpace,
                      onGoToSpaceId: controller.setActiveSpace,
                      accountButton: ClientChooserButton(
                        controller,
                        railStyle: true,
                      ),
                    )
                  : SizedBox(
                      width: 0,
                      height: MediaQuery.sizeOf(context).height,
                    ),
            ),
          ),
          if (showRail) Container(width: 1, color: plus.line),

          Expanded(
            child: GestureDetector(
              onTap: FocusManager.instance.primaryFocus?.unfocus,
              excludeFromSemantics: true,
              behavior: HitTestBehavior.translucent,
              child: Scaffold(
                backgroundColor: plus.background,
                body: SafeArea(
                  bottom: false,
                  left: false,
                  right: false,
                  child: Material(
                    color: plus.background,
                    child: ChatListViewBody(controller),
                  ),
                ),
                floatingActionButton:
                    !controller.isSearchMode &&
                        controller.activeSpaceId == null &&
                        !FluffyThemes.isColumnMode(context)
                    ? ValueListenableBuilder(
                        valueListenable: controller.scrolledToTop,
                        builder: (context, scrolledToTop, _) => StartChatFab(
                          extended:
                              scrolledToTop &&
                              !AppSettings.displayNavigationRail.value,
                        ),
                      )
                    : const SizedBox.shrink(),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
