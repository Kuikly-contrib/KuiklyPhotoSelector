# KuiklyAlbum

Kuikly 跨端相册组件，提供 Android / iOS / 鸿蒙三端统一的相册图片读取能力。

## 功能

- 请求相册权限
- 获取本地图片列表
- 获取相册列表
- 按相册获取图片


## KMP 层 API

```kotlin
val albumModule = acquireModule<KRAlbumModule>(KRAlbumModule.MODULE_NAME)

// 请求权限
albumModule.requestPermission { result -> /* {"granted": true/false} */ }

// 检查权限（同步）
val status = albumModule.checkPermission() // "granted" | "denied" | "not_determined"

// 获取图片列表
albumModule.fetchImages(maxCount = 200) { result ->
    // JSON Array: [{"id":"...", "uri":"...", "width":1080, "height":1920}, ...]
}

// 获取相册列表
albumModule.fetchAlbums { result ->
    // JSON Array: [{"id":"...", "name":"Camera Roll", "count":100}, ...]
}

// 获取指定相册中的图片
albumModule.fetchImagesFromAlbum(albumId = "...", maxCount = 200) { result ->
    // JSON Array: [{"id":"...", "uri":"...", "width":1080, "height":1920}, ...]
}
```

## 各端接入

### Android

在 `build.gradle.kts` 中添加依赖：
```kotlin
implementation(project(":KuiklyAlbumAndroid"))
```

在 `KuiklyRenderActivity` 中注册：
```kotlin
override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
    super.registerExternalModule(kuiklyRenderExport)
    with(kuiklyRenderExport) {
        moduleExport(KRAlbumModule.MODULE_NAME) { KRAlbumModule() }
    }
}
```

### iOS

在 `Podfile` 中添加：
```ruby
pod 'KuiklyAlbumIOS', :git => '仓库地址', :branch => 'main'
```

Module 通过 `NSClassFromString` 自动发现，无需手动注册。

在 `Info.plist` 中添加 `NSPhotoLibraryUsageDescription`。

### 鸿蒙

在 `oh-package.json5` 中添加依赖：
```json5
{ "dependencies": { "KuiklyAlbumOhos": "file:../../KuiklyAlbumOhos" } }
```

在 `KuiklyViewDelegate.ets` 中注册：
```typescript
import { KRAlbumModule } from 'KuiklyAlbumOhos';
map.set(KRAlbumModule.MODULE_NAME, () => new KRAlbumModule());
```

在 `module.json5` 中添加权限：
```json5
"requestPermissions": [{
  "name": "ohos.permission.READ_IMAGEVIDEO",
  "reason": "$string:album_reason",
  "usedScene": { "abilities": ["EntryAbility"], "when": "inuse" }
}]
```
