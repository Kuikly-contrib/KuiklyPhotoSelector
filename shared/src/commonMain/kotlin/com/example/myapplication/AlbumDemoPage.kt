package com.example.myapplication

import com.example.myapplication.base.BasePager
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.*
import com.tencent.kuiklybase.album.KRAlbumModule
import com.tencent.kuiklybase.album.KuiklyAlbum

@Page("router")
internal class AlbumDemoPage : BasePager() {

    private var selectedCount by observable(0)
    private var hasVideo by observable(false)
    private var hasPhoto by observable(false)

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

            // 状态栏
            View {
                attr {
                    height(ctx.pagerData.statusBarHeight)
                    backgroundColor(Color.WHITE)
                }
            }

            // 标题栏
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
                        val count = ctx.selectedCount
                        val label = when {
                            count == 0 -> "完成"
                            ctx.hasVideo && ctx.hasPhoto -> "完成($count/9 项)"
                            ctx.hasVideo -> "完成($count/9 视频)"
                            else -> "完成($count/9 照片)"
                        }
                        text(label)
                        fontSize(15f)
                        color(if (count > 0) Color(0xFF07C160) else Color(0xFF999999))
                    }
                }
            }

            // 相册组件
            KuiklyAlbum {
                attr {
                    maxSelectCount(9)
                    columnCount(3)
                    itemSpacing(3f)
                    themeColor(Color(0xFF07C160))
                    showVideoLabel(true)
                    flex(1f)
                }
                event {
                    onSelectionChanged { images ->
                        ctx.selectedCount = images.size
                        var foundVideo = false
                        var foundPhoto = false
                        for (img in images) {
                            if (img.isVideo) foundVideo = true else foundPhoto = true
                            if (foundVideo && foundPhoto) break
                        }
                        ctx.hasVideo = foundVideo
                        ctx.hasPhoto = foundPhoto
                    }
                    onConfirm { images ->
                        println("Confirmed ${images.size} images")
                    }
                }
            }
        }
    }
}
