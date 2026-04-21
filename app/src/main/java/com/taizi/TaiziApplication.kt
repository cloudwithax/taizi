package com.taizi

import android.app.Application
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TaiziApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Flycast's intent filter only accepts file:// URIs, and its native
        // code stat()s the raw URI path without URL-decoding — so %20 breaks
        // ROMs with spaces. We hand it an unencoded file:// URI, which
        // requires disabling FileUriExposedException detection.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
