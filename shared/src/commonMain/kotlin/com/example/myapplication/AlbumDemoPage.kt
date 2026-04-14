package com.example.myapplication

import com.example.myapplication.base.BasePager
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.*
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuiklybase.album.KRAlbumModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

data class AlbumImage(
    val id: String,
    val uri: String,
    val width: Int = 0,
    val height: Int = 0
)

@Page("router")
internal class AlbumDemoPage : BasePager() {

    private var images by observableList<AlbumImage>()
    private var selectedIds by observable(setOf<String>())
    private var loading by observable(true)
    private val maxSelectCount = 9

    override fun createExternalModules(): Map<String, Module>? {
        val modules = super.createExternalModules()?.toMutableMap() ?: hashMapOf()
        modules[KRAlbumModule.MODULE_NAME] = KRAlbumModule()
        return modules
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFF5F5F5))
                flex(1f)
            }

            View {
                attr {
                    height(ctx.pagerData.statusBarHeight)
                    backgroundColor(Color.WHITE)
                }
            }

            View {
                attr {
                    height(56f)
                    backgroundColor(Color.WHITE)
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentSpaceBetween()
                    paddingLeft(16f)
                    paddingRight(16f)
                }
                Text {
                    attr {
                        text("相册")
                        fontSize(18f)
                        fontWeightBold()
                        color(Color.BLACK)
                    }
                }
                Text {
                    attr {
                        val count = ctx.selectedIds.size
                        text(if (count > 0) "完成($count/${ctx.maxSelectCount})" else "完成")
                        fontSize(15f)
                        color(if (count > 0) Color(0xFF007AFF) else Color(0xFF999999))
                    }
                    event {
                        click {
                            if (ctx.selectedIds.isNotEmpty()) {
                                val selected = ctx.images.filter { ctx.selectedIds.contains(it.id) }
                                println("Selected ${selected.size} images")
                            }
                        }
                    }
                }
            }

            vif({ ctx.loading }) {
                View {
                    attr {
                        flex(1f)
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    Text {
                        attr {
                            text("加载中...")
                            fontSize(16f)
                            color(Color(0xFF999999))
                        }
                    }
                }
            }
            velse {
                Scroller {
                    attr {
                        flex(1f)
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            flexWrapWrap()
                        }
                        vforIndex({ ctx.images }) { image, index, _ ->
                            View {
                                attr {
                                    val itemSize = (ctx.pagerData.pageViewWidth - 6f) / 3f
                                    width(itemSize)
                                    height(itemSize)
                                    marginRight(if ((index + 1) % 3 == 0) 0f else 3f)
                                    marginBottom(3f)
                                }
                                Image {
                                    attr {
                                        positionAbsolute()
                                        top(0f)
                                        left(0f)
                                        right(0f)
                                        bottom(0f)
                                        src(image.uri)
                                        resizeCover()
                                    }
                                }
                                View {
                                    attr {
                                        positionAbsolute()
                                        top(6f)
                                        right(6f)
                                        width(24f)
                                        height(24f)
                                        borderRadius(12f)
                                        val isSelected = ctx.selectedIds.contains(image.id)
                                        if (isSelected) {
                                            backgroundColor(Color(0xFF007AFF))
                                        } else {
                                            backgroundColor(Color(0x66000000))
                                            border(Border(1.5f, BorderStyle.SOLID, Color.WHITE))
                                        }
                                        alignItemsCenter()
                                        justifyContentCenter()
                                    }
                                    event {
                                        click {
                                            ctx.toggleSelect(image)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        println("KRAlbum: viewDidLoad")
        val module = acquireModule<KRAlbumModule>(KRAlbumModule.MODULE_NAME)
        module.requestPermission { result ->
            println("KRAlbum: permission result=$result")
            val granted = try {
                val json = if (result is JSONObject) result else JSONObject(result.toString())
                json.optBoolean("granted", false)
            } catch (_: Exception) {
                result.toString().contains("true")
            }
            println("KRAlbum: granted=$granted")
            if (granted) {
                loadImages(module)
            } else {
                println("KRAlbum: permission denied, stop loading")
                loading = false
            }
        }
    }

    private fun loadImages(module: KRAlbumModule) {
        println("KRAlbum: loadImages start")
        module.fetchImages(200) { imageResult ->
            println("KRAlbum: fetchImages callback received")
            val list = mutableListOf<AlbumImage>()
            try {
                val json = if (imageResult is JSONObject) imageResult else JSONObject(imageResult.toString())
                val array = json.optJSONArray("data") ?: JSONArray()
                println("KRAlbum: got ${array.length()} images")
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val img = AlbumImage(
                        id = item.optString("id", ""),
                        uri = item.optString("uri", ""),
                        width = item.optInt("width", 0),
                        height = item.optInt("height", 0)
                    )
                    if (i < 3) println("KRAlbum: image[$i] uri=${img.uri}")
                    list.add(img)
                }
            } catch (e: Exception) {
                println("KRAlbum: parse error: $e")
            }
            println("KRAlbum: adding ${list.size} images, setting loading=false")
            list.forEach { images.add(it) }
            loading = false
        }
    }

    private fun toggleSelect(image: AlbumImage) {
        val current = selectedIds.toMutableSet()
        if (current.contains(image.id)) {
            current.remove(image.id)
        } else {
            if (current.size >= maxSelectCount) return
            current.add(image.id)
        }
        selectedIds = current
    }
}
