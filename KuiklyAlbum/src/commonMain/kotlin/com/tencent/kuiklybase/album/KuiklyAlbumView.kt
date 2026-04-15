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
import com.tencent.kuikly.core.views.*

// ─── Attr ───

class KuiklyAlbumAttr : ComposeAttr() {
    internal var maxSelectCount = 9
    internal var columnCount = 3
    internal var itemSpacing = 3f
    internal var themeColor = Color(0xFF07C160)
    internal var showVideoLabel = true
    internal var maxImageCount = Int.MAX_VALUE

    init { flex(1f) }

    fun maxSelectCount(count: Int) { maxSelectCount = count }
    fun columnCount(count: Int) { columnCount = count }
    fun itemSpacing(spacing: Float) { itemSpacing = spacing }
    fun themeColor(color: Color) { themeColor = color }
    fun showVideoLabel(show: Boolean) { showVideoLabel = show }
    fun maxImageCount(count: Int) { maxImageCount = count }
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
 * 架构：元数据全量 + 可见性驱动图片懒加载
 *
 * 1. 元数据（路径/ID/尺寸）全量同步分组 → rowList
 * 2. Scroller + vforIndex 全量创建虚拟节点（轻量，每行几百字节）
 * 3. 图片 src 由可见性版本号 (visibleVersion) 驱动：
 *    - willAppear：行进入可见区 → 加入 _visibleRows → visibleVersion++ → attr 读取时设置 src
 *    - didDisappear：行离开可见区 → 移出 _visibleRows → visibleVersion++ → attr 读取时清空 src
 * 4. 不可见的行只有浅灰占位，不触发图片解码，内存可控
 */
class KuiklyAlbumView : ComposeView<KuiklyAlbumAttr, KuiklyAlbumEvent>() {

    // ─── 原始数据 ───
    private val _images = mutableListOf<KRAlbumImage>()

    // ─── 行分组（普通 List，body 时直接 for 遍历） ───
    private val _rowList = mutableListOf<KRAlbumRow>()

    // ─── scroll 驱动图片懒加载 ───
    /** 当前加载图片的行范围 [loadStartRow, loadEndRow) */
    private var loadStartRow by observable(0)
    private var loadEndRow by observable(Int.MAX_VALUE)

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

    fun isRowLoaded(rowIndex: Int): Boolean = rowIndex in loadStartRow until loadEndRow

    override fun createAttr(): KuiklyAlbumAttr = KuiklyAlbumAttr()
    override fun createEvent(): KuiklyAlbumEvent = KuiklyAlbumEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val screenWidth = pagerData.pageViewWidth
        val screenHeight = pagerData.pageViewHeight

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
            vif({ !ctx.loading && !ctx.permissionDenied && ctx._images.isEmpty() }) {
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
            vif({ !ctx.loading && !ctx.permissionDenied && ctx._images.isNotEmpty() }) {
                val a = ctx.attr
                val cols = a.columnCount
                val spacing = a.itemSpacing
                val itemSize = (screenWidth - spacing * (cols + 1).toFloat()) / cols.toFloat()

                val rowHeight = itemSize + spacing

                Scroller {
                    attr {
                        flex(1f)
                        showScrollerIndicator(false)
                    }
                    event {
                        scroll { params ->
                            val offsetY = params.offsetY
                            // 上下各一屏缓冲（共 3 屏）
                            val visibleTop = (offsetY - screenHeight).coerceAtLeast(0f)
                            val visibleBottom = offsetY + screenHeight + screenHeight
                            val newStart = (visibleTop / rowHeight).toInt().coerceAtLeast(0)
                            val newEnd = ((visibleBottom / rowHeight).toInt() + 1).coerceAtMost(ctx._rowList.size)
                            if (newStart != ctx.loadStartRow || newEnd != ctx.loadEndRow) {
                                ctx.loadStartRow = newStart
                                ctx.loadEndRow = newEnd
                            }
                        }
                    }

                    for ((rowIndex, row) in ctx._rowList.withIndex()) {
                        View {
                            attr {
                                flexDirectionRow()
                                width(screenWidth)
                                height(rowHeight)
                                paddingTop(spacing)
                                paddingLeft(spacing)
                            }

                            for (col in 0 until cols) {
                                val img = row.items.getOrNull(col)
                                if (img != null) {
                                    ctx.buildImageCell(this, img, row.startIndex + col, rowIndex, itemSize, spacing)
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
        rowIndex: Int,
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

                // 缩略图：只有行可见时才设置 src，不可见时清空
                Image {
                    attr {
                        positionAbsolute()
                        top(0f)
                        left(0f)
                        right(0f)
                        bottom(0f)
                        // 读取 loadStartRow/loadEndRow 建立响应式依赖
                        val s = ctx.loadStartRow
                        val e = ctx.loadEndRow
                        if (ctx.isRowLoaded(rowIndex)) {
                            src(image.displayUri())
                        } else {
                            src("")
                        }
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
                        if (ctx._selectedSet.contains(image.id)) {
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
                        if (ctx._selectedSet.contains(image.id)) {
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
                            val selectNum = ctx.getSelectIndex(image.id)
                            if (ctx._selectedSet.contains(image.id) && selectNum > 0) {
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
        val max = attr.maxImageCount
        if (max < list.size) {
            _images.addAll(list.subList(0, max))
        } else {
            _images.addAll(list)
        }
        val cols = attr.columnCount
        _rowList.clear()
        var i = 0
        while (i < _images.size) {
            val end = (i + cols).coerceAtMost(_images.size)
            _rowList.add(KRAlbumRow(items = _images.subList(i, end).toList(), startIndex = i))
            i += cols
        }
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
        val image = _images.getOrNull(index) ?: return
        preview.open(
            imageUrl = image.uri,
            imageId = image.id,
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
