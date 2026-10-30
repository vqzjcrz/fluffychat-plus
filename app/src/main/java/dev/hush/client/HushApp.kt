package dev.hush.client

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dev.hush.client.data.SettingsStore
import dev.hush.client.data.TokenStore
import dev.hush.client.discord.DiscordHeaders
import dev.hush.client.discord.DiscordRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class HushApp : Application(), ImageLoaderFactory {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var repository: DiscordRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
            isLenient = true
        }
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(DiscordHeaders)
            .build()

        tokenStore = TokenStore(this)
        settingsStore = SettingsStore(this)
        repository = DiscordRepository(http, json, tokenStore)

        // Connect while the app is visible; the repository disconnects a while after it's backgrounded.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = repository.onForeground()
            override fun onStop(owner: LifecycleOwner) = repository.onBackground()
        })
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
