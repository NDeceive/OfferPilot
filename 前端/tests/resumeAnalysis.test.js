import { test } from "node:test";
import assert from "node:assert/strict";
import {
  applyAnalysisSuggestion,
  recommendedTargets,
  groupAnalysisItems,
} from "../src/utils/resumeAnalysis.js";
const item = {
  kind: "SUGGESTION",
  field: "projects",
  quote: "参与接口测试。参与接口测试。",
  suggestion: "参与接口测试。",
  requiresFacts: false,
};
test("analysis changes one exact draft range and keeps other fields and original snapshot", () => {
  const original = {
    projects: "验收材料\n参与接口测试。参与接口测试。\n提交记录",
    skills: "测试技能",
  };
  const next = applyAnalysisSuggestion(original, item);
  assert.equal(next.projects, "验收材料\n参与接口测试。\n提交记录");
  assert.equal(next.skills, original.skills);
  assert.equal(
    original.projects,
    "验收材料\n参与接口测试。参与接口测试。\n提交记录"
  );
});
test("changed, ambiguous or unstructured original cannot be silently overwritten", () => {
  assert.throws(() => applyAnalysisSuggestion({ projects: "新内容" }, item));
  assert.throws(() =>
    applyAnalysisSuggestion({ projects: item.quote + "\n" + item.quote }, item)
  );
  assert.throws(() =>
    applyAnalysisSuggestion({ raw: item.quote }, { ...item, field: "raw" })
  );
  assert.throws(() =>
    applyAnalysisSuggestion(
      { projects: item.quote },
      { ...item, requiresFacts: true }
    )
  );
});
test("training suggestions use real modules, retain current choices when possible and limit five", () => {
  const modules = ["a", "b", "c", "d", "e", "f"].map((code) => ({ code }));
  assert.deepEqual(
    recommendedTargets(["f", "unknown", "f"], modules, [
      "a",
      "b",
      "c",
      "d",
      "e",
    ]),
    ["f", "a", "b", "c", "d"]
  );
  assert.deepEqual(recommendedTargets([], modules, ["a", "b"]), ["a", "b"]);
});

test("three groups keep every item, distinguish general missing facts from job gaps, and retain edit identity", () => {
  const general = {kind: "IMPROVEMENT", title: "补充联系方式", reason: "未提供", requiresFacts: true, priority: "HIGH"};
  const gap = {kind: "JOB_GAP", title: "补充岗位技能", jobEvidence: "SQL", priority: "MEDIUM"};
  const legacy = {kind: "IMPROVEMENT", title: "核对岗位关联", priority: "LOW"};
  const strength = {kind: "STRENGTH", title: "项目相关"};
  const other = [1,2,3,4].map((n) => ({kind:"ISSUE", title: `核实${n}`, priority:"MEDIUM"}));
  const items = [general,gap,legacy,strength,...other];
  const groups = groupAnalysisItems(items);
  assert.equal(groups.improvements.length,5);
  assert.deepEqual(groups.jobGaps,[gap,legacy]);
  assert.deepEqual(groups.strengths,[strength]);
  assert.equal(groups.improvements[0],general);
  assert.equal(items[0],general);
});
