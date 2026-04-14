package com.tencent.kuiklybase.album

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

class KRAlbumModule : Module() {

    override fun moduleName(): String = MODULE_NAME

    fun requestPermission(callback: CallbackFn) {
        callNative(METHOD_REQUEST_PERMISSION, null, callback)
    }

    fun checkPermission(): String {
        return syncCallNative(METHOD_CHECK_PERMISSION, null, null)
    }

    fun fetchImages(maxCount: Int = 200, callback: CallbackFn) {
        val params = JSONObject().apply {
            put("maxCount", maxCount)
        }
        callNative(METHOD_FETCH_IMAGES, params, callback)
    }

    fun fetchAlbums(callback: CallbackFn) {
        callNative(METHOD_FETCH_ALBUMS, null, callback)
    }

    fun fetchImagesFromAlbum(albumId: String, maxCount: Int = 200, callback: CallbackFn) {
        val params = JSONObject().apply {
            put("albumId", albumId)
            put("maxCount", maxCount)
        }
        callNative(METHOD_FETCH_IMAGES_FROM_ALBUM, params, callback)
    }

    private fun callNative(method: String, data: JSONObject?, callback: CallbackFn?) {
        toNative(false, method, data?.toString(), callback, false)
    }

    private fun syncCallNative(method: String, data: JSONObject?, callback: CallbackFn?): String {
        return toNative(false, method, data?.toString(), callback, true).toString()
    }

    companion object {
        const val MODULE_NAME = "KRAlbumModule"
        const val METHOD_REQUEST_PERMISSION = "requestPermission"
        const val METHOD_CHECK_PERMISSION = "checkPermission"
        const val METHOD_FETCH_IMAGES = "fetchImages"
        const val METHOD_FETCH_ALBUMS = "fetchAlbums"
        const val METHOD_FETCH_IMAGES_FROM_ALBUM = "fetchImagesFromAlbum"
    }
}
