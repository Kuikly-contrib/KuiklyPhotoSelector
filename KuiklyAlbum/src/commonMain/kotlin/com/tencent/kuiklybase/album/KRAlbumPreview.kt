package com.tencent.kuiklybase.album

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.pager.IPager
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.*

/**
 * 相册图片预览组件
 */
class KRAlbumPreview(private val pager: IPager) {

    var visible by observable(false)
    var currentIndex by observable(0)

    private var imageUrl by observable("")
    private var imageId by observable("")
    /** 选中 ID 集合（O(1) 查找） */
    private var selectedSet = hashSetOf<String>()
    /** 有序选中列表（保持顺序） */
    private var selectedList = mutableListOf<String>()
    /** 选中状态版本号（触发 UI 更新） */
    private var selectVersion by observable(0)
    private var maxSelectCount = 9
    private var themeColor = Color(0xFF07C160)
    private var onSelectionChanged: ((Set<String>) -> Unit)? = null
    private var opening = false
    private var showSelect = true
    /** 直接持有外部引用，不拷贝 */
    private var imageList: List<KRAlbumImage> = emptyList()
    private lateinit var containerRef: ViewRef<DivView>
    private var counterText by observable("")

    fun open(
        images: List<KRAlbumImage>,
        index: Int,
        selectedIds: Set<String> = emptySet(),
        maxSelectCount: Int = 9,
        themeColor: Color = Color(0xFF07C160),
        showSelect: Boolean = true,
        onSelectionChanged: ((Set<String>) -> Unit)? = null
    ) {
        if (opening || visible) return
        opening = true

        val image = images.getOrNull(index) ?: run { opening = false; return }
        this.imageList = images // 直接引用，不拷贝
        this.imageUrl = image.uri
        this.imageId = image.id
        this.selectedSet = hashSetOf<String>().apply { addAll(selectedIds) }
        this.selectedList = selectedIds.toMutableList()
        this.maxSelectCount = maxSelectCount
        this.themeColor = themeColor
        this.showSelect = showSelect
        this.onSelectionChanged = onSelectionChanged
        currentIndex = index
        updateCounterText()
        visible = true
    }

    fun close() {
        if (!visible) return
        if (::containerRef.isInitialized) {
            containerRef.view?.animateToAttr(
                Animation.easeOut(0.2f),
                completion = { _ ->
                    visible = false
                    opening = false
                    releaseResources()
                },
                attrBlock = {
                    opacity(0f)
                    transform(scale = Scale(0.85f, 0.85f))
                }
            )
        } else {
            visible = false
            opening = false
            releaseResources()
        }
    }

    /** 及时释放资源，避免内存泄漏 */
    private fun releaseResources() {
        imageList = emptyList()
        onSelectionChanged = null
        selectedSet.clear()
        selectedList.clear()
    }

    private fun showPrevious() {
        if (currentIndex > 0) navigateTo(currentIndex - 1)
    }

    private fun showNext() {
        if (currentIndex < imageList.size - 1) navigateTo(currentIndex + 1)
    }

    private fun navigateTo(index: Int) {
        val image = imageList.getOrNull(index) ?: return
        currentIndex = index
        imageUrl = image.uri
        imageId = image.id
        updateCounterText()
    }

    private fun updateCounterText() {
        counterText = "${currentIndex + 1} / ${imageList.size}"
    }

    fun buildPreview(): ViewBuilder {
        val preview = this
        val screenWidth = pager.pageData.pageViewWidth
        val screenHeight = pager.pageData.pageViewHeight
        val statusBarHeight = pager.pageData.statusBarHeight

        return {
            vif({ preview.visible }) {
                Modal(true) {
                    attr {
                        absolutePosition(0f, 0f, 0f, 0f)
                    }
                    event {
                        willDismiss {
                            preview.close()
                        }
                    }

                    View {
                        ref {
                            preview.containerRef = it
                            it.view?.animateToAttr(
                                Animation.easeOut(0.2f),
                                attrBlock = {
                                    opacity(1f)
                                    transform(scale = Scale(1f, 1f))
                                }
                            )
                        }
                        attr {
                            absolutePosition(0f, 0f, 0f, 0f)
                            backgroundColor(Color.BLACK)
                            opacity(0f)
                            transform(scale = Scale(0.85f, 0.85f))
                        }

                        // ─── 顶部栏：计数 + 选中按钮 ───
                        View {
                            attr {
                                positionAbsolute()
                                top(0f)
                                left(0f)
                                right(0f)
                                height(statusBarHeight + 56f)
                                paddingTop(statusBarHeight)
                                flexDirectionRow()
                                alignItemsCenter()
                                justifyContentSpaceBetween()
                                paddingLeft(16f)
                                paddingRight(16f)
                                zIndex(10)
                            }
                            // 关闭按钮
                            View {
                                attr {
                                    width(32f)
                                    height(32f)
                                    borderRadius(16f)
                                    backgroundColor(Color(0x66000000))
                                    alignItemsCenter()
                                    justifyContentCenter()
                                }
                                event {
                                    click { preview.close() }
                                }
                                Text {
                                    attr {
                                        text("✕")
                                        fontSize(16f)
                                        color(Color.WHITE)
                                    }
                                }
                            }
                            // 图片计数
                            Text {
                                attr {
                                    text(preview.counterText)
                                    fontSize(16f)
                                    color(Color.WHITE)
                                    fontWeightBold()
                                }
                            }
                            // 选中按钮
                            vif({ preview.showSelect }) {
                                // 读取 selectVersion 以订阅选中状态变化
                                val ver = preview.selectVersion
                                View {
                                    attr {
                                        width(28f)
                                        height(28f)
                                        borderRadius(14f)
                                        val isSelected = preview.selectedSet.contains(preview.imageId)
                                        if (isSelected) {
                                            backgroundColor(preview.themeColor)
                                        } else {
                                            backgroundColor(Color(0x66000000))
                                            border(Border(1.5f, BorderStyle.SOLID, Color.WHITE))
                                        }
                                        alignItemsCenter()
                                        justifyContentCenter()
                                    }
                                    event {
                                        click { preview.toggleSelect() }
                                    }
                                    // 选中编号
                                    vif({ preview.selectedSet.contains(preview.imageId) }) {
                                        Text {
                                            attr {
                                                val num = preview.selectedList.indexOf(preview.imageId) + 1
                                                text(if (num > 0) "$num" else "")
                                                fontSize(12f)
                                                color(Color.WHITE)
                                                fontWeightBold()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ─── 图片展示区域 ───
                        View {
                            attr {
                                flex(1f)
                                justifyContentCenter()
                                alignItemsCenter()
                            }
                            event {
                                click { preview.close() }
                            }
                            Image {
                                attr {
                                    size(screenWidth, screenHeight)
                                    src(preview.imageUrl)
                                    resizeContain()
                                }
                                event {
                                    loadSuccess { preview.opening = false }
                                    loadFailure { preview.opening = false }
                                    click { preview.close() }
                                }
                            }
                        }

                        // ─── 左侧切换区域 ───
                        vif({ preview.currentIndex > 0 }) {
                            View {
                                attr {
                                    positionAbsolute()
                                    top(statusBarHeight + 56f)
                                    left(0f)
                                    width(screenWidth * 0.25f)
                                    bottom(60f)
                                }
                                event {
                                    click { preview.showPrevious() }
                                }
                            }
                        }

                        // ─── 右侧切换区域 ───
                        vif({ preview.currentIndex < preview.imageList.size - 1 }) {
                            View {
                                attr {
                                    positionAbsolute()
                                    top(statusBarHeight + 56f)
                                    right(0f)
                                    width(screenWidth * 0.25f)
                                    bottom(60f)
                                }
                                event {
                                    click { preview.showNext() }
                                }
                            }
                        }

                        // ─── 底部导航提示 ───
                        View {
                            attr {
                                positionAbsolute()
                                bottom(0f)
                                left(0f)
                                right(0f)
                                height(60f)
                                flexDirectionRow()
                                alignItemsCenter()
                                justifyContentCenter()
                            }
                            vif({ preview.currentIndex > 0 }) {
                                View {
                                    attr {
                                        width(40f)
                                        height(40f)
                                        borderRadius(20f)
                                        backgroundColor(Color(0x66000000))
                                        alignItemsCenter()
                                        justifyContentCenter()
                                        marginRight(24f)
                                    }
                                    event {
                                        click { preview.showPrevious() }
                                    }
                                    Text {
                                        attr {
                                            text("‹")
                                            fontSize(24f)
                                            color(Color.WHITE)
                                        }
                                    }
                                }
                            }
                            vif({ preview.currentIndex < preview.imageList.size - 1 }) {
                                View {
                                    attr {
                                        width(40f)
                                        height(40f)
                                        borderRadius(20f)
                                        backgroundColor(Color(0x66000000))
                                        alignItemsCenter()
                                        justifyContentCenter()
                                        marginLeft(24f)
                                    }
                                    event {
                                        click { preview.showNext() }
                                    }
                                    Text {
                                        attr {
                                            text("›")
                                            fontSize(24f)
                                            color(Color.WHITE)
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

    private fun toggleSelect() {
        if (selectedSet.contains(imageId)) {
            selectedSet.remove(imageId)
            selectedList.remove(imageId)
        } else {
            if (selectedList.size >= maxSelectCount) return
            selectedSet.add(imageId)
            selectedList.add(imageId)
        }
        selectVersion++ // 触发 UI 更新
        onSelectionChanged?.invoke(selectedSet)
    }
}
