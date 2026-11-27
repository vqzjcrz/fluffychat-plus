// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/app_config.dart';
import 'package:fluffychat/config/plus_colors.dart';
import 'package:fluffychat/config/setting_keys.dart';
import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/utils/space_prefs_extension.dart';
import 'package:fluffychat/widgets/matrix.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

/// Swaps the accent colors of [theme] for ones made from [seed], keeping its
/// backgrounds and text colors, so the theme's readability stays intact.
ThemeData themeWithSpaceAccent(ThemeData theme, Color seed) {
  final light = theme.brightness == Brightness.light;
  final accent = ColorScheme.fromSeed(
    seedColor: seed,
    brightness: theme.brightness,
    dynamicSchemeVariant: DynamicSchemeVariant.fidelity,
  );
  final plus =
      theme.extension<PlusColors>() ?? PlusColors.fromScheme(theme.colorScheme);
  final primary = accent.primary;
  final onPrimary = _readableOn(primary);
  Color mix(Color a, Color b, double t) => Color.lerp(a, b, t)!;

  // Themes whose own bubbles are the accent itself (Mono) keep that; the
  // others get a deeper shade with white text, like their own bubbles.
  final bubbleIsAccent = plus.ownBubble == plus.accent;
  final ownBubble = bubbleIsAccent
      ? primary
      : light
      ? _darkenUntilReadable(primary)
      : _darkenUntilReadable(mix(primary, Colors.black, 0.45));
  final onOwnBubble = bubbleIsAccent ? onPrimary : Colors.white;
  final ownLink = bubbleIsAccent
      ? mix(onPrimary, primary, 0.3)
      : mix(primary, Colors.white, 0.7);
  final container = mix(plus.background, primary, light ? 0.16 : 0.24);

  return theme.copyWith(
    extensions: [
      plus.copyWith(
        accent: primary,
        onAccent: onPrimary,
        ownBubble: ownBubble,
        onOwnBubble: onOwnBubble,
        ownLink: ownLink,
      ),
    ],
    colorScheme: theme.colorScheme.copyWith(
      primary: primary,
      onPrimary: onPrimary,
      primaryContainer: container,
      onPrimaryContainer: plus.text,
      primaryFixed: primary,
      onPrimaryFixed: onPrimary,
      secondary: primary,
      onSecondary: onPrimary,
      tertiary: primary,
      onTertiary: onPrimary,
      inversePrimary: primary,
    ),
  );
}

double _contrast(Color a, Color b) {
  final la = a.computeLuminance();
  final lb = b.computeLuminance();
  return ((la > lb ? la : lb) + 0.05) / ((la > lb ? lb : la) + 0.05);
}

/// Near-black or white, whichever reads better on [background].
Color _readableOn(Color background) {
  const dark = Color(0xFF0A0A0A);
  return _contrast(dark, background) >= _contrast(Colors.white, background)
      ? dark
      : Colors.white;
}

/// Darkens [color] until white text on it reaches 4.5:1.
Color _darkenUntilReadable(Color color) {
  var result = color;
  for (var i = 0; i < 12 && _contrast(Colors.white, result) < 4.5; i++) {
    result = Color.lerp(result, Colors.black, 0.12)!;
  }
  return result;
}

/// The color of the space a chat belongs to. When the chat is in several
/// colored spaces, the one that was open last wins.
int? spaceColorForRoom(Client client, String roomId) {
  int? found;
  final lastSpace = AppSettings.lastSpace.value;
  for (final space in client.rooms) {
    if (!space.isSpace || space.membership != Membership.join) continue;
    final color = client.spaceColor(space.id);
    if (color == null) continue;
    if (!space.spaceChildren.any((child) => child.roomId == roomId)) continue;
    if (space.id == lastSpace) return color;
    found ??= color;
  }
  return found;
}

/// Gives [child] the accent color of a space: [spaceId] itself, or else the
/// space that the chat [roomId] is in.
class SpaceAccentTheme extends StatelessWidget {
  final String? spaceId;
  final String? roomId;
  final Widget child;

  const SpaceAccentTheme({
    this.spaceId,
    this.roomId,
    required this.child,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final client = Matrix.of(context).client;
    final spaceId = this.spaceId;
    final roomId = this.roomId;
    final color = spaceId != null
        ? client.spaceColor(spaceId)
        : roomId != null
        ? spaceColorForRoom(client, roomId)
        : null;
    if (color == null) return child;
    return Theme(
      data: themeWithSpaceAccent(Theme.of(context), Color(color)),
      child: child,
    );
  }
}

/// Lets the user pick an accent color for a space. Returns the ARGB value,
/// -1 for "no color", or null when cancelled.
Future<int?> showSpaceColorPicker(BuildContext context, int? current) {
  final l10n = L10n.of(context);
  final colors = [AppConfig.chatColor, ...Colors.primaries];
  return showDialog<int>(
    context: context,
    builder: (context) {
      final theme = Theme.of(context);
      return AlertDialog(
        title: Text(l10n.spaceColor),
        content: Wrap(
          spacing: 10,
          runSpacing: 10,
          children: [
            for (final color in colors)
              _ColorDot(
                color: color,
                selected: current == color.toARGB32(),
                onTap: () => Navigator.of(context).pop(color.toARGB32()),
              ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(-1),
            child: Text(
              l10n.noSpaceColor,
              style: TextStyle(color: theme.colorScheme.error),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(l10n.cancel),
          ),
        ],
      );
    },
  );
}

class _ColorDot extends StatelessWidget {
  final Color color;
  final bool selected;
  final VoidCallback onTap;

  const _ColorDot({
    required this.color,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      onTap: onTap,
      customBorder: const CircleBorder(),
      child: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: color,
          shape: BoxShape.circle,
          border: selected
              ? Border.all(color: theme.colorScheme.onSurface, width: 3)
              : null,
        ),
        child: selected
            ? const Icon(Icons.check, color: Colors.white, size: 20)
            : null,
      ),
    );
  }
}
