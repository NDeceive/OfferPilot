/**
 * public/ 下静态资源的地址（音频处理器、数字人视频、岗位图标等）。
 *
 * 为什么不能直接写 '/assets/...'：
 *   Web / nginx 构建  BASE_URL === '/'  → '/assets/...'，与改造前逐字节相同；
 *   HBuilderX 5+App  BASE_URL === './'  → './assets/...'。
 *   APK 里页面是以 file:///.../index.html 加载的，'/assets' 会被解析成
 *   file:///assets（设备根目录），必然 404——数字人视频和岗位图标会全空。
 */
export function assetUrl(path) {
  return `${import.meta.env.BASE_URL}${String(path || '').replace(/^\/+/, '')}`
}
