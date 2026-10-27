/** 只传最近的已完成消息；本轮用户输入单独放在 message，避免重复。 */
export function buildPrepHistory(messages, userText) {
  const history = messages
    .filter(m => !m.failed && (m.role === 'ai' || m.role === 'user') && m.text?.trim())
    .map(m => ({ role: m.role === 'ai' ? 'assistant' : 'user', content: m.text.slice(0, 1500) }))
  if (userText && history.at(-1)?.role === 'user' && history.at(-1).content === userText) history.pop()
  return history.slice(-20)
}

export const PREP_SCRIPT = {
  GREET: '你好，我是小面，可以陪你梳理经历、了解岗位。还没想好方向也没关系，你最近学过什么，或者做过什么项目？',
  PICK_JOB: '下面的岗位可以随时浏览，不用急着选。我们可以先聊聊你做过的项目，或比较你感兴趣的方向。',
  UPLOAD: '岗位已确认，你可以上传或在线填写简历。若还有顾虑，也可以继续聊岗位和准备方向。',
  DONE: '简历已就绪，可以查看简历分析、继续配置面试。你也可以先聊聊岗位适配或想重点准备的内容。',
}

/** AI 故障不能把一条咨询替换成催选岗的阶段话术，更不能执行半截意图。 */
export function prepUnavailableReply(payload) {
  const reasons = {
    AUTH_REQUIRED: '登录已过期，请重新登录后继续对话。',
    NETWORK_ERROR: '暂时连不上对话服务，请确认后端已启动后点击“重试回复”。你的消息会保留。',
    SERVER_ERROR: '对话服务暂时出错，请点击“重试回复”。你的消息会保留。',
    INVALID_REQUEST: '这次对话请求未能通过校验，请刷新页面后重试。你的岗位选择不会被自动更改。',
    AI_DISABLED: '当前服务尚未启用 AI 对话，请配置后端 AI 服务后重试。你仍可以浏览和选择岗位。',
    TIMEOUT: '这次回复等待超时，请点击“重试回复”。你的消息会保留。',
  }
  if (reasons[payload.reason]) return reasons[payload.reason]
  return payload.message
    ? '这次 AI 回复未能完成，请点击“重试回复”。你刚才的消息已保留，也可以先浏览岗位，不必现在做决定。'
    : PREP_SCRIPT[payload.stage] || PREP_SCRIPT.GREET
}

export function prepFailureReason(error) {
  if (error?.code === 401) return 'AUTH_REQUIRED'
  if (error?.code === 400) return 'INVALID_REQUEST'
  if (error?.code >= 500) return 'SERVER_ERROR'
  return error instanceof TypeError ? 'NETWORK_ERROR' : 'AI_ERROR'
}

/** 离线演示仅把明确的选择表达作为操作候选，最终仍需确认。 */
export function isExplicitPrepChoice(message) {
  // 离线演示没有语义模型，宁可继续聊，也不把关键词命中当成选择。
  if (/[?？]|怎么样|区别|适合|推荐|建议|不会|不懂|没学|犹豫|纠结|没想好|不确定|考虑|了解|先聊|还是|不想|不要|不选|不考虑|不做|不投|别选|别换/.test(message)) return false
  return /就选|选定|确定选|决定选|选择|改成|换成|改选|想选|我想(?:面|投|做)|我要(?:选|面|投)|选.+吧/.test(message)
}
