package com.tencent.kuiklybase.album

/**
 * 相册图片/视频条目数据模型
 * 支持图片和视频的统一表示
 */
data class KRAlbumImage(
    /** 唯一标识（对应 MediaStore _ID） */
    val id: String,
    /** 原图/原视频 URI */
    val uri: String,
    /** 缩略图 URI（优先使用，加载更快） */
    val thumbnailUri: String = "",
    /** 原始宽度 */
    val width: Int = 0,
    /** 原始高度 */
    val height: Int = 0,
    /** 是否为视频 */
    val isVideo: Boolean = false,
    /** 视频时长（秒），仅视频有效 */
    val duration: Int = 0,
    /** 文件大小（字节） */
    val size: Long = 0,
    /** 所属相册 ID */
    val albumId: String = ""
) {
    /**
     * 获取格式化的视频时长字符串，如 "1:23"
     */
    fun formatDuration(): String {
        if (!isVideo || duration <= 0) return ""
        val minutes = duration / 60
        val seconds = duration % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    /**
     * 获取最佳显示 URI（优先缩略图）
     */
    fun displayUri(): String {
        return thumbnailUri.ifEmpty { uri }
    }
}

/**
 * 相册信息数据模型
 */
data class KRAlbumInfo(
    /** 相册唯一标识（对应 MediaStore BUCKET_ID） */
    val id: String,
    /** 相册名称 */
    val name: String,
    /** 相册内媒体数量 */
    val count: Int = 0,
    /** 封面图 URI */
    val coverUri: String = "",
    /** 是否仅包含视频 */
    val videoOnly: Boolean = false
)
