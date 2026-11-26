// SPDX-FileCopyrightText: 2019-Present Christian Kußowski
// SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import 'package:matrix/matrix.dart';

/// Runs [request] and, when the homeserver answers "Too many requests"
/// (M_LIMIT_EXCEEDED), waits as long as the server asks and tries again, so
/// bulk actions on many chats finish instead of failing halfway.
Future<T> retryOnRateLimit<T>(
  Future<T> Function() request, {
  int maxAttempts = 20,
}) async {
  for (var attempt = 1; ; attempt++) {
    try {
      return await request();
    } on MatrixException catch (e) {
      if (e.error != MatrixError.M_LIMIT_EXCEEDED || attempt >= maxAttempts) {
        rethrow;
      }
      // Servers normally say how long to wait; otherwise back off gradually.
      final waitMs = e.retryAfterMs ?? (attempt < 7 ? attempt * 2000 : 15000);
      Logs().i('Rate limited, retrying in ${waitMs}ms (attempt $attempt)');
      await Future.delayed(Duration(milliseconds: waitMs + 250));
    }
  }
}
