// Read the first XLSX worksheet through browser ZIP decompression; no spreadsheet runtime.
// Formulas and macros are rejected; importing only resolves existing student accounts.
const LIMIT = 2 * 1024 * 1024, XML_LIMIT = 8 * 1024 * 1024
const decode = bytes => new TextDecoder().decode(bytes)
function xml(text) { if (/<!DOCTYPE/i.test(text)) throw new Error('导入工作簿包含不支持的XML声明'); const doc = new DOMParser().parseFromString(text, 'application/xml'); if (doc.querySelector('parsererror')) throw new Error('工作簿结构无效'); return doc }
async function unzip(buffer) {
  const view = new DataView(buffer), bytes = new Uint8Array(buffer), files = new Map()
  let end = buffer.byteLength - 22
  while (end >= Math.max(0, buffer.byteLength - 65557) && view.getUint32(end, true) !== 0x06054b50) end--
  if (end < 0 || end < buffer.byteLength - 65557) throw new Error('不是有效的 XLSX 文件')
  let pos = view.getUint32(end + 16, true), used = 0
  const count = view.getUint16(end + 10, true)
  if (count > 200) throw new Error('工作簿文件过于复杂')
  for (let i = 0; i < count; i++) {
    if (pos + 46 > buffer.byteLength || view.getUint32(pos, true) !== 0x02014b50) throw new Error('工作簿目录损坏')
    const flags = view.getUint16(pos + 8, true), method = view.getUint16(pos + 10, true), size = view.getUint32(pos + 20, true), unpacked = view.getUint32(pos + 24, true), nameSize = view.getUint16(pos + 28, true), extra = view.getUint16(pos + 30, true), comment = view.getUint16(pos + 32, true), offset = view.getUint32(pos + 42, true)
    const name = decode(bytes.slice(pos + 46, pos + 46 + nameSize)); pos += 46 + nameSize + extra + comment
    if (/vbaProject|macros/i.test(name)) throw new Error('导入文件不得含宏');
    if (flags & 1) throw new Error('不支持加密工作簿')
    if (!name.endsWith('.xml') && !name.endsWith('.rels')) continue
    used += unpacked; if (used > XML_LIMIT || unpacked > XML_LIMIT) throw new Error('工作簿解压后过大')
    if (offset + 30 > buffer.byteLength || view.getUint32(offset, true) !== 0x04034b50) throw new Error('工作簿数据损坏')
    const start = offset + 30 + view.getUint16(offset + 26, true) + view.getUint16(offset + 28, true)
    if (start + size > buffer.byteLength) throw new Error('工作簿数据不完整')
    const raw = bytes.slice(start, start + size)
    let output
    if (method === 0) output = raw
    else if (method === 8) {
      const reader = new Blob([raw]).stream().pipeThrough(new DecompressionStream('deflate-raw')).getReader(), chunks = []; let length = 0
      try { while (true) { const part = await reader.read(); if (part.done) break; length += part.value.length; if (length > XML_LIMIT || length > unpacked) { await reader.cancel(); throw new Error('工作簿解压超出限制') }; chunks.push(part.value) } } finally { reader.releaseLock() }
      output = new Uint8Array(length); let at = 0; for (const chunk of chunks) { output.set(chunk, at); at += chunk.length }
    } else throw new Error('工作簿压缩格式不支持')
    if (output.length !== unpacked) throw new Error('工作簿大小校验失败')
    files.set(name, decode(output))
  }
  return files
}
function csvRows(text) {
  const rows = []; let row = [], cell = '', quoted = false
  for (let i = 0; i < text.length; i++) { const c = text[i]; if (c === '"') { if (quoted && text[i + 1] === '"') { cell += '"'; i++ } else quoted = !quoted } else if (!quoted && (c === ',' || c === '\n')) { row.push(cell); cell = ''; if (c === '\n') { rows.push(row); row = [] } } else if (c !== '\r' || quoted) cell += c }
  if (quoted) throw new Error('CSV 引号未闭合'); row.push(cell); rows.push(row); return rows
}
export async function readMemberFile(file) {
  if (!file || file.size > LIMIT) throw new Error('请选择不超过2MB的 XLSX 或 CSV 文件')
  let rows
  if (/\.csv$/i.test(file.name)) rows = csvRows((await file.text()).replace(/^\uFEFF/, ''))
  else if (/\.xlsx$/i.test(file.name)) {
    const files = await unzip(await file.arrayBuffer()), workbook = xml(files.get('xl/workbook.xml') || '')
    const first = workbook.getElementsByTagName('sheet')[0], relationId = first?.getAttribute('r:id')
    const relation = [...xml(files.get('xl/_rels/workbook.xml.rels') || '').getElementsByTagName('Relationship')].find(r => r.getAttribute('Id') === relationId)
    const target = relation?.getAttribute('Target') || '', sheetPath = target.startsWith('/') ? target.slice(1) : 'xl/' + target.replace(/^\.\//, '')
    if (!files.has(sheetPath)) throw new Error('未找到第一张工作表')
    const strings = files.has('xl/sharedStrings.xml') ? [...xml(files.get('xl/sharedStrings.xml')).getElementsByTagName('si')].map(si => [...si.getElementsByTagName('t')].map(t => t.textContent).join('')) : []
    const doc = xml(files.get(sheetPath)); if (doc.getElementsByTagName('f').length) throw new Error('导入表不得含公式，请先粘贴为值')
    rows = [...doc.getElementsByTagName('row')].map(row => { const values = []; for (const c of row.getElementsByTagName('c')) { const col = (c.getAttribute('r') || 'A').match(/^[A-Z]+/)[0]; let index = 0; for (const letter of col) index = index * 26 + letter.charCodeAt(0) - 64; if (index > 10) throw new Error('模板列数过多'); const v = c.getElementsByTagName('v')[0]?.textContent || ''; values[index - 1] = c.getAttribute('t') === 's' ? strings[Number(v)] || '' : c.getAttribute('t') === 'inlineStr' ? [...c.getElementsByTagName('t')].map(t => t.textContent).join('') : v } return values })
  } else throw new Error('仅支持 XLSX 和 CSV；旧版 XLS 请先另存为 XLSX')
  rows = rows.filter(r => r.some(v => String(v || '').trim()))
  if (rows.length > 1001) throw new Error('单次导入最多1000行')
  if (rows[0]?.[0]?.trim() !== '姓名' || rows[0]?.[1]?.trim() !== '学号') throw new Error('前两列必须为“姓名”“学号”，请使用导入模板')
  if (rows.length < 2) throw new Error('文件中没有学生数据')
  return rows.slice(1)
}
export function downloadCsv(filename, rows) {
  const protect = v => /^[=+\-@\t\r]/.test(String(v)) ? "'" + v : String(v)
  const content = '\uFEFF' + rows.map(r => r.map(v => '"' + protect(v ?? '').replaceAll('"', '""') + '"').join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' })), a = document.createElement('a'); a.href = url; a.download = filename; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
