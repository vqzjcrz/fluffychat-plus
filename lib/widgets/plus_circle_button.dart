// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/plus_colors.dart';
import 'package:material_ui/material_ui.dart';

/// Round outlined icon button used in the redesigned headers.
class PlusCircleButton extends StatelessWidget {
  final IconData icon;
  final String? tooltip;
  final VoidCallback? onPressed;
  final double size;

  const PlusCircleButton({
    required this.icon,
    this.tooltip,
    this.onPressed,
    this.size = 38,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final plus = PlusColors.of(context);
    final button = Material(
      color: Colors.transparent,
      shape: CircleBorder(side: BorderSide(color: plus.line)),
      clipBehavior: Clip.hardEdge,
      child: InkWell(
        onTap: onPressed,
        child: SizedBox.square(
          dimension: size,
          child: Icon(icon, size: size * 0.5, color: plus.text),
        ),
      ),
    );
    return Padding(
      padding: const EdgeInsets.only(left: 6),
      child: tooltip == null ? button : Tooltip(message: tooltip, child: button),
    );
  }
}
