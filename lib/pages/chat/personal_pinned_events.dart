// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/pages/chat/chat.dart';
import 'package:fluffychat/pages/chat/chat_app_bar_list_tile.dart';
import 'package:fluffychat/utils/matrix_sdk_extensions/matrix_locals.dart';
import 'package:fluffychat/utils/personal_pins_extension.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_modal_action_popup.dart';
import 'package:fluffychat/widgets/future_loading_dialog.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

/// Bar under the chat title with the messages the user pinned for
/// themselves. Tap to jump to one, tap the pin to remove the newest.
class PersonalPinnedEvents extends StatelessWidget {
  final ChatController controller;

  const PersonalPinnedEvents(this.controller, {super.key});

  static bool isVisible(ChatController controller) =>
      controller.activeThreadId == null &&
      controller.room.client.personalPins(controller.room.id).isNotEmpty;

  String _label(BuildContext context, Event event) {
    final l10n = L10n.of(context);
    return l10n.senderMessage(
      event.senderId == event.room.client.userID
          ? l10n.you
          : event.senderFromMemoryOrFallback.calcDisplayname(),
      event
          .calcLocalizedBody(MatrixLocals(l10n), hideReply: true)
          .trim()
          .replaceAll('\n', ' '),
    );
  }

  Future<void> _showAll(BuildContext context, List<String> eventIds) async {
    final l10n = L10n.of(context);
    final room = controller.room;
    final eventsResult = await showFutureLoadingDialog(
      context: context,
      future: () => Future.wait(eventIds.map(room.getEventById)),
    );
    final events = eventsResult.result;
    if (events == null || !context.mounted) return;
    final found = events.whereType<Event>().toList().reversed.toList();
    if (found.isEmpty) return;

    final eventId = found.length == 1
        ? found.single.eventId
        : await showModalActionPopup<String>(
            context: context,
            title: l10n.pinnedForMe,
            cancelLabel: l10n.cancel,
            actions: found
                .map(
                  (event) => AdaptiveModalAction(
                    value: event.eventId,
                    icon: const Icon(Icons.push_pin_outlined),
                    label: _label(context, event),
                  ),
                )
                .toList(),
          );
    if (eventId != null) controller.scrollToEventId(eventId);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    if (!isVisible(controller)) return const SizedBox.shrink();
    final eventIds = controller.room.client.personalPins(controller.room.id);

    return FutureBuilder<Event?>(
      future: controller.room.getEventById(eventIds.last),
      builder: (context, snapshot) {
        final event = snapshot.data;
        return ChatAppBarListTile(
          title: event == null
              ? l10n.loadingPleaseWait
              : _label(context, event),
          leading: IconButton(
            splashRadius: 18,
            iconSize: 18,
            color: theme.colorScheme.tertiary,
            icon: const Icon(Icons.push_pin),
            tooltip: l10n.unpinForMe,
            onPressed: () => controller.setPersonalPin(eventIds.last, false),
          ),
          onTap: () => _showAll(context, eventIds),
        );
      },
    );
  }
}
