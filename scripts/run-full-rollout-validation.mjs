import { execFileSync } from "node:child_process";
import { createHash, createHmac } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

import { chromium } from "file:///C:/Users/%EA%B9%80%EB%AF%BC%ED%98%B8/CD2/flowify-FE/node_modules/@playwright/test/index.mjs";

const FE_BASE_URL = "http://localhost:5173";
const API_BASE_URL = "http://localhost:8080/api";
const REPO_ROOT = "C:/Users/김민호/CD2/flowify-BE-spring";
const FE_REPO_ROOT = "C:/Users/김민호/CD2/flowify-FE";
const OUTPUT_DIR = path.join(REPO_ROOT, ".tmp-full-rollout-validation");
const CLICK_OUTPUT_DIR = path.join(OUTPUT_DIR, "click");
const EXECUTION_OUTPUT_DIR = path.join(OUTPUT_DIR, "execution");
const MONGO_CONTAINER = "flowify-mongodb-canvas-drive-test";
const MONGO_DB = "flowify";

const AUTH_USER = {
  id: "6a0664640d4aaa140e72c8e4",
  email: "mhtiger362@gmail.com",
  name: "\uAE40\uBBFC\uD638",
  picture: null,
  createdAt: "2026-05-15T00:00:00Z",
};

const JWT_SECRET =
  "6ab652f5e569f39b9e44bef2d32cd4ada47cfb776090e4521ff2d5df13efced7";

const WORKFLOW_SUFFIX = "\uC804\uCCB4\uAC80\uC99D";
const NEWS_KEYWORD = "\uCDE8\uC5C5";
const GITHUB_REPOSITORY = "openai/openai-python";
const GMAIL_FALLBACK_EMAIL = "mhtiger362@gmail.com";
const GMAIL_MESSAGE_PLACEHOLDER = "TEMP_MESSAGE_ID";
const DRIVE_FILE_QUERY = "output";
const GMAIL_LABEL_CANDIDATES = [
  "INBOX",
  "IMPORTANT",
  "UNREAD",
  "CATEGORY_PERSONAL",
  "CHAT",
];

const BASE_WORKFLOW_IDS = {
  SINGLE_FILE: "6a116b764f14740c71658dbd",
  SINGLE_EMAIL: "6a116cdc4f14740c71658dce",
  SPREADSHEET_DATA: "6a116c2c4f14740c71658dc5",
  ARTICLE_LIST: "6a116c4f4f14740c71658dc6",
  API_RESPONSE: "6a116bbb4f14740c71658dc0",
};

const HIDDEN_PRIMARY_MODES = new Set(["web_news.seboard_posts"]);

const REQUIRED_NON_EMPTY_MODES = new Set([
  "google_drive.single_file",
  "google_drive.file_changed",
  "google_drive.new_file",
  "google_drive.folder_new_file",
  "google_drive.folder_all_files",
  "gmail.label_emails",
  "gmail.new_email",
  "gmail.sender_email",
  "gmail.single_email",
  "google_sheets.sheet_all",
  "google_sheets.new_row",
  "google_sheets.row_updated",
  "canvas_lms.course_files",
  "canvas_lms.course_new_file",
  "canvas_lms.term_all_files",
  "github.new_pr",
  "naver_news.article_search",
  "naver_news.new_articles",
  "web_news.seboard_posts",
  "web_news.seboard_new_posts",
  "web_news.website_feed",
]);

const CASES = [
  { serviceKey: "canvas_lms", modeKey: "course_files", expectedOutputType: "FILE_LIST" },
  { serviceKey: "canvas_lms", modeKey: "course_new_file", expectedOutputType: "SINGLE_FILE" },
  { serviceKey: "canvas_lms", modeKey: "term_all_files", expectedOutputType: "FILE_LIST" },
  { serviceKey: "google_drive", modeKey: "single_file", expectedOutputType: "SINGLE_FILE" },
  { serviceKey: "google_drive", modeKey: "file_changed", expectedOutputType: "SINGLE_FILE" },
  { serviceKey: "google_drive", modeKey: "new_file", expectedOutputType: "SINGLE_FILE" },
  { serviceKey: "google_drive", modeKey: "folder_new_file", expectedOutputType: "SINGLE_FILE" },
  { serviceKey: "google_drive", modeKey: "folder_all_files", expectedOutputType: "FILE_LIST" },
  { serviceKey: "google_sheets", modeKey: "sheet_all", expectedOutputType: "SPREADSHEET_DATA" },
  { serviceKey: "google_sheets", modeKey: "new_row", expectedOutputType: "SPREADSHEET_DATA" },
  { serviceKey: "google_sheets", modeKey: "row_updated", expectedOutputType: "SPREADSHEET_DATA" },
  { serviceKey: "gmail", modeKey: "label_emails", expectedOutputType: "EMAIL_LIST" },
  { serviceKey: "gmail", modeKey: "new_email", expectedOutputType: "SINGLE_EMAIL" },
  { serviceKey: "gmail", modeKey: "sender_email", expectedOutputType: "SINGLE_EMAIL" },
  { serviceKey: "gmail", modeKey: "single_email", expectedOutputType: "SINGLE_EMAIL" },
  { serviceKey: "gmail", modeKey: "starred_email", expectedOutputType: "SINGLE_EMAIL" },
  { serviceKey: "gmail", modeKey: "attachment_email", expectedOutputType: "FILE_LIST" },
  { serviceKey: "github", modeKey: "new_pr", expectedOutputType: "API_RESPONSE" },
  { serviceKey: "naver_news", modeKey: "article_search", expectedOutputType: "ARTICLE_LIST" },
  { serviceKey: "naver_news", modeKey: "new_articles", expectedOutputType: "ARTICLE_LIST" },
  { serviceKey: "web_news", modeKey: "seboard_posts", expectedOutputType: "ARTICLE_LIST" },
  { serviceKey: "web_news", modeKey: "seboard_new_posts", expectedOutputType: "ARTICLE_LIST" },
  { serviceKey: "web_news", modeKey: "website_feed", expectedOutputType: "ARTICLE_LIST" },
];

const UI_TEXT = {
  next: ["다음"],
  createStartNode: ["시작 노드 만들기"],
  manualCreate: ["직접 생성하기"],
};

const CHECKPOINT_CONFIG_ALLOWLIST = {
  "naver_news.new_articles": [],
  "web_news.seboard_new_posts": ["keyword"],
  "web_news.website_feed": ["keyword", "targets"],
};

const wait = (page, ms = 1000) => page.waitForTimeout(ms);

const ensureDirectory = (targetDir) => {
  mkdirSync(targetDir, { recursive: true });
  return targetDir;
};

const saveJson = (targetPath, value) => {
  ensureDirectory(path.dirname(targetPath));
  writeFileSync(targetPath, JSON.stringify(value, null, 2), "utf8");
};

const saveText = (targetPath, value) => {
  ensureDirectory(path.dirname(targetPath));
  writeFileSync(targetPath, value, "utf8");
};

const toBase64Url = (value) =>
  Buffer.from(JSON.stringify(value)).toString("base64url").replace(/=/g, "");

const buildAccessToken = () => {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "HS256", typ: "JWT" };
  const payload = {
    sub: AUTH_USER.id,
    email: AUTH_USER.email,
    name: AUTH_USER.name,
    iat: now,
    exp: now + 3600,
  };
  const encodedHeader = toBase64Url(header);
  const encodedPayload = toBase64Url(payload);
  const message = `${encodedHeader}.${encodedPayload}`;
  const signature = createHmac("sha256", JWT_SECRET)
    .update(message)
    .digest("base64url")
    .replace(/=/g, "");

  return `${message}.${signature}`;
};

const ACCESS_TOKEN = buildAccessToken();

const api = async (pathName, init = {}) => {
  const response = await fetch(`${API_BASE_URL}${pathName}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${ACCESS_TOKEN}`,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });

  const text = await response.text();
  let parsed = null;

  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = { raw: text };
    }
  }

  if (!response.ok) {
    throw new Error(
      `API ${init.method ?? "GET"} ${pathName} failed with ${response.status}: ${text}`,
    );
  }

  if (!parsed?.success) {
    throw new Error(
      `API ${init.method ?? "GET"} ${pathName} returned unsuccessful response: ${text}`,
    );
  }

  return parsed.data;
};

const getSourceCatalog = () => api("/editor-catalog/sources");

const getWorkflow = (workflowId) => api(`/workflows/${workflowId}`);

const createWorkflow = (body) =>
  api("/workflows", {
    method: "POST",
    body: JSON.stringify(body),
  });

const updateWorkflow = (workflowId, body) =>
  api(`/workflows/${workflowId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });

const executeWorkflow = (workflowId) =>
  api(`/workflows/${workflowId}/execute`, {
    method: "POST",
    body: "{}",
  });

const getExecutionDetail = (workflowId, executionId) =>
  api(`/workflows/${workflowId}/executions/${executionId}`);

const getSourceTargetOptions = (
  serviceKey,
  modeKey,
  { cursor, parentId, query } = {},
) => {
  const params = new URLSearchParams({ mode: modeKey });
  if (cursor) {
    params.set("cursor", cursor);
  }
  if (parentId) {
    params.set("parentId", parentId);
  }
  if (query) {
    params.set("query", query);
  }
  return api(`/editor-catalog/sources/${serviceKey}/target-options?${params}`);
};

const getSinkTargetOptions = (serviceKey, type, parentId) => {
  const params = new URLSearchParams({ type });
  if (parentId) {
    params.set("parentId", parentId);
  }
  return api(`/editor-catalog/sinks/${serviceKey}/target-options?${params}`);
};

const waitForExecutionCompletion = async (
  workflowId,
  executionId,
  timeoutMs = 180000,
) => {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    const detail = await getExecutionDetail(workflowId, executionId);
    if (!["pending", "running"].includes(detail.state)) {
      return detail;
    }
    await new Promise((resolve) => setTimeout(resolve, 3000));
  }

  throw new Error(`Execution polling timed out for workflow ${workflowId}`);
};

const normalizeText = (value) => value.replace(/\s+/g, " ").trim();

const findStartNode = (workflow) =>
  workflow.nodes.find((node) => node.role === "start") ?? null;

const findEndNode = (workflow) =>
  workflow.nodes.find((node) => node.role === "end") ?? null;

const getExecutionStartLog = (executionDetail, startNodeId) =>
  executionDetail.nodeLogs.find((log) => log.nodeId === startNodeId) ?? null;

const getExecutionEndLog = (executionDetail, endNodeId) =>
  executionDetail.nodeLogs.find((log) => log.nodeId === endNodeId) ?? null;

const deepClone = (value) => JSON.parse(JSON.stringify(value));

const isEffectivelyEmpty = (value) => {
  if (value == null) {
    return true;
  }
  if (typeof value === "string") {
    return value.trim().length === 0;
  }
  if (Array.isArray(value)) {
    return value.length === 0 || value.every((item) => isEffectivelyEmpty(item));
  }
  if (typeof value === "object") {
    const entries = Object.entries(value);
    return (
      entries.length === 0 ||
      entries.every(([, entryValue]) => isEffectivelyEmpty(entryValue))
    );
  }
  return false;
};

const summarizeStartOutput = (outputData) => {
  if (!outputData || typeof outputData !== "object") {
    return { empty: true, type: null };
  }

  const type = outputData.type ?? null;
  if (type === "SINGLE_FILE") {
    return {
      empty:
        isEffectivelyEmpty(outputData.file_id) &&
        isEffectivelyEmpty(outputData.filename),
      type,
      fileId: outputData.file_id ?? "",
      filename: outputData.filename ?? "",
      mimeType: outputData.mime_type ?? "",
    };
  }
  if (type === "FILE_LIST") {
    const items = Array.isArray(outputData.items) ? outputData.items : [];
    return {
      empty: items.length === 0,
      type,
      itemCount: items.length,
      firstItem: items[0] ?? null,
    };
  }
  if (type === "SINGLE_EMAIL") {
    return {
      empty:
        isEffectivelyEmpty(outputData.id) &&
        isEffectivelyEmpty(outputData.subject),
      type,
      id: outputData.id ?? "",
      subject: outputData.subject ?? "",
      sender: outputData.sender ?? outputData.from ?? "",
    };
  }
  if (type === "EMAIL_LIST") {
    const items = Array.isArray(outputData.items) ? outputData.items : [];
    return {
      empty: items.length === 0,
      type,
      itemCount: items.length,
      firstItem: items[0] ?? null,
    };
  }
  if (type === "SPREADSHEET_DATA") {
    const headers = Array.isArray(outputData.headers) ? outputData.headers : [];
    const rows = Array.isArray(outputData.rows) ? outputData.rows : [];
    return {
      empty: headers.length === 0 && rows.length === 0,
      type,
      headerCount: headers.length,
      rowCount: rows.length,
      firstHeader: headers[0] ?? null,
    };
  }
  if (type === "ARTICLE_LIST" || type === "API_RESPONSE") {
    const items = Array.isArray(outputData.items) ? outputData.items : [];
    return {
      empty: items.length === 0,
      type,
      itemCount: items.length,
      firstItem: items[0] ?? null,
      freshness: outputData.metadata?.freshness ?? null,
    };
  }
  if (type === "TEXT") {
    return {
      empty: isEffectivelyEmpty(outputData.content),
      type,
      contentLength:
        typeof outputData.content === "string" ? outputData.content.length : 0,
    };
  }

  return {
    empty: isEffectivelyEmpty(outputData),
    type,
  };
};

const assert = (condition, message) => {
  if (!condition) {
    throw new Error(message);
  }
};

const clickFirstPlaceholder = async (page) => {
  const placeholder = page.locator(".react-flow__node-placeholder").first();
  await placeholder.waitFor({ state: "visible", timeout: 15000 });
  await placeholder.click({ force: true });
  await wait(page, 800);
};

const fillFirstVisibleInput = async (page, value) => {
  const inputs = page.locator("input");
  const count = await inputs.count();

  for (let index = 0; index < count; index += 1) {
    const input = inputs.nth(index);
    if (await input.isVisible().catch(() => false)) {
      await input.fill(value);
      return;
    }
  }

  throw new Error("Visible input not found.");
};

const clickText = async (page, text, timeout = 15000, useLast = true) => {
  const locator = page.getByText(text, { exact: false });
  const candidate = useLast ? locator.last() : locator.first();
  await candidate.waitFor({ state: "visible", timeout });
  await candidate.click({ force: true });
};

const tryClickText = async (page, text, timeout = 1500, useLast = true) => {
  try {
    await clickText(page, text, timeout, useLast);
    return true;
  } catch {
    return false;
  }
};

const clickFromCandidates = async (
  page,
  candidates,
  { timeout = 5000, useLast = true } = {},
) => {
  for (const candidate of candidates.filter(Boolean)) {
    if (await tryClickText(page, candidate, timeout, useLast)) {
      await wait(page, 500);
      return candidate;
    }
  }
  return null;
};

const clickLastVisibleButton = async (page) => {
  const buttons = page.locator("button");
  const count = await buttons.count();

  for (let index = count - 1; index >= 0; index -= 1) {
    const button = buttons.nth(index);
    if (await button.isVisible().catch(() => false)) {
      await button.click({ force: true });
      await wait(page, 500);
      return index;
    }
  }

  throw new Error("Visible button fallback not found.");
};

const clickWizardAction = async (page, candidates) => {
  const clicked = await clickFromCandidates(page, candidates, { timeout: 2500 });
  if (clicked) {
    return clicked;
  }
  await clickLastVisibleButton(page);
  return "fallback:last-visible-button";
};

const pickRemoteOptionByText = async (page, labels, timeout = 15000) => {
  for (const label of labels.filter(Boolean)) {
    const locator = page.getByText(label, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click({ force: true });
      await wait(page, 700);
      return label;
    }
    try {
      await locator.waitFor({ state: "visible", timeout });
      await locator.click({ force: true });
      await wait(page, 700);
      return label;
    } catch {
      // keep trying
    }
  }

  throw new Error(`Remote picker option not found. Candidates=${labels.join(", ")}`);
};

const buildWorkflowName = (plan) =>
  `${plan.serviceKey}-${plan.modeKey}-${WORKFLOW_SUFFIX}`;

const workflowIdFromUrl = (url) =>
  url.split("/workflows/")[1]?.split(/[?#]/)[0] ?? null;

const ensureManualCreationSelected = async (page) => {
  const placeholder = page.locator(".react-flow__node-placeholder").first();
  if (await placeholder.isVisible().catch(() => false)) {
    return "placeholder-already-visible";
  }

  const nodeButtons = page.locator(".react-flow__node button");
  const count = await nodeButtons.count();
  for (let index = 0; index < count; index += 1) {
    const button = nodeButtons.nth(index);
    if (await button.isVisible().catch(() => false)) {
      await button.click({ force: true });
      await placeholder.waitFor({ state: "visible", timeout: 15000 });
      await wait(page, 700);
      return "clicked-node-button";
    }
  }

  const textClicked = await clickFromCandidates(page, UI_TEXT.manualCreate, {
    timeout: 2000,
    useLast: false,
  });
  if (textClicked) {
    await placeholder.waitFor({ state: "visible", timeout: 15000 });
    await wait(page, 700);
    return `clicked-manual-text:${textClicked}`;
  }

  throw new Error("Could not switch to manual workflow creation.");
};

const snapshotPage = async (page, slug, step) => {
  ensureDirectory(CLICK_OUTPUT_DIR);
  const targetPrefix = path.join(CLICK_OUTPUT_DIR, `${slug}-${step}`);
  const bodyText = await page.locator("body").innerText().catch(() => "");
  const nodeLabels = await page
    .locator(".react-flow__node")
    .evaluateAll((nodes) =>
      nodes
        .map((node) => node.textContent?.replace(/\s+/g, " ").trim() ?? "")
        .filter(Boolean),
    )
    .catch(() => []);

  await page.screenshot({ path: `${targetPrefix}.png`, fullPage: true });
  saveJson(`${targetPrefix}.json`, {
    step,
    url: page.url(),
    bodyText,
    nodeLabels,
  });
};

const parseRolloutAllowlist = () => {
  const source = readFileSync(
    path.join(FE_REPO_ROOT, "src/features/add-node/model/source-rollout.ts"),
    "utf8",
  );
  const match = source.match(
    /SOURCE_SERVICE_ROLLOUT_ALLOWLIST\s*=\s*(\{[\s\S]*?\})\s*as const/,
  );
  if (!match) {
    throw new Error("Could not parse SOURCE_SERVICE_ROLLOUT_ALLOWLIST.");
  }
  return Function(`"use strict"; return (${match[1]});`)();
};

const assertCoverageMatchesCurrentRollout = () => {
  const allowlist = parseRolloutAllowlist();
  const rolloutKeys = Object.entries(allowlist)
    .flatMap(([serviceKey, modes]) =>
      modes.map((modeKey) => `${serviceKey}.${modeKey}`),
    )
    .sort();
  const caseKeys = CASES.map((entry) => `${entry.serviceKey}.${entry.modeKey}`).sort();

  const missing = rolloutKeys.filter((key) => !caseKeys.includes(key));
  const extra = caseKeys.filter((key) => !rolloutKeys.includes(key));

  if (missing.length || extra.length) {
    throw new Error(
      `Rollout coverage mismatch. Missing=${missing.join(", ") || "-"} Extra=${extra.join(", ") || "-"}`,
    );
  }
};

const findSourceMode = (catalog, serviceKey, modeKey) => {
  const service = catalog.services.find((item) => item.key === serviceKey);
  if (!service) {
    throw new Error(`Source service not found: ${serviceKey}`);
  }
  const mode = service.source_modes.find((item) => item.key === modeKey);
  if (!mode) {
    throw new Error(`Source mode not found: ${serviceKey}.${modeKey}`);
  }
  return { service, mode };
};

const collectSourcePlans = async (catalog) => {
  const plans = [];

  for (const testCase of CASES) {
    const { service, mode } = findSourceMode(
      catalog,
      testCase.serviceKey,
      testCase.modeKey,
    );
    const plan = {
      ...testCase,
      key: `${testCase.serviceKey}.${testCase.modeKey}`,
      slug: `${testCase.serviceKey}-${testCase.modeKey}`,
      workflowName: buildWorkflowName(testCase),
      hiddenInPrimaryList: HIDDEN_PRIMARY_MODES.has(
        `${testCase.serviceKey}.${testCase.modeKey}`,
      ),
      serviceLabel: service.label,
      modeLabel: mode.label,
      triggerKind: mode.trigger_kind,
      targetSchemaType:
        typeof mode.target_schema?.type === "string"
          ? mode.target_schema.type
          : "",
      targetSchema: mode.target_schema ?? {},
      targetValue: "",
      targetLabel: "",
      targetMeta: null,
      remoteTarget: false,
      query: "",
      clickLabels: [],
      spreadsheetLabel: "",
      spreadsheetId: null,
      sheetName: null,
      sheetId: null,
      extraTargets: [],
    };

    if (testCase.serviceKey === "canvas_lms") {
      const options = await getSourceTargetOptions("canvas_lms", testCase.modeKey);
      const option = options.items?.[0] ?? null;
      assert(option, `No Canvas target option for ${plan.key}`);
      plan.remoteTarget = true;
      plan.targetValue = option.id;
      plan.targetLabel = option.label;
      plan.targetMeta = option.metadata ?? null;
      plan.clickLabels = [option.label];
    }

    if (testCase.serviceKey === "google_drive") {
      const options = ["single_file", "file_changed"].includes(testCase.modeKey)
        ? await getSourceTargetOptions("google_drive", testCase.modeKey, {
            query: DRIVE_FILE_QUERY,
          })
        : await getSourceTargetOptions("google_drive", testCase.modeKey);
      const option = options.items?.[0] ?? null;
      assert(option, `No Google Drive target option for ${plan.key}`);
      plan.remoteTarget = true;
      plan.query = ["single_file", "file_changed"].includes(testCase.modeKey)
        ? DRIVE_FILE_QUERY
        : "";
      plan.targetValue = option.id;
      plan.targetLabel = option.label;
      plan.targetMeta = option.metadata ?? null;
      plan.clickLabels = [option.label];
    }

    if (testCase.serviceKey === "google_sheets") {
      const spreadsheetOptions = await getSourceTargetOptions(
        "google_sheets",
        testCase.modeKey,
      );
      const spreadsheet = spreadsheetOptions.items?.[0] ?? null;
      assert(spreadsheet, `No Google Sheets spreadsheet option for ${plan.key}`);
      const sheetOptions = await getSourceTargetOptions("google_sheets", testCase.modeKey, {
        parentId: spreadsheet.id,
      });
      const sheet =
        sheetOptions.items?.find((item) => item.type === "sheet") ??
        sheetOptions.items?.[0] ??
        null;
      assert(sheet, `No Google Sheets sheet option for ${plan.key}`);
      plan.remoteTarget = true;
      plan.targetValue = spreadsheet.id;
      plan.targetLabel = sheet.label;
      plan.targetMeta = sheet.metadata ?? null;
      plan.clickLabels = [sheet.label];
      plan.spreadsheetLabel = spreadsheet.label;
      plan.spreadsheetId = sheet.metadata?.spreadsheetId ?? spreadsheet.id;
      plan.sheetName = sheet.metadata?.sheetName ?? sheet.label;
      plan.sheetId = sheet.metadata?.sheetId ?? null;
    }

    if (testCase.serviceKey === "gmail") {
      if (testCase.modeKey === "label_emails") {
        const options = await getSourceTargetOptions("gmail", "label_emails");
        const option =
          GMAIL_LABEL_CANDIDATES.map((candidate) =>
            options.items?.find((item) => item.label === candidate),
          ).find(Boolean) ??
          options.items?.[0] ??
          null;
        assert(option, `No Gmail label option for ${plan.key}`);
        plan.remoteTarget = true;
        plan.targetValue = option.id;
        plan.targetLabel = option.label;
        plan.targetMeta = option.metadata ?? null;
        plan.clickLabels = [option.label];
      } else if (testCase.modeKey === "single_email") {
        plan.targetValue = GMAIL_MESSAGE_PLACEHOLDER;
        plan.targetLabel = GMAIL_MESSAGE_PLACEHOLDER;
      } else if (testCase.modeKey === "sender_email") {
        plan.targetValue = GMAIL_FALLBACK_EMAIL;
        plan.targetLabel = GMAIL_FALLBACK_EMAIL;
      }
    }

    if (testCase.serviceKey === "github") {
      plan.targetValue = GITHUB_REPOSITORY;
      plan.targetLabel = GITHUB_REPOSITORY;
    }

    if (testCase.serviceKey === "naver_news") {
      plan.targetValue = NEWS_KEYWORD;
      plan.targetLabel = NEWS_KEYWORD;
    }

    if (testCase.serviceKey === "web_news") {
      const options = await getSourceTargetOptions("web_news", testCase.modeKey);
      const option =
        (testCase.modeKey === "website_feed"
          ? options.items?.find((item) => item.label === "BBC News")
          : null) ??
        options.items?.[0] ??
        null;
      assert(option, `No web_news target option for ${plan.key}`);
      plan.targetValue = option.id;
      plan.targetLabel =
        option.metadata?.displayPath || option.label || option.id || "";
      plan.targetMeta = option.metadata ?? null;
      plan.clickLabels = [
        option.metadata?.categoryName,
        option.metadata?.displayPath,
        option.label,
      ].filter(Boolean);
      if (testCase.modeKey === "website_feed") {
        plan.remoteTarget = true;
        plan.extraTargets = [option.id];
      } else if (!plan.hiddenInPrimaryList) {
        plan.remoteTarget = true;
      }
    }

    plans.push(plan);
  }

  return plans;
};

const openWorkflowEditor = async (page, workflowId) => {
  await page.goto(`${FE_BASE_URL}/workflows/${workflowId}`, {
    waitUntil: "domcontentloaded",
    timeout: 30000,
  });
  await wait(page, 2200);
  await ensureManualCreationSelected(page);
  await wait(page, 600);
};

const configureTargetThroughUi = async (page, plan) => {
  if (plan.hiddenInPrimaryList || !Object.keys(plan.targetSchema).length) {
    return;
  }

  if (plan.remoteTarget) {
    if (plan.query) {
      await fillFirstVisibleInput(page, plan.query);
      await wait(page, 1200);
    }

    if (plan.targetSchemaType === "sheet_picker") {
      await pickRemoteOptionByText(page, [plan.spreadsheetLabel]);
      await pickRemoteOptionByText(page, plan.clickLabels);
      return;
    }

    await pickRemoteOptionByText(page, plan.clickLabels);
    return;
  }

  if (plan.targetValue) {
    await fillFirstVisibleInput(page, plan.targetValue);
    await wait(page, 500);
  }
};

const createSourceWorkflowByClick = async (page, plan) => {
  const workflow = await createWorkflow({
    name: plan.workflowName,
    nodes: [],
    edges: [],
  });

  await openWorkflowEditor(page, workflow.id);
  await clickFirstPlaceholder(page);
  await clickText(page, plan.serviceLabel);
  await wait(page, 800);

  if (plan.hiddenInPrimaryList) {
    const bodyText = normalizeText(
      await page.locator("body").innerText().catch(() => ""),
    );
    assert(
      !bodyText.includes(plan.modeLabel),
      `${plan.key} should be hidden in the primary source-mode list.`,
    );
    await snapshotPage(page, plan.slug, "hidden-primary");
    return {
      ok: true,
      slug: plan.slug,
      key: plan.key,
      workflowId: workflow.id,
      workflowName: plan.workflowName,
      workflowUrl: page.url(),
      clickStatus: "hidden_expected",
      startNodeId: null,
    };
  }

  await clickText(page, plan.modeLabel);
  await wait(page, 900);

  if (Object.keys(plan.targetSchema).length > 0) {
    await configureTargetThroughUi(page, plan);
    await clickWizardAction(page, UI_TEXT.next);
  }

  await clickWizardAction(page, UI_TEXT.createStartNode);
  await wait(page, 2000);

  const persistedWorkflow = await getWorkflow(workflow.id);
  const startNode = findStartNode(persistedWorkflow);
  assert(startNode, `Start node was not created for ${plan.key}`);
  assert(
    startNode.type === plan.serviceKey,
    `Start node service mismatch for ${plan.key}: ${startNode.type}`,
  );
  assert(
    startNode.config?.source_mode === plan.modeKey,
    `Start node mode mismatch for ${plan.key}: ${startNode.config?.source_mode}`,
  );
  assert(
    startNode.outputDataType === plan.expectedOutputType,
    `Output type mismatch for ${plan.key}: ${startNode.outputDataType}`,
  );

  await snapshotPage(page, plan.slug, "source-created");

  return {
    ok: true,
    slug: plan.slug,
    key: plan.key,
    workflowId: workflow.id,
    workflowName: plan.workflowName,
    workflowUrl: page.url(),
    clickStatus: "created",
    startNodeId: startNode.id,
  };
};

const buildStartNodeSkeleton = (plan) => ({
  id: `node_${plan.slug}_start`,
  category: "service",
  type: plan.serviceKey,
  label: plan.modeLabel,
  role: "start",
  dataType: null,
  outputDataType: plan.expectedOutputType,
  position: { x: 80, y: 180 },
  config: {
    isConfigured: true,
    source_mode: plan.modeKey,
    trigger_kind: plan.triggerKind,
    target: plan.targetValue ?? "",
    target_label: plan.targetLabel ?? "",
    target_meta: plan.targetMeta ?? null,
  },
});

const buildCustomLoopNode = ({ nodeId, dataType, outputDataType }) => ({
  id: nodeId,
  category: "control",
  type: "loop",
  role: "middle",
  dataType,
  outputDataType,
  position: { x: 320, y: 180 },
  config: {
    isConfigured: true,
    targetField: null,
    maxIterations: 100,
    timeout: 300,
    choiceNodeType: "LOOP",
  },
});

const buildCustomDataFilterNode = ({
  nodeId,
  dataType,
  actionId,
  fields,
}) => ({
  id: nodeId,
  category: "ai",
  type: "llm",
  role: "middle",
  dataType,
  outputDataType: "SPREADSHEET_DATA",
  position: { x: 420, y: 180 },
  config: {
    isConfigured: true,
    choiceActionId: actionId,
    choiceNodeType: "DATA_FILTER",
    choiceSelections: {
      follow_up: fields,
    },
  },
});

const buildCustomSummaryNode = ({ nodeId, dataType }) => ({
  id: nodeId,
  category: "ai",
  type: "llm",
  role: "middle",
  dataType,
  outputDataType: "TEXT",
  position: { x: 520, y: 180 },
  config: {
    isConfigured: true,
    prompt: "",
    model: null,
    outputFormat: "text",
    temperature: 0.7,
    choiceActionId: "summarize",
    choiceNodeType: "AI",
    choiceSelections: {
      follow_up: "one_paragraph",
    },
  },
});

const buildNotionSinkConfig = async (workflowName) => {
  const notionTargets = await getSinkTargetOptions("notion", "page");
  const notionPage = Array.isArray(notionTargets.items)
    ? notionTargets.items[0] ?? null
    : null;
  if (!notionPage) {
    throw new Error("No Notion page target is available.");
  }

  return {
    config: {
      isConfigured: true,
      service: "notion",
      target_type: "page",
      target_id: notionPage.id,
      title_template: `${workflowName} {{date}}`,
    },
    target: notionPage,
  };
};

const loadBaseWorkflows = async () => {
  const entries = await Promise.all(
    Object.entries(BASE_WORKFLOW_IDS).map(async ([key, workflowId]) => [
      key,
      await getWorkflow(workflowId),
    ]),
  );
  return Object.fromEntries(entries);
};

const composeWorkflowGraph = async ({
  baseWorkflow,
  startNode,
  workflowName,
  customGraphType,
}) => {
  const notionSink = await buildNotionSinkConfig(workflowName);

  if (customGraphType === "FILE_LIST" || customGraphType === "EMAIL_LIST") {
    const dataFilterNode = buildCustomDataFilterNode({
      nodeId: `node_${customGraphType.toLowerCase()}_filter`,
      dataType: customGraphType,
      actionId:
        customGraphType === "FILE_LIST"
          ? "filter_metadata_table"
          : "filter_fields_table",
      fields:
        customGraphType === "FILE_LIST"
          ? ["filename", "link", "upload_time", "file_size"]
          : [
              "message_id",
              "subject",
              "sender",
              "label_list",
              "attachment_names",
            ],
    });
    const sinkNode = {
      id: `node_${customGraphType.toLowerCase()}_notion_end`,
      category: "service",
      type: "notion",
      role: "end",
      dataType: "SPREADSHEET_DATA",
      outputDataType: null,
      position: { x: 700, y: 180 },
      config: notionSink.config,
    };

    return {
      nodes: [startNode, dataFilterNode, sinkNode],
      edges: [
        {
          id: `edge_${startNode.id}_filter`,
          source: startNode.id,
          target: dataFilterNode.id,
        },
        {
          id: `edge_${dataFilterNode.id}_sink`,
          source: dataFilterNode.id,
          target: sinkNode.id,
        },
      ],
      sinkTarget: notionSink.target,
    };
  }

  const baseNodes = deepClone(baseWorkflow.nodes);
  const baseEdges = deepClone(baseWorkflow.edges);
  const baseStartNode = baseNodes.find((node) => node.role === "start");
  assert(baseStartNode, `Base workflow start node missing: ${baseWorkflow.id}`);

  const nodes = [startNode];
  for (const node of baseNodes) {
    if (node.id === baseStartNode.id) {
      continue;
    }

    if (node.role === "end" && node.type === "notion") {
      nodes.push({ ...node, config: notionSink.config });
      continue;
    }

    nodes.push(node);
  }

  const edges = baseEdges.map((edge) => ({
    ...edge,
    source: edge.source === baseStartNode.id ? startNode.id : edge.source,
    target: edge.target === baseStartNode.id ? startNode.id : edge.target,
  }));

  return {
    nodes,
    edges,
    sinkTarget: notionSink.target,
  };
};

const stableNormalize = (value) => {
  if (Array.isArray(value)) {
    return value.map((item) => stableNormalize(item));
  }
  if (value && typeof value === "object") {
    return Object.keys(value)
      .sort()
      .reduce((accumulator, key) => {
        accumulator[key] = stableNormalize(value[key]);
        return accumulator;
      }, {});
  }
  return value;
};

const renderPythonStyleJson = (value) => {
  if (Array.isArray(value)) {
    return `[${value.map((item) => renderPythonStyleJson(item)).join(", ")}]`;
  }

  if (value && typeof value === "object") {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}: ${renderPythonStyleJson(value[key])}`)
      .join(", ")}}`;
  }

  return JSON.stringify(value);
};

const buildTargetHash = (serviceKey, modeKey, target, config) => {
  const allowlist = CHECKPOINT_CONFIG_ALLOWLIST[`${serviceKey}.${modeKey}`] ?? [];
  const stableConfig = allowlist.reduce((accumulator, key) => {
    if (config[key] !== undefined) {
      accumulator[key] = config[key];
    }
    return accumulator;
  }, {});
  const rawValue = renderPythonStyleJson(
    stableNormalize({ target, config: stableConfig }),
  );
  return createHash("sha256").update(rawValue, "utf8").digest("hex");
};

const runMongoEval = (statement) =>
  execFileSync(
    "docker",
    [
      "exec",
      MONGO_CONTAINER,
      "mongosh",
      "--quiet",
      "--port",
      "27017",
      MONGO_DB,
      "--eval",
      statement,
    ],
    { encoding: "utf8" },
  );

const upsertMongoDocument = (collection, query, document) => {
  const statement = `db.getCollection(${JSON.stringify(
    collection,
  )}).replaceOne(${JSON.stringify(query)}, ${JSON.stringify(document)}, { upsert: true });`;
  return runMongoEval(statement);
};

const seedFreshnessCheckpoint = ({
  workflowId,
  nodeId,
  plan,
  config,
}) => {
  const targetHash = buildTargetHash(
    plan.serviceKey,
    plan.modeKey,
    config.target ?? "",
    config,
  );
  const query = {
    userId: AUTH_USER.id,
    workflowId,
    nodeId,
    service: plan.serviceKey,
    mode: plan.modeKey,
    targetHash,
  };
  const document = {
    ...query,
    cursorType: "item_key",
    cursorValue: "__seed__",
    seenItemKeys: [],
    updatedAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
  };
  upsertMongoDocument("source_checkpoints", query, document);
  return { query, document };
};

const seedGithubState = ({ workflowId, nodeId }) => {
  const query = { workflowId, nodeId };
  const document = {
    workflowId,
    nodeId,
    service: "github",
    state: {
      last_seen_pr_created_at: "1970-01-01T00:00:00Z",
      last_seen_pr_number: 0,
    },
    updatedAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
  };
  upsertMongoDocument("workflow_node_states", query, document);
  return { query, document };
};

const applyExecutionSourceOverrides = ({ plan, startNode, context }) => {
  const nextStartNode = deepClone(startNode);
  nextStartNode.config = {
    ...(nextStartNode.config ?? {}),
    isConfigured: true,
    source_mode: plan.modeKey,
    trigger_kind: plan.triggerKind,
  };

  if (plan.targetValue) {
    nextStartNode.config.target = plan.targetValue;
  }
  if (plan.targetLabel) {
    nextStartNode.config.target_label = plan.targetLabel;
  }
  if (plan.targetMeta !== undefined) {
    nextStartNode.config.target_meta = plan.targetMeta;
  }

  if (plan.serviceKey === "google_sheets") {
    nextStartNode.config.spreadsheet_id =
      context.googleSheetsSource?.spreadsheetId ?? plan.spreadsheetId;
    nextStartNode.config.sheet_name =
      context.googleSheetsSource?.sheetName ?? plan.sheetName;
    nextStartNode.config.sheet_id =
      context.googleSheetsSource?.sheetId ?? plan.sheetId;
    nextStartNode.config.target =
      context.googleSheetsSource?.spreadsheetId ?? plan.spreadsheetId;
    nextStartNode.config.target_label =
      context.googleSheetsSource?.targetLabel ?? plan.targetLabel;
    nextStartNode.config.header_row = 1;
    nextStartNode.config.data_start_row = 2;
    if (["new_row", "row_updated"].includes(plan.modeKey)) {
      nextStartNode.config.initial_sync_mode = "emit_existing";
    }
    if (plan.modeKey === "row_updated" && context.googleSheetsKeyColumn) {
      nextStartNode.config.key_column = context.googleSheetsKeyColumn;
    }
  }

  if (plan.serviceKey === "gmail") {
    if (plan.modeKey === "label_emails") {
      nextStartNode.config.maxResults = 5;
    }
    if (plan.modeKey === "single_email" && context.gmailMessageId) {
      nextStartNode.config.target = context.gmailMessageId;
      nextStartNode.config.target_label = context.gmailMessageId;
    }
    if (plan.modeKey === "sender_email" && context.gmailSender) {
      nextStartNode.config.target = context.gmailSender;
      nextStartNode.config.target_label = context.gmailSender;
    }
  }

  if (plan.serviceKey === "github") {
    nextStartNode.config.target = GITHUB_REPOSITORY;
    nextStartNode.config.target_label = GITHUB_REPOSITORY;
  }

  if (plan.serviceKey === "naver_news") {
    nextStartNode.config.maxResults = 5;
  }

  if (plan.serviceKey === "web_news") {
    nextStartNode.config.maxResults = 5;
    if (plan.modeKey === "website_feed") {
      nextStartNode.config.targets = plan.extraTargets.length
        ? plan.extraTargets
        : [plan.targetValue];
    }
  }

  return nextStartNode;
};

const maybeSeedEventState = ({ workflowId, startNode, plan }) => {
  if (plan.serviceKey === "github" && plan.modeKey === "new_pr") {
    return {
      type: "workflow_node_states",
      ...seedGithubState({ workflowId, nodeId: startNode.id }),
    };
  }

  if (
    plan.serviceKey === "naver_news" &&
    plan.modeKey === "new_articles"
  ) {
    return {
      type: "source_checkpoints",
      ...seedFreshnessCheckpoint({
        workflowId,
        nodeId: startNode.id,
        plan,
        config: startNode.config,
      }),
    };
  }

  if (
    plan.serviceKey === "web_news" &&
    ["seboard_new_posts", "website_feed"].includes(plan.modeKey)
  ) {
    return {
      type: "source_checkpoints",
      ...seedFreshnessCheckpoint({
        workflowId,
        nodeId: startNode.id,
        plan,
        config: startNode.config,
      }),
    };
  }

  return null;
};

const executeValidationWorkflow = async ({
  plan,
  clickResult,
  baseWorkflows,
  context,
}) => {
  let workflow = await getWorkflow(clickResult.workflowId);
  let startNode = findStartNode(workflow);

  if (!startNode) {
    startNode = buildStartNodeSkeleton(plan);
    await updateWorkflow(workflow.id, {
      name: plan.workflowName,
      nodes: [startNode],
      edges: [],
    });
    workflow = await getWorkflow(workflow.id);
    startNode = findStartNode(workflow);
  }

  assert(startNode, `Execution start node missing for ${plan.key}`);
  const patchedStartNode = applyExecutionSourceOverrides({
    plan,
    startNode,
    context,
  });
  const customGraphType =
    plan.expectedOutputType === "FILE_LIST" ||
    plan.expectedOutputType === "EMAIL_LIST"
      ? plan.expectedOutputType
      : null;
  const baseWorkflow =
    customGraphType == null ? baseWorkflows[plan.expectedOutputType] : null;

  const composed = await composeWorkflowGraph({
    baseWorkflow,
    startNode: {
      ...patchedStartNode,
      outputDataType: plan.expectedOutputType,
    },
    workflowName: plan.workflowName,
    customGraphType,
  });

  await updateWorkflow(workflow.id, {
    name: plan.workflowName,
    nodes: composed.nodes,
    edges: composed.edges,
  });

  const executionWorkflow = await getWorkflow(workflow.id);
  const executionStartNode = findStartNode(executionWorkflow);
  const eventSeed = executionStartNode
    ? maybeSeedEventState({
        workflowId: workflow.id,
        startNode: executionStartNode,
        plan,
      })
    : null;

  const executionId = await executeWorkflow(workflow.id);
  const detail = await waitForExecutionCompletion(workflow.id, executionId);
  const completedWorkflow = await getWorkflow(workflow.id);
  const completedStartNode = findStartNode(completedWorkflow);
  const completedEndNode = findEndNode(completedWorkflow);
  const startLog = completedStartNode
    ? getExecutionStartLog(detail, completedStartNode.id)
    : null;
  const endLog = completedEndNode ? getExecutionEndLog(detail, completedEndNode.id) : null;
  const sourceSummary = summarizeStartOutput(startLog?.outputData ?? null);
  const requiresNonEmpty = REQUIRED_NON_EMPTY_MODES.has(plan.key);
  const sourceNonEmpty = !sourceSummary.empty;
  const sinkExecuted = Boolean(endLog);
  const sinkOk = endLog?.status === "success";
  const overallOk =
    detail.state === "success" &&
    sinkOk &&
    (!requiresNonEmpty || sourceNonEmpty);

  if (plan.serviceKey === "gmail" && sourceNonEmpty) {
    if (plan.modeKey === "label_emails") {
      const firstItem = startLog?.outputData?.items?.[0] ?? null;
      if (firstItem?.id && !context.gmailMessageId) {
        context.gmailMessageId = firstItem.id;
      }
      if ((firstItem?.sender || firstItem?.from) && !context.gmailSender) {
        context.gmailSender = firstItem.sender || firstItem.from;
      }
    }
    if (plan.modeKey === "new_email") {
      if (startLog?.outputData?.id && !context.gmailMessageId) {
        context.gmailMessageId = startLog.outputData.id;
      }
      if ((startLog?.outputData?.sender || startLog?.outputData?.from) && !context.gmailSender) {
        context.gmailSender = startLog.outputData.sender || startLog.outputData.from;
      }
    }
  }

  if (
    plan.serviceKey === "google_sheets" &&
    sourceNonEmpty &&
    !context.googleSheetsKeyColumn
  ) {
    const firstHeader = startLog?.outputData?.headers?.[0] ?? null;
    if (typeof firstHeader === "string" && firstHeader.trim()) {
      context.googleSheetsKeyColumn = firstHeader;
    }
  }

  const result = {
    ok: overallOk,
    workflowId: workflow.id,
    workflowName: plan.workflowName,
    workflowUrl: `${FE_BASE_URL}/workflows/${workflow.id}`,
    executionId,
    state: detail.state,
    errorMessage: detail.errorMessage ?? null,
    sourceSummary,
    requiresNonEmpty,
    sourceNonEmpty,
    sinkExecuted,
    sinkOk,
    eventSeed,
    startNodeId: completedStartNode?.id ?? null,
    endNodeId: completedEndNode?.id ?? null,
    startLog,
    endLog,
  };

  saveJson(path.join(EXECUTION_OUTPUT_DIR, `${plan.slug}.json`), result);
  return result;
};

const main = async () => {
  ensureDirectory(OUTPUT_DIR);
  ensureDirectory(CLICK_OUTPUT_DIR);
  ensureDirectory(EXECUTION_OUTPUT_DIR);
  assertCoverageMatchesCurrentRollout();

  const catalog = await getSourceCatalog();
  const plans = await collectSourcePlans(catalog);
  const baseWorkflows = await loadBaseWorkflows();
  const baseSheetsStartNode = findStartNode(baseWorkflows.SPREADSHEET_DATA);
  const browser = await chromium.launch({
    headless: true,
    executablePath: "C:/Program Files/Google/Chrome/Application/chrome.exe",
  });
  const browserContext = await browser.newContext({
    viewport: { width: 1600, height: 1200 },
  });
  const page = await browserContext.newPage();

  page.setDefaultTimeout(15000);
  page.setDefaultNavigationTimeout(30000);
  await page.addInitScript(
    ({ token, authUser }) => {
      window.localStorage.setItem("accessToken", token);
      window.localStorage.setItem("refreshToken", token);
      window.localStorage.setItem("authUser", JSON.stringify(authUser));
    },
    { token: ACCESS_TOKEN, authUser: AUTH_USER },
  );

  const clickResults = [];
  const executionResults = [];
  const derivedContext = {
    gmailMessageId: null,
    gmailSender: null,
    googleSheetsKeyColumn: null,
    googleSheetsSource: baseSheetsStartNode
      ? {
          spreadsheetId: baseSheetsStartNode.config?.spreadsheet_id ?? null,
          sheetName: baseSheetsStartNode.config?.sheet_name ?? null,
          sheetId: baseSheetsStartNode.config?.sheet_id ?? null,
          targetLabel: baseSheetsStartNode.config?.target_label ?? null,
        }
      : null,
  };

  try {
    for (const plan of plans) {
      try {
        const result = await createSourceWorkflowByClick(page, plan);
        clickResults.push(result);
      } catch (error) {
        clickResults.push({
          ok: false,
          slug: plan.slug,
          key: plan.key,
          workflowId: null,
          workflowName: plan.workflowName,
          message: error instanceof Error ? error.message : String(error),
        });
      }
    }

    saveJson(path.join(OUTPUT_DIR, "click-summary.json"), {
      ok: clickResults.every((result) => result.ok),
      total: clickResults.length,
      passed: clickResults.filter((result) => result.ok).length,
      failed: clickResults.filter((result) => !result.ok).length,
      results: clickResults,
    });

    for (const plan of plans) {
      const clickResult = clickResults.find((item) => item.key === plan.key);
      if (!clickResult?.ok || !clickResult.workflowId) {
        executionResults.push({
          ok: false,
          slug: plan.slug,
          key: plan.key,
          workflowId: clickResult?.workflowId ?? null,
          workflowName: plan.workflowName,
          message: "Click validation did not create a usable workflow.",
        });
        continue;
      }

      try {
        const result = await executeValidationWorkflow({
          plan,
          clickResult,
          baseWorkflows,
          context: derivedContext,
        });
        executionResults.push({
          slug: plan.slug,
          key: plan.key,
          ...result,
        });
      } catch (error) {
        executionResults.push({
          ok: false,
          slug: plan.slug,
          key: plan.key,
          workflowId: clickResult.workflowId,
          workflowName: plan.workflowName,
          message: error instanceof Error ? error.message : String(error),
        });
      }
    }

    const summary = {
      ok:
        clickResults.every((result) => result.ok) &&
        executionResults.every((result) => result.ok),
      executedAt: new Date().toISOString(),
      totals: {
        rolloutModes: plans.length,
        clickPassed: clickResults.filter((result) => result.ok).length,
        clickFailed: clickResults.filter((result) => !result.ok).length,
        executionPassed: executionResults.filter((result) => result.ok).length,
        executionFailed: executionResults.filter((result) => !result.ok).length,
      },
      derivedContext,
      clickResults,
      executionResults,
    };

    saveJson(path.join(OUTPUT_DIR, "summary.json"), summary);
    console.log(JSON.stringify(summary, null, 2));
    if (!summary.ok) {
      process.exitCode = 1;
    }
  } finally {
    await browser.close();
  }
};

main().catch((error) => {
  const failure = {
    ok: false,
    executedAt: new Date().toISOString(),
    message: error instanceof Error ? error.message : String(error),
    stack: error instanceof Error ? error.stack : null,
  };
  saveJson(path.join(OUTPUT_DIR, "failure.json"), failure);
  console.error(JSON.stringify(failure, null, 2));
  process.exitCode = 1;
});
