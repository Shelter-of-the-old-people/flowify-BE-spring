import { createHmac } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const FE_BASE_URL = "http://localhost:5173";
export const API_BASE_URL = "http://localhost:8080/api";

export const AUTH_USER = {
  id: "6a0664640d4aaa140e72c8e4",
  email: "mhtiger362@gmail.com",
  name: "김민호",
  picture: null,
  createdAt: "2026-05-15T00:00:00Z",
};

export const JWT_SECRET =
  "6ab652f5e569f39b9e44bef2d32cd4ada47cfb776090e4521ff2d5df13efced7";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
export const REPO_ROOT = path.resolve(__dirname, "..");

export const wait = (page, ms = 1200) => page.waitForTimeout(ms);

export const ensureDirectory = (targetDir) => {
  mkdirSync(targetDir, { recursive: true });
  return targetDir;
};

export const saveJson = (targetPath, value) => {
  ensureDirectory(path.dirname(targetPath));
  writeFileSync(targetPath, JSON.stringify(value, null, 2), "utf8");
};

export const saveText = (targetPath, value) => {
  ensureDirectory(path.dirname(targetPath));
  writeFileSync(targetPath, value, "utf8");
};

const toBase64Url = (value) =>
  Buffer.from(JSON.stringify(value)).toString("base64url").replace(/=/g, "");

export const buildAccessToken = () => {
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

export const ACCESS_TOKEN = buildAccessToken();

export const api = async (pathName, init = {}) => {
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

export const getSourceCatalog = () => api("/editor-catalog/sources");

export const getWorkflow = (workflowId) => api(`/workflows/${workflowId}`);

export const createWorkflow = (body) =>
  api("/workflows", {
    method: "POST",
    body: JSON.stringify(body),
  });

export const updateWorkflow = (workflowId, body) =>
  api(`/workflows/${workflowId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });

export const renameWorkflow = async (workflowId, name) =>
  updateWorkflow(workflowId, { name });

export const executeWorkflow = (workflowId) =>
  api(`/workflows/${workflowId}/execute`, {
    method: "POST",
    body: "{}",
  });

export const getExecutionDetail = (workflowId, executionId) =>
  api(`/workflows/${workflowId}/executions/${executionId}`);

export const previewNode = (workflowId, nodeId, body = {}) =>
  api(`/workflows/${workflowId}/nodes/${nodeId}/preview`, {
    method: "POST",
    body: JSON.stringify(body),
  });

export const getSourceTargetOptions = (
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

export const getSinkTargetOptions = (serviceKey, type, parentId) => {
  const params = new URLSearchParams({ type });
  if (parentId) {
    params.set("parentId", parentId);
  }
  return api(`/editor-catalog/sinks/${serviceKey}/target-options?${params}`);
};

export const createGoogleSheetsSpreadsheet = (name) =>
  api("/editor-catalog/google-sheets/spreadsheets", {
    method: "POST",
    body: JSON.stringify({ name }),
  });

export const createGoogleSheet = (spreadsheetId, sheetName) =>
  api("/editor-catalog/google-sheets/sheets", {
    method: "POST",
    body: JSON.stringify({ spreadsheetId, sheetName }),
  });

export const waitForExecutionCompletion = async (
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

export const findStartNode = (workflow) =>
  workflow.nodes.find((node) => node.role === "start") ?? null;

export const findEndNode = (workflow) =>
  workflow.nodes.find((node) => node.role === "end") ?? null;

export const workflowIdFromUrl = (url) =>
  url.split("/workflows/")[1]?.split(/[?#]/)[0] ?? null;

export const normalizeText = (value) => value.replace(/\s+/g, " ").trim();

export const clickText = async (page, text, timeout = 15000) => {
  const locator = page.getByText(text, { exact: false }).last();
  await locator.waitFor({ state: "visible", timeout });
  await locator.click({ force: true });
};

export const fillFirstVisibleInput = async (page, value) => {
  const inputs = page.locator("input");
  const count = await inputs.count();

  for (let index = 0; index < count; index += 1) {
    const input = inputs.nth(index);
    if (await input.isVisible().catch(() => false)) {
      await input.fill(value);
      return input;
    }
  }

  throw new Error("visible input not found");
};

export const ensureManualCreationSelected = async (page) => {
  const placeholder = page.locator(".react-flow__node-placeholder").first();

  if (await placeholder.isVisible().catch(() => false)) {
    return false;
  }

  const manualButton = page.locator(".react-flow__node button").first();
  await manualButton.waitFor({ state: "visible", timeout: 15000 });
  await manualButton.click({ force: true });
  await placeholder.waitFor({ state: "visible", timeout: 15000 });
  await wait(page, 700);
  return true;
};

export const createBlankWorkflowThroughUi = async (page) => {
  await page.goto(`${FE_BASE_URL}/workflows`, {
    waitUntil: "domcontentloaded",
    timeout: 30000,
  });
  await wait(page, 2200);
  await page
    .getByRole("button", { name: /만들기/i })
    .first()
    .click({ force: true });
  await page.waitForURL(/\/workflows\/[a-f0-9]+/, { timeout: 30000 });
  await wait(page, 2200);
  return workflowIdFromUrl(page.url());
};

export const clickFirstPlaceholder = async (page) => {
  await page.locator(".react-flow__node-placeholder").first().click({
    force: true,
  });
  await wait(page, 900);
};

export const pickRemoteOptionByText = async (page, text, timeout = 15000) => {
  const locator = page.getByText(text, { exact: false }).first();
  await locator.waitFor({ state: "visible", timeout });
  await locator.click({ force: true });
  await wait(page, 600);
};

export const findSourceMode = (catalog, serviceKey, modeKey) => {
  const service = catalog.services.find((item) => item.key === serviceKey);
  if (!service) {
    throw new Error(`source service not found: ${serviceKey}`);
  }
  const mode = service.source_modes.find((item) => item.key === modeKey);
  if (!mode) {
    throw new Error(`source mode not found: ${serviceKey}.${modeKey}`);
  }
  return { service, mode };
};

export const getPrimaryModeLabel = (serviceKey, mode) => {
  if (serviceKey === "web_news" && mode.key === "seboard_new_posts") {
    return "SE Board 새글 가져오기";
  }
  if (serviceKey === "web_news" && mode.key === "seboard_posts") {
    return "SE Board 게시글 가져오기";
  }
  return mode.label;
};

export const getTargetOptionItemLabel = (option) =>
  typeof option?.metadata?.categoryName === "string" &&
  option.metadata.categoryName.trim()
    ? option.metadata.categoryName
    : option?.label ?? option?.id ?? "";

export const getTargetOptionDisplayLabel = (option) =>
  typeof option?.metadata?.displayPath === "string" &&
  option.metadata.displayPath.trim()
    ? option.metadata.displayPath
    : option?.label ?? option?.id ?? "";

export const isEffectivelyEmpty = (value) => {
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

export const summarizeStartOutput = (outputData) => {
  if (!outputData || typeof outputData !== "object") {
    return { empty: true, type: null };
  }

  const type = outputData.type ?? null;
  if (type === "SINGLE_FILE") {
    return {
      empty: isEffectivelyEmpty(outputData.file_id) && isEffectivelyEmpty(outputData.filename),
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
      empty: isEffectivelyEmpty(outputData.id) && isEffectivelyEmpty(outputData.subject),
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
      contentLength: typeof outputData.content === "string" ? outputData.content.length : 0,
    };
  }
  return {
    empty: isEffectivelyEmpty(outputData),
    type,
  };
};

export const getExecutionStartLog = (executionDetail, startNodeId) =>
  executionDetail.nodeLogs.find((log) => log.nodeId === startNodeId) ?? null;

export const getExecutionEndLog = (executionDetail, endNodeId) =>
  executionDetail.nodeLogs.find((log) => log.nodeId === endNodeId) ?? null;

export const buildNotionSinkConfig = async (workflowName) => {
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

export const deepClone = (value) => JSON.parse(JSON.stringify(value));
