Pod::Spec.new do |spec|
  spec.name                     = 'KuiklyAlbumIOS'
  spec.version                  = '1.0.0'
  spec.homepage                 = 'https://github.com/Kuikly-contrib/KuiklyAlbum'
  spec.source                   = { :git => 'https://github.com/Kuikly-contrib/KuiklyAlbum.git', :branch => 'main' }
  spec.authors                  = 'Kuikly Team'
  spec.license                  = { :type => 'MIT', :file => 'LICENSE' }
  spec.summary                  = 'KuiklyAlbum iOS Native Module'

  spec.ios.deployment_target    = '12.0'

  spec.source_files             = 'KuiklyAlbumIOS/**/*.{h,m,mm}'
  spec.public_header_files      = 'KuiklyAlbumIOS/**/*.h'

  spec.dependency 'OpenKuiklyIOSRender', '~> 2.7.0'

  spec.frameworks               = 'UIKit', 'Photos'
  spec.requires_arc             = true
end
