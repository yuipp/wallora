package com.wallora.app.ui.favorites

import androidx.lifecycle.ViewModel
import com.wallora.app.data.repository.WallpaperRepository
import com.wallora.app.domain.model.Wallpaper
import com.wallora.app.domain.usecase.SaveFavoritesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    repository: WallpaperRepository,
    private val saveFavorites: SaveFavoritesUseCase,
) : ViewModel() {
    val favorites = repository.observeFavorites()

    val saveProgress = saveFavorites.progress

    fun saveAll(wallpapers: List<Wallpaper>) = saveFavorites.start(wallpapers)

    fun dismissSaveResult() = saveFavorites.dismiss()
}
