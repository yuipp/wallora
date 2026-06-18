package com.wallora.app.di

import com.wallora.app.data.remote.FlickrSource
import com.wallora.app.data.remote.NasaSource
import com.wallora.app.data.remote.OpenverseSource
import com.wallora.app.data.remote.PexelsSource
import com.wallora.app.data.remote.PixabaySource
import com.wallora.app.data.remote.RedditSource
import com.wallora.app.data.remote.UnsplashSource
import com.wallora.app.data.remote.WallhavenSource
import com.wallora.app.data.remote.WikimediaCommonsSource
import com.wallora.app.data.remote.api.FlickrApi
import com.wallora.app.data.remote.api.NasaApi
import com.wallora.app.data.remote.api.OpenverseApi
import com.wallora.app.data.remote.api.PexelsApi
import com.wallora.app.data.remote.api.PixabayApi
import com.wallora.app.data.remote.api.RedditApi
import com.wallora.app.data.remote.api.UnsplashApi
import com.wallora.app.data.remote.api.WallhavenApi
import com.wallora.app.data.remote.api.WikimediaCommonsApi
import com.wallora.app.domain.WallpaperSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun providePexelsApi(@Named(RETROFIT_PEXELS) retrofit: Retrofit): PexelsApi =
        retrofit.create(PexelsApi::class.java)

    @Provides
    @Singleton
    fun provideWallhavenApi(@Named(RETROFIT_WALLHAVEN) retrofit: Retrofit): WallhavenApi =
        retrofit.create(WallhavenApi::class.java)

    @Provides
    @Singleton
    fun provideRedditApi(@Named(RETROFIT_REDDIT) retrofit: Retrofit): RedditApi =
        retrofit.create(RedditApi::class.java)

    @Provides
    @Singleton
    fun provideUnsplashApi(@Named(RETROFIT_UNSPLASH) retrofit: Retrofit): UnsplashApi =
        retrofit.create(UnsplashApi::class.java)

    @Provides
    @Singleton
    fun providePixabayApi(@Named(RETROFIT_PIXABAY) retrofit: Retrofit): PixabayApi =
        retrofit.create(PixabayApi::class.java)

    @Provides
    @Singleton
    fun provideOpenverseApi(@Named(RETROFIT_OPENVERSE) retrofit: Retrofit): OpenverseApi =
        retrofit.create(OpenverseApi::class.java)

    @Provides
    @Singleton
    fun provideNasaApi(@Named(RETROFIT_NASA) retrofit: Retrofit): NasaApi =
        retrofit.create(NasaApi::class.java)

    @Provides
    @Singleton
    fun provideFlickrApi(@Named(RETROFIT_FLICKR) retrofit: Retrofit): FlickrApi =
        retrofit.create(FlickrApi::class.java)

    @Provides
    @Singleton
    fun provideWikimediaCommonsApi(@Named(RETROFIT_WIKIMEDIA) retrofit: Retrofit): WikimediaCommonsApi =
        retrofit.create(WikimediaCommonsApi::class.java)

    // Multibinding: set of all sources so the repository can iterate them
    @Provides
    @Singleton
    @IntoSet
    fun bindPexelsSource(source: PexelsSource): WallpaperSource = source

    @Provides
    @Singleton
    @IntoSet
    fun bindWallhavenSource(source: WallhavenSource): WallpaperSource = source

    @Provides
    @Singleton
    @IntoSet
    fun bindRedditSource(source: RedditSource): WallpaperSource = source

    @Provides
    @Singleton
    @IntoSet
    fun bindUnsplashSource(source: UnsplashSource): WallpaperSource = source

    @Provides
    @Singleton
    @IntoSet
    fun bindPixabaySource(source: PixabaySource): WallpaperSource = source

    @Provides
    @Singleton
    @IntoSet
    fun bindOpenverseSource(source: OpenverseSource): WallpaperSource = source

    @Provides
    @Singleton
    @IntoSet
    fun bindNasaSource(source: NasaSource): WallpaperSource = source

    @Provides
    @Singleton
    @IntoSet
    fun bindFlickrSource(source: FlickrSource): WallpaperSource = source

    @Provides
    @Singleton
    @IntoSet
    fun bindWikimediaCommonsSource(source: WikimediaCommonsSource): WallpaperSource = source
}
