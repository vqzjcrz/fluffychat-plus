// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:fluffychat/config/app_config.dart';
import 'package:fluffychat/config/setting_keys.dart';
import 'package:fluffychat/pages/image_viewer/image_viewer.dart';
import 'package:fluffychat/utils/instagram_reel.dart';
import 'package:fluffychat/utils/matrix_sdk_extensions/event_extension.dart';
import 'package:fluffychat/utils/platform_infos.dart';
import 'package:fluffychat/widgets/avatar.dart';
import 'package:fluffychat/widgets/blur_hash.dart';
import 'package:fluffychat/widgets/mxc_image.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';

/// A reel in the chat, shown as a tall card like a reel shared in an
/// Instagram DM. Tapping it plays it in the normal media viewer.
class ReelBubble extends StatelessWidget {
  final Event event;
  final Timeline? timeline;

  const ReelBubble(this.event, {this.timeline, super.key});

  static const double width = 200;
  static const double height = width * 16 / 9;
  static const String fallbackBlurHash = 'L5H2EC=PM+yV0g-mq.wG9c010J}I';

  @override
  Widget build(BuildContext context) {
    final reel = InstagramReel.fromEvent(event);
    final author = reel?.author ?? event.senderFromMemoryOrFallback.calcDisplayname();
    final caption = reel?.caption ?? '';
    final blurHash =
        (event.infoMap as Map<String, dynamic>).tryGet<String>(
          'xyz.amorgan.blurhash',
        ) ??
        fallbackBlurHash;
    const shadow = [Shadow(blurRadius: 6, color: Color(0x99000000))];

    return Material(
      color: Colors.black,
      borderRadius: BorderRadius.circular(AppConfig.borderRadius),
      clipBehavior: Clip.hardEdge,
      child: InkWell(
        // Opens in the normal media viewer, like any other video.
        onTap: () => PlatformInfos.supportsVideoPlayer
            ? showDialog(
                context: context,
                builder: (_) => ImageViewer(
                  event,
                  timeline: timeline,
                  outerContext: context,
                ),
              )
            : event.saveFile(context),
        child: SizedBox(
          width: width,
          height: height,
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (event.hasThumbnail &&
                  AppSettings.showThumbnailsInTimeline.value)
                MxcImage(
                  event: event,
                  cacheKey: event.transactionId ?? event.eventId,
                  cacheName: event.room.id,
                  isThumbnail: true,
                  width: width,
                  height: height,
                  fit: BoxFit.cover,
                  placeholder: (context) => BlurHash(
                    blurhash: blurHash,
                    width: width,
                    height: height,
                    fit: BoxFit.cover,
                  ),
                )
              else
                const DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        Color(0xFF833AB4),
                        Color(0xFFC13584),
                        Color(0xFFF77737),
                      ],
                    ),
                  ),
                ),
              const DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    stops: [0, 0.25, 0.6, 1],
                    colors: [
                      Color(0x99000000),
                      Color(0x00000000),
                      Color(0x00000000),
                      Color(0xAA000000),
                    ],
                  ),
                ),
              ),
              Positioned(
                top: 10,
                left: 10,
                right: 10,
                child: Row(
                  children: [
                    Avatar(
                      mxContent: reel?.authorAvatar,
                      name: author,
                      size: 24,
                      client: event.room.client,
                    ),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        author,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          shadows: shadow,
                        ),
                      ),
                    ),
                    const SizedBox(width: 4),
                    const Icon(
                      Icons.movie_outlined,
                      color: Colors.white,
                      size: 18,
                      shadows: shadow,
                    ),
                  ],
                ),
              ),
              Center(
                child: Icon(
                  PlatformInfos.supportsVideoPlayer
                      ? Icons.play_arrow_rounded
                      : Icons.file_download_outlined,
                  color: Colors.white,
                  size: 56,
                  shadows: const [Shadow(blurRadius: 16)],
                ),
              ),
              if (caption.isNotEmpty || reel?.source != null)
                Positioned(
                  left: 10,
                  right: 10,
                  bottom: 10,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: .min,
                    children: [
                      if (caption.isNotEmpty)
                        Text(
                          caption,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 12.5,
                            height: 1.25,
                            shadows: shadow,
                          ),
                        ),
                      if (reel?.source != null)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(
                            reel!.source!,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: Color(0xCCFFFFFF),
                              fontSize: 11.5,
                              fontWeight: FontWeight.w600,
                              shadows: shadow,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
