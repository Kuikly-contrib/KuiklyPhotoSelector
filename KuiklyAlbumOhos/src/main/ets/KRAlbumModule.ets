import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { dataSharePredicates } from '@kit.ArkData';
import { BusinessError } from '@kit.BasicServicesKit';
import { abilityAccessCtrl, common, Permissions } from '@kit.AbilityKit';

class AlbumImageInfo {
  id: string = '';
  uri: string = '';
  thumbnailUri: string = '';
  width: number = 0;
  height: number = 0;
}

class AlbumInfo {
  id: string = '';
  name: string = '';
  count: number = 0;
}

export class KRAlbumModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRAlbumModule';

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    console.info(`KRAlbumModule: call method=${method}, params=${JSON.stringify(params)}`);
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
    console.info('KRAlbumModule: requestPermission called');
    const permissions: Permissions[] = ['ohos.permission.READ_IMAGEVIDEO'];
    const atManager = abilityAccessCtrl.createAtManager();
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const grantStatus = atManager.checkAccessTokenSync(
        context.applicationInfo.accessTokenId, permissions[0]
      );
      console.info(`KRAlbumModule: checkAccessToken grantStatus=${grantStatus}`);
      if (grantStatus === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED) {
        console.info('KRAlbumModule: permission already granted');
        callback?.(JSON.stringify({ granted: true }));
        return;
      }
      const result = await atManager.requestPermissionsFromUser(context, permissions);
      const granted = result.authResults[0] === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
      console.info(`KRAlbumModule: requestPermissionsFromUser granted=${granted}`);
      callback?.(JSON.stringify({ granted }));
    } catch (error) {
      const err = error as BusinessError;
      console.error(`KRAlbumModule: requestPermission error: ${err.message}`);
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
    console.info(`KRAlbumModule: fetchImages called, paramsStr=${paramsStr}`);
    let maxCount = Number.MAX_SAFE_INTEGER;
    if (paramsStr) {
      try {
        const p = JSON.parse(paramsStr) as Record<string, number>;
        maxCount = p['maxCount'] ?? Number.MAX_SAFE_INTEGER;
      } catch (_) {}
    }
    console.info(`KRAlbumModule: fetchImages maxCount=${maxCount}`);
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const helper = photoAccessHelper.getPhotoAccessHelper(context);
      console.info('KRAlbumModule: got photoAccessHelper');
      const predicates = new dataSharePredicates.DataSharePredicates();
      const fetchOptions: photoAccessHelper.FetchOptions = {
        fetchColumns: ['uri', 'display_name', 'width', 'height', 'media_type'],
        predicates: predicates
      };
      const fetchResult = await helper.getAssets(fetchOptions);
      console.info(`KRAlbumModule: getAssets count=${fetchResult.getCount()}`);
      const images: AlbumImageInfo[] = [];
      let count = 0;
      if (fetchResult.getCount() > 0) {
        let asset = await fetchResult.getFirstObject();
        while (asset && count < maxCount) {
          const info = new AlbumImageInfo();
          info.id = asset.uri;
          info.uri = asset.uri;
          info.thumbnailUri = asset.uri;
          info.width = asset.get('width') as number ?? 0;
          info.height = asset.get('height') as number ?? 0;
          if (count < 3) {
            console.info(`KRAlbumModule: image[${count}] uri=${info.uri}`);
          }
          images.push(info);
          count++;
          try {
            asset = await fetchResult.getNextObject();
          } catch (_) {
            break;
          }
        }
      }
      fetchResult.close();
      console.info(`KRAlbumModule: fetchImages done, total=${images.length}`);
      const result = JSON.stringify({ data: images });
      console.info(`KRAlbumModule: callback data length=${result.length}`);
      callback?.(result);
    } catch (error) {
      const err = error as BusinessError;
      console.error(`KRAlbumModule fetchImages error: ${err.code} ${err.message}`);
      callback?.(JSON.stringify({ data: [] }));
    }
  }

  private async fetchAlbums(callback: KuiklyRenderCallback | null): Promise<void> {
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const helper = photoAccessHelper.getPhotoAccessHelper(context);
      const albumFetchResult = await helper.getAlbums(photoAccessHelper.AlbumType.USER, photoAccessHelper.AlbumSubtype.ANY);
      const albums: AlbumInfo[] = [];
      let album = await albumFetchResult.getFirstObject();
      while (album) {
        const info = new AlbumInfo();
        info.id = album.albumUri;
        info.name = album.albumName;
        info.count = album.count;
        albums.push(info);
        try {
          album = await albumFetchResult.getNextObject();
        } catch (_) {
          break;
        }
      }
      albumFetchResult.close();
      callback?.(JSON.stringify({ data: albums }));
    } catch (error) {
      const err = error as BusinessError;
      console.error(`KRAlbumModule fetchAlbums error: ${err.message}`);
      callback?.(JSON.stringify({ data: [] }));
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
      callback?.(JSON.stringify({ data: [] }));
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

      const predicates = new dataSharePredicates.DataSharePredicates();
      const fetchOptions: photoAccessHelper.FetchOptions = {
        fetchColumns: ['uri', 'display_name', 'width', 'height'],
        predicates: predicates
      };
      const assetResult = await targetAlbum.getAssets(fetchOptions);
      const images: AlbumImageInfo[] = [];
      let count = 0;
      let asset = await assetResult.getFirstObject();
      while (asset && count < maxCount) {
        const info = new AlbumImageInfo();
        info.id = asset.uri;
        info.uri = asset.uri;
        info.thumbnailUri = asset.uri;
        info.width = asset.get('width') as number ?? 0;
        info.height = asset.get('height') as number ?? 0;
        images.push(info);
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
