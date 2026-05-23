const normalizeText = (value) => value.replace(/\s+/g, " ").trim();

export const wait = (page, ms = 1200) => page.waitForTimeout(ms);

export const renameWorkflow = async (accessToken, workflowId, name) => {
  const response = await fetch(`http://localhost:8080/api/workflows/${workflowId}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ name }),
  });

  if (!response.ok) {
    const message = await response.text().catch(() => "");
    throw new Error(`workflow rename failed: ${response.status} ${message}`);
  }

  return response.json().catch(() => null);
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
      return;
    }
  }

  throw new Error("visible input not found");
};

export const getVisibleBodyText = async (page) => {
  const bodyText = await page.locator("body").innerText().catch(() => "");
  return normalizeText(bodyText);
};

export const assertChoiceExpectations = async (
  page,
  { visible = [], hidden = [] },
  contextLabel,
) => {
  const bodyText = await getVisibleBodyText(page);
  const missing = visible.filter((text) => !bodyText.includes(text));
  const unexpected = hidden.filter((text) => bodyText.includes(text));

  if (missing.length > 0 || unexpected.length > 0) {
    const parts = [`${contextLabel} expectation failed.`];

    if (missing.length > 0) {
      parts.push(`missing: ${missing.join(", ")}`);
    }

    if (unexpected.length > 0) {
      parts.push(`unexpected: ${unexpected.join(", ")}`);
    }

    throw new Error(parts.join(" "));
  }

  return {
    contextLabel,
    visible,
    hidden,
  };
};

const extractSectionText = (bodyText, start, end) => {
  const startIndex = bodyText.indexOf(start);

  if (startIndex < 0) {
    throw new Error(`section start not found: ${start}`);
  }

  const contentStartIndex = startIndex + start.length;
  const endIndex = end ? bodyText.indexOf(end, contentStartIndex) : bodyText.length;

  if (end && endIndex < 0) {
    throw new Error(`section end not found: ${end}`);
  }

  return endIndex >= 0
    ? bodyText.slice(startIndex, endIndex).trim()
    : bodyText.slice(startIndex).trim();
};

export const assertSectionTextExpectations = async (
  page,
  { start, end, visible = [], hidden = [] },
  contextLabel,
) => {
  const bodyText = await getVisibleBodyText(page);
  const sectionText = extractSectionText(bodyText, start, end);
  const missing = visible.filter((text) => !sectionText.includes(text));
  const unexpected = hidden.filter((text) => sectionText.includes(text));

  if (missing.length > 0 || unexpected.length > 0) {
    const parts = [`${contextLabel} expectation failed.`];

    if (missing.length > 0) {
      parts.push(`missing: ${missing.join(", ")}`);
    }

    if (unexpected.length > 0) {
      parts.push(`unexpected: ${unexpected.join(", ")}`);
    }

    throw new Error(parts.join(" "));
  }

  return {
    contextLabel,
    visible,
    hidden,
    start,
    end,
  };
};
