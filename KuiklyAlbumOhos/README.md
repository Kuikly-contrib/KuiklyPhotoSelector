# @yuki8273/kuikly-album

Kuikly 跨端相册组件的鸿蒙原生模块，提供相册图片选取和预览能力。

## 安装

```bash
ohpm install @yuki8273/kuikly-album
```

## 使用

### 1. 注册 Module

在 `KuiklyViewDelegate.ets` 中注册：

```typescript
import { KRAlbumModule } from '@yuki8273/kuikly-album';

getCustomRenderModuleCreatorRegisterMap(): Map<string, KRRenderModuleExportCreator> {
    const map = new Map();
    map.set(KRAlbumModule.MODULE_NAME, () => new KRAlbumModule());
    return map;
}
```

### 2. 声明权限

在 `module.json5` 中添加：

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

在 `string.json` 中添加权限描述：

```json
{ "name": "permission_read_imagevideo", "value": "访问照片和视频以浏览相册" }
```

## API

### KRAlbumModule

| 方法 | 说明 |
|------|------|
| `requestPermission` | 请求相册读取权限，回调返回 `{granted: boolean}` |
| `checkPermission` | 同步检查权限状态，返回 `"granted"` 或 `"denied"` |
| `fetchImages` | 获取本地图片列表，参数 `{maxCount: number}` |
| `fetchAlbums` | 获取相册列表 |
| `fetchImagesFromAlbum` | 获取指定相册图片，参数 `{albumId: string, maxCount: number}` |
| `fetchMetadata` | 获取图片元数据（ID/URI/尺寸） |
| `requestThumbnail` | 按需请求单张缩略图，参数 `{imageId: string}` |
| `cancelThumbnailRequest` | 取消缩略图请求 |

## 依赖

- `@kuikly-open/render` >= 2.7.0

## License

MIT
