export function minutesLabel(meta, busy, error) {
  if (busy) return '设置同步中'
  if (error || !meta) return '状态待确认'
  if (meta.lifecycle === 'DELETED') return '纪要已删除'
  if (meta.lifecycle === 'UNAVAILABLE') return '本场未开启纪要'
  if (meta.lifecycle === 'FINALIZED') {
    if (['QUEUED', 'RUNNING'].includes(meta.summaryState)) return '正在整理总结'
    if (meta.summaryState === 'FAILED') return '总结生成失败'
    return meta.hasSummary ? '总结已生成' : '无可总结内容'
  }
  if (meta.paused && meta.enabled) return '纪要随面试暂停'
  if (meta.enabled) return meta.waitingForQuestion ? '等待下一问记录' : '正在记录'
  return meta.recordedEntryCount ? '纪要已关闭' : '开启纪要'
}
export function clampOrb(xRatio, yRatio, viewport, size = 56) {
  const margin = 16, clamp = value => Math.max(0, Math.min(1, Number(value) || 0))
  return {
    x: viewport.left + margin + clamp(xRatio) * Math.max(0, viewport.width - size - margin * 2),
    y: viewport.top + margin + clamp(yRatio) * Math.max(0, viewport.height - size - margin * 2),
  }
}

export function summaryParagraph(summary) {
  if (!summary) return ''
  if (typeof summary.text === 'string') return summary.text.replace(/\s+/g, ' ').trim()
  for (const key of ['overview', 'topics', 'qaHighlights', 'explicitActions', 'unresolvedItems']) {
    const text = (summary[key] || []).map(item => item.text || '').filter(Boolean).join(' ').replace(/\s+/g, ' ').trim()
    if (text) return text
  }
  return ''
}
