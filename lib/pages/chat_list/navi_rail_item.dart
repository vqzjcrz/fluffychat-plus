// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/plus_colors.dart';
import 'package:fluffychat/widgets/hover_builder.dart';
import 'package:fluffychat/widgets/matrix.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

import '../../config/themes.dart';

/// One entry of the spaces rail: an accent bar on the left edge marks the
/// open one, and a badge shows mentions (red count) or unread chats (dot).
class NaviRailItem extends StatelessWidget {
  final String toolTip;
  final bool isSelected;
  final void Function() onTap;
  final Widget icon;
  final Widget? selectedIcon;
  final bool Function(Room)? unreadBadgeFilter;

  /// Set to false when a long press is used for something else, such as
  /// dragging to reorder. Hovering with a mouse still shows the tooltip.
  final bool tooltipOnLongPress;

  const NaviRailItem({
    required this.toolTip,
    required this.isSelected,
    required this.onTap,
    required this.icon,
    this.selectedIcon,
    this.tooltipOnLongPress = true,
    this.unreadBadgeFilter,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final plus = PlusColors.of(context);
    final icon = isSelected ? selectedIcon ?? this.icon : this.icon;
    final unreadBadgeFilter = this.unreadBadgeFilter;
    return HoverBuilder(
      builder: (context, hovered) {
        return SizedBox(
          height: 60,
          child: Stack(
            alignment: Alignment.center,
            children: [
              Positioned(
                left: 0,
                child: AnimatedContainer(
                  width: 3,
                  height: isSelected
                      ? 30
                      : hovered
                      ? 12
                      : 0,
                  duration: FluffyThemes.animationDuration,
                  curve: FluffyThemes.animationCurve,
                  decoration: BoxDecoration(
                    color: plus.accent,
                    borderRadius: const BorderRadius.horizontal(
                      right: Radius.circular(3),
                    ),
                  ),
                ),
              ),
              Tooltip(
                message: toolTip,
                triggerMode: tooltipOnLongPress
                    ? null
                    : TooltipTriggerMode.manual,
                child: AnimatedScale(
                  scale: hovered ? 1.06 : 1.0,
                  duration: FluffyThemes.animationDuration,
                  curve: FluffyThemes.animationCurve,
                  child: Material(
                    color: Colors.transparent,
                    shape: const CircleBorder(),
                    clipBehavior: Clip.none,
                    child: InkWell(
                      customBorder: const CircleBorder(),
                      onTap: onTap,
                      child: Stack(
                        clipBehavior: Clip.none,
                        children: [
                          icon,
                          if (unreadBadgeFilter != null)
                            Positioned(
                              top: -4,
                              right: -6,
                              child: RailBadge(filter: unreadBadgeFilter),
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

/// Red count of chats that mention you or invite you, or else an accent dot
/// when something is unread, or nothing.
class RailBadge extends StatelessWidget {
  final bool Function(Room) filter;

  const RailBadge({required this.filter, super.key});

  @override
  Widget build(BuildContext context) {
    final plus = PlusColors.of(context);
    final rooms = Matrix.of(context).client.rooms.where(filter);
    var mentions = 0;
    var unread = false;
    for (final room in rooms) {
      if (room.membership == Membership.invite ||
          (room.isUnread && room.highlightCount > 0)) {
        mentions++;
      } else if (room.isUnread) {
        unread = true;
      }
    }
    if (mentions > 0) {
      return Container(
        constraints: const BoxConstraints(minWidth: 20),
        height: 20,
        padding: const EdgeInsets.symmetric(horizontal: 5),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: plus.danger,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: plus.background, width: 2),
        ),
        child: Text(
          mentions > 99 ? '99+' : '$mentions',
          style: TextStyle(
            color: plus.onDanger,
            fontSize: 10,
            fontWeight: FontWeight.w800,
            height: 1,
          ),
        ),
      );
    }
    if (unread) {
      return Container(
        width: 12,
        height: 12,
        margin: const EdgeInsets.only(top: 2, right: 4),
        decoration: BoxDecoration(
          color: plus.accent,
          shape: BoxShape.circle,
          border: Border.all(color: plus.background, width: 2),
        ),
      );
    }
    return const SizedBox.shrink();
  }
}

/// The round tile behind an icon in the rail (Chats, add a space).
class RailCircle extends StatelessWidget {
  final Widget child;
  final bool selected;
  final bool dashed;

  const RailCircle({
    required this.child,
    this.selected = false,
    this.dashed = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final plus = PlusColors.of(context);
    final content = IconTheme(
      data: IconThemeData(
        color: selected ? plus.text : plus.textMuted,
        size: 21,
      ),
      child: child,
    );
    if (dashed) {
      return CustomPaint(
        painter: _DashedCirclePainter(plus.textFaint),
        child: SizedBox.square(dimension: 44, child: Center(child: content)),
      );
    }
    return AnimatedContainer(
      duration: FluffyThemes.animationDuration,
      width: 44,
      height: 44,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: selected ? plus.raised : Colors.transparent,
        border: Border.all(color: plus.line),
      ),
      child: Center(child: content),
    );
  }
}

class _DashedCirclePainter extends CustomPainter {
  final Color color;

  const _DashedCirclePainter(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2;
    final rect = Offset.zero & size;
    const dashes = 20;
    const sweep = 2 * 3.141592653589793 / dashes;
    for (var i = 0; i < dashes; i++) {
      canvas.drawArc(rect.deflate(0.6), i * sweep, sweep * 0.55, false, paint);
    }
  }

  @override
  bool shouldRepaint(_DashedCirclePainter oldDelegate) =>
      oldDelegate.color != color;
}

/// Shows a space's avatar faded to gray unless it's the open one, which gets
/// a ring instead.
class RailSpaceAvatar extends StatelessWidget {
  final Widget avatar;
  final bool selected;

  const RailSpaceAvatar({
    required this.avatar,
    required this.selected,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final plus = PlusColors.of(context);
    if (selected) {
      return Container(
        padding: const EdgeInsets.all(2),
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(color: plus.text, width: 2),
        ),
        child: avatar,
      );
    }
    return Padding(
      padding: const EdgeInsets.all(4),
      child: ColorFiltered(colorFilter: mutedColorFilter, child: avatar),
    );
  }
}
