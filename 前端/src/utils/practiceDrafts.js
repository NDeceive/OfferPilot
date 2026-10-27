// Local drafts contain only practice content, never authentication credentials.
export function practiceStorageKey(userId, sessionId) {
  if (!userId || !sessionId) throw new Error('无法确认用户，草稿尚未保存')
  return `offerpilot:practice:${userId}:${sessionId}`
}

export function readPracticeDraft(storage, userId, sessionId) {
  const value = JSON.parse(storage.getItem(practiceStorageKey(userId, sessionId)) || '{}')
  const object = item => item && typeof item === 'object' && !Array.isArray(item)
  if (!object(value) || (value.questions != null && !object(value.questions))) throw new Error('草稿格式损坏')
  for (const draft of Object.values(value.questions || {})) {
    if (!object(draft) || ['answer', 'code'].some(key => draft[key] != null && typeof draft[key] !== 'string')) throw new Error('草稿内容损坏')
  }
  return value
}

export function writePracticeDraft(storage, userId, sessionId, value) {
  storage.setItem(practiceStorageKey(userId, sessionId), JSON.stringify(value))
}

export function submissionState(question, draft) {
  if (question.latestSubmission || question.status === 'ANSWERED' || draft?.submitted) return '已提交'
  if (draft?.answer?.trim() || (draft?.code && draft.code !== (question.starter || ''))) return '草稿'
  return '未作答'
}
