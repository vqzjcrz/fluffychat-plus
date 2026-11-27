// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/plus_colors.dart';
import 'package:fluffychat/config/themes.dart';
import 'package:fluffychat/l10n/l10n.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

class StartChatFab extends StatelessWidget {
  final bool extended;
  const StartChatFab({this.extended = false, super.key});

  @override
  Widget build(BuildContext context) {
    final plus = PlusColors.of(context);
    return FloatingActionButton.extended(
      heroTag: 'start_chat_fab',
      backgroundColor: plus.accent,
      foregroundColor: plus.onAccent,
      elevation: 0,
      highlightElevation: 0,
      shape: extended ? const StadiumBorder() : const CircleBorder(),
      onPressed: () => context.go('/rooms/newprivatechat'),
      extendedIconLabelSpacing: extended ? 10 : 0,
      extendedPadding: extended
          ? null
          : const EdgeInsets.symmetric(horizontal: 16),
      label: AnimatedSize(
        alignment: Alignment.centerLeft,
        duration: FluffyThemes.animationDuration,
        curve: FluffyThemes.animationCurve,
        child: extended ? Text(L10n.of(context).newChat) : SizedBox.shrink(),
      ),
      icon: const Icon(Icons.edit_outlined),
    );
  }
}
