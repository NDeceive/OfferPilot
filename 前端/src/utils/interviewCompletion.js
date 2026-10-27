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
