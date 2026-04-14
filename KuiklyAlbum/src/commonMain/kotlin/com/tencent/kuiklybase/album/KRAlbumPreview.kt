package com.tencent.kuiklybase.album

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.*

class KRAlbumPreview(private val pager: Pager) {

    var visible by observable(false)
    var currentIndex by observable(0)

    private var scale by observable(1f)
    private var baseScale by observable(1f)
    private var offsetX by observable(0f)
    private var offsetY by observable(0f)

    private var images by observableList<KRAlbumImage>()
    private var selectedIds by observable(setOf<String>())
    private var maxSelectCount = 9
    private var themeColor = Color(0xFF07C160)
    private var onSelectionChanged: ((Set<String>) -> Unit)? = null
    private lateinit var scrollerRef: ViewRef<ScrollerView<*, *>>
    private var needsInitialScroll = false

    fun open(
        images: List<KRAlbumImage>,
        index: Int,
        selectedIds: Set<String> = emptySet(),
        maxSelectCount: Int = 9,
        themeColor: Color = Color(0xFF07C160),
        onSelectionChanged: ((Set<String>) -> Unit)? = null
    ) {
        this.images.clear()
        images.forEach { this.images.add(it) }
        this.selectedIds = selectedIds
        this.maxSelectCount = maxSelectCount
        this.themeColor = themeColor
        this.onSelectionChanged = onSelectionChanged
        resetState()
        currentIndex = index
        needsInitialScroll = index > 0
        visible = true
    }

    fun close() {
        visible = false
        resetState()
    }

    fun buildPreview(): ViewBuilder {
        val preview = this
        val screenWidth = pager.pagerData.pageViewWidth
        val screenHeight = pager.pagerData.pageViewHeight
        val statusBarHeight = pager.pagerData.statusBarHeight

        return {
            vif({ preview.visible }) {
                Modal(true) {
                    attr {
                        absolutePosition(0f, 0f, 0f, 0f)
                        backgroundColor(Color.BLACK)
                    }
                    event {
                        willDismiss {
                            preview.close()
                        }
                    }

                    Scroller {
                        ref {
                            preview.scrollerRef = it
                            if (preview.currentIndex > 0) {
                                it.view?.setContentOffset(preview.currentIndex * screenWidth, 0f)
                            }
                        }
                        attr {
                            flex(1f)
                            flexDirectionRow()
                            pagingEnable(true)
                            showScrollerIndicator(false)
                            scrollEnable(preview.scale <= 1.01f)
                        }
                        event {
                            scrollEnd {
                                val newIndex = (it.offsetX / screenWidth + 0.5f).toInt()
                                    .coerceIn(0, preview.images.size - 1)
                                if (newIndex != preview.currentIndex) {
                                    preview.currentIndex = newIndex
                                    preview.resetState()
                                }
                            }
                            contentSizeChanged { _, _ ->
                                if (preview.needsInitialScroll) {
                                    preview.needsInitialScroll = false
                                    preview.scrollerRef.view?.setContentOffset(
                                        preview.currentIndex * screenWidth, 0f
                                    )
                                }
                            }
                        }

                        vforIndex({ preview.images }) { image, index, _ ->
                            View {
                                attr {
                                    size(screenWidth, screenHeight)
                                    justifyContentCenter()
                                    alignItemsCenter()
                                }
                                event {
                                    click {
                                        if (preview.scale <= 1.01f) {
                                            preview.close()
                                        }
                                    }
                                }
                                Image {
                                    attr {
                                        size(screenWidth, screenHeight)
                                        src(image.uri)
                                        resizeContain()
                                        if (index == preview.currentIndex) {
                                            transform(
                                                scale = Scale(preview.scale, preview.scale),
                                                translate = Translate(
                                                    preview.offsetX / screenWidth,
                                                    preview.offsetY / screenHeight
                                                )
                                            )
                                        }
                                    }
                                    event {
                                        click {
                                            if (preview.scale <= 1.01f) {
                                                preview.close()
                                            }
                                        }
                                        pinch {
                                            if (it.state == "end") {
                                                preview.baseScale = (preview.baseScale * it.scale).coerceIn(0.5f, 5f)
                                                preview.scale = preview.baseScale
                                            } else {
                                                preview.scale = (preview.baseScale * it.scale).coerceIn(0.5f, 5f)
                                            }
                                        }
                                        pan {
                                            if (preview.scale > 1f && it.state == "move") {
                                                preview.offsetX += it.x
                                                preview.offsetY += it.y
                                            }
                                        }
                                        doubleClick {
                                            preview.resetState()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    View {
                        attr {
                            positionAbsolute()
                            top(statusBarHeight + 10f)
                            right(16f)
                            width(24f)
                            height(24f)
                            borderRadius(12f)
                            val currentImage = if (preview.currentIndex in 0 until preview.images.size) preview.images[preview.currentIndex] else null
                            val isSelected = currentImage != null && preview.selectedIds.contains(currentImage.id)
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
                            click {
                                preview.toggleSelect()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toggleSelect() {
        if (currentIndex !in 0 until images.size) return
        val currentImage = images[currentIndex]
        val current = selectedIds.toMutableSet()
        if (current.contains(currentImage.id)) {
            current.remove(currentImage.id)
        } else {
            if (current.size >= maxSelectCount) return
            current.add(currentImage.id)
        }
        selectedIds = current
        onSelectionChanged?.invoke(selectedIds)
    }

    private fun resetState() {
        scale = 1f
        baseScale = 1f
        offsetX = 0f
        offsetY = 0f
    }
}
