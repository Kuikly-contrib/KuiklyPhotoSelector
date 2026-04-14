# KuiklyAlbum

[![GitHub](https://img.shields.io/badge/GitHub-KuiklyAlbum-blue?logo=github)](https://github.com/Kuikly-contrib/KuiklyAlbum)

Kuikly 跨端相册组件，提供 Android / iOS / 鸿蒙三端统一的相册图片选取和预览能力。


## 依赖引入

### KMP 跨端层

在 `build.gradle.kts` 的 `commonMain` 中添加：

```kotlin
// Maven 方式
implementation("com.tencent.kuiklybase:KuiklyAlbum:1.0.0-2.0.21")
```

### Android 原生端

```kotlin
// build.gradle.kts中添加
implementation("com.tencent.kuiklybase:KuiklyAlbumAndroid:1.0.0-2.0.21")
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

### KRAlbumModule

在 Pager 中注册并使用：

```kotlin
override fun createExternalModules(): Map<String, Module>? {
    val modules = super.createExternalModules()?.toMutableMap() ?: hashMapOf()
    modules[KRAlbumModule.MODULE_NAME] = KRAlbumModule()
    return modules
}

val module = acquireModule<KRAlbumModule>(KRAlbumModule.MODULE_NAME)
```

#### requestPermission

请求相册读取权限。

```kotlin
module.requestPermission { result ->
    // result: {"granted": true} 或 {"granted": false, "message": "..."}
}
```

#### checkPermission

同步检查权限状态。

```kotlin
val status = module.checkPermission() // "granted" | "denied"
```

#### fetchImages

获取本地图片列表。

```kotlin
module.fetchImages(maxCount = 200) { result ->
    // result: {"data": [{"id":"...", "uri":"...", "width":1080, "height":1920}, ...]}
}
```

#### fetchAlbums

获取相册列表。

```kotlin
module.fetchAlbums { result ->
    // result: {"data": [{"id":"...", "name":"Camera Roll", "count":100}, ...]}
}
```

#### fetchImagesFromAlbum

获取指定相册中的图片。

```kotlin
module.fetchImagesFromAlbum(albumId = "...", maxCount = 200) { result ->
    // result: {"data": [{"id":"...", "uri":"...", "width":1080, "height":1920}, ...]}
}
```

### KRAlbumImage

图片数据类。

```kotlin
data class KRAlbumImage(
    val id: String,
    val uri: String,
    val width: Int = 0,
    val height: Int = 0
)
```

### KRAlbumPreview

全屏图片预览组件，支持过渡动画、加载指示器、选中操作。

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
    images = imageList,           // List<KRAlbumImage>
    index = clickedIndex,         // 点击的图片索引
    selectedIds = selectedIds,    // 当前选中的图片 ID 集合
    maxSelectCount = 9,           // 最大选中数量
    themeColor = Color(0xFF07C160), // 主题色（选中按钮颜色）
    onSelectionChanged = { ids -> // 选中状态变化回调
        selectedIds = ids
    }
)
```

#### 关闭预览

```kotlin
preview.close()
```

点击图片也会自动关闭预览。