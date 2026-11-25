// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/utils/scheduled_messages.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_modal_action_popup.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_ok_cancel_alert_dialog.dart';
import 'package:fluffychat/widgets/future_loading_dialog.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

enum _SendLaterOption { oneHour, threeHours, tonight, tomorrowMorning, pick }

/// "Today 8:00 PM" style label for a scheduled time.
String formatScheduledTime(BuildContext context, DateTime time) {
  final localizations = MaterialLocalizations.of(context);
  final clock = localizations.formatTimeOfDay(TimeOfDay.fromDateTime(time));
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final day = DateTime(time.year, time.month, time.day);
  final days = day.difference(today).inDays;
  if (days == 0) return clock;
  if (days == 1) return '${L10n.of(context).tomorrow}, $clock';
  return '${localizations.formatMediumDate(time)}, $clock';
}

/// Asks when to send [text] and hands it to the server. Returns true when
/// it was scheduled.
Future<bool> showSendLaterDialog(
  BuildContext context,
  Room room,
  String text,
) async {
  final l10n = L10n.of(context);
  final now = DateTime.now();
  final tonight = DateTime(now.year, now.month, now.day, 20);
  final tomorrowMorning = DateTime(now.year, now.month, now.day + 1, 8);

  final option = await showModalActionPopup<_SendLaterOption>(
    context: context,
    title: l10n.sendLater,
    cancelLabel: l10n.cancel,
    actions: [
      AdaptiveModalAction(
        label: l10n.inOneHour,
        value: _SendLaterOption.oneHour,
        icon: const Icon(Icons.timer_outlined),
      ),
      AdaptiveModalAction(
        label: l10n.inThreeHours,
        value: _SendLaterOption.threeHours,
        icon: const Icon(Icons.more_time),
      ),
      if (now.isBefore(tonight.subtract(const Duration(minutes: 30))))
        AdaptiveModalAction(
          label: '${l10n.tonight}, ${formatScheduledTime(context, tonight)}',
          value: _SendLaterOption.tonight,
          icon: const Icon(Icons.nights_stay_outlined),
        ),
      AdaptiveModalAction(
        label: formatScheduledTime(context, tomorrowMorning),
        value: _SendLaterOption.tomorrowMorning,
        icon: const Icon(Icons.wb_sunny_outlined),
      ),
      AdaptiveModalAction(
        label: l10n.pickDateAndTime,
        value: _SendLaterOption.pick,
        icon: const Icon(Icons.event_outlined),
      ),
    ],
  );
  if (option == null || !context.mounted) return false;

  final DateTime sendAt;
  if (option == _SendLaterOption.pick) {
    final date = await showDatePicker(
      context: context,
      initialDate: DateTime.now(),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (date == null || !context.mounted) return false;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(
        DateTime.now().add(const Duration(hours: 1)),
      ),
    );
    if (time == null || !context.mounted) return false;
    sendAt = DateTime(date.year, date.month, date.day, time.hour, time.minute);
  } else {
    sendAt = switch (option) {
      _SendLaterOption.oneHour => DateTime.now().add(const Duration(hours: 1)),
      _SendLaterOption.threeHours => DateTime.now().add(
        const Duration(hours: 3),
      ),
      _SendLaterOption.tonight => tonight,
      _SendLaterOption.tomorrowMorning => tomorrowMorning,
      _SendLaterOption.pick => DateTime.now(),
    };
  }
  if (!sendAt.isAfter(DateTime.now())) {
    await showOkAlertDialog(
      context: context,
      title: l10n.sendLater,
      message: l10n.pickFutureTime,
      okLabel: l10n.ok,
    );
    return false;
  }

  final scheduledAt = sendAt;
  final result = await showFutureLoadingDialog(
    context: context,
    future: () async {
      try {
        return await room.client.scheduleMessage(room, text, scheduledAt);
      } on ScheduledMessagesUnsupported {
        throw l10n.scheduledMessagesUnsupported;
      } on ScheduledMessageTooFar catch (e) {
        throw l10n.scheduledMessageTooFar(e.maxDelay.inHours);
      }
    },
  );
  if (result.error != null || !context.mounted) return false;
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Text(
        l10n.scheduledFor(formatScheduledTime(context, scheduledAt)),
      ),
    ),
  );
  return true;
}

/// The messages scheduled in [room], with buttons to send now or cancel.
Future<void> showScheduledMessagesSheet(BuildContext context, Room room) =>
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useRootNavigator: true,
      builder: (context) => _ScheduledMessagesSheet(room: room),
    );

class _ScheduledMessagesSheet extends StatefulWidget {
  final Room room;

  const _ScheduledMessagesSheet({required this.room});

  @override
  State<_ScheduledMessagesSheet> createState() =>
      _ScheduledMessagesSheetState();
}

class _ScheduledMessagesSheetState extends State<_ScheduledMessagesSheet> {
  late List<ScheduledMessage> _messages;

  @override
  void initState() {
    super.initState();
    _messages = widget.room.client.scheduledMessages(widget.room.id);
    widget.room.client.refreshScheduledMessages(widget.room.id).then((list) {
      if (mounted) setState(() => _messages = list);
    });
  }

  Future<void> _update(ScheduledMessage message, {required bool sendNow}) async {
    await showFutureLoadingDialog(
      context: context,
      future: () => widget.room.client.updateScheduledMessage(
        message,
        sendNow: sendNow,
      ),
    );
    if (!mounted) return;
    setState(
      () => _messages = widget.room.client.scheduledMessages(widget.room.id),
    );
    if (_messages.isEmpty) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return SafeArea(
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * 0.7,
        ),
        child: Column(
          mainAxisSize: .min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
              child: Text(
                l10n.scheduledMessages,
                style: theme.textTheme.titleMedium,
              ),
            ),
            if (_messages.isEmpty)
              Padding(
                padding: const EdgeInsets.all(20),
                child: Text(l10n.noScheduledMessages),
              ),
            Flexible(
              child: ListView(
                shrinkWrap: true,
                children: [
                  for (final message in _messages)
                    ListTile(
                      leading: const Icon(Icons.schedule_send_outlined),
                      title: Text(
                        message.body,
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(
                        formatScheduledTime(context, message.sendAt),
                      ),
                      trailing: Row(
                        mainAxisSize: .min,
                        children: [
                          IconButton(
                            icon: const Icon(Icons.send_outlined),
                            tooltip: l10n.sendNow,
                            onPressed: () => _update(message, sendNow: true),
                          ),
                          IconButton(
                            icon: Icon(
                              Icons.delete_outlined,
                              color: theme.colorScheme.error,
                            ),
                            tooltip: l10n.cancel,
                            onPressed: () => _update(message, sendNow: false),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
