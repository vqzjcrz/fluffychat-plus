// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/pages/chat/chat.dart';
import 'package:fluffychat/utils/room_plus_extension.dart';
import 'package:material_ui/material_ui.dart';

/// Takes the place of the message input in chats the user can't post in.
class ReadOnlyComposerNote extends StatelessWidget {
  final ChatController controller;

  const ReadOnlyComposerNote({required this.controller, super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final room = controller.room;
    // Discord portals only block posting on the server, so bridge commands
    // can still be sent from the normal input.
    final canWriteAnyway = room.canSendDefaultMessages;
    return Material(
      color: theme.colorScheme.surfaceContainer,
      borderRadius: BorderRadius.circular(32),
      child: Padding(
        padding: EdgeInsets.fromLTRB(20, 8, canWriteAnyway ? 8 : 20, 8),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 40),
          child: Row(
            children: [
              Icon(
                Icons.lock_outline,
                size: 20,
                color: theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  room.isDiscordBridged && canWriteAnyway
                      ? l10n.readOnlyDiscordNote
                      : l10n.cantPostHereNote,
                  style: TextStyle(color: theme.colorScheme.onSurfaceVariant),
                ),
              ),
              if (canWriteAnyway)
                TextButton(
                  onPressed: controller.revealReadOnlyComposer,
                  child: Text(l10n.sendCommand),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
