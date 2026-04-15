package com.tencent.kuiklybase.album

data class KRAlbumImage(
    val id: String,
    val uri: String,
    val thumbnailUri: String = "",
    val width: Int = 0,
    val height: Int = 0
)
