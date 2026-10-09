/**
 * 5+ App（离线打包，页面跑在 file:// WebView）里 Android 的运行时权限没人替你申请：
 * WebView 内核对 getUserMedia 是无条件放行的（DCloud 运行时 getResources→grant），
 * 但系统层的「录音/摄像头」权限必须显式申请，否则录音/相机设备永远打不开。
 * 桌面浏览器里不存在 plus 全局，整段直接短路（返回 'skipped'），网页版行为不变。
 *
 * @param {string[]} permissions android.permission.* 列表
 * @returns {Promise<'granted'|'denied'|'skipped'>} 不抛错；拒绝也放行后续流程，让原有报错分支出文案
 */
export function ensureAppPermissions(permissions) {
  const plus = window.plus
  if (!plus?.android?.requestPermissions) return Promise.resolve('skipped')
  return new Promise((resolve) => {
    try {
      plus.android.requestPermissions(
        permissions,
        (result) => {
          const granted = result?.granted || []
          resolve(permissions.every((p) => granted.includes(p)) ? 'granted' : 'denied')
        },
        () => resolve('denied'),
      )
    } catch {
      resolve('skipped')
    }
  })
}
