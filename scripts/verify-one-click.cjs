// Exercise the real unified launcher, including ownership on repeated startup.
const cp = require('node:child_process')
const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const root = path.resolve(__dirname, '..')
const out = path.join(root, 'artifacts/startup-verification')
fs.mkdirSync(out, { recursive: true })
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const entries = [
  ['backend', 'http://127.0.0.1:8080/api/dashboard/overview', 401],
  ['digitalHuman', 'http://127.0.0.1:8010/offerpilot-embed.html', 200],
  ['frontend', 'http://localhost:5173/', 200],
  ['voiceAnalysis', 'http://127.0.0.1:8020/health', 200],
]
async function health() {
  return Object.fromEntries(await Promise.all(entries.map(async ([name, url, expected]) => {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(2000) })
      if (response.status !== expected) return [name, false]
      if (name === 'voiceAnalysis') return [name, (await response.json()).ok === true]
      return [name, true]
    } catch { return [name, false] }
  })))
}
function pids() {
  const command = 'Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 5173,8010,8020,8080 } | Select-Object LocalPort,OwningProcess | ConvertTo-Json -Compress'
  const output = cp.execFileSync('powershell.exe', ['-NoProfile', '-Command', command], { encoding: 'utf8', windowsHide: true }).trim()
  return Object.fromEntries([].concat(output ? JSON.parse(output) : []).map(row => [row.LocalPort, row.OwningProcess]))
}
;(async () => {
  const result = { before: await health() }
  assert.ok(Object.values(result.before).every(ready => !ready), 'run this cold-start test when application services are stopped')
  const owner = cp.spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(root, 'scripts/dev-start.ps1'), '-NoBrowser'], { cwd: root, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] })
  let log = '', ownerExited = false
  owner.stdout.on('data', chunk => { log += chunk.toString() })
  owner.stderr.on('data', chunk => { log += chunk.toString() })
  const completion = new Promise(resolve => owner.once('exit', code => { ownerExited = true; resolve(code) }))
  try {
    let ready = false
    for (let i = 0; i < 240; i++) {
      const snapshot = await health()
      if (Object.values(snapshot).every(Boolean)) { result.started = snapshot; ready = true; break }
      if (ownerExited) throw new Error('launcher exited before services became ready: ' + log.slice(-2000))
      await sleep(1000)
    }
    assert.ok(ready, 'all four services must start')
    // Let the first launcher enter its interactive lifetime, without opening a browser.
    await sleep(1000)
    result.initialPids = pids()
    console.log('Cold start: four services healthy', JSON.stringify(result.initialPids))
    const repeated = cp.spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(root, 'scripts/dev-start.ps1'), '-SmokeTest'], { cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
    let repeatLog = ''
    repeated.stdout.on('data', chunk => { repeatLog += chunk.toString() })
    repeated.stderr.on('data', chunk => { repeatLog += chunk.toString() })
    const repeatCode = await new Promise(resolve => repeated.once('exit', resolve))
    fs.writeFileSync(path.join(out, 'reuse.log'), repeatLog)
    assert.equal(repeatCode, 0, repeatLog)
    result.reused = await health()
    result.reusedPids = pids()
    assert.ok(Object.values(result.reused).every(Boolean))
    assert.deepEqual(result.reusedPids, result.initialPids, 'repeat startup must retain every existing service process')
    console.log('Repeated startup: same process IDs retained; no service stopped')
    owner.stdin.end('\n')
    const exitCode = await Promise.race([completion, sleep(20000).then(() => 'timeout')])
    assert.equal(exitCode, 0, 'owner launcher must exit after Enter')
    await sleep(1000)
    result.after = await health()
    assert.ok(Object.values(result.after).every(ready => !ready), 'owned application processes must stop on exit')
    result.passed = true
    fs.writeFileSync(path.join(out, 'result.json'), JSON.stringify(result, null, 2))
    console.log('Cleanup: all four owned application services stopped; verification passed')
  } finally {
    fs.writeFileSync(path.join(out, 'startup.log'), log)
    if (!ownerExited) {
      owner.stdin.end('\n')
      await Promise.race([completion, sleep(10000)])
      if (!ownerExited) cp.spawnSync('taskkill.exe', ['/PID', String(owner.pid), '/T', '/F'], { windowsHide: true })
    }
  }
})().catch(error => { console.error(error); process.exitCode = 1 })
