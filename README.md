# KuiklyAlbum

[![GitHub](https://img.shields.io/badge/GitHub-KuiklyAlbum-blue?logo=github)](https://github.com/Kuikly-contrib/KuiklyAlbum)

Kuikly 跨端相册组件，提供 Android / iOS / 鸿蒙三端统一的相册图片选取和预览能力。


## 依赖引入

### KMP 跨端层

在 `build.gradle.kts` 中添加：

```kotlin
// Maven 方式
implementation("com.tencent.kuiklybase:KuiklyAlbum:0.0.1-2.0.21")
```

鸿蒙端在 `build.ohos.gradle.kts` 中添加：

```kotlin
// Maven 方式
implementation("com.tencent.kuiklybase:KuiklyAlbum:0.0.1-2.0.21-KBA-010")
```

### Android 原生端

1. 在 `build.gradle.kts` 中添加：

```kotlin
implementation("com.tencent.kuiklybase:KuiklyAlbumAndroid:0.0.1-2.0.21")
```

2. 在 `KuiklyRenderActivity` 中注册 Module：
```kotlin
override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
    super.registerExternalModule(kuiklyRenderExport)
    with(kuiklyRenderExport) {
        moduleExport(KRAlbumModule.MODULE_NAME) { KRAlbumModule() }
    }
}
```

### iOS

1. 在 `Podfile` 中添加：

```ruby
pod 'KuiklyAlbumIOS', :git => 'https://github.com/Kuikly-contrib/KuiklyAlbum.git', :branch => 'main'
```

2. Module 通过 `NSClassFromString` 自动发现，无需手动注册。

3. 在 `Info.plist` 中添加权限描述：

```xml
<key>NSPhotoLibraryUsageDescription</key>
<string>需要访问相册以选择图片</string>
```

### 鸿蒙

1. 安装依赖：

```bash
ohpm install @yuki8273/kuikly-album
```

2. 在 `KuiklyViewDelegate.ets` 中注册 Module：

```typescript
import { KRAlbumModule } from '@yuki8273/kuikly-album';

getCustomRenderModuleCreatorRegisterMap(): Map<string, KRRenderModuleExportCreator> {
    const map = new Map();
    map.set(KRAlbumModule.MODULE_NAME, () => new KRAlbumModule());
    return map;
}
```

3. 在 `module.json5` 中声明权限：

```json5
"requestPermissions": [
  {
    "name": "ohos.permission.READ_IMAGEVIDEO",
    "reason": "$string:permission_read_imagevideo",
    "usedScene": {
      "abilities": ["EntryAbility"],
      "when": "inuse"
    }
  }
]
```

4. 在 `string.json` 中添加权限描述字符串：

```json
{ "name": "permission_read_imagevideo", "value": "访问照片和视频以浏览相册" }
```

## API

### KuiklyAlbumView

相册选择器核心组件（ComposeView），提供网格展示、多选、预览等完整能力。内置权限请求、两步加载、全局缓存等优化。

#### 基本使用

```kotlin
// 1. 在 Pager 中注册 Module
override fun createExternalModules(): Map<String, Module>? {
    val modules = super.createExternalModules()?.toMutableMap() ?: hashMapOf()
    modules[KRAlbumModule.MODULE_NAME] = KRAlbumModule()
    return modules
}

// 2. 在 body() 中使用组件
KuiklyAlbum {
    attr {
        maxSelectCount(9)            // 最大选中数量（默认 9）
        columnCount(4)               // 网格列数（默认 3）
        itemSpacing(3f)              // 网格间距（默认 3f）
        themeColor(Color(0xFF07C160)) // 主题色（默认微信绿）
        showVideoLabel(true)         // 是否显示视频时长标签（默认 true）
        maxImageCount(Int.MAX_VALUE) // 最大加载图片数量（默认不限）
        flex(1f)
    }
    event {
        onSelectionChanged { images ->  // 选中状态变化回调
            // images: List<KRAlbumImage>
        }
        onConfirm { images ->           // 确认选择回调
            // images: List<KRAlbumImage>
        }
    }
}
```

#### 公开属性与方法

| 属性/方法 | 类型 | 说明 |
|-----------|------|------|
| `images` | `List<KRAlbumImage>` | 当前加载的图片列表（只读） |
| `selectedIds` | `Set<String>` | 当前选中的 ID 集合（只读） |
| `selectedOrder` | `List<String>` | 有序选中列表（只读） |
| `loading` | `Boolean` | 是否正在加载 |
| `getSelectIndex(imageId)` | `Int` | 获取图片选中编号（从1开始，未选中返回0） |
| `getSelectedImages()` | `List<KRAlbumImage>` | 获取已选中的图片列表（按选择顺序） |

### KRAlbumModule

底层 Module，提供权限请求、图片/视频获取、相册列表等原生桥接能力。`KuiklyAlbumView` 内部已自动使用此 Module，通常无需直接调用。

如需自定义相册 UI，可直接使用：

```kotlin
val module = acquireModule<KRAlbumModule>(KRAlbumModule.MODULE_NAME)
```

#### requestPermission

请求相册读取权限。

```kotlin
module.requestPermission { result ->
    // result: {"granted": true} 或 {"granted": false, "message": "..."}
}
```

#### isPermissionGranted

判断权限请求回调结果是否已授权（辅助方法）。

```kotlin
module.requestPermission { result ->
    if (module.isPermissionGranted(result)) {
        // 已授权
    }
}
```

#### checkPermission

同步检查权限状态。

```kotlin
val status = module.checkPermission() // "granted" | "denied" | "not_determined"(仅 iOS)
```

#### fetchImages

获取本地图片和视频列表（原始 JSON 回调）。

```kotlin
module.fetchImages(maxCount = 200) { result ->
    // result: {"data": [{"id":"...", "uri":"...", "thumbnailUri":"...", "width":1080, "height":1920, "isVideo":false, "duration":0, "size":1234567}, ...]}
}
```

#### fetchAlbums

获取相册列表（原始 JSON 回调）。

```kotlin
module.fetchAlbums { result ->
    // result: {"data": [{"id":"...", "name":"Camera Roll", "count":100}, ...]}
}
```

#### fetchImagesFromAlbum

获取指定相册中的图片和视频（原始 JSON 回调）。

```kotlin
module.fetchImagesFromAlbum(albumId = "...", maxCount = 200) { result ->
    // result: {"data": [{"id":"...", "uri":"...", "thumbnailUri":"...", "width":1080, "height":1920, "isVideo":false, "duration":0, "size":1234567}, ...]}
}
```

#### fetchMetadata

只获取元数据（路径/ID/尺寸），不解码图片。用于两步加载的第一步，快速显示网格。

```kotlin
module.fetchMetadata(maxCount = 200) { result ->
    // result 格式同 fetchImages
}
```

#### 高级封装（带类型解析）

自动将 JSON 解析为类型安全的对象列表：

```kotlin
// 获取图片/视频列表
module.fetchImageList(maxCount = 200) { images: List<KRAlbumImage> -> }

// 获取指定相册的图片/视频列表
module.fetchImageListFromAlbum(albumId = "...", maxCount = 200) { images: List<KRAlbumImage> -> }

// 获取相册列表
module.fetchAlbumList { albums: List<KRAlbumInfo> -> }

// 只获取元数据
module.fetchMetadataList(maxCount = 200) { images: List<KRAlbumImage> -> }
```

#### ImageCache / AlbumCache

全局静态缓存，二次打开相册时跳过扫描（加载时间从 ~800ms 降到 ~5ms）。

```kotlin
// 获取/写入/清除图片缓存
KRAlbumModule.ImageCache.get()          // List<KRAlbumImage>?
KRAlbumModule.ImageCache.put(images)
KRAlbumModule.ImageCache.invalidate()   // 媒体库变化时调用

// 相册缓存同理
KRAlbumModule.AlbumCache.get()
KRAlbumModule.AlbumCache.put(albums)
KRAlbumModule.AlbumCache.invalidate()
```

### 数据类

#### KRAlbumImage

图片/视频条目数据模型。

```kotlin
data class KRAlbumImage(
    val id: String,            // 唯一标识
    val uri: String,           // 原图/原视频 URI
    val thumbnailUri: String,  // 缩略图 URI（优先使用，加载更快）
    val width: Int,            // 原始宽度
    val height: Int,           // 原始高度
    val isVideo: Boolean,      // 是否为视频
    val duration: Int,         // 视频时长（秒），仅视频有效
    val size: Long,            // 文件大小（字节）
    val albumId: String        // 所属相册 ID
)
```

辅助方法：
- `formatDuration()` — 格式化视频时长，如 `"1:23"`
- `displayUri()` — 获取最佳显示 URI（优先缩略图）

#### KRAlbumInfo

相册信息数据模型。

```kotlin
data class KRAlbumInfo(
    val id: String,            // 相册唯一标识
    val name: String,          // 相册名称
    val count: Int,            // 相册内媒体数量
    val coverUri: String,      // 封面图 URI
    val videoOnly: Boolean     // 是否仅包含视频
)
```

#### KRAlbumRow

相册网格行数据（内部使用）。

```kotlin
data class KRAlbumRow(
    val items: List<KRAlbumImage>,  // 该行的图片列表（1~columnCount 张）
    val startIndex: Int             // 此行第一张图片在原始列表中的起始索引
)
```

### KRAlbumPreview

全屏图片预览组件，支持过渡动画、选中操作、双指缩放、拖拽平移、双击放大/还原。

#### 初始化

```kotlin
private val preview by lazy { KRAlbumPreview(this) }
```

#### 挂载到页面

在 `body()` 的 ViewBuilder 末尾调用：

```kotlin
override fun body(): ViewBuilder {
    val ctx = this
    return {
        // ... 页面内容 ...

        ctx.preview.buildPreview()()
    }
}
```

#### 打开预览

```kotlin
preview.open(
    imageUrl = image.uri,             // 图片 URI
    imageId = image.id,               // 图片唯一标识
    selectedIds = selectedIds,        // 当前选中的图片 ID 集合
    maxSelectCount = 9,               // 最大选中数量（默认 9）
    themeColor = Color(0xFF07C160),   // 主题色（默认微信绿）
    showSelect = true,                // 是否显示选中按钮（默认 true）
    onSelectionChanged = { ids ->     // 选中状态变化回调
        // ids: Set<String>
    }
)
```
