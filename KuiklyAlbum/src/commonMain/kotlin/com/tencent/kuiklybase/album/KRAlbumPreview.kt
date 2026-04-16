package com.tencent.kuiklybase.album

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
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
    private lateinit var containerRef: ViewRef<DivView>

    // ─── 图片手势状态 ───
    /** 图片累计宽度（pinch end 时固化） */
    private var imageWidth by observable(0f)
    /** 图片累计高度（pinch end 时固化） */
    private var imageHeight by observable(0f)
    /** 当次 pinch 手势的实时缩放倍数（手势结束后归 1） */
    private var pinchScale by observable(1f)
    /** 图片位移偏移量（像素） */
    private var translateX by observable(0f)
    private var translateY by observable(0f)
    /** pan 手势基准点 */
    private var panStartX = 0f
    private var panStartY = 0f
    private var baseTranslateX = 0f
    private var baseTranslateY = 0f

    fun open(
        imageUrl: String,
        imageId: String,
        selectedIds: Set<String> = emptySet(),
        maxSelectCount: Int = 9,
        themeColor: Color = Color(0xFF07C160),
        showSelect: Boolean = true,
        onSelectionChanged: ((Set<String>) -> Unit)? = null
    ) {
        if (opening || visible) return
        opening = true

        this.imageUrl = imageUrl
        this.imageId = imageId
        this.selectedSet = hashSetOf<String>().apply { addAll(selectedIds) }
        this.selectedList = selectedIds.toMutableList()
        this.maxSelectCount = maxSelectCount
        this.themeColor = themeColor
        this.showSelect = showSelect
        this.onSelectionChanged = onSelectionChanged
        // 重置手势状态，使用屏幕尺寸作为初始图片尺寸
        imageWidth = pager.pageData.pageViewWidth
        imageHeight = pager.pageData.pageViewHeight
        pinchScale = 1f
        translateX = 0f
        translateY = 0f
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
        onSelectionChanged = null
        selectedSet.clear()
        selectedList.clear()
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

                        // ─── 导航栏 ───
                        View {
                            attr {
                                positionAbsolute()
                                top(0f)
                                left(0f)
                                right(0f)
                                height(statusBarHeight + 56f)
                                zIndex(10)
                            }
                            View {
                                attr {
                                    absolutePosition(0f, 0f, 0f, 0f)
                                    backgroundColor(Color(0xFF333333))
                                }
                            }
                            // 内容层
                            View {
                                attr {
                                    absolutePosition(0f, 0f, 0f, 0f)
                                    paddingTop(statusBarHeight)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    justifyContentSpaceBetween()
                                    paddingLeft(16f)
                                    paddingRight(16f)
                                }
                                // 关闭按钮
                                View {
                                    attr {
                                        width(40f)
                                        height(40f)
                                        alignItemsCenter()
                                        justifyContentCenter()
                                    }
                                    event {
                                        click { preview.close() }
                                    }
                                    Text {
                                        attr {
                                            text("❮")
                                            fontSize(18f)
                                            color(Color.WHITE)
                                        }
                                    }
                                }
                                // 选中按钮
                                vif({ preview.showSelect }) {
                                View {
                                    attr {
                                        width(28f)
                                        height(28f)
                                        borderRadius(14f)
                                        // 读取 selectVersion 建立响应式依赖
                                        val ver = preview.selectVersion
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
                                    Text {
                                        attr {
                                            val ver = preview.selectVersion
                                            val isSelected = preview.selectedSet.contains(preview.imageId)
                                            if (isSelected) {
                                                val num = preview.selectedList.indexOf(preview.imageId) + 1
                                                text(if (num > 0) "$num" else "")
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
                            } // 内容层
                        }

                        // ─── 图片展示区域（支持拖拽 + 双指缩放） ───
                        View {
                            attr {
                                absolutePositionAllZero()
                                allCenter()
                            }

                            Image {
                                attr {
                                    // 实时尺寸 = 累计尺寸 × 当次 pinch 倍数
                                    val w = preview.pinchScale * preview.imageWidth
                                    val h = preview.pinchScale * preview.imageHeight
                                    size(w, h)
                                    resizeContain() // 等比缩放，完整显示图片
                                    src(preview.imageUrl)
                                    // 拖拽位移（Translate 参数是相对组件宽高的百分比）
                                    val tx = preview.translateX
                                    val ty = preview.translateY
                                    if (tx != 0f || ty != 0f) {
                                        transform(
                                            translate = Translate(tx / w, ty / h)
                                        )
                                    }
                                }
                                event {
                                    loadSuccess { preview.opening = false }
                                    loadFailure { preview.opening = false }
                                    // 双指缩放（与官方 PinchGestureExampleDemo 一致）
                                    pinch {
                                        if (it.state != "end") {
                                            preview.pinchScale = it.scale
                                        } else {
                                            preview.imageWidth *= it.scale
                                            preview.imageHeight *= it.scale
                                            preview.pinchScale = 1f
                                        }
                                    }
                                    // 拖拽移动
                                    pan {
                                        when (it.state) {
                                            "start" -> {
                                                preview.panStartX = it.pageX
                                                preview.panStartY = it.pageY
                                                preview.baseTranslateX = preview.translateX
                                                preview.baseTranslateY = preview.translateY
                                            }
                                            "move" -> {
                                                preview.translateX = preview.baseTranslateX + (it.pageX - preview.panStartX)
                                                preview.translateY = preview.baseTranslateY + (it.pageY - preview.panStartY)
                                            }
                                        }
                                    }
                                    // 双击复位
                                    doubleClick {
                                        preview.resetImageTransform()
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }

    private fun resetImageTransform() {
        imageWidth = pager.pageData.pageViewWidth
        imageHeight = pager.pageData.pageViewHeight
        pinchScale = 1f
        translateX = 0f
        translateY = 0f
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
