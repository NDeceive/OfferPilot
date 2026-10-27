export const analysisFields = {
  name: "基本信息",
  phone: "基本信息",
  email: "基本信息",
  targetJob: "基本信息",
  summary: "基本信息",
  education: "教育背景",
  projects: "项目经历",
  experience: "实习经历",
  skills: "专业技能",
  awards: "荣誉奖项",
  raw: "文件原文",
};

// A suggestion addresses one exact, unambiguous text range. Never replace a whole resume.
export function applyAnalysisSuggestion(content, item) {
  if (
    !item ||
    item.kind !== "SUGGESTION" ||
    item.requiresFacts ||
    !item.quote ||
    !item.suggestion ||
    item.field === "raw"
  )
    throw new Error("此建议需要手动补充真实内容");
  const value = content[item.field];
  if (typeof value !== "string") throw new Error("找不到对应草稿字段");
  const at = value.indexOf(item.quote);
  if (at < 0 || value.indexOf(item.quote, at + 1) >= 0)
    throw new Error("原文已变化或有多个位置，请重新核查后手动编辑");
  return {
    ...content,
    [item.field]:
      value.slice(0, at) +
      item.suggestion +
      value.slice(at + item.quote.length),
  };
}

export function recommendedTargets(suggested, modules, current) {
  const valid = new Set(modules.map((m) => m.code));
  return [
    ...new Set([
      ...suggested.filter((code) => valid.has(code)),
      ...current.filter((code) => valid.has(code)),
    ]),
  ].slice(0, 5);
}

// Older saved reports have no JOB_GAP kind. Only migrate clearly job-related gaps.
export function groupAnalysisItems(items) {
  const jobGap = (item) => item.kind === "JOB_GAP" ||
    (["ISSUE", "IMPROVEMENT"].includes(item.kind) &&
      (item.title === "核对岗位关联" ||
        (item.jobEvidence && /未体现|缺少|缺乏|尚未|补充|不足/.test(item.title + item.reason))));
  const rank = (item) => ({ HIGH: 0, MEDIUM: 1, LOW: 2 }[item.priority] ?? 3);
  return {
    strengths: items.filter((item) => item.kind === "STRENGTH"),
    improvements: items.filter((item) => ["ISSUE", "IMPROVEMENT", "SUGGESTION"].includes(item.kind) && !jobGap(item)).sort((a,b) => rank(a)-rank(b)),
    jobGaps: items.filter(jobGap).sort((a,b) => rank(a)-rank(b)),
  };
}
