// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/utils/chat_nickname_extension.dart';
import 'package:fluffychat/utils/matrix_sdk_extensions/matrix_locals.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_text_input_dialog.dart';
import 'package:fluffychat/widgets/future_loading_dialog.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

/// Gives [room] a name only this user sees. Leaving it empty brings back the
/// chat's real name.
Future<void> showRenameForMeDialog(BuildContext context, Room room) async {
  final l10n = L10n.of(context);
  final name = await showTextInputDialog(
    context: context,
    title: l10n.renameForMe,
    message: l10n.renameForMeDescription(
      room.getLocalizedDisplayname(MatrixLocals(l10n)),
    ),
    initialText: room.nickname,
    hintText: room.getLocalizedDisplayname(MatrixLocals(l10n)),
    okLabel: l10n.ok,
    cancelLabel: l10n.cancel,
    maxLines: 1,
  );
  if (name == null || !context.mounted) return;
  await showFutureLoadingDialog(
    context: context,
    future: () => room.client.setChatNickname(room.id, name),
  );
}
