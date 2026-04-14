import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { abilityAccessCtrl, common, Permissions } from '@kit.AbilityKit';

export class KRAlbumModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRAlbumModule';

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    switch (method) {
      case 'requestPermission':
        this.requestPermission(callback);
        return null;
      case 'checkPermission':
        return this.checkPermission();
      case 'fetchImages':
        this.fetchImages(params as string, callback);
        return null;
      case 'fetchAlbums':
        this.fetchAlbums(callback);
        return null;
      case 'fetchImagesFromAlbum':
        this.fetchImagesFromAlbum(params as string, callback);
        return null;
      default:
        return null;
    }
  }

  private async requestPermission(callback: KuiklyRenderCallback | null): Promise<void> {
    const permissions: Permissions[] = ['ohos.permission.READ_IMAGEVIDEO'];
    const atManager = abilityAccessCtrl.createAtManager();
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const grantStatus = atManager.checkAccessTokenSync(
        context.applicationInfo.accessTokenId, permissions[0]
      );
      if (grantStatus === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED) {
        callback?.(JSON.stringify({ granted: true }));
        return;
      }
      const result = await atManager.requestPermissionsFromUser(context, permissions);
      const granted = result.authResults[0] === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
      callback?.(JSON.stringify({ granted }));
    } catch (error) {
      const err = error as BusinessError;
      callback?.(JSON.stringify({ granted: false, message: err.message }));
    }
  }

  private checkPermission(): string {
    const permission: Permissions = 'ohos.permission.READ_IMAGEVIDEO';
    const atManager = abilityAccessCtrl.createAtManager();
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const grantStatus = atManager.checkAccessTokenSync(
        context.applicationInfo.accessTokenId, permission
      );
      return grantStatus === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED ? 'granted' : 'denied';
    } catch (error) {
      return 'denied';
    }
  }

  private async fetchImages(paramsStr: string | null, callback: KuiklyRenderCallback | null): Promise<void> {
    let maxCount = 200;
    if (paramsStr) {
      try {
        const p = JSON.parse(paramsStr) as Record<string, number>;
        maxCount = p['maxCount'] ?? 200;
      } catch (_) {}
    }
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const helper = photoAccessHelper.getPhotoAccessHelper(context);
      const fetchOptions: photoAccessHelper.FetchOptions = {
        fetchColumns: ['uri', 'display_name', 'width', 'height', 'media_type'],
        predicates: new photoAccessHelper.FetchOptions({
          fetchColumns: [],
          predicates: undefined
        }).predicates
      };
      const fetchResult = await helper.getAssets(fetchOptions);
      const images: object[] = [];
      let count = 0;
      let asset = await fetchResult.getFirstObject();
      while (asset && count < maxCount) {
        images.push({
          id: asset.uri,
          uri: asset.uri,
          width: asset.get('width') as number ?? 0,
          height: asset.get('height') as number ?? 0
        });
        count++;
        try {
          asset = await fetchResult.getNextObject();
        } catch (_) {
          break;
        }
      }
      fetchResult.close();
      callback?.(JSON.stringify(images));
    } catch (error) {
      const err = error as BusinessError;
      console.error(`KRAlbumModule fetchImages error: ${err.message}`);
      callback?.(JSON.stringify([]));
    }
  }

  private async fetchAlbums(callback: KuiklyRenderCallback | null): Promise<void> {
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const helper = photoAccessHelper.getPhotoAccessHelper(context);
      const albumFetchResult = await helper.getAlbums(photoAccessHelper.AlbumType.USER, photoAccessHelper.AlbumSubtype.ANY);
      const albums: object[] = [];
      let album = await albumFetchResult.getFirstObject();
      while (album) {
        albums.push({
          id: album.albumUri,
          name: album.albumName,
          count: album.count
        });
        try {
          album = await albumFetchResult.getNextObject();
        } catch (_) {
          break;
        }
      }
      albumFetchResult.close();
      callback?.(JSON.stringify(albums));
    } catch (error) {
      const err = error as BusinessError;
      console.error(`KRAlbumModule fetchAlbums error: ${err.message}`);
      callback?.(JSON.stringify([]));
    }
  }

  private async fetchImagesFromAlbum(paramsStr: string | null, callback: KuiklyRenderCallback | null): Promise<void> {
    if (!paramsStr) {
      callback?.(JSON.stringify({ data: [] }));
      return;
    }
    let albumId = '';
    let maxCount = 200;
    try {
      const p = JSON.parse(paramsStr) as Record<string, string | number>;
      albumId = p['albumId'] as string ?? '';
      maxCount = p['maxCount'] as number ?? 200;
    } catch (_) {}

    if (!albumId) {
      callback?.(JSON.stringify([]));
      return;
    }

    try {
      const context = getContext(this) as common.UIAbilityContext;
      const helper = photoAccessHelper.getPhotoAccessHelper(context);
      const albumFetchResult = await helper.getAlbums(photoAccessHelper.AlbumType.USER, photoAccessHelper.AlbumSubtype.ANY);
      let targetAlbum: photoAccessHelper.Album | null = null;
      let album = await albumFetchResult.getFirstObject();
      while (album) {
        if (album.albumUri === albumId) {
          targetAlbum = album;
          break;
        }
        try {
          album = await albumFetchResult.getNextObject();
        } catch (_) {
          break;
        }
      }
      albumFetchResult.close();

      if (!targetAlbum) {
        callback?.(JSON.stringify({ data: [] }));
        return;
      }

      const fetchOptions: photoAccessHelper.FetchOptions = {
        fetchColumns: ['uri', 'display_name', 'width', 'height'],
        predicates: new photoAccessHelper.FetchOptions({
          fetchColumns: [],
          predicates: undefined
        }).predicates
      };
      const assetResult = await targetAlbum.getAssets(fetchOptions);
      const images: object[] = [];
      let count = 0;
      let asset = await assetResult.getFirstObject();
      while (asset && count < maxCount) {
        images.push({
          id: asset.uri,
          uri: asset.uri,
          width: asset.get('width') as number ?? 0,
          height: asset.get('height') as number ?? 0
        });
        count++;
        try {
          asset = await assetResult.getNextObject();
        } catch (_) {
          break;
        }
      }
      assetResult.close();
      callback?.(JSON.stringify({ data: images }));
    } catch (error) {
      const err = error as BusinessError;
      console.error(`KRAlbumModule fetchImagesFromAlbum error: ${err.message}`);
      callback?.(JSON.stringify({ data: [] }));
    }
  }
}
