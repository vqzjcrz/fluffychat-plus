// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/utils/snooze_extension.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_modal_action_popup.dart';
import 'package:fluffychat/widgets/future_loading_dialog.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

enum _SnoozeOption { oneHour, eightHours, tomorrowMorning, end }

/// "8:00 AM" for today, "Nov 25, 8:00 AM" for any other day.
String formatSnoozeEnd(BuildContext context, DateTime until) {
  final localizations = MaterialLocalizations.of(context);
  final time = localizations.formatTimeOfDay(TimeOfDay.fromDateTime(until));
  final now = DateTime.now();
  if (until.year == now.year &&
      until.month == now.month &&
      until.day == now.day) {
    return time;
  }
  return '${localizations.formatShortMonthDay(until)}, $time';
}

/// Lets the user snooze [room] for a while, or end a running snooze.
Future<void> showSnoozeDialog(BuildContext context, Room room) async {
  final l10n = L10n.of(context);
  final snoozedUntil = room.client.snoozedUntil(room.id);
  final option = await showModalActionPopup<_SnoozeOption>(
    context: context,
    title: l10n.snoozeChat,
    message: snoozedUntil == null
        ? l10n.snoozeDescription
        : l10n.snoozedUntil(formatSnoozeEnd(context, snoozedUntil)),
    cancelLabel: l10n.cancel,
    actions: [
      AdaptiveModalAction(
        label: l10n.snoozeOneHour,
        value: _SnoozeOption.oneHour,
        icon: const Icon(Icons.timer_outlined),
      ),
      AdaptiveModalAction(
        label: l10n.snoozeEightHours,
        value: _SnoozeOption.eightHours,
        icon: const Icon(Icons.bedtime_outlined),
      ),
      AdaptiveModalAction(
        label: l10n.snoozeUntilTomorrow,
        value: _SnoozeOption.tomorrowMorning,
        icon: const Icon(Icons.wb_sunny_outlined),
      ),
      if (snoozedUntil != null)
        AdaptiveModalAction(
          label: l10n.endSnooze,
          value: _SnoozeOption.end,
          icon: const Icon(Icons.notifications_active_outlined),
        ),
    ],
  );
  if (option == null) return;
  if (!context.mounted) return;

  final now = DateTime.now();
  final until = switch (option) {
    _SnoozeOption.oneHour => now.add(const Duration(hours: 1)),
    _SnoozeOption.eightHours => now.add(const Duration(hours: 8)),
    _SnoozeOption.tomorrowMorning => tomorrowMorning(),
    _SnoozeOption.end => null,
  };
  await showFutureLoadingDialog(
    context: context,
    future: () => room.client.snoozeRoom(room.id, until),
  );
}
