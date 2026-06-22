package com.carspotter.features.post

enum class PostSource {
    CAMERA,
    GALLERY;

    companion object {
        fun fromStringOrGallery(value: String?): PostSource =
            if (value != null) entries.firstOrNull { it.name == value.uppercase() } ?: GALLERY
            else GALLERY
    }
}
