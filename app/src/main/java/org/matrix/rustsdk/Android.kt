package org.matrix.rustsdk

import android.content.Context

/**
 * JNI entry point exported by the Matrix SDK's native library
 * (`Java_org_matrix_rustsdk_Android_init`). Sets up the JavaVM and
 * rustls-platform-verifier; must run before `initPlatform`.
 */
object Android {
    @JvmStatic
    external fun init(context: Context)
}
