package com.tencent.kuiklybase.album

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.*

class KRAlbumPreview(private val pager: Pager) {

    var visible by observable(false)
    var currentIndex by observable(0)

    private var imageUrl by observable("")
    private var imageId by observable("")
    private var selectedIds by observable(setOf<String>())
    private var maxSelectCount = 9
    private var themeColor = Color(0xFF07C160)
    private var onSelectionChanged: ((Set<String>) -> Unit)? = null
    private var imageLoading by observable(true)
    private var spinTrigger by observable(0)
    private var opening = false
    private var showSelect = true
    private lateinit var containerRef: ViewRef<DivView>

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
        this.imageUrl = image.uri
        this.imageId = image.id
        this.selectedIds = selectedIds
        this.maxSelectCount = maxSelectCount
        this.themeColor = themeColor
        this.showSelect = showSelect
        this.onSelectionChanged = onSelectionChanged
        imageLoading = true
        currentIndex = index
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
                },
                attrBlock = {
                    opacity(0f)
                    transform(scale = Scale(0.85f, 0.85f))
                }
            )
        } else {
            visible = false
            opening = false
        }
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

                        View {
                            attr {
                                flex(1f)
                                justifyContentCenter()
                                alignItemsCenter()
                            }
                            event {
                                click {
                                    preview.close()
                                }
                            }
                            Image {
                                attr {
                                    size(screenWidth, screenHeight)
                                    src(preview.imageUrl)
                                    resizeContain()
                                }
                                event {
                                    loadSuccess {
                                        preview.imageLoading = false
                                        preview.opening = false
                                    }
                                    loadFailure {
                                        preview.imageLoading = false
                                        preview.opening = false
                                    }
                                    click {
                                        preview.close()
                                    }
                                }
                            }
                        }

                        vif({ preview.imageLoading }) {
                            View {
                                attr {
                                    positionAbsolute()
                                    top(0f)
                                    left(0f)
                                    right(0f)
                                    bottom(0f)
                                    justifyContentCenter()
                                    alignItemsCenter()
                                }
                                View {
                                    ref {
                                        val spinView = it.view
                                        fun startSpin() {
                                            spinView?.animateToAttr(
                                                Animation.linear(0.8f),
                                                completion = { finished ->
                                                    if (finished) {
                                                        spinView.animateToAttr(
                                                            Animation.linear(0.01f),
                                                            completion = { startSpin() },
                                                            attrBlock = { transform(rotate = Rotate(0.01f)) }
                                                        )
                                                    }
                                                },
                                                attrBlock = { transform(rotate = Rotate(360f)) }
                                            )
                                        }
                                        startSpin()
                                    }
                                    attr {
                                        width(30f)
                                        height(30f)
                                        borderRadius(15f)
                                        border(Border(2.5f, BorderStyle.SOLID, Color(0x33FFFFFF)))
                                        transform(rotate = Rotate(0f))
                                    }
                                }
                            }
                        }

                        vif({ preview.showSelect }) {
                            View {
                                attr {
                                    positionAbsolute()
                                    top(statusBarHeight + 10f)
                                    right(16f)
                                    width(24f)
                                    height(24f)
                                    borderRadius(12f)
                                    val isSelected = preview.selectedIds.contains(preview.imageId)
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
        }
    }

    private fun toggleSelect() {
        val current = selectedIds.toMutableSet()
        if (current.contains(imageId)) {
            current.remove(imageId)
        } else {
            if (current.size >= maxSelectCount) return
            current.add(imageId)
        }
        selectedIds = current
        onSelectionChanged?.invoke(selectedIds)
    }
}
