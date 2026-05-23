import { createHmac } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";

const BASE_URL = "http://localhost:8080/api";
const OUTPUT_DIR =
  "C:/Users/김민호/CD2/flowify-BE-spring/.tmp-click-workflow-executions";

const AUTH_USER = {
  id: "6a0664640d4aaa140e72c8e4",
  email: "mhtiger362@gmail.com",
  name: "김민호",
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

const WORKFLOW_NAMES = [
  "드라이브-Gmail-검증",
  "드라이브-Notion-검증",
  "드라이브-시트-검증",
  "깃허브-Gmail-검증",
  "깃허브-Notion-검증",
  "깃허브-Discord-검증",
  "깃허브-시트-검증",
  "Gmail-Notion-검증",
  "Gmail-시트-검증",
  "구글시트-중간처리-검증",
  "뉴스-Notion-검증",
  "뉴스-시트-검증",
  "SEBoard-Notion-검증",
  "SEBoard-레거시-Notion-검증",
  "Gmail-할일-Notion-검증",
  "캔버스-Notion-검증",
  "캔버스-v2-Notion-검증",
];

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const saveJson = (name, value) => {
  writeFileSync(`${OUTPUT_DIR}/${name}.json`, JSON.stringify(value, null, 2), "utf8");
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

const getWorkflowList = async () => {
  const workflows = [];

  for (let page = 0; page < 5; page += 1) {
    const data = await api(`/workflows?page=${page}&size=100`);
    workflows.push(...data.content);
    if (data.last) {
      break;
    }
  }

  return workflows;
};

const getWorkflow = async (workflowId) => api(`/workflows/${workflowId}`);

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

const buildTimestamp = () => new Date().toISOString().replace(/\.\d{3}Z$/, "Z");

const findLatestWorkflowIdsByName = (workflows) => {
  const latest = new Map();
  for (const workflow of workflows) {
    if (!WORKFLOW_NAMES.includes(workflow.name)) {
      continue;
    }
    const existing = latest.get(workflow.name);
    if (!existing || workflow.id > existing.id) {
      latest.set(workflow.name, workflow);
    }
  }
  return latest;
};

const findEndNode = (workflow) => workflow.nodes.find((node) => node.role === "end") ?? null;

const configureNotionSink = async (workflow, sinkNode) => {
  const notionTargets = await getSinkTargetOptions("notion", "page");
  const notionPage = Array.isArray(notionTargets.items) ? notionTargets.items[0] : null;
  if (!notionPage) {
    throw new Error("No Notion page target is available.");
  }

  sinkNode.config = {
    ...sinkNode.config,
    isConfigured: true,
    service: "notion",
    target_type: "page",
    target_id: notionPage.id,
    title_template: `${workflow.name} {{date}}`,
  };

  return { sinkNode, target: notionPage };
};

const configureGmailSink = async (workflow, sinkNode) => {
  sinkNode.config = {
    ...sinkNode.config,
    isConfigured: true,
    service: "gmail",
    to: RECIPIENT_EMAIL,
    subject: `${workflow.name} ${buildTimestamp()}`,
    action: "send",
    text_delivery_mode: "body",
  };

  return { sinkNode };
};

const configureDiscordSink = async (workflow, sinkNode) => {
  sinkNode.config = {
    ...sinkNode.config,
    isConfigured: true,
    service: "discord",
    webhook_url: DISCORD_WEBHOOK,
    username: "Flowify",
    message_template: `${workflow.name}\n\n{{content}}`,
  };

  return { sinkNode };
};

const configureSheetsSink = async (workflow, sinkNode) => {
  const spreadsheet = await createGoogleSheetsSpreadsheet(
    `${workflow.name} ${buildTimestamp()}`,
  );
  const spreadsheetId =
    spreadsheet?.metadata?.spreadsheetId ?? spreadsheet?.id ?? null;
  if (!spreadsheetId) {
    throw new Error("Google Sheets spreadsheet id is missing.");
  }
  const ensuredSheet = await createGoogleSheet(spreadsheetId, "Sheet1");
  const sheetName = ensuredSheet?.metadata?.sheetName ?? "Sheet1";

  sinkNode.config = {
    ...sinkNode.config,
    isConfigured: true,
    service: "google_sheets",
    spreadsheet_id: spreadsheetId,
    sheet_name: sheetName,
    write_mode: "append_rows",
  };

  return {
    sinkNode,
    spreadsheet: {
      spreadsheetId,
      sheetName,
      createdSpreadsheet: spreadsheet,
      createdSheet: ensuredSheet,
    },
  };
};

const configureSinkNode = async (workflow) => {
  const sinkNode = findEndNode(workflow);
  if (!sinkNode) {
    throw new Error("End node not found.");
  }

  if (sinkNode.type === "notion") {
    return configureNotionSink(workflow, sinkNode);
  }
  if (sinkNode.type === "gmail") {
    return configureGmailSink(workflow, sinkNode);
  }
  if (sinkNode.type === "discord") {
    return configureDiscordSink(workflow, sinkNode);
  }
  if (sinkNode.type === "google_sheets") {
    return configureSheetsSink(workflow, sinkNode);
  }

  throw new Error(`Unsupported sink type for execution: ${sinkNode.type}`);
};

const runWorkflowExecution = async (workflow) => {
  const configured = await configureSinkNode(workflow);
  const updatedWorkflow = await updateNode(workflow.id, configured.sinkNode);
  const executionId = await executeWorkflow(workflow.id);
  const detail = await pollExecutionDetail(workflow.id, executionId);

  return {
    workflowId: workflow.id,
    workflowName: workflow.name,
    sinkType: configured.sinkNode.type,
    startType: workflow.nodes.find((node) => node.role === "start")?.type ?? null,
    startMode:
      workflow.nodes.find((node) => node.role === "start")?.config?.source_mode ?? null,
    executionId,
    state: detail.state,
    errorMessage: detail.errorMessage ?? null,
    nodeLogs: detail.nodeLogs.map(summarizeNodeLog),
    sinkConfigDetail:
      configured.target ?? configured.spreadsheet ?? null,
  };
};

const main = async () => {
  const workflows = await getWorkflowList();
  const connectedServices = await getConnectedServices();
  const latestByName = findLatestWorkflowIdsByName(workflows);

  const summary = {
    ok: true,
    executedAt: new Date().toISOString(),
    recipientEmail: RECIPIENT_EMAIL,
    connectedServices,
    totalTargets: WORKFLOW_NAMES.length,
    targets: {},
  };

  for (const workflowName of WORKFLOW_NAMES) {
    const workflowListItem = latestByName.get(workflowName);

    if (!workflowListItem) {
      summary.ok = false;
      summary.targets[workflowName] = {
        ok: false,
        message: "Workflow not found.",
      };
      continue;
    }

    try {
      const workflow = await getWorkflow(workflowListItem.id);
      const result = await runWorkflowExecution(workflow);
      const ok = result.state === "success";
      if (!ok) {
        summary.ok = false;
      }
      summary.targets[workflowName] = {
        ok,
        ...result,
      };
      saveJson(`execution-${workflow.id}`, summary.targets[workflowName]);
    } catch (error) {
      summary.ok = false;
      summary.targets[workflowName] = {
        ok: false,
        workflowId: workflowListItem.id,
        workflowName,
        message: error instanceof Error ? error.message : String(error),
      };
      saveJson(`execution-${workflowListItem.id}`, summary.targets[workflowName]);
    }
  }

  saveJson("summary", summary);
  console.log(JSON.stringify(summary, null, 2));
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
