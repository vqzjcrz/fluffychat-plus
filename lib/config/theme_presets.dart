// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:collection/collection.dart';
import 'package:fluffychat/config/plus_colors.dart';
import 'package:material_ui/material_ui.dart';

/// The colors shown on a theme's swatch in the settings.
class ThemePalette {
  final Color background;
  final Color foreground;
  final Color primary;
  final Color secondary;
  final Color tertiary;
  final Color error;

  const ThemePalette({
    required this.background,
    required this.foreground,
    required this.primary,
    required this.secondary,
    required this.tertiary,
    required this.error,
  });
}

/// A FluffyChat+ theme. All of its colors are set by hand and checked
/// against each other for contrast, chat bubbles included.
class ThemePreset {
  final String id;
  final String name;
  final Brightness brightness;
  final PlusColors colors;

  const ThemePreset({
    required this.id,
    required this.name,
    required this.brightness,
    required this.colors,
  });

  /// Every preset comes in one brightness only and always uses it.
  Brightness effectiveBrightness(Brightness requested) => brightness;

  ColorScheme colorScheme(Brightness requested, Color seed) {
    final c = colors;
    final dark = brightness == Brightness.dark;
    Color mix(Color a, Color b, double t) => Color.lerp(a, b, t)!;
    return ColorScheme(
      brightness: brightness,
      primary: c.accent,
      onPrimary: c.onAccent,
      // FluffyChat uses these for icons and highlights on normal
      // backgrounds, so they follow the usual Material meaning. Bubbles get
      // their colors from PlusColors instead.
      primaryContainer: mix(c.background, c.accent, dark ? 0.24 : 0.16),
      onPrimaryContainer: c.text,
      primaryFixed: c.accent,
      primaryFixedDim: mix(c.accent, c.background, 0.2),
      onPrimaryFixed: c.onAccent,
      secondary: c.accent,
      onSecondary: c.onAccent,
      secondaryContainer: c.raised,
      onSecondaryContainer: c.text,
      tertiary: c.accent,
      onTertiary: c.onAccent,
      tertiaryContainer: c.raised,
      onTertiaryContainer: c.text,
      error: c.danger,
      onError: c.onDanger,
      errorContainer: mix(c.background, c.danger, dark ? 0.22 : 0.14),
      onErrorContainer: c.text,
      surface: c.background,
      onSurface: c.text,
      surfaceDim: c.background,
      surfaceBright: c.raised,
      surfaceContainerLowest: dark ? c.background : Colors.white,
      surfaceContainerLow: mix(c.background, c.surface, 0.5),
      surfaceContainer: c.surface,
      surfaceContainerHigh: c.raised,
      surfaceContainerHighest: mix(c.raised, c.text, dark ? 0.06 : 0.04),
      onSurfaceVariant: c.textMuted,
      outline: c.textFaint,
      outlineVariant: c.line,
      inverseSurface: c.text,
      onInverseSurface: c.background,
      inversePrimary: c.accent,
      shadow: Colors.black,
      scrim: Colors.black,
      surfaceTint: Colors.transparent,
    );
  }

  ThemePalette previewPalette(Brightness requested, Color seed) =>
      ThemePalette(
        background: colors.background,
        foreground: colors.text,
        primary: colors.accent,
        secondary: colors.ownBubble,
        tertiary: colors.bubbleIn,
        error: colors.danger,
      );

  static ThemePreset? byId(String id) =>
      themePresets.firstWhereOrNull((preset) => preset.id == id);

  /// The preset for a saved setting: none (Material You) for an empty one,
  /// and Mono for presets that no longer exist.
  static ThemePreset? resolve(String id) {
    if (id.isEmpty) return null;
    return byId(id) ?? byId(monoPresetId);
  }
}

const String monoPresetId = 'mono';

const List<ThemePreset> themePresets = [
  ThemePreset(
    id: monoPresetId,
    name: 'Mono',
    brightness: Brightness.dark,
    colors: PlusColors(
      background: Color(0xFF000000),
      surface: Color(0xFF0E0E10),
      raised: Color(0xFF18181B),
      bubbleIn: Color(0xFF18181B),
      line: Color(0xFF1F1F23),
      text: Color(0xFFFFFFFF),
      textMuted: Color(0xFFA1A1AA),
      textFaint: Color(0xFF80808A),
      accent: Color(0xFFD4FF3F),
      onAccent: Color(0xFF0A0A0A),
      ownBubble: Color(0xFFD4FF3F),
      onOwnBubble: Color(0xFF0A0A0A),
      ownLink: Color(0xFF2E4A00),
      danger: Color(0xFFFF5A5F),
      onDanger: Color(0xFF0A0A0A),
    ),
  ),
  ThemePreset(
    id: 'midnight',
    name: 'Midnight',
    brightness: Brightness.dark,
    colors: PlusColors(
      background: Color(0xFF16171A),
      surface: Color(0xFF1E1F23),
      raised: Color(0xFF25262B),
      bubbleIn: Color(0xFF25262B),
      line: Color(0xFF2C2D32),
      text: Color(0xFFEDEEF0),
      textMuted: Color(0xFFA6A9B0),
      textFaint: Color(0xFF8A8E96),
      accent: Color(0xFF8F9CFF),
      onAccent: Color(0xFF0B0B0D),
      ownBubble: Color(0xFF4553D6),
      onOwnBubble: Color(0xFFFFFFFF),
      ownLink: Color(0xFFDCE0FF),
      danger: Color(0xFFFF6B70),
      onDanger: Color(0xFF0B0B0D),
    ),
  ),
  ThemePreset(
    id: 'amoled',
    name: 'AMOLED',
    brightness: Brightness.dark,
    colors: PlusColors(
      background: Color(0xFF000000),
      surface: Color(0xFF0E0E10),
      raised: Color(0xFF1A1A1D),
      bubbleIn: Color(0xFF1A1A1D),
      line: Color(0xFF1F1F23),
      text: Color(0xFFFFFFFF),
      textMuted: Color(0xFFA1A1AA),
      textFaint: Color(0xFF80808A),
      accent: Color(0xFF8AB4FF),
      onAccent: Color(0xFF0A0A0A),
      ownBubble: Color(0xFF2B4FD8),
      onOwnBubble: Color(0xFFFFFFFF),
      ownLink: Color(0xFFD7E3FF),
      danger: Color(0xFFFF5A5F),
      onDanger: Color(0xFF0A0A0A),
    ),
  ),
  ThemePreset(
    id: 'graphite',
    name: 'Graphite',
    brightness: Brightness.dark,
    colors: PlusColors(
      background: Color(0xFF1C1C1E),
      surface: Color(0xFF242426),
      raised: Color(0xFF2C2C2F),
      bubbleIn: Color(0xFF2C2C2F),
      line: Color(0xFF343437),
      text: Color(0xFFF2F2F3),
      textMuted: Color(0xFFA9A9AF),
      textFaint: Color(0xFF8E8E94),
      accent: Color(0xFF64D2B3),
      onAccent: Color(0xFF0A0A0A),
      ownBubble: Color(0xFF13695A),
      onOwnBubble: Color(0xFFFFFFFF),
      ownLink: Color(0xFFBFF5E6),
      danger: Color(0xFFFF7A7A),
      onDanger: Color(0xFF0A0A0A),
    ),
  ),
  ThemePreset(
    id: 'nord',
    name: 'Nord',
    brightness: Brightness.dark,
    colors: PlusColors(
      background: Color(0xFF242933),
      surface: Color(0xFF2B313C),
      raised: Color(0xFF313844),
      bubbleIn: Color(0xFF313844),
      line: Color(0xFF3A4150),
      text: Color(0xFFECEFF4),
      textMuted: Color(0xFFB4BCCB),
      textFaint: Color(0xFF98A0AF),
      accent: Color(0xFF88C0D0),
      onAccent: Color(0xFF1A1E25),
      ownBubble: Color(0xFF3D5A80),
      onOwnBubble: Color(0xFFFFFFFF),
      ownLink: Color(0xFFCFE6F0),
      danger: Color(0xFFF08A92),
      onDanger: Color(0xFF1A1E25),
    ),
  ),
  ThemePreset(
    id: 'mocha',
    name: 'Mocha',
    brightness: Brightness.dark,
    colors: PlusColors(
      background: Color(0xFF1E1E2E),
      surface: Color(0xFF252536),
      raised: Color(0xFF2C2D3F),
      bubbleIn: Color(0xFF2C2D3F),
      line: Color(0xFF35364B),
      text: Color(0xFFCDD6F4),
      textMuted: Color(0xFFA6ADC8),
      textFaint: Color(0xFF8E94B0),
      accent: Color(0xFFCBA6F7),
      onAccent: Color(0xFF11111B),
      ownBubble: Color(0xFF5B4A9E),
      onOwnBubble: Color(0xFFFFFFFF),
      ownLink: Color(0xFFE8DBFF),
      danger: Color(0xFFF38BA8),
      onDanger: Color(0xFF11111B),
    ),
  ),
  ThemePreset(
    id: 'forest',
    name: 'Forest',
    brightness: Brightness.dark,
    colors: PlusColors(
      background: Color(0xFF1A1F1C),
      surface: Color(0xFF202622),
      raised: Color(0xFF262D29),
      bubbleIn: Color(0xFF262D29),
      line: Color(0xFF2F3732),
      text: Color(0xFFE7EEE9),
      textMuted: Color(0xFFA3B1A8),
      textFaint: Color(0xFF8A988F),
      accent: Color(0xFF8BD3A0),
      onAccent: Color(0xFF0D130F),
      ownBubble: Color(0xFF2F6B45),
      onOwnBubble: Color(0xFFFFFFFF),
      ownLink: Color(0xFFCDEFD8),
      danger: Color(0xFFF28B82),
      onDanger: Color(0xFF0D130F),
    ),
  ),
  ThemePreset(
    id: 'paper',
    name: 'Paper',
    brightness: Brightness.light,
    colors: PlusColors(
      background: Color(0xFFF5F6F8),
      surface: Color(0xFFFFFFFF),
      raised: Color(0xFFE9EBF0),
      bubbleIn: Color(0xFFFFFFFF),
      line: Color(0xFFE1E4EA),
      text: Color(0xFF15161A),
      textMuted: Color(0xFF5B6070),
      textFaint: Color(0xFF696E7E),
      accent: Color(0xFF2F54EB),
      onAccent: Color(0xFFFFFFFF),
      ownBubble: Color(0xFF2F54EB),
      onOwnBubble: Color(0xFFFFFFFF),
      ownLink: Color(0xFFE3E9FF),
      danger: Color(0xFFC62828),
      onDanger: Color(0xFFFFFFFF),
      outlineBubbles: true,
    ),
  ),
  ThemePreset(
    id: 'sand',
    name: 'Sand',
    brightness: Brightness.light,
    colors: PlusColors(
      background: Color(0xFFF3EEE6),
      surface: Color(0xFFFFFCF7),
      raised: Color(0xFFE8E1D5),
      bubbleIn: Color(0xFFFFFCF7),
      line: Color(0xFFE2DACD),
      text: Color(0xFF2A231E),
      textMuted: Color(0xFF6B5F55),
      textFaint: Color(0xFF76695E),
      accent: Color(0xFFA04A1C),
      onAccent: Color(0xFFFFFFFF),
      ownBubble: Color(0xFF8C3F17),
      onOwnBubble: Color(0xFFFFFFFF),
      ownLink: Color(0xFFFFE2CF),
      danger: Color(0xFFB42318),
      onDanger: Color(0xFFFFFFFF),
      outlineBubbles: true,
    ),
  ),
];
