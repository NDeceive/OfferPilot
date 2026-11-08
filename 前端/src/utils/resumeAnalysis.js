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
  skillTags: "技能标签",
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

// Explicit question types take precedence over the resume field for old reports.
export function groupInterviewQuestions(items) {
  const groups = [
    { key: "skills", title: "技能题", items: [] },
    { key: "projects", title: "项目题", items: [] },
  ];
  for (const item of items.filter((item) => item.kind === "INTERVIEW")) {
    const type = (item.title || "").split(/[·：:]/)[0];
    const project = /项目|经历|行为|情境/.test(type) ? true
      : /技能|技术|原理|场景设计|故障/.test(type) ? false
      : ["projects", "experience"].includes(item.field) || /project/.test(item.moduleCode || "");
    groups[project ? 1 : 0].items.push(item);
  }
  groups[0].items = groups[0].items.slice(0, 3);
  return groups;
}

export function interviewQuestionText(groups, jobName = "") {
  return ["面试押题", jobName, ...groups.flatMap((group) => [
    "", group.title,
    ...(group.items.length ? group.items.map((item, i) =>
      `${i + 1}、${item.question || item.title}\n回答思路：${item.action || "暂无回答思路，请重新分析。"}`
    ) : ["暂无相关题目"]),
  ])].filter((line) => line !== undefined).join("\n");
}
