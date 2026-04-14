#import "KuiklyRenderComponentExpandHandler.h"
#import <SDWebImage/UIImageView+WebCache.h>
#import <Photos/Photos.h>

@implementation KuiklyRenderComponentExpandHandler

+ (void)load {
    [KuiklyRenderBridge registerComponentExpandHandler:[self new]];
}

- (BOOL)hr_setImageWithUrl:(NSString *)url forImageView:(UIImageView *)imageView {
    if (!url || url.length == 0) {
        imageView.image = nil;
        return YES;
    }
    if ([url hasPrefix:@"ph://"]) {
        NSString *localIdentifier = [url substringFromIndex:5];
        PHFetchResult *result = [PHAsset fetchAssetsWithLocalIdentifiers:@[localIdentifier] options:nil];
        PHAsset *asset = result.firstObject;
        if (asset) {
            PHImageRequestOptions *options = [[PHImageRequestOptions alloc] init];
            options.deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat;
            options.networkAccessAllowed = YES;
            CGFloat scale = [UIScreen mainScreen].scale;
            CGSize targetSize = CGSizeMake(imageView.bounds.size.width * scale, imageView.bounds.size.height * scale);
            if (targetSize.width <= 0 || targetSize.height <= 0) {
                targetSize = CGSizeMake(300 * scale, 300 * scale);
            }
            [[PHImageManager defaultManager] requestImageForAsset:asset
                                                       targetSize:targetSize
                                                      contentMode:PHImageContentModeAspectFill
                                                          options:options
                                                    resultHandler:^(UIImage *result, NSDictionary *info) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    imageView.image = result;
                });
            }];
        }
        return YES;
    }
    [imageView sd_setImageWithURL:[NSURL URLWithString:url]];
    return YES;
}

- (UIColor *)hr_colorWithValue:(NSString *)value {
    return nil;
}

@end