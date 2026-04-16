package com.tencent.kuiklybase.album.android

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONArray
import org.json.JSONObject

class KRAlbumModule : KuiklyRenderBaseModule() {

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "requestPermission" -> { requestPermission(callback); null }
            "checkPermission" -> checkPermission()
            "fetchImages" -> { fetchImages(params, callback); null }
            "fetchAlbums" -> { fetchAlbums(callback); null }
            "fetchImagesFromAlbum" -> { fetchImagesFromAlbum(params, callback); null }
            "fetchMetadata" -> { fetchMetadata(params, callback); null }
            "requestThumbnail" -> { requestThumbnail(params, callback); null }
            "cancelThumbnailRequest" -> { cancelThumbnailRequest(params); null }
            else -> null
        }
    }

    private fun requestPermission(callback: KuiklyRenderCallback?) {
        val ctx = context ?: run {
            callback?.invoke(JSONObject().put("granted", false).put("message", "Context is null").toString())
            return
        }
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED) {
            callback?.invoke(JSONObject().put("granted", true).toString())
            return
        }
        val act = activity
        if (act != null) {
            ActivityCompat.requestPermissions(act, arrayOf(permission), PERMISSION_REQUEST_CODE)
            pendingPermissionCallback = callback
        } else {
            callback?.invoke(JSONObject().put("granted", false).put("message", "Activity is null").toString())
        }
    }

    private fun checkPermission(): String {
        val ctx = context ?: return "denied"
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return if (ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED) "granted" else "denied"
    }

    private fun fetchImages(params: String?, callback: KuiklyRenderCallback?) {
        val maxCount = params?.let {
            try { JSONObject(it).optInt("maxCount", Int.MAX_VALUE) } catch (_: Exception) { Int.MAX_VALUE }
        } ?: Int.MAX_VALUE
        Thread {
            val images = queryImages(null, maxCount)
            Handler(Looper.getMainLooper()).post {
                callback?.invoke(JSONObject().put("data", images).toString())
            }
        }.start()
    }

    private fun fetchAlbums(callback: KuiklyRenderCallback?) {
        Thread {
            val ctx = context ?: run {
                Handler(Looper.getMainLooper()).post {
                    callback?.invoke(JSONObject().put("data", JSONArray()).toString())
                }
                return@Thread
            }
            val albums = JSONArray()
            val albumMap = linkedMapOf<String, Pair<String, Int>>()
            val projection = arrayOf(
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            val cursor = ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val bucketId = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID))
                    val bucketName = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)) ?: "Unknown"
                    val existing = albumMap[bucketId]
                    albumMap[bucketId] = Pair(bucketName, (existing?.second ?: 0) + 1)
                }
            }
            for ((id, pair) in albumMap) {
                albums.put(JSONObject().apply {
                    put("id", id)
                    put("name", pair.first)
                    put("count", pair.second)
                })
            }
            Handler(Looper.getMainLooper()).post {
                callback?.invoke(JSONObject().put("data", albums).toString())
            }
        }.start()
    }

    private fun fetchImagesFromAlbum(params: String?, callback: KuiklyRenderCallback?) {
        val json = params?.let { try { JSONObject(it) } catch (_: Exception) { null } }
        val albumId = json?.optString("albumId")
        val maxCount = json?.optInt("maxCount", 200) ?: 200
        Thread {
            val images = queryImages(albumId, maxCount)
            Handler(Looper.getMainLooper()).post {
                callback?.invoke(JSONObject().put("data", images).toString())
            }
        }.start()
    }

    private fun queryImages(bucketId: String?, maxCount: Int): JSONArray {
        val ctx = context
        android.util.Log.d("KRAlbum", "queryImages: context=$ctx, maxCount=$maxCount")
        if (ctx == null) {
            android.util.Log.e("KRAlbum", "queryImages: context is NULL!")
            return JSONArray()
        }
        val images = JSONArray()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = bucketId?.let { "${MediaStore.Images.Media.BUCKET_ID} = ?" }
        val selectionArgs = bucketId?.let { arrayOf(it) }
        val cursor = ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )
        android.util.Log.d("KRAlbum", "queryImages: cursor=$cursor, count=${cursor?.count}")
        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < maxCount) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()
                val width = it.getInt(it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH))
                val height = it.getInt(it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))
                images.put(JSONObject().apply {
                    put("id", id.toString())
                    put("uri", contentUri)
                    put("thumbnailUri", contentUri)
                    put("width", width)
                    put("height", height)
                })
                count++
            }
        }
        return images
    }

    // ─── 两步加载：元数据与缩略图分离 ───

    /**
     * 第一步：快速查询元数据，返回可用的 thumbnailUri
     * - 只查 _ID/DATE_ADDED 两个必须字段（WIDTH/HEIGHT 很多设备为 0，省略可加速）
     * - thumbnailUri 直接设为 contentUri（Image 组件可直接加载）
     * - 支持 maxCount 限制，首次只加载首屏（如 60 张），快速返回
     */
    private fun fetchMetadata(params: String?, callback: KuiklyRenderCallback?) {
        val maxCount = params?.let {
            try { JSONObject(it).optInt("maxCount", Int.MAX_VALUE) } catch (_: Exception) { Int.MAX_VALUE }
        } ?: Int.MAX_VALUE
        Thread {
            val images = queryImagesFast(null, maxCount)
            Handler(Looper.getMainLooper()).post {
                callback?.invoke(JSONObject().put("data", images).toString())
            }
        }.start()
    }

    /**
     * 高性能查询：只查 _ID，用 ContentUris 构造 URI
     * 比 queryImages 少查 DATA/WIDTH/HEIGHT/DATE_ADDED，JSON 序列化更快
     */
    private fun queryImagesFast(bucketId: String?, maxCount: Int): JSONArray {
        val ctx = context ?: return JSONArray()
        val images = JSONArray()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = bucketId?.let { "${MediaStore.Images.Media.BUCKET_ID} = ?" }
        val selectionArgs = bucketId?.let { arrayOf(it) }
        val cursor = ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )
        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < maxCount) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()
                images.put(JSONObject().apply {
                    put("id", id.toString())
                    put("uri", contentUri)
                    put("thumbnailUri", contentUri)
                })
                count++
            }
        }
        return images
    }

    /**
     * 第二步：按需请求单张缩略图
     * 缩略图就绪后通过 callback 返回 thumbnailUri
     * Android 上 content:// URI 本身即可被 Kuikly Image 组件加载，
     * 所以这里直接返回 content URI 作为缩略图 URI
     */
    private fun requestThumbnail(params: String?, callback: KuiklyRenderCallback?) {
        if (params == null || callback == null) return
        val json = try { JSONObject(params) } catch (_: Exception) { return }
        val imageId = json.optString("imageId", "") ?: return
        if (imageId.isEmpty()) return

        Thread {
            // 根据 imageId 查询 content URI
            val ctx = context
            if (ctx == null) {
                Handler(Looper.getMainLooper()).post {
                    callback.invoke(JSONObject().put("imageId", imageId).put("thumbnailUri", "").toString())
                }
                return@Thread
            }
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media._ID} = ?"
            val selectionArgs = arrayOf(imageId)
            val cursor = ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )
            var thumbnailUri = ""
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    thumbnailUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()
                }
            }
            val result = JSONObject().apply {
                put("imageId", imageId)
                put("thumbnailUri", thumbnailUri)
            }
            Handler(Looper.getMainLooper()).post {
                callback.invoke(result.toString())
            }
        }.start()
    }

    /**
     * 取消缩略图请求（当前简化实现，后续可扩展任务队列取消）
     */
    private fun cancelThumbnailRequest(params: String?) {
        // 预留：后续可实现优先级队列取消逻辑
    }

    companion object {
        const val MODULE_NAME = "KRAlbumModule"
        const val PERMISSION_REQUEST_CODE = 10087
        var pendingPermissionCallback: KuiklyRenderCallback? = null

        fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
            if (requestCode == PERMISSION_REQUEST_CODE) {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                pendingPermissionCallback?.invoke(JSONObject().put("granted", granted).toString())
                pendingPermissionCallback = null
            }
        }
    }
}
