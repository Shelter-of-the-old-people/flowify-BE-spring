import { createHmac } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";

const BASE_URL = "http://localhost:8080/api";
const OUTPUT_DIR =
  "C:/Users/김민호/CD2/flowify-BE-spring/.tmp-sink-e2e-user-values";

const AUTH_USER = {
  id: "6a0664640d4aaa140e72c8e4",
  email: "mhtiger362@gmail.com",
  name: "김민호",
  picture: null,
  createdAt: "2026-05-15T00:00:00Z",
};

const JWT_SECRET =
  "6ab652f5e569f39b9e44bef2d32cd4ada47cfb776090e4521ff2d5df13efced7";

const RECIPIENT_EMAIL = process.env.FLOWIFY_E2E_EMAIL?.trim() ?? "";
const DISCORD_WEBHOOK = process.env.FLOWIFY_E2E_DISCORD_WEBHOOK?.trim() ?? "";

if (!RECIPIENT_EMAIL) {
  throw new Error("FLOWIFY_E2E_EMAIL is required.");
}

if (!DISCORD_WEBHOOK) {
  throw new Error("FLOWIFY_E2E_DISCORD_WEBHOOK is required.");
}

mkdirSync(OUTPUT_DIR, { recursive: true });

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

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
    exp: now + 60 * 60,
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

const saveJson = (name, value) => {
  writeFileSync(
    `${OUTPUT_DIR}/${name}.json`,
    JSON.stringify(value, null, 2),
    "utf8",
  );
};

const api = async (path, init = {}) => {
  const response = await fetch(`${BASE_URL}${path}`, {
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
      `API ${init.method ?? "GET"} ${path} failed with ${response.status}: ${text}`,
    );
  }

  if (!parsed?.success) {
    throw new Error(
      `API ${init.method ?? "GET"} ${path} returned unsuccessful response: ${text}`,
    );
  }

  return parsed.data;
};

const findTemplate = (templates, predicate, label) => {
  const template = templates.find(predicate);
  if (!template) {
    throw new Error(`Template not found: ${label}`);
  }
  return template;
};

const findNode = (workflow, predicate, label) => {
  const node = workflow.nodes.find(predicate);
  if (!node) {
    throw new Error(`Node not found: ${label}`);
  }
  return node;
};

const updateNode = async (workflowId, node) =>
  api(`/workflows/${workflowId}/nodes/${node.id}`, {
    method: "PUT",
    body: JSON.stringify({
      category: node.category,
      type: node.type,
      label: node.label,
      config: node.config,
      position: node.position,
      dataType: node.dataType,
      outputDataType: node.outputDataType,
      role: node.role,
      authWarning: node.authWarning,
    }),
  });

const instantiateTemplate = async (templateId) =>
  api(`/templates/${templateId}/instantiate`, { method: "POST", body: "{}" });

const executeWorkflow = async (workflowId) =>
  api(`/workflows/${workflowId}/execute`, { method: "POST", body: "{}" });

const getExecutionDetail = async (workflowId, executionId) =>
  api(`/workflows/${workflowId}/executions/${executionId}`);

const getConnectedServices = async () => api("/oauth-tokens");
const getSinkTargetOptions = async (serviceKey, type, parentId) => {
  const params = new URLSearchParams({ type });
  if (parentId) {
    params.set("parentId", parentId);
  }
  return api(`/editor-catalog/sinks/${serviceKey}/target-options?${params.toString()}`);
};
const createGoogleSheetsSpreadsheet = async (name) =>
  api("/editor-catalog/google-sheets/spreadsheets", {
    method: "POST",
    body: JSON.stringify({ name }),
  });
const createGoogleSheet = async (spreadsheetId, sheetName) =>
  api("/editor-catalog/google-sheets/sheets", {
    method: "POST",
    body: JSON.stringify({ spreadsheetId, sheetName }),
  });

const pollExecutionDetail = async (workflowId, executionId, timeoutMs = 180000) => {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    const detail = await getExecutionDetail(workflowId, executionId);
    if (detail.state !== "running" && detail.state !== "pending") {
      return detail;
    }
    await wait(3000);
  }

  throw new Error(`Execution polling timed out for workflow ${workflowId}`);
};

const summarizeNodeLog = (log) => ({
  nodeId: log.nodeId,
  status: log.status,
  error: log.error ?? null,
  outputData: log.outputData ?? null,
});

const assertSuccessState = (detail, label) => {
  if (detail.state !== "success") {
    throw new Error(
      `${label} execution did not succeed. state=${detail.state}, error=${detail.errorMessage ?? "null"}`,
    );
  }
};

const assertGmailSinkResult = (detail, gmailNodeId) => {
  const sinkLog = detail.nodeLogs.find((log) => log.nodeId === gmailNodeId);
  if (!sinkLog) {
    throw new Error("Gmail sink log not found.");
  }
  if (sinkLog.status !== "success") {
    throw new Error(`Gmail sink did not succeed: ${sinkLog.status}`);
  }

  const output = sinkLog.outputData ?? {};
  const sinkDetail = output.detail ?? {};
  const messageId = sinkDetail.messageId ?? null;

  if (!messageId) {
    throw new Error("Gmail sink result does not contain messageId.");
  }

  const recipients = Array.isArray(sinkDetail.to) ? sinkDetail.to : [];
  if (!recipients.includes(RECIPIENT_EMAIL)) {
    throw new Error("Gmail sink recipient does not match the requested email.");
  }

  return {
    nodeId: sinkLog.nodeId,
    status: sinkLog.status,
    messageId,
    threadId: sinkDetail.threadId ?? null,
    subject: sinkDetail.subject ?? null,
    to: recipients,
  };
};

const assertDiscordSinkResult = (detail, discordNodeId) => {
  const sinkLog = detail.nodeLogs.find((log) => log.nodeId === discordNodeId);
  if (!sinkLog) {
    throw new Error("Discord sink log not found.");
  }
  if (sinkLog.status !== "success") {
    throw new Error(`Discord sink did not succeed: ${sinkLog.status}`);
  }

  const output = sinkLog.outputData ?? {};
  const sinkDetail = output.detail ?? {};
  const statusCode = sinkDetail.status_code ?? null;

  if (typeof statusCode !== "number" || statusCode < 200 || statusCode >= 300) {
    throw new Error(`Discord sink returned unexpected status code: ${statusCode}`);
  }

  return {
    nodeId: sinkLog.nodeId,
    status: sinkLog.status,
    statusCode,
  };
};

const assertServiceSinkSuccess = (detail, nodeId, expectedService) => {
  const sinkLog = detail.nodeLogs.find((log) => log.nodeId === nodeId);
  if (!sinkLog) {
    throw new Error(`${expectedService} sink log not found.`);
  }
  if (sinkLog.status !== "success") {
    throw new Error(`${expectedService} sink did not succeed: ${sinkLog.status}`);
  }

  const output = sinkLog.outputData ?? {};
  const service = output.service ?? null;
  if (service !== expectedService) {
    throw new Error(
      `${expectedService} sink service mismatch: expected ${expectedService}, got ${service}`,
    );
  }

  return {
    nodeId: sinkLog.nodeId,
    status: sinkLog.status,
    outputData: sinkLog.outputData ?? null,
  };
};

const buildTimestamp = () => new Date().toISOString().replace(/\.\d{3}Z$/, "Z");

const runNewsToGmail = async (templates) => {
  const template = findTemplate(
    templates,
    (item) =>
      Array.isArray(item.requiredServices) &&
      item.requiredServices.includes("naver_news") &&
      item.requiredServices.includes("gmail") &&
      item.nodes?.some((node) => node.type === "naver_news") &&
      item.nodes?.some((node) => node.type === "gmail"),
    "naver_news -> gmail",
  );

  let workflow = await instantiateTemplate(template.id);
  const sourceNode = findNode(workflow, (node) => node.type === "naver_news", "naver_news source");
  const gmailNode = findNode(workflow, (node) => node.type === "gmail", "gmail sink");
  const timestamp = buildTimestamp();
  const query = "AI";

  sourceNode.config = {
    ...sourceNode.config,
    isConfigured: true,
    service: "naver_news",
    source_mode: "article_search",
    target: query,
    target_label: query,
    maxResults: 5,
  };

  gmailNode.config = {
    ...gmailNode.config,
    isConfigured: true,
    service: "gmail",
    to: RECIPIENT_EMAIL,
    subject: `Flowify E2E Gmail ${timestamp}`,
    action: "send",
    text_delivery_mode: "body",
  };

  workflow = await updateNode(workflow.id, sourceNode);
  workflow = await updateNode(workflow.id, gmailNode);

  saveJson("news-gmail-workflow-configured", {
    workflowId: workflow.id,
    sourceNode,
    gmailNode,
  });

  const executionId = await executeWorkflow(workflow.id);
  const detail = await pollExecutionDetail(workflow.id, executionId);
  saveJson("news-gmail-execution-detail", detail);
  assertSuccessState(detail, "news-gmail");

  return {
    workflowId: workflow.id,
    executionId,
    source: {
      nodeId: sourceNode.id,
      query,
    },
    sink: assertGmailSinkResult(detail, gmailNode.id),
    nodeLogs: detail.nodeLogs.map(summarizeNodeLog),
  };
};

const pickSeBoardCategory = async () => {
  const response = await api(
    "/editor-catalog/sources/web_news/target-options?mode=seboard_new_posts",
  );
  const items = Array.isArray(response.items) ? response.items : [];

  if (items.length === 0) {
    throw new Error("SE Board category options are empty.");
  }

  return items[0];
};

const runNewsToDiscord = async (templates) => {
  const template = findTemplate(
    templates,
    (item) =>
      Array.isArray(item.requiredServices) &&
      item.requiredServices.includes("naver_news") &&
      item.requiredServices.includes("gmail") &&
      item.nodes?.some((node) => node.type === "naver_news") &&
      item.nodes?.some((node) => node.type === "gmail"),
    "naver_news -> gmail template for discord conversion",
  );

  let workflow = await instantiateTemplate(template.id);
  const sourceNode = findNode(workflow, (node) => node.type === "naver_news", "naver_news source");
  const discordNode = findNode(workflow, (node) => node.type === "gmail", "gmail sink to convert");
  const timestamp = buildTimestamp();
  const query = "AI";

  sourceNode.config = {
    ...sourceNode.config,
    isConfigured: true,
    service: "naver_news",
    source_mode: "article_search",
    target: query,
    target_label: query,
    maxResults: 5,
  };

  discordNode.type = "discord";
  discordNode.category = "service";
  discordNode.role = "end";
  discordNode.dataType = "TEXT";
  discordNode.outputDataType = null;
  discordNode.config = {
    isConfigured: true,
    service: "discord",
    webhook_url: DISCORD_WEBHOOK,
    username: "Flowify",
    message_template: `Flowify E2E Discord ${timestamp}\n\n{{content}}`,
  };

  workflow = await updateNode(workflow.id, sourceNode);
  workflow = await updateNode(workflow.id, discordNode);

  saveJson("news-discord-workflow-configured", {
    workflowId: workflow.id,
    sourceNode,
    discordNode,
    query,
  });

  const executionId = await executeWorkflow(workflow.id);
  const detail = await pollExecutionDetail(workflow.id, executionId);
  saveJson("news-discord-execution-detail", detail);
  assertSuccessState(detail, "news-discord");

  return {
    workflowId: workflow.id,
    executionId,
    source: {
      nodeId: sourceNode.id,
      query,
    },
    sink: assertDiscordSinkResult(detail, discordNode.id),
    nodeLogs: detail.nodeLogs.map(summarizeNodeLog),
  };
};

const runNewsToNotion = async (templates) => {
  const template = findTemplate(
    templates,
    (item) =>
      Array.isArray(item.requiredServices) &&
      item.requiredServices.includes("naver_news") &&
      item.requiredServices.includes("notion") &&
      item.nodes?.some((node) => node.type === "naver_news") &&
      item.nodes?.some((node) => node.type === "notion"),
    "naver_news -> notion",
  );

  const notionTargets = await getSinkTargetOptions("notion", "page");
  const notionPage = Array.isArray(notionTargets.items) ? notionTargets.items[0] : null;
  if (!notionPage) {
    throw new Error("No Notion page target is available.");
  }

  let workflow = await instantiateTemplate(template.id);
  const sourceNode = findNode(workflow, (node) => node.type === "naver_news", "naver_news source");
  const notionNode = findNode(workflow, (node) => node.type === "notion", "notion sink");
  const query = "AI";

  sourceNode.config = {
    ...sourceNode.config,
    isConfigured: true,
    service: "naver_news",
    source_mode: "article_search",
    target: query,
    target_label: query,
    maxResults: 5,
  };

  notionNode.config = {
    ...notionNode.config,
    isConfigured: true,
    service: "notion",
    target_type: "page",
    target_id: notionPage.id,
    title_template: `Flowify E2E Notion - {{date}}`,
  };

  workflow = await updateNode(workflow.id, sourceNode);
  workflow = await updateNode(workflow.id, notionNode);

  saveJson("news-notion-workflow-configured", {
    workflowId: workflow.id,
    sourceNode,
    notionNode,
    notionPage,
  });

  const executionId = await executeWorkflow(workflow.id);
  const detail = await pollExecutionDetail(workflow.id, executionId);
  saveJson("news-notion-execution-detail", detail);
  assertSuccessState(detail, "news-notion");

  return {
    workflowId: workflow.id,
    executionId,
    source: {
      nodeId: sourceNode.id,
      query,
    },
    sink: assertServiceSinkSuccess(detail, notionNode.id, "notion"),
    nodeLogs: detail.nodeLogs.map(summarizeNodeLog),
  };
};

const runNewsToSheets = async (templates) => {
  const template = findTemplate(
    templates,
    (item) =>
      Array.isArray(item.requiredServices) &&
      item.requiredServices.includes("naver_news") &&
      item.requiredServices.includes("google_sheets") &&
      item.nodes?.some((node) => node.type === "naver_news") &&
      item.nodes?.some((node) => node.type === "google_sheets"),
    "naver_news -> google_sheets",
  );

  const spreadsheet = await createGoogleSheetsSpreadsheet(
    `Flowify E2E Sheet ${buildTimestamp()}`,
  );
  const spreadsheetId =
    spreadsheet?.metadata?.spreadsheetId ?? spreadsheet?.id ?? null;
  if (!spreadsheetId) {
    throw new Error("Google Sheets spreadsheet id is missing.");
  }

  const ensuredSheet = await createGoogleSheet(spreadsheetId, "Sheet1");
  const sheetName =
    ensuredSheet?.metadata?.sheetName ?? "Sheet1";

  let workflow = await instantiateTemplate(template.id);
  const sourceNode = findNode(workflow, (node) => node.type === "naver_news", "naver_news source");
  const sheetsNode = findNode(
    workflow,
    (node) => node.type === "google_sheets",
    "google_sheets sink",
  );
  const query = "AI";

  sourceNode.config = {
    ...sourceNode.config,
    isConfigured: true,
    service: "naver_news",
    source_mode: "article_search",
    target: query,
    target_label: query,
    maxResults: 5,
  };

  sheetsNode.config = {
    ...sheetsNode.config,
    isConfigured: true,
    service: "google_sheets",
    spreadsheet_id: spreadsheetId,
    sheet_name: sheetName,
    write_mode: "append_rows",
  };

  workflow = await updateNode(workflow.id, sourceNode);
  workflow = await updateNode(workflow.id, sheetsNode);

  saveJson("news-sheets-workflow-configured", {
    workflowId: workflow.id,
    sourceNode,
    sheetsNode,
    spreadsheet,
    ensuredSheet,
  });

  const executionId = await executeWorkflow(workflow.id);
  const detail = await pollExecutionDetail(workflow.id, executionId);
  saveJson("news-sheets-execution-detail", detail);
  assertSuccessState(detail, "news-sheets");

  return {
    workflowId: workflow.id,
    executionId,
    source: {
      nodeId: sourceNode.id,
      query,
    },
    sink: assertServiceSinkSuccess(detail, sheetsNode.id, "google_sheets"),
    nodeLogs: detail.nodeLogs.map(summarizeNodeLog),
  };
};

const main = async () => {
  const templates = await api("/templates");
  saveJson("template-snapshot", templates);
  const connectedServices = await getConnectedServices();
  saveJson("connected-services", connectedServices);

  const results = {
    ok: true,
    executedAt: new Date().toISOString(),
    recipientEmail: RECIPIENT_EMAIL,
    connectedServices,
    runs: {},
  };

  try {
    results.runs.newsToGmail = await runNewsToGmail(templates);
  } catch (error) {
    results.ok = false;
    results.runs.newsToGmail = {
      ok: false,
      message: error instanceof Error ? error.message : String(error),
    };
  }

  try {
    results.runs.newsToDiscord = await runNewsToDiscord(templates);
  } catch (error) {
    results.ok = false;
    results.runs.newsToDiscord = {
      ok: false,
      message: error instanceof Error ? error.message : String(error),
    };
  }

  try {
    results.runs.newsToNotion = await runNewsToNotion(templates);
  } catch (error) {
    results.ok = false;
    results.runs.newsToNotion = {
      ok: false,
      message: error instanceof Error ? error.message : String(error),
    };
  }

  try {
    results.runs.newsToSheets = await runNewsToSheets(templates);
  } catch (error) {
    results.ok = false;
    results.runs.newsToSheets = {
      ok: false,
      message: error instanceof Error ? error.message : String(error),
    };
  }

  saveJson("summary", results);
  console.log(JSON.stringify(results, null, 2));
};

main().catch((error) => {
  const failure = {
    ok: false,
    executedAt: new Date().toISOString(),
    message: error instanceof Error ? error.message : String(error),
    stack: error instanceof Error ? error.stack : null,
  };
  saveJson("failure", failure);
  console.error(JSON.stringify(failure, null, 2));
  process.exitCode = 1;
});
