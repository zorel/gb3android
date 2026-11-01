package org.zorel.gb3android

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

class Gb3Application : Application(), ImageLoaderFactory {
    /**
     * Animated totoz are common, so teach Coil's singleton loader to decode
     * them. ImageDecoder also covers animated WebP on Android 9+; older
     * releases fall back to the GIF decoder.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}
