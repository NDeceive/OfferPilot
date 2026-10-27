const {
  chromium,
} = require("C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright");
const assert = require("node:assert/strict"),
  fs = require("node:fs"),
  path = require("node:path");
const base = process.env.RESUME_QA_URL || "http://127.0.0.1:5173",
  out = path.resolve("output/resume-v2-qa");
fs.mkdirSync(out, { recursive: true });
(async () => {
  const username = "resumeqa" + Date.now().toString().slice(-10),
    password = require("node:crypto").randomBytes(9).toString("hex");
  async function api(url, body, token) {
    const r = await fetch(base + "/api" + url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: "Bearer " + token } : {}),
      },
      body: JSON.stringify(body),
    });
    const v = await r.json();
    assert.equal(v.code, 200, v.message);
    return v.data;
  }
  await api("/auth/register", {
    username,
    password,
    nickname: "工作台验收账号",
    role: "STUDENT",
  });
  const auth = await api("/auth/login", { username, password });
  const browser = await chromium.launch({ channel: "msedge", headless: true });
  try {
    const context = await browser.newContext({
      viewport: { width: 1920, height: 1080 },
      reducedMotion: "reduce",
      acceptDownloads: true,
    });
    await context.addInitScript((data) => {
      for (const [key, value] of Object.entries(data))
        if (value != null) localStorage.setItem(key, String(value));
    }, auth);
    const page = await context.newPage(),
      errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    page.on("dialog", (d) => d.accept());
    await page.goto(base + "/resume/builder");
    await page.getByText("还没有真实经历记录", { exact: true }).waitFor();
    await page.screenshot({ path: path.join(out, "01-facts-1920.png") });
    await page.getByRole("button", { name: "02制作简历" }).click();
    await page.getByText("当前简历设置", { exact: true }).waitFor();
    await page.screenshot({ path: path.join(out, "02-editor-1920.png") });
    await page.getByRole("button", { name: "03预览与导出" }).click();
    await page.getByText("还没有可预览的简历", { exact: true }).waitFor();
    assert(
      await page
        .getByRole("button", { name: "下载 PDF", exact: true })
        .isDisabled(),
    );
    await page.screenshot({ path: path.join(out, "03-preview-1920.png") });
    for (const [width, height] of [
      [1920, 1080],
      [1440, 900],
      [1366, 768],
      [390, 844],
      [360, 800],
    ]) {
      await page.setViewportSize({ width, height });
      for (const name of ["01资料与事实", "02制作简历", "03预览与导出"]) {
        await page.getByRole("button", { name, exact: true }).click();
        assert(
          await page.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
          name + " overflow " + width,
        );
      }
    }
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.getByRole("button", { name: "01资料与事实" }).click();
    await page
      .getByLabel("经历名称", { exact: true })
      .fill("验收记录：界面操作检查");
    await page
      .getByLabel("原始事实描述", { exact: true })
      .fill(
        "此为自动验收账号的测试记录，用于验证保存与人工确认流程，不代表学生履历。",
      );
    await page.getByRole("button", { name: "02制作简历" }).click();
    await page
      .getByRole("dialog")
      .filter({ hasText: "有未保存的修改" })
      .waitFor();
    await page.getByRole("button", { name: "取消", exact: true }).click();
    assert.equal(
      await page.getByLabel("经历名称", { exact: true }).inputValue(),
      "验收记录：界面操作检查",
    );
    await page.getByRole("button", { name: "保存事实", exact: true }).click();
    await page.getByText("验收记录：界面操作检查", { exact: true }).waitFor();
    await page
      .getByRole("button", { name: "查看 / 编辑", exact: true })
      .click();
    await page.getByLabel("核实状态").selectOption("CONFIRMED");
    await page.getByRole("button", { name: "保存事实", exact: true }).click();
    await page.locator(".status.confirmed").waitFor();
    await page.getByRole("button", { name: "02制作简历" }).click();
    await page
      .getByRole("button", { name: "追加到项目经历", exact: true })
      .click();
    assert(
      (await page.locator(".entry-editor textarea").inputValue()).includes(
        "自动验收账号",
      ),
    );
    await page
      .getByRole("button", { name: "＋ 添加项目经历", exact: true })
      .click();
    await page
      .locator(".entry-editor textarea")
      .nth(1)
      .fill("第二条验收记录，仅验证多条经历排序。");
    await page.getByRole("button", { name: "基本信息", exact: true }).click();
    await page.getByLabel("姓名", { exact: true }).fill("验收账号");
    await page.getByRole("button", { name: "项目经历", exact: true }).click();
    assert.equal(await page.locator(".entry-editor textarea").count(), 2);
    await page
      .getByRole("button", { name: "上移", exact: true })
      .nth(1)
      .click();
    assert(
      (
        await page.locator(".entry-editor textarea").first().inputValue()
      ).startsWith("第二条"),
    );
    await page
      .getByRole("button", { name: "保存为新版本", exact: true })
      .click();
    await page
      .getByText("新版本已保存，历史版本保持不变", { exact: true })
      .waitFor();
    assert.equal(await page.locator(".version-card").count(), 1);
    await page.getByLabel("版本名称", { exact: true }).fill("第二次验收快照");
    const versionsUrl = "**/api/resume-workbench/versions";
    await page.route(versionsUrl, (route) =>
      route.request().method() === "POST"
        ? route.fulfill({
            status: 500,
            json: { code: 500, message: "验收注入保存失败" },
          })
        : route.continue(),
    );
    await page
      .getByRole("button", { name: "保存为新版本", exact: true })
      .click();
    await page.getByRole("alert").filter({ hasText: "500" }).waitFor();
    assert.equal(
      await page.getByLabel("版本名称", { exact: true }).inputValue(),
      "第二次验收快照",
    );
    assert.equal(await page.locator(".version-card").count(), 1);
    await page.unroute(versionsUrl);
    await page.route(versionsUrl, async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      const response = await route.fetch();
      await new Promise((r) => setTimeout(r, 700));
      await route.fulfill({ response });
    });
    await page
      .getByRole("button", { name: "保存为新版本", exact: true })
      .click();
    await page
      .getByLabel("版本名称", { exact: true })
      .fill("保存期间的后续输入");
    await page
      .getByText(
        "提交时的内容已保存为新版本；保存期间的后续修改仍保留在草稿中",
        { exact: true },
      )
      .waitFor();
    assert.equal(
      await page.getByLabel("版本名称", { exact: true }).inputValue(),
      "保存期间的后续输入",
    );
    assert.equal(await page.locator(".version-card").count(), 2);
    await page.unroute(versionsUrl);
    await page.getByRole("button", { name: "03预览与导出" }).click();
    await page.getByRole("button", { name: "放弃修改", exact: true }).click();
    await page.getByRole("button", { name: "02制作简历" }).click();
    await page
      .getByRole("button", { name: "保存为新版本", exact: true })
      .click();
    await page.waitForFunction(
      () => document.querySelectorAll(".version-card").length === 3,
    );
    await page.getByRole("button", { name: "03预览与导出" }).click();
    await page.locator(".page-surface canvas").waitFor({ timeout: 45000 });
    await page
      .getByText("读取本页文字", { exact: true })
      .waitFor({ timeout: 45000 });
    await page.screenshot({
      path: path.join(out, "03-preview-saved-1920.png"),
    });
    for (const format of ["PDF", "DOCX"]) {
      const [download] = await Promise.all([
        page.waitForEvent("download"),
        page
          .getByRole("button", { name: "下载 " + format, exact: true })
          .click(),
      ]);
      await download.saveAs(path.join(out, download.suggestedFilename()));
      assert.equal(await download.failure(), null);
    }
    await page.getByLabel("历史版本").selectOption({ index: 1 });
    await page.getByText("两版内容相同", { exact: true }).waitFor();
    await page.getByRole("button", { name: "02制作简历" }).click();
    const versionReadUrl = "**/api/resume-workbench/versions/*";
    await page.route(versionReadUrl, async (route) => {
      if (route.request().method() !== "GET") return route.continue();
      const response = await route.fetch();
      await new Promise((resolve) => setTimeout(resolve, 700));
      await route.fulfill({ response });
    });
    await page.locator(".version-card").last().click();
    await page
      .getByLabel("版本名称", { exact: true })
      .fill("版本载入期间的输入");
    await page
      .getByText("版本已选中；载入期间新增的输入仍保留在草稿中，请核对后保存", {
        exact: true,
      })
      .waitFor();
    assert.equal(
      await page.getByLabel("版本名称", { exact: true }).inputValue(),
      "版本载入期间的输入",
    );
    await page.unroute(versionReadUrl);
    await page.getByRole("button", { name: "01资料与事实" }).click();
    await page.getByRole("button", { name: "放弃修改", exact: true }).click();
    await page.getByRole("button", { name: "02制作简历" }).click();
    await page.getByRole("button", { name: "专业技能", exact: true }).click();
    await page
      .getByLabel("专业技能", { exact: true })
      .fill("验收技能文本，仅用于验证表单读写。");
    const wordingUrl = "**/api/resume-workbench/claim-wording/ai";
    await page.route(wordingUrl, (route) =>
      route.fulfill({
        status: 500,
        json: { code: 500, message: "验收注入AI失败" },
      }),
    );
    await page
      .getByRole("button", { name: "优化当前内容", exact: true })
      .click();
    await page.getByRole("alert").filter({ hasText: "500" }).waitFor();
    assert.equal(
      await page.getByLabel("专业技能", { exact: true }).inputValue(),
      "验收技能文本，仅用于验证表单读写。",
    );
    await page.unroute(wordingUrl);
    let liveAi = "not requested";
    if (process.env.RUN_RESUME_AI === "1") {
      await page
        .getByRole("button", { name: "优化当前内容", exact: true })
        .click();
      await Promise.race([
        page
          .getByRole("dialog")
          .filter({ hasText: "审核 AI 建议" })
          .waitFor({ timeout: 100000 }),
        page.getByRole("alert").waitFor({ timeout: 100000 }),
      ]);
      if (
        await page
          .getByRole("dialog")
          .filter({ hasText: "审核 AI 建议" })
          .isVisible()
      ) {
        assert.equal(
          await page.getByLabel("专业技能", { exact: true }).inputValue(),
          "验收技能文本，仅用于验证表单读写。",
        );
        const suggestion = await page
          .locator(
            ".review-dialog[open] .comparison .form-row > div:last-child p",
          )
          .first()
          .innerText();
        await page
          .getByRole("button", { name: "核对并接受建议", exact: true })
          .click();
        assert.equal(
          await page.getByLabel("专业技能", { exact: true }).inputValue(),
          suggestion,
        );
        liveAi =
          "real AI suggestion reviewed; original preserved before acceptance";
      } else liveAi = "real AI unavailable; manual content retained";
    }
    await page.getByRole("button", { name: "教育背景", exact: true }).click();
    await page
      .locator(".entry-editor textarea")
      .fill("教育字段验收文本，不代表真实学历。");
    await page
      .getByRole("button", { name: "实习 / 工作", exact: true })
      .click();
    await page
      .locator(".entry-editor textarea")
      .fill("实习字段验收文本，不代表真实公司经历。");
    await page.getByRole("button", { name: "荣誉奖项", exact: true }).click();
    await page
      .getByLabel("荣誉奖项", { exact: true })
      .fill("奖项字段验收文本，不代表真实荣誉。");
    await page.getByRole("button", { name: "基本信息", exact: true }).click();
    await page.getByLabel("电话", { exact: true }).fill("01000000000");
    await page.getByRole("button", { name: "项目经历", exact: true }).click();
    await page
      .locator(".entry-editor textarea")
      .first()
      .fill(
        Array.from(
          { length: 120 },
          (_, i) => `验收长文第${i + 1}行，用于验证真实PDF换页与长内容处理。`,
        ).join("\n"),
      );
    await page
      .getByRole("button", { name: "保存为新版本", exact: true })
      .click();
    await page.waitForFunction(
      () => document.querySelectorAll(".version-card").length === 4,
    );
    await page.getByRole("button", { name: "03预览与导出" }).click();
    await page
      .getByText("读取本页文字", { exact: true })
      .waitFor({ timeout: 45000 });
    assert(
      (await page.locator(".page-controls").innerText()).includes("共 4 页") ||
        (await page.locator(".page-controls").innerText()).includes("共 3 页"),
    );
    await page.getByRole("button", { name: "下一页", exact: true }).click();
    await page.getByText("读取本页文字", { exact: true }).waitFor();
    await page.getByRole("button", { name: "放大预览", exact: true }).click();
    await page.getByRole("button", { name: "缩小预览", exact: true }).click();
    await page.getByLabel("历史版本").selectOption({ index: 1 });
    await page.locator(".comparison").filter({ hasText: "电话" }).waitFor();
    let liveInitial = "not requested";
    if (process.env.RUN_RESUME_AI === "1") {
      await page.getByRole("button", { name: "02制作简历" }).click();
      await page.getByLabel("目标岗位").selectOption({ index: 1 });
      const originalProject = await page
        .locator(".entry-editor textarea")
        .first()
        .inputValue();
      await page.getByRole("button", { name: "生成初稿", exact: true }).click();
      await Promise.race([
        page
          .getByRole("dialog")
          .filter({ hasText: "审核 AI 建议" })
          .waitFor({ timeout: 100000 }),
        page.getByRole("alert").waitFor({ timeout: 100000 }),
      ]);
      if (
        await page
          .getByRole("dialog")
          .filter({ hasText: "审核 AI 建议" })
          .isVisible()
      ) {
        assert.equal(
          await page.locator(".entry-editor textarea").first().inputValue(),
          originalProject,
        );
        await page
          .getByRole("button", { name: "保留原文", exact: true })
          .click();
        assert.equal(await page.locator(".version-card").count(), 5);
        liveInitial =
          "real AI candidate snapshot created; current draft preserved";
      } else
        liveInitial =
          "real AI generation failed; draft preserved: " +
          (await page.getByRole("alert").innerText());
      await page.getByRole("button", { name: "01资料与事实" }).click();
      await page.getByRole("button", { name: "放弃修改", exact: true }).click();
    }
    await page.getByRole("button", { name: "01资料与事实" }).click();
    const docx = fs.readdirSync(out).find((f) => f.endsWith(".docx"));
    await page.locator("input[type=file]").setInputFiles(path.join(out, docx));
    await page
      .getByText("简历已解析。现在点击“从已上传简历提取待核实条目”。", {
        exact: true,
      })
      .waitFor({ timeout: 45000 });
    await page
      .getByRole("button", { name: "提取候选事实", exact: true })
      .click();
    await page.waitForFunction(
      () =>
        !Array.from(document.querySelectorAll("button")).some(
          (b) => b.textContent === "提取中…",
        ),
    );
    assert(await page.locator(".file-source").innerText());
    await page
      .getByRole("button", { name: "查看 / 编辑", exact: true })
      .click();
    const claimDeleteUrl = "**/api/resume-workbench/claims/*";
    await page.route(claimDeleteUrl, async (route) => {
      if (route.request().method() !== "DELETE") return route.continue();
      const response = await route.fetch();
      await new Promise((resolve) => setTimeout(resolve, 700));
      await route.fulfill({ response });
    });
    await page.getByRole("button", { name: "移除", exact: true }).click();
    await page
      .getByLabel("经历名称", { exact: true })
      .fill("删除期间的后续输入");
    await page
      .getByText("原事实已移除；删除期间新增的输入仍保留为待保存的新事实", {
        exact: true,
      })
      .waitFor();
    assert.equal(
      await page.getByLabel("经历名称", { exact: true }).inputValue(),
      "删除期间的后续输入",
    );
    await page.unroute(claimDeleteUrl);

    await page.getByRole("button", { name: "03预览与导出" }).click();
    await page.getByRole("button", { name: "放弃修改", exact: true }).click();
    await page
      .getByRole("button", { name: "使用当前简历 →", exact: true })
      .click();
    await page.waitForURL("**/jobs?**");
    assert(new URL(page.url()).searchParams.get("resumeVersionId"));
    assert.deepEqual(errors, []);
    fs.writeFileSync(
      path.join(out, "verification.json"),
      JSON.stringify(
        {
          account: username,
          backend:
            "real local API; failures deliberately injected for recovery checks",
          liveAi,
          liveInitial,
          checks: [
            "empty states",
            "1920/1440/1366/390/360 no overflow",
            "dirty cancel retains input",
            "pending then confirmed fact persistence",
            "six section draft retention",
            "multiple entries and ordering",
            "independent snapshots",
            "save failure retains input",
            "input added during save retained",
            "input added during version loading and claim deletion retained",
            "PDF preview",
            "PDF and DOCX downloads",
            "field comparison",
            "interview preparation version handoff",
            "six sections persisted",
            "AI failure retains original",
            "real DOCX upload and parse",
            "multi-page PDF and page controls",
            "actual field differences",
          ],
          errors,
        },
        null,
        2,
      ),
    );
    console.log("PASS real backend resume V2 flow; artifacts: " + out);
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
