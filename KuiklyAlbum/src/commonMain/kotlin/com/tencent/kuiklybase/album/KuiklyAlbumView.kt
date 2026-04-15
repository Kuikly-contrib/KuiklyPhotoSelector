package com.tencent.kuiklybase.album

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.*

// ─── Attr ───

class KuiklyAlbumAttr : ComposeAttr() {
    internal var maxSelectCount = 9
    internal var columnCount = 3
    internal var itemSpacing = 3f
    internal var themeColor = Color(0xFF07C160)
    internal var showVideoLabel = true

    init {
        flex(1f)
    }

    fun maxSelectCount(count: Int) { maxSelectCount = count }
    fun columnCount(count: Int) { columnCount = count }
    fun itemSpacing(spacing: Float) { itemSpacing = spacing }
    fun themeColor(color: Color) { themeColor = color }
    fun showVideoLabel(show: Boolean) { showVideoLabel = show }
}

// ─── Event ───

class KuiklyAlbumEvent : ComposeEvent() {
    internal var onConfirm: ((List<KRAlbumImage>) -> Unit)? = null
    internal var onSelectionChanged: ((List<KRAlbumImage>) -> Unit)? = null

    fun onConfirm(handler: (List<KRAlbumImage>) -> Unit) { onConfirm = handler }
    fun onSelectionChanged(handler: (List<KRAlbumImage>) -> Unit) { onSelectionChanged = handler }
}

// ─── ComposeView ───

/**
 * 相册选择器核心组件
 *
 * 架构：List + vforIndex 全量加载行分组
 *
 * 原始图片按 columnCount 预分组为行 → rowList (observableList)
 * vforIndex 遍历 rowList，每次迭代创建一个行 View（行内 for 循环渲染格子）
 * 全量虚拟节点 + 平台 View 动态创建销毁，无复用，无白屏闪烁
 */
class KuiklyAlbumView : ComposeView<KuiklyAlbumAttr, KuiklyAlbumEvent>() {

    // ─── 原始数据 ───
    private val _images = mutableListOf<KRAlbumImage>()

    // ─── 行分组（vforIndex 消费） ───
    var rowList by observableList<KRAlbumRow>()
        private set

    var selectionVersion by observable(0)
        private set

    var loading by observable(true)
        private set

    private var permissionDenied by observable(false)

    private val preview by lazy { KRAlbumPreview(getPager()) }

    // ─── 选中状态 ───

    private val _selectedList = mutableListOf<String>()
    private val _selectedSet = hashSetOf<String>()
    private val _selectIndexMap = hashMapOf<String, Int>()

    val selectedIds: Set<String> get() = _selectedSet
    val selectedOrder: List<String> get() = _selectedList.toList()
    val images: List<KRAlbumImage> get() = _images

    fun getSelectIndex(imageId: String): Int = _selectIndexMap[imageId] ?: 0

    private fun rebuildIndexMap() {
        _selectIndexMap.clear()
        for (i in _selectedList.indices) {
            _selectIndexMap[_selectedList[i]] = i + 1
        }
    }

    override fun createAttr(): KuiklyAlbumAttr = KuiklyAlbumAttr()
    override fun createEvent(): KuiklyAlbumEvent = KuiklyAlbumEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val screenWidth = pagerData.pageViewWidth

        return {
            // ─── 加载中 ───
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

            // ─── 权限拒绝 ───
            vif({ !ctx.loading && ctx.permissionDenied }) {
                View {
                    attr {
                        flex(1f)
                        alignItemsCenter()
                        justifyContentCenter()
                        paddingLeft(32f)
                        paddingRight(32f)
                    }
                    Text { attr { text("📷"); fontSize(48f) } }
                    Text {
                        attr {
                            text("需要相册访问权限")
                            fontSize(18f)
                            fontWeightBold()
                            color(Color.BLACK)
                            marginTop(16f)
                        }
                    }
                    Text {
                        attr {
                            text("请在系统设置中允许访问照片和视频")
                            fontSize(14f)
                            color(Color(0xFF999999))
                            marginTop(8f)
                        }
                    }
                }
            }

            // ─── 空状态 ───
            vif({ !ctx.loading && !ctx.permissionDenied && ctx.rowList.isEmpty() }) {
                View {
                    attr {
                        flex(1f)
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    Text { attr { text("🖼️"); fontSize(48f) } }
                    Text {
                        attr {
                            text("暂无照片")
                            fontSize(16f)
                            color(Color(0xFF999999))
                            marginTop(16f)
                        }
                    }
                }
            }

            // ─── 图片网格 ───
            vif({ !ctx.loading && !ctx.permissionDenied && ctx.rowList.isNotEmpty() }) {
                val a = ctx.attr
                val cols = a.columnCount
                val spacing = a.itemSpacing
                val itemSize = (screenWidth - spacing * (cols + 1).toFloat()) / cols.toFloat()

                Scroller {
                    attr {
                        flex(1f)
                        showScrollerIndicator(false)
                    }

                    // 直接 for 循环构建所有行，Scroller 无 View 复用
                    for (row in ctx.rowList) {
                        View {
                            attr {
                                flexDirectionRow()
                                width(screenWidth)
                                height(itemSize + spacing)
                                paddingTop(spacing)
                                paddingLeft(spacing)
                            }

                            for (col in 0 until cols) {
                                val img = row.items.getOrNull(col)
                                if (img != null) {
                                    ctx.buildImageCell(this, img, row.startIndex + col, itemSize, spacing)
                                } else {
                                    View {
                                        attr {
                                            size(itemSize, itemSize)
                                            marginRight(spacing)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── 预览浮层 ───
            ctx.preview.buildPreview()()
        }
    }

    private fun buildImageCell(
        parent: ViewContainer<*, *>,
        image: KRAlbumImage,
        globalIndex: Int,
        itemSize: Float,
        spacing: Float
    ) {
        val ctx = this
        with(parent) {
            View {
                attr {
                    size(itemSize, itemSize)
                    marginRight(spacing)
                    backgroundColor(Color(0xFFE5E5E5))
                }

                Image {
                    attr {
                        positionAbsolute()
                        top(0f)
                        left(0f)
                        right(0f)
                        bottom(0f)
                        src(image.displayUri())
                        resizeCover()
                    }
                    event {
                        click { ctx.openPreview(globalIndex) }
                    }
                }

                // 选中遮罩
                View {
                    attr {
                        positionAbsolute()
                        top(0f)
                        left(0f)
                        right(0f)
                        bottom(0f)
                        val ver = ctx.selectionVersion
                        val isSelected = ctx._selectedSet.contains(image.id)
                        if (isSelected) {
                            backgroundColor(Color(0x33000000))
                        } else {
                            backgroundColor(Color.TRANSPARENT)
                        }
                    }
                }

                // 视频时长标签
                vif({ image.isVideo && ctx.attr.showVideoLabel }) {
                    View {
                        attr {
                            positionAbsolute()
                            bottom(6f)
                            left(6f)
                            flexDirectionRow()
                            alignItemsCenter()
                            backgroundColor(Color(0x99000000))
                            borderRadius(4f)
                            paddingLeft(4f)
                            paddingRight(4f)
                            paddingTop(2f)
                            paddingBottom(2f)
                        }
                        Text {
                            attr {
                                text("▶")
                                fontSize(8f)
                                color(Color.WHITE)
                                marginRight(3f)
                            }
                        }
                        Text {
                            attr {
                                text(image.formatDuration())
                                fontSize(11f)
                                color(Color.WHITE)
                            }
                        }
                    }
                }

                // 右上角选择按钮
                View {
                    attr {
                        positionAbsolute()
                        top(4f)
                        right(4f)
                        width(28f)
                        height(28f)
                        borderRadius(14f)
                        val ver = ctx.selectionVersion
                        val isSelected = ctx._selectedSet.contains(image.id)
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
                        click { ctx.toggleSelect(image) }
                    }
                    Text {
                        attr {
                            val ver = ctx.selectionVersion
                            val isSelected = ctx._selectedSet.contains(image.id)
                            val selectNum = ctx.getSelectIndex(image.id)
                            if (isSelected && selectNum > 0) {
                                text("$selectNum")
                            } else {
                                text("")
                            }
                            fontSize(12f)
                            color(Color.WHITE)
                            fontWeightBold()
                        }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        val module = acquireModule<KRAlbumModule>(KRAlbumModule.MODULE_NAME)
        module.requestPermission { result ->
            if (module.isPermissionGranted(result)) {
                val cached = KRAlbumModule.ImageCache.get()
                if (cached != null) {
                    setImages(cached)
                    loading = false
                } else {
                    module.fetchImageList { list ->
                        KRAlbumModule.ImageCache.put(list)
                        setImages(list)
                        loading = false
                    }
                }
            } else {
                permissionDenied = true
                loading = false
            }
        }
    }

    private fun setImages(list: List<KRAlbumImage>) {
        _images.clear()
        _images.addAll(list)
        val cols = attr.columnCount
        val rows = mutableListOf<KRAlbumRow>()
        var i = 0
        while (i < _images.size) {
            val end = (i + cols).coerceAtMost(_images.size)
            rows.add(KRAlbumRow(items = _images.subList(i, end).toList(), startIndex = i))
            i += cols
        }
        rowList.addAll(rows)
    }

    fun getSelectedImages(): List<KRAlbumImage> {
        if (_selectedList.isEmpty()) return emptyList()
        val result = mutableListOf<KRAlbumImage>()
        for (image in _images) {
            if (_selectedSet.contains(image.id)) {
                result.add(image)
            }
            if (result.size == _selectedList.size) break
        }
        return result
    }

    private fun toggleSelect(image: KRAlbumImage) {
        if (_selectedSet.contains(image.id)) {
            _selectedSet.remove(image.id)
            _selectedList.remove(image.id)
        } else {
            if (_selectedList.size >= attr.maxSelectCount) return
            _selectedSet.add(image.id)
            _selectedList.add(image.id)
        }
        rebuildIndexMap()
        selectionVersion++
        event.onSelectionChanged?.invoke(getSelectedImages())
    }

    private fun openPreview(index: Int) {
        preview.open(
            images = _images,
            index = index,
            selectedIds = _selectedSet,
            maxSelectCount = attr.maxSelectCount,
            themeColor = attr.themeColor,
            onSelectionChanged = { ids ->
                _selectedSet.clear()
                _selectedList.clear()
                _selectedSet.addAll(ids)
                _selectedList.addAll(ids)
                rebuildIndexMap()
                selectionVersion++
                event.onSelectionChanged?.invoke(getSelectedImages())
            }
        )
    }
}

// ─── DSL ───

fun ViewContainer<*, *>.KuiklyAlbum(init: KuiklyAlbumView.() -> Unit) {
    addChild(KuiklyAlbumView(), init)
}
