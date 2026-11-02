export const INTERVIEW_CLOSING_SECONDS = 30
export const INTERVIEW_CLOSING_TEXT = '好，那我们今天就先聊到这里。谢谢你的分享，辛苦了。'

export async function presentInterviewClosing(show, play, wait = ms => new Promise(resolve => setTimeout(resolve, ms))) {
  show(INTERVIEW_CLOSING_TEXT)
  // Keep the text readable even if the avatar is offline or playback fails immediately.
  await Promise.all([wait(5000), Promise.resolve().then(() => play(INTERVIEW_CLOSING_TEXT)).catch(() => {})])
}

export async function pollInterviewReport(getStatus, {
  budgetMs = 10000, cancelled = () => false, now = () => performance.now(),
  wait = ms => new Promise(resolve => setTimeout(resolve, ms)),
} = {}) {
  const deadline = now() + budgetMs
  while (!cancelled() && now() < deadline) {
    try {
      const status = await getStatus({ timeout: Math.max(1, Math.min(3000, deadline - now())) })
      if (cancelled()) return null
      if (status?.state === 'FAILED' || (status?.ready && status.reportId)) return status
    } catch (error) {
      if (error.code === 401 || error.code === 403 || [401, 403].includes(error.response?.status)) throw error
    }
    const remaining = deadline - now()
    if (remaining > 0 && !cancelled()) await wait(Math.min(1000, remaining))
  }
  return null
}
export function interviewReportRoute(sessionId, reportId, assignmentId) {
  return {
    path: reportId ? `/history/${reportId}` : `/history/session/${sessionId}`,
    query: assignmentId ? { assignmentId } : {},
  }
}
