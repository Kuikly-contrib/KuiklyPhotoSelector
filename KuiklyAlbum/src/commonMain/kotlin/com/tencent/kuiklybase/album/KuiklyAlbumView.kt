package com.tencent.kuiklybase.album

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.*

// ─── Attr ───

class KuiklyAlbumAttr : ComposeAttr() {
    internal var maxSelectCount = 9
    internal var columnCount = 3
    internal var itemSpacing = 3f
    internal var themeColor = Color(0xFF07C160)

    init {
        flex(1f)
    }

    fun maxSelectCount(count: Int) { maxSelectCount = count }
    fun columnCount(count: Int) { columnCount = count }
    fun itemSpacing(spacing: Float) { itemSpacing = spacing }
    fun themeColor(color: Color) { themeColor = color }
}

// ─── Event ───

class KuiklyAlbumEvent : ComposeEvent() {
    internal var onConfirm: ((List<KRAlbumImage>) -> Unit)? = null
    internal var onSelectionChanged: ((List<KRAlbumImage>) -> Unit)? = null

    fun onConfirm(handler: (List<KRAlbumImage>) -> Unit) { onConfirm = handler }
    fun onSelectionChanged(handler: (List<KRAlbumImage>) -> Unit) { onSelectionChanged = handler }
}

// ─── ComposeView ───

class KuiklyAlbumView : ComposeView<KuiklyAlbumAttr, KuiklyAlbumEvent>() {

    var images by observableList<KRAlbumImage>()
        private set
    var selectedIds by observable(setOf<String>())
    var loading by observable(true)
        private set

    private val preview by lazy { KRAlbumPreview(getPager()) }

    override fun createAttr(): KuiklyAlbumAttr = KuiklyAlbumAttr()
    override fun createEvent(): KuiklyAlbumEvent = KuiklyAlbumEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val screenWidth = pagerData.pageViewWidth
        println("[KuiklyAlbum] body() screenWidth=$screenWidth")

        return {
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
                val a = ctx.attr
                val itemSize = (screenWidth - a.itemSpacing * (a.columnCount - 1).toFloat()) / a.columnCount.toFloat()
                println("[KuiklyAlbum] velse: itemSize=$itemSize, columnCount=${a.columnCount}, itemSpacing=${a.itemSpacing}")
                WaterfallList {
                    attr {
                        flex(1f)
                        listWidth(screenWidth)
                        columnCount(a.columnCount)
                        itemSpacing(a.itemSpacing)
                        lineSpacing(a.itemSpacing)
                    }
                    vforIndex({ 
                        println("[KuiklyAlbum] vforIndex data source size=${ctx.images.size}")
                        ctx.images 
                    }) { image, index, _ ->
                        if (index < 3) {
                            println("[KuiklyAlbum] vforIndex item[$index] uri=${image.thumbnailUri}")
                        }
                        View {
                            attr {
                                width(itemSize)
                                height(itemSize)
                            }
                            Image {
                                attr {
                                    positionAbsolute()
                                    top(0f)
                                    left(0f)
                                    right(0f)
                                    bottom(0f)
                                    src(image.thumbnailUri)
                                    resizeCover()
                                }
                                event {
                                    click {
                                        ctx.preview.open(
                                            images = ctx.images.toList(),
                                            index = index,
                                            selectedIds = ctx.selectedIds,
                                            maxSelectCount = ctx.attr.maxSelectCount,
                                            themeColor = ctx.attr.themeColor,
                                            onSelectionChanged = { ids ->
                                                ctx.selectedIds = ids
                                                ctx.event.onSelectionChanged?.invoke(ctx.getSelectedImages())
                                            }
                                        )
                                    }
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
                                        backgroundColor(ctx.attr.themeColor)
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

            ctx.preview.buildPreview()()
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        println("[KuiklyAlbum] viewDidLoad, requesting permission...")
        val module = acquireModule<KRAlbumModule>(KRAlbumModule.MODULE_NAME)
        module.requestPermission { result ->
            println("[KuiklyAlbum] permission result: $result")
            if (module.isPermissionGranted(result)) {
                println("[KuiklyAlbum] permission granted, fetching images...")
                module.fetchImageList { list ->
                    println("[KuiklyAlbum] fetched ${list.size} images")
                    if (list.isNotEmpty()) {
                        println("[KuiklyAlbum] first image: id=${list.first().id}, uri=${list.first().thumbnailUri}")
                    }
                    list.forEach { images.add(it) }
                    loading = false
                }
            } else {
                println("[KuiklyAlbum] permission denied")
                loading = false
            }
        }
    }

    fun getSelectedImages(): List<KRAlbumImage> {
        return images.filter { selectedIds.contains(it.id) }
    }

    private fun toggleSelect(image: KRAlbumImage) {
        val current = selectedIds.toMutableSet()
        if (current.contains(image.id)) {
            current.remove(image.id)
        } else {
            if (current.size >= attr.maxSelectCount) return
            current.add(image.id)
        }
        selectedIds = current
        event.onSelectionChanged?.invoke(getSelectedImages())
    }
}

// ─── DSL 扩展函数 ───

fun ViewContainer<*, *>.KuiklyAlbum(init: KuiklyAlbumView.() -> Unit) {
    addChild(KuiklyAlbumView(), init)
}
