// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:math';

import 'package:collection/collection.dart';
import 'package:material_ui/material_ui.dart';

/// The colors a preset theme is built from. Everything else in the color
/// scheme is derived from these.
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

  ColorScheme toColorScheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    Color mix(Color a, Color b, double t) => Color.lerp(a, b, t)!;
    Color container(Color color) => mix(background, color, isDark ? 0.3 : 0.2);
    Color onAccent(Color color) =>
        _contrast(color, background) >= _contrast(color, foreground)
        ? background
        : foreground;
    final strongError = isDark
        ? mix(error, Colors.white, 0.25)
        : mix(error, Colors.black, 0.25);

    return ColorScheme.fromSeed(
      seedColor: primary,
      brightness: brightness,
    ).copyWith(
      primary: primary,
      onPrimary: onAccent(primary),
      primaryContainer: container(primary),
      onPrimaryContainer: foreground,
      secondary: secondary,
      onSecondary: onAccent(secondary),
      secondaryContainer: container(secondary),
      onSecondaryContainer: foreground,
      tertiary: tertiary,
      onTertiary: onAccent(tertiary),
      tertiaryContainer: container(tertiary),
      onTertiaryContainer: foreground,
      error: error,
      onError: onAccent(error),
      errorContainer: container(error),
      onErrorContainer: strongError,
      surface: background,
      onSurface: foreground,
      surfaceDim: mix(background, Colors.black, isDark ? 0.2 : 0.06),
      surfaceBright: mix(background, foreground, isDark ? 0.16 : 0.0),
      surfaceContainerLowest: isDark
          ? mix(background, Colors.black, 0.25)
          : mix(background, Colors.white, 0.6),
      surfaceContainerLow: mix(background, foreground, 0.03),
      surfaceContainer: mix(background, foreground, 0.06),
      surfaceContainerHigh: mix(background, foreground, 0.1),
      surfaceContainerHighest: mix(background, foreground, 0.14),
      onSurfaceVariant: mix(foreground, background, 0.25),
      outline: mix(foreground, background, 0.5),
      outlineVariant: mix(foreground, background, 0.78),
      inverseSurface: foreground,
      onInverseSurface: background,
      inversePrimary: mix(primary, isDark ? Colors.black : Colors.white, 0.4),
      surfaceTint: primary,
    );
  }

  static double _contrast(Color a, Color b) {
    final la = a.computeLuminance();
    final lb = b.computeLuminance();
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05);
  }
}

class ThemePreset {
  final String id;
  final String name;
  final ThemePalette? light;
  final ThemePalette? dark;

  /// Keeps the user's accent color and only swaps the surfaces to black.
  final bool amoled;

  const ThemePreset({
    required this.id,
    required this.name,
    this.light,
    this.dark,
    this.amoled = false,
  });

  bool get hasLight => light != null;
  bool get hasDark => dark != null || amoled;

  /// The brightness this preset shows for [requested]. Presets with only a
  /// dark (or only a light) variant always use that one.
  Brightness effectiveBrightness(Brightness requested) {
    if (requested == Brightness.light && !hasLight) return Brightness.dark;
    if (requested == Brightness.dark && !hasDark) return Brightness.light;
    return requested;
  }

  ColorScheme colorScheme(Brightness brightness, Color seed) {
    final effective = effectiveBrightness(brightness);
    if (amoled) {
      return ColorScheme.fromSeed(
        seedColor: seed,
        brightness: Brightness.dark,
        dynamicSchemeVariant: DynamicSchemeVariant.rainbow,
      ).copyWith(
        surface: Colors.black,
        surfaceDim: Colors.black,
        surfaceBright: const Color(0xFF1E1E1E),
        surfaceContainerLowest: Colors.black,
        surfaceContainerLow: const Color(0xFF080808),
        surfaceContainer: const Color(0xFF101010),
        surfaceContainerHigh: const Color(0xFF181818),
        surfaceContainerHighest: const Color(0xFF222222),
      );
    }
    final palette = effective == Brightness.dark ? dark! : light!;
    return palette.toColorScheme(effective);
  }

  /// Colors for the preview swatch in the settings.
  ThemePalette previewPalette(Brightness brightness, Color seed) {
    if (amoled) {
      final scheme = colorScheme(Brightness.dark, seed);
      return ThemePalette(
        background: Colors.black,
        foreground: Colors.white,
        primary: scheme.primary,
        secondary: scheme.secondary,
        tertiary: scheme.tertiary,
        error: scheme.error,
      );
    }
    return effectiveBrightness(brightness) == Brightness.dark ? dark! : light!;
  }

  static ThemePreset? byId(String id) =>
      themePresets.firstWhereOrNull((preset) => preset.id == id);
}

const String amoledPresetId = 'amoled';

const List<ThemePreset> themePresets = [
  ThemePreset(id: amoledPresetId, name: 'AMOLED Black', amoled: true),
  ThemePreset(
    id: 'nord',
    name: 'Nord',
    light: ThemePalette(
      background: Color(0xFFECEFF4),
      foreground: Color(0xFF2E3440),
      primary: Color(0xFF5E81AC),
      secondary: Color(0xFF81A1C1),
      tertiary: Color(0xFFB48EAD),
      error: Color(0xFFBF616A),
    ),
    dark: ThemePalette(
      background: Color(0xFF2E3440),
      foreground: Color(0xFFECEFF4),
      primary: Color(0xFF88C0D0),
      secondary: Color(0xFF81A1C1),
      tertiary: Color(0xFFB48EAD),
      error: Color(0xFFBF616A),
    ),
  ),
  ThemePreset(
    id: 'dracula',
    name: 'Dracula',
    light: ThemePalette(
      background: Color(0xFFFFFBEB),
      foreground: Color(0xFF1F1F1F),
      primary: Color(0xFF644AC9),
      secondary: Color(0xFFA3144D),
      tertiary: Color(0xFF036A96),
      error: Color(0xFFCB3A2A),
    ),
    dark: ThemePalette(
      background: Color(0xFF282A36),
      foreground: Color(0xFFF8F8F2),
      primary: Color(0xFFBD93F9),
      secondary: Color(0xFFFF79C6),
      tertiary: Color(0xFF8BE9FD),
      error: Color(0xFFFF5555),
    ),
  ),
  ThemePreset(
    id: 'catppuccin',
    name: 'Catppuccin',
    light: ThemePalette(
      background: Color(0xFFEFF1F5),
      foreground: Color(0xFF4C4F69),
      primary: Color(0xFF8839EF),
      secondary: Color(0xFF1E66F5),
      tertiary: Color(0xFF179299),
      error: Color(0xFFD20F39),
    ),
    dark: ThemePalette(
      background: Color(0xFF1E1E2E),
      foreground: Color(0xFFCDD6F4),
      primary: Color(0xFFCBA6F7),
      secondary: Color(0xFF89B4FA),
      tertiary: Color(0xFF94E2D5),
      error: Color(0xFFF38BA8),
    ),
  ),
  ThemePreset(
    id: 'gruvbox',
    name: 'Gruvbox',
    light: ThemePalette(
      background: Color(0xFFFBF1C7),
      foreground: Color(0xFF3C3836),
      primary: Color(0xFFAF3A03),
      secondary: Color(0xFF79740E),
      tertiary: Color(0xFF076678),
      error: Color(0xFF9D0006),
    ),
    dark: ThemePalette(
      background: Color(0xFF282828),
      foreground: Color(0xFFEBDBB2),
      primary: Color(0xFFFE8019),
      secondary: Color(0xFFB8BB26),
      tertiary: Color(0xFF83A598),
      error: Color(0xFFFB4934),
    ),
  ),
  ThemePreset(
    id: 'solarized',
    name: 'Solarized',
    light: ThemePalette(
      background: Color(0xFFFDF6E3),
      foreground: Color(0xFF586E75),
      primary: Color(0xFF268BD2),
      secondary: Color(0xFF2AA198),
      tertiary: Color(0xFF6C71C4),
      error: Color(0xFFDC322F),
    ),
    dark: ThemePalette(
      background: Color(0xFF002B36),
      foreground: Color(0xFF93A1A1),
      primary: Color(0xFF268BD2),
      secondary: Color(0xFF2AA198),
      tertiary: Color(0xFFD33682),
      error: Color(0xFFDC322F),
    ),
  ),
  ThemePreset(
    id: 'tokyonight',
    name: 'Tokyo Night',
    light: ThemePalette(
      background: Color(0xFFE1E2E7),
      foreground: Color(0xFF3760BF),
      primary: Color(0xFF2E7DE9),
      secondary: Color(0xFF9854F1),
      tertiary: Color(0xFF007197),
      error: Color(0xFFF52A65),
    ),
    dark: ThemePalette(
      background: Color(0xFF1A1B26),
      foreground: Color(0xFFC0CAF5),
      primary: Color(0xFF7AA2F7),
      secondary: Color(0xFFBB9AF7),
      tertiary: Color(0xFF7DCFFF),
      error: Color(0xFFF7768E),
    ),
  ),
  ThemePreset(
    id: 'rosepine',
    name: 'Rosé Pine',
    light: ThemePalette(
      background: Color(0xFFFAF4ED),
      foreground: Color(0xFF575279),
      primary: Color(0xFF907AA9),
      secondary: Color(0xFFD7827E),
      tertiary: Color(0xFF56949F),
      error: Color(0xFFB4637A),
    ),
    dark: ThemePalette(
      background: Color(0xFF191724),
      foreground: Color(0xFFE0DEF4),
      primary: Color(0xFFC4A7E7),
      secondary: Color(0xFFEBBCBA),
      tertiary: Color(0xFF9CCFD8),
      error: Color(0xFFEB6F92),
    ),
  ),
  ThemePreset(
    id: 'everforest',
    name: 'Everforest',
    light: ThemePalette(
      background: Color(0xFFFDF6E3),
      foreground: Color(0xFF5C6A72),
      primary: Color(0xFF8DA101),
      secondary: Color(0xFF35A77C),
      tertiary: Color(0xFF3A94C5),
      error: Color(0xFFF85552),
    ),
    dark: ThemePalette(
      background: Color(0xFF2D353B),
      foreground: Color(0xFFD3C6AA),
      primary: Color(0xFFA7C080),
      secondary: Color(0xFF83C092),
      tertiary: Color(0xFF7FBBB3),
      error: Color(0xFFE67E80),
    ),
  ),
  ThemePreset(
    id: 'onedark',
    name: 'One Dark',
    light: ThemePalette(
      background: Color(0xFFFAFAFA),
      foreground: Color(0xFF383A42),
      primary: Color(0xFF4078F2),
      secondary: Color(0xFFA626A4),
      tertiary: Color(0xFF50A14F),
      error: Color(0xFFE45649),
    ),
    dark: ThemePalette(
      background: Color(0xFF282C34),
      foreground: Color(0xFFABB2BF),
      primary: Color(0xFF61AFEF),
      secondary: Color(0xFFC678DD),
      tertiary: Color(0xFF98C379),
      error: Color(0xFFE06C75),
    ),
  ),
];
