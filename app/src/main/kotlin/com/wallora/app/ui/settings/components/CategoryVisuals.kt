package com.wallora.app.ui.settings.components

import androidx.compose.ui.graphics.Color
import com.wallora.app.R
import com.wallora.app.domain.model.Category

/**
 * Visual identity for each [Category] used by the redesigned Categories picker: a two-stop gradient
 * evoking the category's mood, plus the grouping of categories into labelled sections.
 *
 * Kept here (UI layer) rather than on the domain [Category] enum so Compose types don't leak into
 * the domain model. No network images — gradients render instantly and look consistent offline.
 */

/** A labelled group of categories shown as a section in the picker. */
data class CategorySection(val titleRes: Int, val categories: List<Category>)

val CATEGORY_SECTIONS: List<CategorySection> = listOf(
    CategorySection(
        R.string.settings_categories_group_vibrant,
        listOf(Category.VIBRANT, Category.ABSTRACT, Category.NEON, Category.GRADIENT, Category.AI_ART),
    ),
    CategorySection(
        R.string.settings_categories_group_nature,
        listOf(Category.NATURE, Category.LANDSCAPES, Category.CITY, Category.ARCHITECTURE, Category.ANIMALS),
    ),
    CategorySection(
        R.string.settings_categories_group_culture,
        listOf(Category.SPACE, Category.ART, Category.ANIME, Category.CARS, Category.TECHNOLOGY),
    ),
    CategorySection(
        R.string.settings_categories_group_minimal,
        listOf(Category.MINIMAL, Category.AMOLED),
    ),
)

/** Two-stop gradient (top-start → bottom-end) for a category card background. */
fun Category.gradientColors(): List<Color> = when (this) {
    Category.VIBRANT -> listOf(Color(0xFFFF4E50), Color(0xFFF9D423))
    Category.ABSTRACT -> listOf(Color(0xFF7F00FF), Color(0xFFE100FF))
    Category.NEON -> listOf(Color(0xFF00F5A0), Color(0xFF00D9F5))
    Category.GRADIENT -> listOf(Color(0xFFFC5C7D), Color(0xFF6A82FB))
    Category.AI_ART -> listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
    Category.SPACE -> listOf(Color(0xFF0F2027), Color(0xFF2C5364))
    Category.NATURE -> listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    Category.LANDSCAPES -> listOf(Color(0xFF1D976C), Color(0xFF93F9B9))
    Category.CITY -> listOf(Color(0xFF1F1C2C), Color(0xFF928DAB))
    Category.ARCHITECTURE -> listOf(Color(0xFF636363), Color(0xFFA2AB58))
    Category.ANIMALS -> listOf(Color(0xFFF7971E), Color(0xFFFFD200))
    Category.CARS -> listOf(Color(0xFFCB2D3E), Color(0xFFEF473A))
    Category.ANIME -> listOf(Color(0xFFFF6FD8), Color(0xFF3813C2))
    Category.ART -> listOf(Color(0xFFF953C6), Color(0xFFB91D73))
    Category.TECHNOLOGY -> listOf(Color(0xFF396afc), Color(0xFF2948ff))
    Category.MINIMAL -> listOf(Color(0xFFBDC3C7), Color(0xFF2C3E50))
    Category.AMOLED -> listOf(Color(0xFF232526), Color(0xFF000000))
}
