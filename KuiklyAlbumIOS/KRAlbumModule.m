#import "KRAlbumModule.h"
#import <Photos/Photos.h>
#import <OpenKuiklyIOSRender/NSObject+KR.h>

@implementation KRAlbumModule

@synthesize hr_rootView;

TDF_EXPORT_MODULE(KRAlbumModule)

#pragma mark - Permission

- (void)requestPermission:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    PHAuthorizationStatus status;
    if (@available(iOS 14, *)) {
        status = [PHPhotoLibrary authorizationStatusForAccessLevel:PHAccessLevelReadWrite];
    } else {
        status = [PHPhotoLibrary authorizationStatus];
    }
    
    if (status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited) {
        if (callback) callback(@{@"granted": @(YES)});
        return;
    }
    
    if (status == PHAuthorizationStatusNotDetermined) {
        if (@available(iOS 14, *)) {
            [PHPhotoLibrary requestAuthorizationForAccessLevel:PHAccessLevelReadWrite handler:^(PHAuthorizationStatus newStatus) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    BOOL granted = (newStatus == PHAuthorizationStatusAuthorized || newStatus == PHAuthorizationStatusLimited);
                    if (callback) callback(@{@"granted": @(granted)});
                });
            }];
        } else {
            [PHPhotoLibrary requestAuthorization:^(PHAuthorizationStatus newStatus) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    if (callback) callback(@{@"granted": @(newStatus == PHAuthorizationStatusAuthorized)});
                });
            }];
        }
        return;
    }
    
    if (callback) callback(@{@"granted": @(NO), @"message": @"Photo library permission denied"});
}

- (NSString *)checkPermission:(NSDictionary *)args {
    PHAuthorizationStatus status;
    if (@available(iOS 14, *)) {
        status = [PHPhotoLibrary authorizationStatusForAccessLevel:PHAccessLevelReadWrite];
    } else {
        status = [PHPhotoLibrary authorizationStatus];
    }
    switch (status) {
        case PHAuthorizationStatusAuthorized:
        case PHAuthorizationStatusLimited:
            return @"granted";
        case PHAuthorizationStatusNotDetermined:
            return @"not_determined";
        default:
            return @"denied";
    }
}

#pragma mark - Fetch Images

- (void)fetchImages:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSString *paramsStr = args[@"data"];
    NSInteger maxCount = 0;
    if (paramsStr) {
        NSData *data = [paramsStr dataUsingEncoding:NSUTF8StringEncoding];
        NSDictionary *params = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
        NSNumber *maxCountNum = params[@"maxCount"];
        if (maxCountNum) {
            NSInteger val = [maxCountNum integerValue];
            maxCount = (val > 0 && val < NSIntegerMax) ? val : 0;
        }
    }
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        PHFetchOptions *options = [[PHFetchOptions alloc] init];
        options.sortDescriptors = @[[NSSortDescriptor sortDescriptorWithKey:@"creationDate" ascending:NO]];
        if (maxCount > 0) options.fetchLimit = maxCount;
        
        PHFetchResult *result = [PHAsset fetchAssetsWithMediaType:PHAssetMediaTypeImage options:options];
        NSMutableArray *images = [NSMutableArray array];
        
        [result enumerateObjectsUsingBlock:^(PHAsset *asset, NSUInteger idx, BOOL *stop) {
            NSString *phUri = [NSString stringWithFormat:@"ph://%@", asset.localIdentifier];
            [images addObject:@{
                @"id": asset.localIdentifier,
                @"uri": phUri,
                @"thumbnailUri": phUri,
                @"width": @(asset.pixelWidth),
                @"height": @(asset.pixelHeight)
            }];
        }];
        
        dispatch_async(dispatch_get_main_queue(), ^{
            NSData *jsonData = [NSJSONSerialization dataWithJSONObject:@{@"data": images} options:0 error:nil];
            NSString *jsonStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
            if (callback) callback(jsonStr);
        });
    });
}

#pragma mark - Fetch Albums

- (void)fetchAlbums:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSMutableArray *albums = [NSMutableArray array];
        
        PHFetchResult *smartAlbums = [PHAssetCollection fetchAssetCollectionsWithType:PHAssetCollectionTypeSmartAlbum
                                                                              subtype:PHAssetCollectionSubtypeAny
                                                                              options:nil];
        [smartAlbums enumerateObjectsUsingBlock:^(PHAssetCollection *collection, NSUInteger idx, BOOL *stop) {
            PHFetchResult *assets = [PHAsset fetchAssetsInAssetCollection:collection options:nil];
            if (assets.count > 0) {
                [albums addObject:@{
                    @"id": collection.localIdentifier,
                    @"name": collection.localizedTitle ?: @"Unknown",
                    @"count": @(assets.count)
                }];
            }
        }];
        
        PHFetchResult *userAlbums = [PHAssetCollection fetchAssetCollectionsWithType:PHAssetCollectionTypeAlbum
                                                                             subtype:PHAssetCollectionSubtypeAny
                                                                             options:nil];
        [userAlbums enumerateObjectsUsingBlock:^(PHAssetCollection *collection, NSUInteger idx, BOOL *stop) {
            PHFetchResult *assets = [PHAsset fetchAssetsInAssetCollection:collection options:nil];
            if (assets.count > 0) {
                [albums addObject:@{
                    @"id": collection.localIdentifier,
                    @"name": collection.localizedTitle ?: @"Unknown",
                    @"count": @(assets.count)
                }];
            }
        }];
        
        dispatch_async(dispatch_get_main_queue(), ^{
            NSData *jsonData = [NSJSONSerialization dataWithJSONObject:@{@"data": albums} options:0 error:nil];
            NSString *jsonStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
            if (callback) callback(jsonStr);
        });
    });
}

#pragma mark - Fetch Images From Album

- (void)fetchImagesFromAlbum:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSString *paramsStr = args[@"data"];
    NSString *albumId = nil;
    NSInteger maxCount = NSIntegerMax;
    
    if (paramsStr) {
        NSData *data = [paramsStr dataUsingEncoding:NSUTF8StringEncoding];
        NSDictionary *params = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
        albumId = params[@"albumId"];
        NSNumber *maxCountNum = params[@"maxCount"];
        if (maxCountNum) maxCount = [maxCountNum integerValue];
    }
    
    if (!albumId) {
        if (callback) callback(@"[]");
        return;
    }
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        PHFetchResult *collections = [PHAssetCollection fetchAssetCollectionsWithLocalIdentifiers:@[albumId] options:nil];
        PHAssetCollection *collection = collections.firstObject;
        if (!collection) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (callback) callback(@"[]");
            });
            return;
        }
        
        PHFetchOptions *options = [[PHFetchOptions alloc] init];
        options.sortDescriptors = @[[NSSortDescriptor sortDescriptorWithKey:@"creationDate" ascending:NO]];
        if (maxCount > 0) options.fetchLimit = maxCount;
        
        PHFetchResult *result = [PHAsset fetchAssetsInAssetCollection:collection options:options];
        NSMutableArray *images = [NSMutableArray array];
        
        [result enumerateObjectsUsingBlock:^(PHAsset *asset, NSUInteger idx, BOOL *stop) {
            if (asset.mediaType == PHAssetMediaTypeImage) {
                NSString *phUri = [NSString stringWithFormat:@"ph://%@", asset.localIdentifier];
                [images addObject:@{
                    @"id": asset.localIdentifier,
                    @"uri": phUri,
                    @"thumbnailUri": phUri,
                    @"width": @(asset.pixelWidth),
                    @"height": @(asset.pixelHeight)
                }];
            }
        }];
        
        dispatch_async(dispatch_get_main_queue(), ^{
            NSData *jsonData = [NSJSONSerialization dataWithJSONObject:@{@"data": images} options:0 error:nil];
            NSString *jsonStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
            if (callback) callback(jsonStr);
        });
    });
}

@end
