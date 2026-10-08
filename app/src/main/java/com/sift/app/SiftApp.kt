package com.sift.app

import android.app.Application

/**
 * Holds the manual [AppContainer]. The API base resolves in this order:
 * 1. `local.properties` `sift.apiBase` (dev override, gitignored)
 * 2. Production Worker default (see app/build.gradle.kts).
 */
class SiftApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, BuildConfig.SIFT_API_BASE)
    }
}
