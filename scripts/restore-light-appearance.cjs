// Reapply pre-sync light CSS without reverting scripts, templates, new features or dark mode.
const fs = require('node:fs')
const path = require('node:path')
const cp = require('node:child_process')
const crypto = require('node:crypto')
const root = path.resolve(__dirname, '..')
const postcss = require(path.join(root, '前端/node_modules/postcss'))
const baseline = 'bffc928', incoming = 'a911394'
const out = path.join(root, 'artifacts/light-restoration')
const apply = process.argv.includes('--apply')
const git = args => cp.execFileSync('git', args, { cwd: root, encoding: 'utf8', maxBuffer: 12 * 1024 * 1024 })
const hash = value => crypto.createHash('sha256').update(value).digest('hex')
const marker = '/* Restore the pre-sync light appearance; retain current dark-theme rules. */'
const styles = text => [...text.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/g)].map(match => ({ text: match[1], end: match.index + match[0].lastIndexOf('</style>') }))
function rules(text) {
  const indexed = new Map(), occurrences = new Map()
  postcss.parse(text).walkRules(rule => {
    const ancestors = []
    for (let parent = rule.parent; parent?.type !== 'root'; parent = parent.parent) {
      if (parent.type === 'atrule') ancestors.unshift({ name: parent.name, params: parent.params })
    }
    if (ancestors.some(parent => /keyframes$/i.test(parent.name))) return
    const identity = JSON.stringify([ancestors, rule.selector])
    const index = occurrences.get(identity) || 0
    occurrences.set(identity, index + 1)
    indexed.set(identity + index, { rule, ancestors })
  })
  return indexed
}
function lightSelector(selector) {
  return postcss.list.comma(selector).map(item => {
    if (/^(:root|html)(?=[\s.#:[>+~]|$)/.test(item)) return item.replace(/^(:root|html)/, '$1:not([data-theme="dark"])')
    return ':root:not([data-theme="dark"]) ' + item
  }).join(', ')
}
function restore(oldCss, incomingCss, currentCss) {
  const original = rules(oldCss), before = rules(incomingCss), current = rules(currentCss)
  const output = postcss.root()
  for (const [key, previous] of original) {
    const changed = before.get(key), actual = current.get(key)
    if (!changed || !actual) continue
    const restored = postcss.rule({ selector: lightSelector(previous.rule.selector) })
    const oldDecls = previous.rule.nodes.filter(node => node.type === 'decl')
    const newDecls = changed.rule.nodes.filter(node => node.type === 'decl')
    const currentDecls = actual.rule.nodes.filter(node => node.type === 'decl')
    for (let i = 0; i < oldDecls.length; i++) {
      const old = oldDecls[i], next = newDecls[i], live = currentDecls[i]
      if (!next || !live || old.prop !== next.prop || next.prop !== live.prop) continue
      if (old.value === next.value && old.important === next.important) continue
      if (next.value !== live.value || next.important !== live.important) continue
      restored.append(postcss.decl({ prop: old.prop, value: old.value, important: old.important }))
    }
    if (!restored.nodes.length) continue
    let wrapped = restored
    for (const ancestor of previous.ancestors.toReversed()) wrapped = postcss.atRule(ancestor).append(wrapped)
    output.append(wrapped)
  }
  return output.nodes.length ? '\n\n' + marker + '\n' + output.toString() + '\n' : ''
}
fs.mkdirSync(out, { recursive: true })
const files = git(['diff', '--name-only', '--diff-filter=M', '-z', baseline, incoming, '--', '前端/src']).split('\0').filter(file => /\.(vue|css)$/.test(file))
const changes = []
for (const file of files) {
  const absolute = path.join(root, file)
  if (!fs.existsSync(absolute)) continue
  const current = fs.readFileSync(absolute, 'utf8')
  if (current.includes(marker)) continue
  const original = git(['show', baseline + ':' + file]), head = git(['show', incoming + ':' + file])
  let updated = current, count = 0
  if (file.endsWith('.css')) {
    const addition = restore(original, head, current)
    if (addition) { updated += addition; count = addition.split('{').length - 1 }
  } else {
    const oldBlocks = styles(original), headBlocks = styles(head), currentBlocks = styles(current)
    if (oldBlocks.length !== headBlocks.length || headBlocks.length !== currentBlocks.length) throw new Error('Style blocks changed: ' + file)
    for (let i = currentBlocks.length - 1; i >= 0; i--) {
      const addition = restore(oldBlocks[i].text, headBlocks[i].text, currentBlocks[i].text)
      if (!addition) continue
      const end = currentBlocks[i].end
      updated = updated.slice(0, end) + addition + updated.slice(end)
      count += addition.split('{').length - 1
    }
    if (current.replace(/<style\b[^>]*>[\s\S]*?<\/style>/g, '') !== updated.replace(/<style\b[^>]*>[\s\S]*?<\/style>/g, '')) throw new Error('Non-style content changed: ' + file)
  }
  if (current === updated) continue
  changes.push({ file, restoredRules: count, before: hash(current), after: hash(updated) })
  if (apply) {
    const backup = path.join(out, 'before', file)
    fs.mkdirSync(path.dirname(backup), { recursive: true })
    if (!fs.existsSync(backup)) fs.writeFileSync(backup, current)
    fs.writeFileSync(absolute, updated)
  }
}
fs.writeFileSync(path.join(out, apply ? 'applied.json' : 'plan.json'), JSON.stringify({ baseline, incoming, changes }, null, 2))
console.log(JSON.stringify({ applied: apply, files: changes.length, restoredRules: changes.reduce((sum, item) => sum + item.restoredRules, 0), changes: changes.map(item => ({ file: item.file, rules: item.restoredRules })) }, null, 2))
