import { app, authentication, pages } from "@microsoft/teams-js";

const INITIALIZE_TIMEOUT = 3000;

/**
 * @return {Promise<boolean>} whether the page runs in Microsoft Teams
 */
export async function initializeTeams(teamsApp = app, timeout = INITIALIZE_TIMEOUT) {
  try {
    await Promise.race([
      teamsApp.initialize(),
      new Promise((_, reject) => setTimeout(() => reject(new Error("not in teams")), timeout)),
    ]);
    return true;
  } catch {
    return false;
  }
}

/**
 * The page is available with the session cookie, a redirect means the sign in is needed.
 */
export async function isSignedIn(target, fetchFunction = globalThis.fetch) {
  try {
    const response = await fetchFunction(target, { redirect: "manual", credentials: "include" });
    return response.type !== "opaqueredirect" && response.ok;
  } catch {
    return false;
  }
}

export async function initTab(
  body,
  { teamsApp = app, teamsAuthentication = authentication, fetchFunction, navigate } = {},
) {
  const { target, authEnd } = body.dataset;
  const goToTarget = navigate ?? ((url) => globalThis.location.replace(url));

  const inTeams = await initializeTeams(teamsApp);
  if (!inTeams || (await isSignedIn(target, fetchFunction))) {
    // outside of teams the normal sign in of the application takes place
    goToTarget(target);
    return;
  }

  body.querySelector("[data-teams-loading]").hidden = true;
  body.querySelector("[data-teams-login]").hidden = false;
  body.querySelector("[data-teams-login-button]").addEventListener("click", async () => {
    body.querySelector("[data-teams-error]").hidden = true;
    try {
      await teamsAuthentication.authenticate({
        url: new URL(authEnd, globalThis.location.href).href,
        width: 600,
        height: 640,
      });
      goToTarget(target);
    } catch {
      body.querySelector("[data-teams-error]").hidden = false;
    }
  });
}

export async function initAuthEnd(body, { teamsApp = app, teamsAuthentication = authentication, navigate } = {}) {
  if (await initializeTeams(teamsApp)) {
    teamsAuthentication.notifySuccess();
  } else {
    (navigate ?? ((url) => globalThis.location.replace(url)))(body.dataset.fallback);
  }
}

export async function initConfig(body, { teamsApp = app, teamsPages = pages } = {}) {
  if (!(await initializeTeams(teamsApp))) {
    return;
  }
  const contextPath = body.dataset.contextPath ?? "";
  const origin = globalThis.location.origin;
  const selected = () => body.querySelector("input[name=target]:checked");

  teamsPages.config.registerOnSaveHandler(async (saveEvent) => {
    const input = selected();
    const target = input.value;
    await teamsPages.config.setConfig({
      entityId: `uv-${target.replaceAll("/", "-")}`,
      contentUrl: `${origin}${contextPath}/fork/teams/tab?target=${encodeURIComponent(target)}`,
      websiteUrl: `${origin}${contextPath}${target}`,
      suggestedDisplayName: input.dataset.label,
    });
    saveEvent.notifySuccess();
  });

  for (const input of body.querySelectorAll("input[name=target]")) {
    input.addEventListener("change", () => teamsPages.config.setValidityState(true));
  }
  teamsPages.config.setValidityState(Boolean(selected()));
}
