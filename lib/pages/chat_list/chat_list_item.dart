// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/app_config.dart';
import 'package:fluffychat/config/plus_colors.dart';
import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/pages/chat_list/active_call_indicator.dart';
import 'package:fluffychat/pages/chat_list/unread_bubble.dart';
import 'package:fluffychat/utils/chat_nickname_extension.dart';
import 'package:fluffychat/utils/matrix_live_kit_calls/matrix_live_kit_call.dart';
import 'package:fluffychat/utils/matrix_sdk_extensions/matrix_locals.dart';
import 'package:fluffychat/utils/room_plus_extension.dart';
import 'package:fluffychat/utils/room_status_extension.dart';
import 'package:fluffychat/utils/snooze_extension.dart';
import 'package:fluffychat/widgets/hover_builder.dart';
import 'package:fluffychat/widgets/typing_animation.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

import '../../config/themes.dart';
import '../../utils/date_time_extension.dart';
import '../../widgets/avatar.dart';

class ChatListItem extends StatelessWidget {
  final Room room;
  final Room? space;
  final bool activeChat;
  final void Function(BuildContext context)? onLongPress;
  final void Function()? onForget;
  final void Function() onTap;
  final String? filter;

  /// FluffyChat+: picked for a bulk action.
  final bool selected;

  const ChatListItem(
    this.room, {
    this.activeChat = false,
    required this.onTap,
    this.onLongPress,
    this.onForget,
    this.filter,
    this.space,
    this.selected = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final plus = PlusColors.of(context);

    final isMuted = room.pushRuleState != PushRuleState.notify;
    final typingText = room.getLocalizedTypingText(context);
    final lastEvent = room.lastEvent;
    final ownMessage = lastEvent?.senderId == room.client.userID;
    final directChatMatrixId = room.directChatMatrixID;
    final isDirectChat = directChatMatrixId != null;
    final hasNotifications = room.notificationCount > 0;
    final backgroundColor = selected || activeChat ? plus.raised : null;
    // Read chats fade back so the ones that need attention stand out.
    final unread = room.isUnreadOrInvited;
    final displayname = room.getNicknameOrDisplayname(
      MatrixLocals(L10n.of(context)),
    );
    final filter = this.filter;
    if (filter != null && !displayname.toLowerCase().contains(filter)) {
      return const SizedBox.shrink();
    }

    final needLastEventSender =
        lastEvent != null &&
        room.getState(EventTypes.RoomMember, lastEvent.senderId) == null;
    final space = this.space;

    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: plus.line)),
      ),
      child: Material(
        clipBehavior: Clip.hardEdge,
        color: backgroundColor ?? Colors.transparent,
        child: FutureBuilder(
          future: room.name.isEmpty ? room.loadHeroUsers() : null,
          builder: (context, _) => HoverBuilder(
            builder: (context, listTileHovered) => ListTile(
              minVerticalPadding: 12,
              horizontalTitleGap: 12,
              contentPadding: const EdgeInsets.only(left: 18, right: 14),
              onLongPress: () => onLongPress?.call(context),
              leading: HoverBuilder(
                builder: (context, hovered) => AnimatedScale(
                  duration: FluffyThemes.animationDuration,
                  curve: FluffyThemes.animationCurve,
                  scale: hovered ? 1.1 : 1.0,
                  child: SizedBox(
                    width: 44,
                    height: 44,
                    child: Stack(
                      children: [
                        if (space != null)
                          Positioned(
                            top: 0,
                            left: 0,
                            child: Avatar(
                              shapeBorder: RoundedSuperellipseBorder(
                                side: BorderSide(
                                  width: 2,
                                  color:
                                      backgroundColor ??
                                      theme.colorScheme.surface,
                                ),
                                borderRadius: BorderRadius.circular(
                                  AppConfig.spaceBorderRadius * 0.75,
                                ),
                              ),
                              borderRadius: BorderRadius.circular(
                                AppConfig.spaceBorderRadius * 0.75,
                              ),
                              mxContent: space.avatar,
                              size: 44 * 0.75,
                              name: space.getNicknameOrDisplayname(),
                              onTap: () => onLongPress?.call(context),
                            ),
                          ),
                        Positioned(
                          bottom: 0,
                          right: 0,
                          child: ColorFiltered(
                            colorFilter: unread || selected || activeChat
                                ? const ColorFilter.mode(
                                    Colors.transparent,
                                    BlendMode.dst,
                                  )
                                : mutedColorFilter,
                            child: Avatar(
                            shapeBorder: space == null
                                ? room.isSpace
                                      ? RoundedSuperellipseBorder(
                                          side: BorderSide(
                                            width: 1,
                                            color: theme.dividerColor,
                                          ),
                                          borderRadius: BorderRadius.circular(
                                            AppConfig.spaceBorderRadius,
                                          ),
                                        )
                                      : null
                                : RoundedRectangleBorder(
                                    side: BorderSide(
                                      width: 2,
                                      color:
                                          backgroundColor ??
                                          theme.colorScheme.surface,
                                    ),
                                    borderRadius: BorderRadius.circular(44),
                                  ),
                            borderRadius: room.isSpace
                                ? BorderRadius.circular(
                                    AppConfig.spaceBorderRadius,
                                  )
                                : null,
                            mxContent: room.avatar,
                            size: space != null ? 44 * 0.75 : 44,
                            name: displayname,
                            presenceUserId: directChatMatrixId,
                            presenceBackgroundColor: backgroundColor,
                            onTap: () => onLongPress?.call(context),
                          ),
                          ),
                        ),
                        if (selected)
                          Positioned.fill(
                            child: GestureDetector(
                              onTap: () => onLongPress?.call(context),
                              child: DecoratedBox(
                                decoration: BoxDecoration(
                                  color: plus.accent,
                                  shape: BoxShape.circle,
                                ),
                                child: Icon(Icons.check, color: plus.onAccent),
                              ),
                            ),
                          ),
                        Positioned(
                          top: 0,
                          right: 0,
                          child: GestureDetector(
                            onTap: () => onLongPress?.call(context),
                            child: AnimatedScale(
                              duration: FluffyThemes.animationDuration,
                              curve: FluffyThemes.animationCurve,
                              scale: listTileHovered ? 1.0 : 0.0,
                              child: Material(
                                color: backgroundColor,
                                borderRadius: BorderRadius.circular(16),
                                child: const Icon(
                                  Icons.arrow_drop_down_circle_outlined,
                                  size: 18,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              title: Row(
                children: <Widget>[
                  Flexible(
                    child: Text(
                      displayname,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      softWrap: false,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: unread ? FontWeight.w800 : FontWeight.w600,
                        color: unread ? plus.text : plus.textMuted,
                      ),
                    ),
                  ),
                  if (room.isReadOnlyForMe)
                    Padding(
                      padding: const EdgeInsets.only(left: 5.0),
                      child: Icon(
                        Icons.lock_outline,
                        size: 14,
                        color: plus.textFaint,
                      ),
                    ),
                  if (room.client.isSnoozed(room.id))
                    Padding(
                      padding: const EdgeInsets.only(left: 5.0),
                      child: Icon(Icons.snooze, size: 14, color: plus.textFaint),
                    )
                  else if (isMuted)
                    Padding(
                      padding: const EdgeInsets.only(left: 5.0),
                      child: Icon(
                        Icons.notifications_off_outlined,
                        size: 14,
                        color: plus.textFaint,
                      ),
                    ),
                  const Spacer(),
                  if (room.isLowPriority)
                    Padding(
                      padding: EdgeInsets.only(
                        right: hasNotifications ? 4.0 : 0.0,
                      ),
                      child: Icon(
                        Icons.low_priority,
                        size: 16,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                  if (room.isFavourite)
                    Padding(
                      padding: EdgeInsets.only(
                        right: hasNotifications ? 4.0 : 0.0,
                      ),
                      child: Icon(
                        Icons.push_pin,
                        size: 16,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                  if (!room.isSpace && room.membership != Membership.invite)
                    Padding(
                      padding: const EdgeInsets.only(left: 4.0),
                      child: Text(
                        room.latestEventReceivedTime.localizedTimeShort(
                          context,
                        ),
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: plus.textFaint,
                        ),
                      ),
                    ),
                ],
              ),
              subtitle: Row(
                children: [
                  if (room.hasActiveMatrixRtcCall)
                    Padding(
                      padding: const EdgeInsets.only(right: 4.0),
                      child: ActiveCallIndicator(room: room),
                    ),
                  if (typingText.isEmpty &&
                      ownMessage &&
                      room.lastEvent?.status.isSending == true) ...[
                    const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator.adaptive(strokeWidth: 2),
                    ),
                    const SizedBox(width: 4),
                  ],
                  AnimatedSize(
                    clipBehavior: Clip.hardEdge,
                    duration: FluffyThemes.animationDuration,
                    curve: FluffyThemes.animationCurve,
                    child: typingText.isNotEmpty
                        ? Padding(
                            padding: const EdgeInsets.only(right: 4.0),
                            child: TypingAnimation(size: 4.0),
                          )
                        : room.lastEvent?.relationshipType ==
                              RelationshipTypes.thread
                        ? Container(
                            decoration: BoxDecoration(
                              border: Border.all(
                                color: theme.colorScheme.outline,
                              ),
                              borderRadius: BorderRadius.circular(
                                AppConfig.borderRadius,
                              ),
                            ),
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8.0,
                            ),
                            margin: const EdgeInsets.only(right: 4.0),
                            child: Row(
                              mainAxisSize: .min,
                              children: [
                                Icon(
                                  Icons.message_outlined,
                                  size: 12,
                                  color: theme.colorScheme.outline,
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  L10n.of(context).thread,
                                  style: TextStyle(fontSize: 11),
                                ),
                              ],
                            ),
                          )
                        : const SizedBox.shrink(),
                  ),
                  Expanded(
                    child: room.isSpace && room.membership == Membership.join
                        ? Text(
                            L10n.of(
                              context,
                            ).countChats(room.spaceChildren.length),
                            style: TextStyle(
                              fontSize: 13,
                              color: plus.textFaint,
                            ),
                          )
                        : typingText.isNotEmpty
                        ? Text(
                            typingText,
                            style: TextStyle(fontSize: 13, color: plus.accent),
                            maxLines: 1,
                            softWrap: false,
                          )
                        : FutureBuilder(
                            key: ValueKey(
                              '${lastEvent?.eventId}_${lastEvent?.type}_${lastEvent?.redacted}',
                            ),
                            future: needLastEventSender
                                ? lastEvent.fetchSenderUser()
                                : null,
                            builder: (context, snapshot) {
                              var body = lastEvent?.calcLocalizedBody(
                                MatrixLocals(L10n.of(context)),
                                hideReply: true,
                                hideEdit: true,
                                plaintextBody: true,
                                removeMarkdown: true,
                              );
                              if (lastEvent != null &&
                                  body != null &&
                                  (!isDirectChat ||
                                      directChatMatrixId !=
                                          room.lastEvent?.senderId)) {
                                body = L10n.of(context).senderMessage(
                                  lastEvent.senderId == room.client.userID
                                      ? L10n.of(context).you
                                      : snapshot.data?.calcDisplayname() ??
                                            lastEvent.senderFromMemoryOrFallback
                                                .calcDisplayname(),
                                  body,
                                );
                              }
                              return Text(
                                room.membership == Membership.invite
                                    ? room
                                              .getState(
                                                EventTypes.RoomMember,
                                                room.client.userID!,
                                              )
                                              ?.content
                                              .tryGet<String>('reason') ??
                                          (isDirectChat
                                              ? L10n.of(context).newChatRequest
                                              : L10n.of(
                                                  context,
                                                ).inviteGroupChat)
                                    : body ?? L10n.of(context).noMessagesYet,
                                softWrap: false,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  fontSize: 13,
                                  color: unread ? plus.textMuted : plus.textFaint,
                                  decoration: room.lastEvent?.redacted == true
                                      ? TextDecoration.lineThrough
                                      : null,
                                ),
                              );
                            },
                          ),
                  ),
                  const SizedBox(width: 8),
                  UnreadBubble(room: room),
                ],
              ),
              onTap: onTap,
              trailing: onForget == null
                  ? null
                  : IconButton(
                      icon: const Icon(Icons.delete_outlined),
                      onPressed: onForget,
                    ),
            ),
          ),
        ),
      ),
    );
  }
}
