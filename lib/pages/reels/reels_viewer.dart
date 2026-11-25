// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'dart:io';
import 'dart:math';

import 'package:fluffychat/l10n/l10n.dart';
import 'package:fluffychat/utils/date_time_extension.dart';
import 'package:fluffychat/utils/fluffy_share.dart';
import 'package:fluffychat/utils/instagram_reel.dart';
import 'package:fluffychat/utils/localized_exception_extension.dart';
import 'package:fluffychat/utils/matrix_sdk_extensions/event_extension.dart';
import 'package:fluffychat/utils/url_launcher.dart';
import 'package:fluffychat/widgets/adaptive_dialogs/show_modal_action_popup.dart';
import 'package:fluffychat/widgets/avatar.dart';
import 'package:fluffychat/widgets/mxc_image.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:matrix/matrix.dart';
import 'package:path_provider/path_provider.dart';
import 'package:url_launcher/url_launcher_string.dart';
import 'package:video_player/video_player.dart';

const Color _likeRed = Color(0xFFFF3040);

/// Opens the reels of [timeline] full screen at [event], laid out like the
/// Reels tab on Instagram: one reel per screen, swipe up for the next one.
Future<void> showReelsViewer(
  BuildContext context, {
  required Event event,
  Timeline? timeline,
}) => Navigator.of(context, rootNavigator: true).push(
  PageRouteBuilder<void>(
    pageBuilder: (routeContext, animation, secondaryAnimation) => ReelsViewer(
      initialEvent: event,
      timeline: timeline,
      outerContext: context,
    ),
    transitionsBuilder: (routeContext, animation, secondaryAnimation, child) =>
        FadeTransition(opacity: animation, child: child),
  ),
);

class ReelsViewer extends StatefulWidget {
  final Event initialEvent;
  final Timeline? timeline;

  /// The chat the viewer was opened from, for links that leave the viewer.
  final BuildContext outerContext;

  const ReelsViewer({
    required this.initialEvent,
    required this.timeline,
    required this.outerContext,
    super.key,
  });

  @override
  State<ReelsViewer> createState() => _ReelsViewerState();
}

class _ReelsViewerState extends State<ReelsViewer> {
  late List<InstagramReel> _reels;
  late final PageController _pageController;
  int _index = 0;
  bool _muted = false;
  bool _loadingOlder = false;

  @override
  void initState() {
    super.initState();
    _reels = _collectReels();
    _index = max(
      0,
      _reels.indexWhere((reel) => reel.matches(widget.initialEvent)),
    );
    _pageController = PageController(initialPage: _index);
    _maybeLoadOlder();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  List<InstagramReel> _collectReels() {
    final timeline = widget.timeline;
    final reels = timeline == null
        ? <InstagramReel>[]
        : InstagramReel.fromTimeline(timeline);
    if (!reels.any((reel) => reel.matches(widget.initialEvent))) {
      final reel = InstagramReel.fromEvent(widget.initialEvent);
      if (reel != null) return [reel];
    }
    return reels;
  }

  void _onPageChanged(int index) {
    setState(() => _index = index);
    _maybeLoadOlder();
  }

  /// Older reels go at the end, so loading them never moves the current one.
  Future<void> _maybeLoadOlder() async {
    final timeline = widget.timeline;
    if (timeline == null || _loadingOlder || _reels.isEmpty) return;
    if (_index < _reels.length - 2 || !timeline.canRequestHistory) return;
    _loadingOlder = true;
    try {
      await timeline.requestHistory();
    } catch (e, s) {
      Logs().w('Unable to load older reels', e, s);
    }
    _loadingOlder = false;
    if (!mounted) return;
    final current = _reels[_index];
    final reels = _collectReels();
    if (reels.length <= _reels.length) return;
    if (reels.indexWhere((reel) => reel.matches(current.event)) != _index) {
      return;
    }
    setState(() => _reels = reels);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light.copyWith(
        statusBarColor: Colors.transparent,
        systemNavigationBarColor: Colors.black,
      ),
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Stack(
          children: [
            PageView.builder(
              controller: _pageController,
              scrollDirection: Axis.vertical,
              allowImplicitScrolling: true,
              itemCount: _reels.length,
              onPageChanged: _onPageChanged,
              itemBuilder: (context, i) => _ReelPage(
                key: ValueKey(_reels[i].event.eventId),
                reel: _reels[i],
                timeline: widget.timeline,
                outerContext: widget.outerContext,
                active: i == _index,
                muted: _muted,
              ),
            ),
            SafeArea(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: Row(
                  children: [
                    IconButton(
                      icon: const Icon(Icons.arrow_back),
                      color: Colors.white,
                      tooltip: MaterialLocalizations.of(
                        context,
                      ).backButtonTooltip,
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                    Text(
                      l10n.reels,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                        shadows: [Shadow(blurRadius: 8)],
                      ),
                    ),
                    const Spacer(),
                    IconButton(
                      icon: Icon(
                        _muted ? Icons.volume_off : Icons.volume_up,
                        shadows: const [Shadow(blurRadius: 8)],
                      ),
                      color: Colors.white,
                      onPressed: () => setState(() => _muted = !_muted),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ReelPage extends StatefulWidget {
  final InstagramReel reel;
  final Timeline? timeline;
  final BuildContext outerContext;
  final bool active;
  final bool muted;

  const _ReelPage({
    required this.reel,
    required this.timeline,
    required this.outerContext,
    required this.active,
    required this.muted,
    super.key,
  });

  @override
  State<_ReelPage> createState() => _ReelPageState();
}

class _ReelPageState extends State<_ReelPage>
    with SingleTickerProviderStateMixin {
  VideoPlayerController? _controller;
  Object? _error;
  double? _downloadProgress;
  bool _paused = false;
  bool _captionExpanded = false;
  bool? _likedOverride;
  String? _sentLikeId;
  Offset? _heartPosition;
  late final AnimationController _heartAnimation;

  @override
  void initState() {
    super.initState();
    _heartAnimation = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );
    _load();
  }

  @override
  void didUpdateWidget(_ReelPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.active != widget.active && !widget.active) {
      // Like Instagram: a reel starts over when you come back to it.
      _paused = false;
      _controller?.seekTo(Duration.zero);
    }
    if (oldWidget.active != widget.active || oldWidget.muted != widget.muted) {
      _syncPlayback();
    }
  }

  @override
  void dispose() {
    _heartAnimation.dispose();
    _controller?.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final event = widget.reel.displayEvent;
      final size = event.content
          .tryGetMap<String, Object?>('info')
          ?.tryGet<int>('size');
      final file = await event.downloadAndDecryptAttachment(
        onDownloadProgress: size == null || size <= 0
            ? null
            : (bytes) {
                if (!mounted) return;
                setState(
                  () => _downloadProgress = (bytes / size).clamp(0.0, 1.0),
                );
              },
      );
      if (!mounted) return;

      final VideoPlayerController controller;
      if (kIsWeb) {
        controller = VideoPlayerController.networkUrl(
          Uri.dataFromBytes(file.bytes, mimeType: file.mimeType),
        );
      } else {
        final tempDir = await getTemporaryDirectory();
        final name = Uri.encodeComponent(
          event.attachmentMxcUrl?.pathSegments.last ?? event.eventId,
        );
        final videoFile = File('${tempDir.path}/reel_$name.mp4');
        if (!await videoFile.exists()) {
          await videoFile.writeAsBytes(file.bytes);
        }
        controller = VideoPlayerController.file(videoFile);
      }
      await controller.initialize();
      await controller.setLooping(true);
      if (!mounted) {
        await controller.dispose();
        return;
      }
      setState(() => _controller = controller);
      _syncPlayback();
    } catch (e, s) {
      Logs().w('Unable to load reel', e, s);
      if (!mounted) return;
      setState(() => _error = e);
    }
  }

  void _syncPlayback() {
    final controller = _controller;
    if (controller == null || !controller.value.isInitialized) return;
    controller.setVolume(widget.muted ? 0 : 1);
    if (widget.active && !_paused) {
      controller.play();
    } else {
      controller.pause();
    }
  }

  void _togglePause() {
    if (_controller == null) return;
    setState(() => _paused = !_paused);
    _syncPlayback();
  }

  bool get _liked =>
      _likedOverride ?? widget.reel.ownLike(widget.timeline) != null;

  Future<void> _setLiked(bool like) async {
    final wasLiked = _liked;
    if (wasLiked == like) return;
    final reel = widget.reel;
    setState(() => _likedOverride = like);
    try {
      if (like) {
        _sentLikeId = await reel.event.room.sendReaction(
          reel.event.eventId,
          InstagramReel.likeKey,
        );
      } else {
        final own = reel.ownLike(widget.timeline);
        final sentLikeId = _sentLikeId;
        if (own != null) {
          await own.redactEvent();
        } else if (sentLikeId != null) {
          await reel.event.room.redactEvent(sentLikeId);
        }
        _sentLikeId = null;
      }
    } catch (e, s) {
      Logs().w('Unable to change like', e, s);
      if (!mounted) return;
      setState(() => _likedOverride = wasLiked);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(e.toLocalizedString(context))));
    }
  }

  void _onDoubleTap() {
    _heartAnimation.forward(from: 0);
    _setLiked(true);
  }

  Future<void> _comment() async {
    final wasPlaying = _controller?.value.isPlaying ?? false;
    await _controller?.pause();
    if (!mounted) return;
    final text = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useRootNavigator: true,
      builder: (context) => _CommentSheet(author: widget.reel.author),
    );
    if (wasPlaying && mounted) _syncPlayback();
    if (text == null || text.trim().isEmpty || !mounted) return;
    final l10n = L10n.of(context);
    final scaffoldMessenger = ScaffoldMessenger.of(context);
    try {
      await widget.reel.event.room.sendTextEvent(
        text.trim(),
        inReplyTo: widget.reel.event,
      );
      scaffoldMessenger.showSnackBar(SnackBar(content: Text(l10n.replySent)));
    } catch (e, s) {
      Logs().w('Unable to send reply to reel', e, s);
      if (!mounted) return;
      scaffoldMessenger.showSnackBar(
        SnackBar(content: Text(e.toLocalizedString(context))),
      );
    }
  }

  Future<void> _share() async {
    final url = widget.reel.url;
    if (url == null) return;
    await FluffyShare.share(url, context);
  }

  Future<void> _openOnInstagram() async {
    final url = widget.reel.url;
    if (url == null) return;
    await launchUrlString(url, mode: LaunchMode.externalApplication);
  }

  Future<void> _more() async {
    final l10n = L10n.of(context);
    final reel = widget.reel;
    final action = await showModalActionPopup<_ReelAction>(
      context: context,
      title: reel.author,
      cancelLabel: l10n.cancel,
      actions: [
        if (reel.url != null)
          AdaptiveModalAction(
            label: l10n.openOnInstagram,
            value: _ReelAction.openOnInstagram,
            icon: const Icon(Icons.open_in_new),
          ),
        if (reel.originalLink != null)
          AdaptiveModalAction(
            label: l10n.goToOriginalChat,
            value: _ReelAction.goToOriginal,
            icon: const Icon(Icons.forum_outlined),
          ),
        AdaptiveModalAction(
          label: l10n.saveFile,
          value: _ReelAction.save,
          icon: const Icon(Icons.download_outlined),
        ),
      ],
    );
    if (action == null || !mounted) return;
    final outerContext = widget.outerContext;
    switch (action) {
      case _ReelAction.openOnInstagram:
        await _openOnInstagram();
      case _ReelAction.goToOriginal:
        final link = reel.originalLink;
        if (link == null) return;
        Navigator.of(context).pop();
        if (!outerContext.mounted) return;
        await UrlLauncher(outerContext, link).launchUrl();
      case _ReelAction.save:
        await reel.displayEvent.saveFile(context);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final reel = widget.reel;
    final controller = _controller;
    final initialized = controller != null && controller.value.isInitialized;
    final bottomInset = MediaQuery.paddingOf(context).bottom;
    final reactionCount = reel.reactionCount(widget.timeline);
    final liked = _liked;

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: _togglePause,
      onDoubleTapDown: (details) =>
          setState(() => _heartPosition = details.localPosition),
      onDoubleTap: _onDoubleTap,
      onLongPressStart: (_) => controller?.pause(),
      onLongPressEnd: (_) => _syncPlayback(),
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (initialized)
            _video(controller)
          else if (reel.displayEvent.hasThumbnail)
            MxcImage(
              event: reel.displayEvent,
              isThumbnail: true,
              fit: BoxFit.cover,
            ),
          const Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            height: 320,
            child: IgnorePointer(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                    colors: [Color(0xB3000000), Color(0x00000000)],
                  ),
                ),
              ),
            ),
          ),
          const Positioned(
            left: 0,
            right: 0,
            top: 0,
            height: 140,
            child: IgnorePointer(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Color(0x80000000), Color(0x00000000)],
                  ),
                ),
              ),
            ),
          ),
          if (!initialized && _error == null)
            Center(
              child: CircularProgressIndicator.adaptive(
                value: _downloadProgress,
                valueColor: const AlwaysStoppedAnimation(Colors.white),
              ),
            ),
          if (_error != null)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Column(
                  mainAxisSize: .min,
                  children: [
                    const Icon(
                      Icons.error_outline,
                      color: Colors.white,
                      size: 48,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      l10n.couldNotPlayReel,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.white),
                    ),
                    if (reel.url != null)
                      TextButton(
                        onPressed: _openOnInstagram,
                        child: Text(l10n.openOnInstagram),
                      ),
                  ],
                ),
              ),
            ),
          if (_paused)
            const IgnorePointer(
              child: Center(
                child: Icon(
                  Icons.play_arrow_rounded,
                  size: 96,
                  color: Color(0xDDFFFFFF),
                  shadows: [Shadow(blurRadius: 16)],
                ),
              ),
            ),
          _heart(context),
          Positioned(
            right: 4,
            bottom: bottomInset + 28,
            child: Column(
              mainAxisSize: .min,
              children: [
                _ActionButton(
                  icon: liked ? Icons.favorite : Icons.favorite_border,
                  color: liked ? _likeRed : Colors.white,
                  label: reactionCount > 0 ? '$reactionCount' : null,
                  onPressed: () => _setLiked(!liked),
                ),
                _ActionButton(
                  icon: Icons.mode_comment_outlined,
                  onPressed: _comment,
                ),
                if (reel.url != null)
                  _ActionButton(
                    icon: Icons.send_outlined,
                    angle: -0.4,
                    onPressed: _share,
                  ),
                _ActionButton(icon: Icons.more_horiz, onPressed: _more),
              ],
            ),
          ),
          Positioned(
            left: 16,
            right: 72,
            bottom: bottomInset + 28,
            child: _info(context),
          ),
          if (initialized)
            Positioned(
              left: 0,
              right: 0,
              bottom: bottomInset,
              child: VideoProgressIndicator(
                controller,
                allowScrubbing: true,
                padding: const EdgeInsets.only(top: 12),
                colors: const VideoProgressColors(
                  playedColor: Colors.white,
                  bufferedColor: Color(0x55FFFFFF),
                  backgroundColor: Color(0x33FFFFFF),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _video(VideoPlayerController controller) {
    final size = controller.value.size;
    if (size.isEmpty) {
      return Center(
        child: AspectRatio(
          aspectRatio: controller.value.aspectRatio,
          child: VideoPlayer(controller),
        ),
      );
    }
    // Portrait reels fill the screen like on Instagram; anything wider fits.
    return FittedBox(
      fit: size.height >= size.width ? BoxFit.cover : BoxFit.contain,
      clipBehavior: Clip.hardEdge,
      child: SizedBox(
        width: size.width,
        height: size.height,
        child: VideoPlayer(controller),
      ),
    );
  }

  Widget _heart(BuildContext context) {
    final position = _heartPosition;
    return AnimatedBuilder(
      animation: _heartAnimation,
      builder: (context, _) {
        final t = _heartAnimation.value;
        if (t == 0 || t == 1) return const SizedBox.shrink();
        final scale = t < 0.25
            ? Curves.easeOutBack.transform(t / 0.25) * 1.2
            : 1.2 - 0.2 * ((t - 0.25) / 0.75);
        final opacity = t < 0.7 ? 1.0 : 1 - (t - 0.7) / 0.3;
        const size = 110.0;
        final icon = Opacity(
          opacity: opacity.clamp(0.0, 1.0),
          child: Transform.scale(
            scale: scale,
            child: const Icon(
              Icons.favorite,
              size: size,
              color: Colors.white,
              shadows: [Shadow(blurRadius: 24, color: Color(0x66000000))],
            ),
          ),
        );
        if (position == null) {
          return IgnorePointer(child: Center(child: icon));
        }
        return Positioned(
          left: position.dx - size / 2,
          top: position.dy - size / 2,
          child: IgnorePointer(child: icon),
        );
      },
    );
  }

  Widget _info(BuildContext context) {
    final reel = widget.reel;
    final caption = reel.caption;
    const shadow = [Shadow(blurRadius: 6, color: Color(0x99000000))];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: .min,
      children: [
        Row(
          children: [
            Avatar(
              mxContent: reel.authorAvatar,
              name: reel.author,
              size: 32,
              client: reel.event.room.client,
            ),
            const SizedBox(width: 10),
            Flexible(
              child: Text(
                reel.author,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  shadows: shadow,
                ),
              ),
            ),
            if (reel.source != null) ...[
              const SizedBox(width: 8),
              Flexible(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    border: Border.all(color: const Color(0xCCFFFFFF)),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    reel.source!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
        if (caption.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: GestureDetector(
              onTap: () => setState(() => _captionExpanded = !_captionExpanded),
              child: Text(
                caption,
                maxLines: _captionExpanded ? 12 : 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  height: 1.3,
                  shadows: shadow,
                ),
              ),
            ),
          ),
        const SizedBox(height: 8),
        Row(
          children: [
            const Icon(Icons.schedule, size: 14, color: Color(0xCCFFFFFF)),
            const SizedBox(width: 6),
            Text(
              reel.event.originServerTs.localizedTimeShort(context),
              style: const TextStyle(
                color: Color(0xCCFFFFFF),
                fontSize: 12,
                shadows: shadow,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

enum _ReelAction { openOnInstagram, goToOriginal, save }

class _ActionButton extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String? label;
  final double angle;
  final VoidCallback onPressed;

  const _ActionButton({
    required this.icon,
    required this.onPressed,
    this.color = Colors.white,
    this.label,
    this.angle = 0,
  });

  @override
  Widget build(BuildContext context) {
    final label = this.label;
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Column(
        mainAxisSize: .min,
        children: [
          IconButton(
            onPressed: onPressed,
            icon: Transform.rotate(
              angle: angle,
              child: Icon(
                icon,
                size: 30,
                color: color,
                shadows: const [Shadow(blurRadius: 8)],
              ),
            ),
          ),
          if (label != null)
            Text(
              label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 13,
                fontWeight: FontWeight.w600,
                shadows: [Shadow(blurRadius: 6)],
              ),
            ),
        ],
      ),
    );
  }
}

/// Instagram-style comment box. A comment is sent as a reply to the reel,
/// which the reels hub passes on to the chat it came from.
class _CommentSheet extends StatefulWidget {
  final String author;

  const _CommentSheet({required this.author});

  @override
  State<_CommentSheet> createState() => _CommentSheetState();
}

class _CommentSheetState extends State<_CommentSheet> {
  final TextEditingController _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _send() => Navigator.of(context).pop(_controller.text);

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.viewInsetsOf(context).bottom,
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 8, 12),
          child: Column(
            mainAxisSize: .min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                l10n.replyToReel(widget.author),
                style: theme.textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _controller,
                      autofocus: true,
                      minLines: 1,
                      maxLines: 4,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _send(),
                      decoration: InputDecoration(
                        hintText: l10n.addComment,
                        filled: true,
                        border: OutlineInputBorder(
                          borderSide: BorderSide.none,
                          borderRadius: BorderRadius.circular(24),
                        ),
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 10,
                        ),
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.send_outlined),
                    color: theme.colorScheme.primary,
                    tooltip: l10n.send,
                    onPressed: _send,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
