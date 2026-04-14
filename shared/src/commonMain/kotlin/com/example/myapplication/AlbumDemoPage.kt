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
import com.tencent.kuiklybase.album.KRAlbumImage
import com.tencent.kuiklybase.album.KRAlbumPreview

@Page("router")
internal class AlbumDemoPage : BasePager() {

    private var images by observableList<KRAlbumImage>()
    private var selectedIds by observable(setOf<String>())
    private var loading by observable(true)
    private val maxSelectCount = 9

    private val preview by lazy { KRAlbumPreview(this) }

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
                        color(if (count > 0) Color(0xFF07C160) else Color(0xFF999999))
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
                                    event {
                                        click {
                                            ctx.preview.open(
                                                images = ctx.images.toList(),
                                                index = index,
                                                selectedIds = ctx.selectedIds,
                                                maxSelectCount = ctx.maxSelectCount,
                                                onSelectionChanged = { ctx.selectedIds = it }
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
                                            backgroundColor(Color(0xFF07C160))
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

            ctx.preview.buildPreview()()
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        val module = acquireModule<KRAlbumModule>(KRAlbumModule.MODULE_NAME)
        module.requestPermission { result ->
            if (module.isPermissionGranted(result)) {
                module.fetchImageList(200) { list ->
                    list.forEach { images.add(it) }
                    loading = false
                }
            } else {
                loading = false
            }
        }
    }

    private fun toggleSelect(image: KRAlbumImage) {
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
