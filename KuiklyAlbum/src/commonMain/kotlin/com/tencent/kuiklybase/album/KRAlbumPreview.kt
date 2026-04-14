package com.tencent.kuiklybase.album

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
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
    private var opening = false

    fun open(
        images: List<KRAlbumImage>,
        index: Int,
        selectedIds: Set<String> = emptySet(),
        maxSelectCount: Int = 9,
        themeColor: Color = Color(0xFF07C160),
        onSelectionChanged: ((Set<String>) -> Unit)? = null
    ) {
        if (opening || visible) return
        opening = true

        val image = images.getOrNull(index) ?: return
        this.imageUrl = image.uri
        this.imageId = image.id
        this.selectedIds = selectedIds
        this.maxSelectCount = maxSelectCount
        this.themeColor = themeColor
        this.onSelectionChanged = onSelectionChanged
        imageLoading = true
        currentIndex = index
        visible = true
    }

    fun close() {
        visible = false
        opening = false
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
                            Text {
                                attr {
                                    text("加载中...")
                                    fontSize(14f)
                                    color(Color(0xAAFFFFFF))
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
