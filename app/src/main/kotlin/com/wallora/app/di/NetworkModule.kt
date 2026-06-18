package com.wallora.app.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.wallora.app.BuildConfig
import com.wallora.app.data.remote.interceptor.RedditAuthInterceptor
import com.wallora.app.data.remote.interceptor.ThrottleInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private const val TIMEOUT_SECONDS = 30L

/** Hilt qualifier names for per-source Retrofit instances. */
const val RETROFIT_PEXELS = "pexels"
const val RETROFIT_WALLHAVEN = "wallhaven"
const val RETROFIT_REDDIT = "reddit"
const val RETROFIT_UNSPLASH = "unsplash"
const val RETROFIT_PIXABAY = "pixabay"
const val RETROFIT_OPENVERSE = "openverse"
const val RETROFIT_NASA = "nasa"
const val RETROFIT_FLICKR = "flickr"
const val RETROFIT_WIKIMEDIA = "wikimedia"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Shared OkHttpClient for full-res image downloads.
     * 150 MB disk cache so subsequent rotations to the same image are instant.
     * A network interceptor forces Cache-Control: max-age=86400 on responses whose
     * servers don't set cache headers (some CDNs omit them on direct URL hits).
     */
    @Singleton
    @Provides
    fun provideDownloadClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, "wallpaper_http_cache"), 150L * 1024L * 1024L))
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                // Honour server headers; only inject if missing
                if (response.header("Cache-Control") == null) {
                    response.newBuilder()
                        .header("Cache-Control", "max-age=86400")
                        .build()
                } else response
            }
            .build()

    @Singleton
    @Provides
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun buildLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }

    private fun baseClientBuilder(throttleMs: Long = 500L): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(buildLoggingInterceptor())
            .addInterceptor(ThrottleInterceptor(throttleMs))

    @Singleton
    @Provides
    @Named(RETROFIT_PEXELS)
    fun providePexelsRetrofit(json: Json, userKeyCache: UserKeyCache): Retrofit {
        val client = baseClientBuilder(throttleMs = 500L)
            .addInterceptor { chain ->
                // Read current effective key at request time (volatile — updated when user changes key)
                val key = userKeyCache.effectivePexelsKey
                val request = if (key.isNotBlank()) {
                    chain.request().newBuilder().header("Authorization", key).build()
                } else chain.request()
                chain.proceed(request)
            }.build()
        return Retrofit.Builder()
            .baseUrl("https://api.pexels.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Singleton
    @Provides
    @Named(RETROFIT_WALLHAVEN)
    fun provideWallhavenRetrofit(json: Json, userKeyCache: UserKeyCache): Retrofit {
        val client = baseClientBuilder(throttleMs = 1_000L)
            .addInterceptor { chain ->
                val key = userKeyCache.effectiveWallhavenKey
                val req = chain.request().newBuilder().apply {
                    if (key.isNotBlank()) header("X-API-Key", key)
                }.build()
                chain.proceed(req)
            }.build()
        return Retrofit.Builder()
            .baseUrl("https://wallhaven.cc/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    /**
     * Bare OkHttpClient used by [RedditAuthInterceptor] to fetch OAuth tokens.
     * Must NOT contain the Reddit auth interceptor (avoids circular dependency).
     */
    @Singleton
    @Provides
    @Named("reddit_token_client")
    fun provideRedditTokenClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(buildLoggingInterceptor())
            .build()

    @Singleton
    @Provides
    @Named(RETROFIT_REDDIT)
    fun provideRedditRetrofit(
        json: Json,
        redditAuthInterceptor: RedditAuthInterceptor,
    ): Retrofit {
        // Base URL is oauth.reddit.com — the auth interceptor adds Bearer token + User-Agent.
        val client = baseClientBuilder(throttleMs = 2_000L) // polite for Reddit
            .addInterceptor(redditAuthInterceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://oauth.reddit.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Singleton
    @Provides
    @Named(RETROFIT_UNSPLASH)
    fun provideUnsplashRetrofit(json: Json, userKeyCache: UserKeyCache): Retrofit {
        val client = baseClientBuilder(throttleMs = 1_200L)
            .addInterceptor { chain ->
                val key = userKeyCache.effectiveUnsplashKey
                val req = chain.request().newBuilder()
                    .header("Accept-Version", "v1")
                    .apply { if (key.isNotBlank()) header("Authorization", "Client-ID $key") }
                    .build()
                chain.proceed(req)
            }.build()
        return Retrofit.Builder()
            .baseUrl("https://api.unsplash.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Singleton
    @Provides
    @Named(RETROFIT_PIXABAY)
    fun providePixabayRetrofit(json: Json): Retrofit {
        // Pixabay key travels as a query param (?key=…), not a header, so no auth interceptor needed.
        val client = baseClientBuilder(throttleMs = 800L).build()
        return Retrofit.Builder()
            .baseUrl("https://pixabay.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Singleton
    @Provides
    @Named(RETROFIT_OPENVERSE)
    fun provideOpenverseRetrofit(json: Json): Retrofit {
        // Openverse is keyless — anonymous access with polite throttling.
        val client = baseClientBuilder(throttleMs = 600L)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Wallora/1.4 (Android; com.wallora.app)")
                    .build()
                chain.proceed(req)
            }.build()
        return Retrofit.Builder()
            .baseUrl("https://api.openverse.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Singleton
    @Provides
    @Named(RETROFIT_NASA)
    fun provideNasaRetrofit(json: Json): Retrofit {
        // NASA Image Library — no API key, generous rate limits.
        val client = baseClientBuilder(throttleMs = 500L).build()
        return Retrofit.Builder()
            .baseUrl("https://images-api.nasa.gov/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Singleton
    @Provides
    @Named(RETROFIT_FLICKR)
    fun provideFlickrRetrofit(json: Json): Retrofit {
        // Flickr key is a query param (passed by FlickrSource) — no auth interceptor needed.
        val client = baseClientBuilder(throttleMs = 800L).build()
        return Retrofit.Builder()
            .baseUrl("https://api.flickr.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Singleton
    @Provides
    @Named(RETROFIT_WIKIMEDIA)
    fun provideWikimediaRetrofit(json: Json): Retrofit {
        // Wikimedia requires a descriptive User-Agent per their usage policy.
        val client = baseClientBuilder(throttleMs = 600L)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Wallora/1.4 (https://github.com/wallora; ranjandeo@gmail.com)")
                    .build()
                chain.proceed(req)
            }.build()
        return Retrofit.Builder()
            .baseUrl("https://commons.wikimedia.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
