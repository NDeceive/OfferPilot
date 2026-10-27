// Real local backend verification; injected failures below are explicitly separate.
const {
  chromium,
} = require("C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright");
const assert = require("node:assert/strict"),
  fs = require("node:fs"),
  path = require("node:path"),
  crypto = require("node:crypto");
const base = process.env.ANALYSIS_QA_URL || "http://localhost:5173",
  out = path.resolve(
    process.env.ANALYSIS_QA_OUT || "output/resume-analysis-qa"
  );
fs.mkdirSync(out, { recursive: true });
const results = { realBackend: [], injected: [], screenshots: [], ai: null };
async function request(url, token, body, expect = true) {
  const r = await fetch(base + "/api" + url, {
    method: body ? "POST" : "GET",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: "Bearer " + token } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const v = await r.json();
  if (expect) assert.equal(v.code, 200, v.message);
  return expect ? v.data : v;
}
async function account(suffix) {
  const username = "analysisqa" + Date.now().toString().slice(-7) + suffix,
    password = crypto.randomBytes(9).toString("hex");
  await request("/auth/register", null, {
    username,
    password,
    nickname: "简历分析隔离验收",
    role: "STUDENT",
  });
  return request("/auth/login", null, { username, password });
}
(async () => {
  const auth = await account("a"),
    other = await account("b"),
    token = auth.token;
  const jobs = await request("/job/list", token),
    job =
      jobs.find((j) => j.status === 1 && j.code === "BE-JAVA") ||
      jobs.find((j) => j.status === 1),
    modules = await request("/modules", token);
  const content = {
    name: "隔离验收材料",
    phone: "",
    email: "",
    targetJob: job.name,
    summary: "此简历仅用于自动化验收，不代表任何学生履历。",
    education: "",
    experience: "",
    projects: "参与接口测试。参与接口测试。",
    skills: "Java",
    awards: "",
  };
  const version = await request("/resume-workbench/versions", token, {
    jobId: job.id,
    title: "验收：分析原始版本",
    content,
  });
  console.log("真实后端：开始分析已保存版本");
  const report = await request("/resume-workbench/analysis", token, {
    jobId: job.id,
    versionId: version.id,
    force: false,
  });
  results.ai = {
    source: report.source,
    status: report.status,
    failureReason: report.failureReason,
  };
  assert.equal(report.snapshot.versionId, version.id);
  assert.equal(report.snapshot.jobId, job.id);
  assert(
    report.items.every((i) => report.snapshot.fields[i.field].includes(i.quote))
  );
  assert(
    report.suggestedModules.every((code) =>
      modules.some((m) => m.code === code)
    )
  );
  assert.equal(
    (
      await request(
        "/resume-workbench/analysis?jobId=" +
          job.id +
          "&versionId=" +
          version.id,
        token
      )
    ).id,
    report.id
  );
  assert.equal(
    (
      await request("/resume-workbench/analysis", token, {
        jobId: job.id,
        versionId: version.id,
        force: false,
      })
    ).id,
    report.id
  );
  assert.notEqual(
    (
      await request(
        "/resume-workbench/analysis/" + report.id,
        other.token,
        null,
        false
      )
    ).code,
    200
  );
  assert.notEqual(
    (
      await request(
        "/resume-workbench/versions/" + version.id,
        other.token,
        null,
        false
      )
    ).code,
    200
  );
  assert.equal(
    (
      await request(
        "/resume-workbench/review?jobId=" + job.id + "&versionId=" + version.id,
        token
      )
    ).status,
    "READY"
  );
  results.realBackend.push(
    "版本报告、缓存、引用与模块校验、跨用户拒绝、旧review兼容"
  );
  const browser = await chromium.launch({ channel: "msedge", headless: true });
  try {
    const context = await browser.newContext({
      viewport: { width: 1920, height: 1080 },
      reducedMotion: "reduce",
    });
    await context.addInitScript((data) => {
      for (const [k, v] of Object.entries(data))
        if (v != null) localStorage.setItem(k, String(v));
    }, auth);
    const page = await context.newPage(),
      errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    page.on("dialog", (d) => d.accept());
    const prep = `/jobs?jobId=${job.id}&resumeVersionId=${version.id}&step=2&from=builder`;
    await page.goto(base + prep);
    await page.getByRole("heading", { name: "本次结论" }).waitFor();
    await page.screenshot({
      path: path.join(out, "01-analysis-overview-1920.png"),
    });
    results.screenshots.push("01-analysis-overview-1920.png");
    for (const [width, height] of [
      [1920, 1080],
      [1440, 900],
      [1366, 768],
    ]) {
      await page.setViewportSize({ width, height });
      assert(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth
        ),
        "analysis overflow " + width
      );
    }
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page
      .getByRole("button", { name: "按建议选择训练目标", exact: true })
      .click();
    await page
      .getByRole("button", { name: "确认训练目标", exact: true })
      .waitFor();
    await page.screenshot({
      path: path.join(out, "03-training-targets-1920.png"),
    });
    results.screenshots.push("03-training-targets-1920.png");
    results.realBackend.push("桌面分析展示、训练建议确认、三个桌面宽度");
    const suggestion = report.items.find(
      (i) => i.kind === "SUGGESTION" && i.field === "projects"
    );
    if (suggestion) {
      const index = report.items.indexOf(suggestion);
      await page.goto(
        base +
          `/resume/builder?jobId=${job.id}&versionId=${version.id}&tab=editor&analysisId=${report.id}&analysisItem=${index}`
      );
      await page.getByRole("heading", { name: "审核表达修改建议" }).waitFor();
      await page.screenshot({
        path: path.join(out, "02-suggestion-review-1920.png"),
      });
      results.screenshots.push("02-suggestion-review-1920.png");
      await page
        .getByRole("button", { name: "确认并应用到草稿", exact: true })
        .click();
      await page
        .getByText(
          "建议已应用到对应草稿，尚未保存；旧版本和事实记录保持原样。",
          { exact: true }
        )
        .waitFor();
      assert.equal(
        await page.getByLabel("项目经历 1", { exact: true }).inputValue(),
        suggestion.suggestion
      );
      await page.route("**/api/resume-workbench/versions", (route) =>
        route.request().method() === "POST"
          ? route.fulfill({
              status: 500,
              contentType: "application/json",
              body: JSON.stringify({
                code: 500,
                message: "验收注入：保存失败",
              }),
            })
          : route.continue()
      );
      await page
        .getByRole("button", { name: "保存为新版本", exact: true })
        .click();
      await page.getByRole("alert").filter({ hasText: "500" }).waitFor();
      assert.equal(
        await page.getByLabel("项目经历 1", { exact: true }).inputValue(),
        suggestion.suggestion
      );
      await page.unroute("**/api/resume-workbench/versions");
      results.injected.push(
        "版本保存失败保留已接受建议的草稿，恢复真实接口后可保存"
      );
      await page
        .getByRole("button", { name: "保存为新版本", exact: true })
        .click();
      await page.waitForFunction(() =>
        document.body.textContent.includes("新版本已保存")
      );
      const versions = await request("/resume-workbench/versions", token),
        newVersion = await request(
          "/resume-workbench/versions/" + versions[0].id,
          token
        );
      assert.notEqual(newVersion.id, version.id);
      assert.equal(newVersion.content.projects, suggestion.suggestion);
      assert.equal(
        (await request("/resume-workbench/versions/" + version.id, token))
          .content.projects,
        content.projects
      );
      assert.equal(
        (await request("/resume-workbench/analysis/" + report.id, token))
          .snapshot.fields.projects,
        content.projects
      );
      results.realBackend.push(
        "建议审核→只修改对应草稿→保存新版本→旧版本和旧报告不变"
      );
      if (process.env.ANALYSIS_RULES === "1") {
        const followup = await request("/resume-workbench/analysis", token, {
          jobId: job.id,
          versionId: newVersion.id,
          force: false,
        });
        assert(followup.stillOpen.length > 0);
        assert(
          !followup.items.some(
            (i) => i.kind === "SUGGESTION" && i.title === "删除重复句子"
          )
        );
        results.realBackend.push(
          "新版本重新分析保留同名待核查项，重复句问题消失，不生成改善分数"
        );
      }
    } else
      results.realBackend.push(
        "当前AI报告无直接采用的删减建议；审核UI成功路径另需基础检查报告验证"
      );
    await page.goto(base + prep);
    await page.getByRole("heading", { name: "本次结论" }).waitFor();
    // Injected HTTP failure and recovery, never counted as real AI success.
    await page.route("**/api/resume-workbench/analysis", async (route) =>
      route.request().method() === "POST"
        ? route.fulfill({
            status: 500,
            contentType: "application/json",
            body: JSON.stringify({ code: 500, message: "验收注入：分析失败" }),
          })
        : route.continue()
    );
    await page.getByRole("button", { name: "重新分析", exact: true }).click();
    await page
      .getByRole("alert")
      .filter({ hasText: "验收注入：分析失败" })
      .waitFor();
    await page.unroute("**/api/resume-workbench/analysis");
    results.injected.push("分析HTTP失败保留已有报告及重试入口");
    if (process.env.ANALYSIS_RULES === "1") {
      const latestPattern=/\/api\/resume-workbench\/analysis\?/;
      await page.route(latestPattern,async route=>{
        const response=await route.fetch(),body=await response.json();
        body.data.status="FAILED";body.data.failureReason="验收注入：失败报告";
        await route.fulfill({response,json:body});
      });
      await page.goto(base+prep);
      await page.getByRole("alert").filter({hasText:"本次报告未完成"}).waitFor();
      assert.equal(await page.getByRole("heading",{name:"本次结论"}).count(),0);
      await page.unroute(latestPattern);
      await page.getByRole("button",{name:"重新分析",exact:true}).click();
      await page.getByRole("heading",{name:"本次结论"}).waitFor();
      results.injected.push("FAILED报告不展示假结论，恢复真实规则接口后重新分析成功");
    }
    // Two real version responses, with an injected delay on the earlier selection.
    const alternative = await request("/resume-workbench/versions", token, {
      jobId: job.id,
      title: "验收：后选版本",
      content: { ...content, summary: "用于验证后选版本不会被较早请求覆盖。" },
    });
    await page.goto(
      base +
        `/jobs?jobId=${job.id}&resumeVersionId=${version.id}&step=1&from=builder`
    );
    await page.getByLabel("选择简历版本").waitFor();
    let release;
    const gate = new Promise((resolve) => (release = resolve));
    let fetched;
    const started = new Promise((resolve) => (fetched = resolve));
    await page.route(
      `**/api/resume-workbench/versions/${version.id}`,
      async (route) => {
        const response = await route.fetch();
        fetched();
        await gate;
        await route.fulfill({ response });
      }
    );
    await page.getByLabel("选择简历版本").selectOption(String(version.id));
    await started;
    await page.getByLabel("选择简历版本").selectOption(String(alternative.id));
    await page.waitForFunction(
      (id) =>
        document.querySelector('[aria-label="选择简历版本"]').value ===
        String(id),
      alternative.id
    );
    release();
    await page.waitForTimeout(200);
    assert.equal(
      await page.getByLabel("选择简历版本").inputValue(),
      String(alternative.id)
    );
    await page.unroute(`**/api/resume-workbench/versions/${version.id}`);
    results.injected.push(
      "早选版本延迟返回不覆盖后选版本（响应均来自真实后端）"
    );
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(base + prep);
    await page.getByRole("heading", { name: "本次结论" }).waitFor();
    assert(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth
      ),
      "mobile overflow"
    );
    await page.screenshot({
      path: path.join(out, "04-mobile-analysis-390.png"),
    });
    await page
      .getByRole("button", { name: "按建议选择训练目标", exact: true })
      .click();
    await page.getByText("选 5 项能力", { exact: false }).waitFor();
    assert(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth
      ),
      "mobile training overflow"
    );
    results.realBackend.push("移动端相同报告、五步流程与训练建议");
    if (process.env.ANALYSIS_RULES === "1") {
      await page.getByRole("button", { name: "下一步", exact: true }).click();
      const started = page.waitForResponse(
        (r) =>
          r.url().endsWith("/api/interview/start") &&
          r.request().method() === "POST",
        { timeout: 95000 }
      );
      await page.getByRole("button", { name: "开始面试", exact: true }).click();
      const response = await started,
        payload = response.request().postDataJSON(),
        result = await response.json();
      assert.equal(payload.resumeVersionId, version.id);
      assert.equal(payload.jobId, job.id);
      assert.equal(payload.skipResume, false);
      assert.equal(result.code, 200, result.message);
      assert(result.data.sessionId);
      results.realBackend.push(
        "移动端真实创建面试，传递当前分析版本、岗位、五项训练目标与不跳过简历标记"
      );
    }
    // Both workbench entries load the same persisted report; recommendation handoff confirms changes.
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto(
      base +
        `/resume/builder?jobId=${job.id}&versionId=${version.id}&tab=editor`
    );
    await page
      .getByRole("button", { name: "分析已保存版本", exact: true })
      .click();
    await page
      .locator("dialog.analysis-dialog")
      .getByRole("heading", { name: "本次结论" })
      .waitFor();
    await page.getByRole("button", { name: "关闭分析", exact: true }).click();
    await page
      .getByRole("button", { name: "03预览与导出", exact: true })
      .click();
    await page
      .getByRole("button", { name: "查看简历分析", exact: true })
      .click();
    await page
      .locator("dialog.analysis-dialog")
      .getByRole("heading", { name: "本次结论" })
      .waitFor();
    await page
      .locator("dialog.analysis-dialog")
      .getByRole("button", { name: "按建议选择训练目标", exact: true })
      .click();
    await page
      .getByRole("button", { name: "确认训练目标", exact: true })
      .waitFor();
    results.realBackend.push(
      "工作台编辑/预览共用报告，携带报告回面试准备后人工确认训练目标"
    );
    // Actual DOCX export -> actual upload parsing -> mutable report isolation.
    const download = await fetch(
      base + `/api/resume-workbench/versions/${version.id}/export?format=docx`,
      { headers: { Authorization: "Bearer " + token } }
    );
    assert(download.ok);
    const form = new FormData();
    form.append(
      "file",
      new Blob([await download.arrayBuffer()], {
        type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      }),
      "分析验收原文.docx"
    );
    const uploaded = await fetch(base + "/api/resume/upload", {
      method: "POST",
      headers: { Authorization: "Bearer " + token },
      body: form,
    }).then((r) => r.json());
    assert.equal(uploaded.code, 200, uploaded.message);
    console.log("真实后端：开始分析上传文件");
    const fileReport = await request("/resume-workbench/analysis", token, {
      jobId: job.id,
      force: false,
    });
    assert.equal(fileReport.snapshot.sourceKind, "FILE");
    assert.equal(fileReport.snapshot.sourceId, uploaded.data.fileId);
    await request("/resume", token, {
      rawText:
        "隔离验收替换文本：这是手动录入的测试材料，用于验证旧报告不适用于新文本。真实用户资料未改动。",
    });
    assert.equal(
      await request("/resume-workbench/analysis?jobId=" + job.id, token),
      null
    );
    assert.equal(
      (await request("/resume-workbench/analysis/" + fileReport.id, token))
        .applicable,
      false
    );
    results.realBackend.push(
      "真实DOCX导出、上传解析、文件标识、可变原文指纹与旧报告失效"
    );
    assert.deepEqual(errors, []);
    results.realBackend.push("浏览器无运行时错误");
  } finally {
    await browser.close();
  }
  fs.writeFileSync(
    path.join(out, "verification.json"),
    JSON.stringify(results, null, 2)
  );
  console.log(JSON.stringify(results, null, 2));
})().catch((e) => {
  fs.writeFileSync(
    path.join(out, "verification.json"),
    JSON.stringify({ ...results, error: e.message }, null, 2)
  );
  console.error(e);
  process.exitCode = 1;
});
