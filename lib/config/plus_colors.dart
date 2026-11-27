// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:material_ui/material_ui.dart';

/// The FluffyChat+ design colors. Every theme sets all of them, chosen
/// together so text stays readable on whatever it sits on (at least 4.5:1).
/// Material You themes derive them from their color scheme instead.
@immutable
class PlusColors extends ThemeExtension<PlusColors> {
  /// Page background.
  final Color background;

  /// Slightly lifted areas: the message box, cards, the rail when separate.
  final Color surface;

  /// Selected items, search fields, chips.
  final Color raised;

  /// Other people's message bubbles.
  final Color bubbleIn;

  /// Hairline dividers and outlines.
  final Color line;

  final Color text;

  /// Secondary text: previews of unread chats, read chat names.
  final Color textMuted;

  /// Tertiary text: timestamps, previews of read chats, section labels.
  final Color textFaint;

  /// Unread badges, the selected-space bar, links, the send button.
  final Color accent;
  final Color onAccent;

  /// Your own message bubbles.
  final Color ownBubble;
  final Color onOwnBubble;
  final Color ownLink;

  /// Mentions and errors.
  final Color danger;
  final Color onDanger;

  /// Light themes draw a hairline around incoming bubbles, since those are
  /// close to the background.
  final bool outlineBubbles;

  const PlusColors({
    required this.background,
    required this.surface,
    required this.raised,
    required this.bubbleIn,
    required this.line,
    required this.text,
    required this.textMuted,
    required this.textFaint,
    required this.accent,
    required this.onAccent,
    required this.ownBubble,
    required this.onOwnBubble,
    required this.ownLink,
    required this.danger,
    required this.onDanger,
    this.outlineBubbles = false,
  });

  /// For Material You themes, which only have a color scheme.
  factory PlusColors.fromScheme(ColorScheme scheme) {
    final light = scheme.brightness == Brightness.light;
    return PlusColors(
      background: scheme.surface,
      surface: scheme.surfaceContainer,
      raised: scheme.surfaceContainerHigh,
      bubbleIn: scheme.surfaceContainerHigh,
      line: light ? scheme.surfaceContainerHighest : scheme.outlineVariant,
      text: scheme.onSurface,
      textMuted: scheme.onSurfaceVariant,
      textFaint: scheme.outline,
      accent: scheme.primary,
      onAccent: scheme.onPrimary,
      ownBubble: light ? scheme.primary : scheme.primaryContainer,
      onOwnBubble: light ? scheme.onPrimary : scheme.onPrimaryContainer,
      ownLink: light ? scheme.primaryFixed : scheme.onTertiaryContainer,
      danger: scheme.error,
      onDanger: scheme.onError,
    );
  }

  static PlusColors of(BuildContext context) {
    final theme = Theme.of(context);
    return theme.extension<PlusColors>() ??
        PlusColors.fromScheme(theme.colorScheme);
  }

  @override
  PlusColors copyWith({
    Color? background,
    Color? surface,
    Color? raised,
    Color? bubbleIn,
    Color? line,
    Color? text,
    Color? textMuted,
    Color? textFaint,
    Color? accent,
    Color? onAccent,
    Color? ownBubble,
    Color? onOwnBubble,
    Color? ownLink,
    Color? danger,
    Color? onDanger,
    bool? outlineBubbles,
  }) => PlusColors(
    background: background ?? this.background,
    surface: surface ?? this.surface,
    raised: raised ?? this.raised,
    bubbleIn: bubbleIn ?? this.bubbleIn,
    line: line ?? this.line,
    text: text ?? this.text,
    textMuted: textMuted ?? this.textMuted,
    textFaint: textFaint ?? this.textFaint,
    accent: accent ?? this.accent,
    onAccent: onAccent ?? this.onAccent,
    ownBubble: ownBubble ?? this.ownBubble,
    onOwnBubble: onOwnBubble ?? this.onOwnBubble,
    ownLink: ownLink ?? this.ownLink,
    danger: danger ?? this.danger,
    onDanger: onDanger ?? this.onDanger,
    outlineBubbles: outlineBubbles ?? this.outlineBubbles,
  );

  @override
  PlusColors lerp(PlusColors? other, double t) {
    if (other == null) return this;
    Color l(Color a, Color b) => Color.lerp(a, b, t)!;
    return PlusColors(
      background: l(background, other.background),
      surface: l(surface, other.surface),
      raised: l(raised, other.raised),
      bubbleIn: l(bubbleIn, other.bubbleIn),
      line: l(line, other.line),
      text: l(text, other.text),
      textMuted: l(textMuted, other.textMuted),
      textFaint: l(textFaint, other.textFaint),
      accent: l(accent, other.accent),
      onAccent: l(onAccent, other.onAccent),
      ownBubble: l(ownBubble, other.ownBubble),
      onOwnBubble: l(onOwnBubble, other.onOwnBubble),
      ownLink: l(ownLink, other.ownLink),
      danger: l(danger, other.danger),
      onDanger: l(onDanger, other.onDanger),
      outlineBubbles: t < 0.5 ? outlineBubbles : other.outlineBubbles,
    );
  }
}

/// Text styles of the redesign.
abstract class PlusText {
  /// Big screen titles ("Chats", a space's name).
  static const String displayFamily = 'InterDisplay';

  /// Small all-caps labels above titles and section headers.
  static TextStyle kicker(Color color) => TextStyle(
    fontSize: 11,
    fontWeight: FontWeight.w800,
    letterSpacing: 1.8,
    color: color,
    height: 1.2,
  );
}

const double _ms = 0.3;
const double _mb = 0.85;

/// Fades a picture toward gray, for avatars of read chats and spaces that
/// aren't open.
const ColorFilter mutedColorFilter = ColorFilter.matrix(<double>[
  (0.2126 * (1 - _ms) + _ms) * _mb, 0.7152 * (1 - _ms) * _mb, 0.0722 * (1 - _ms) * _mb, 0, 0, //
  0.2126 * (1 - _ms) * _mb, (0.7152 * (1 - _ms) + _ms) * _mb, 0.0722 * (1 - _ms) * _mb, 0, 0, //
  0.2126 * (1 - _ms) * _mb, 0.7152 * (1 - _ms) * _mb, (0.0722 * (1 - _ms) + _ms) * _mb, 0, 0, //
  0, 0, 0, 1, 0, //
]);
