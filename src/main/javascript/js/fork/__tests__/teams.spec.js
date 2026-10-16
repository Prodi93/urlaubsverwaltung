import { initAuthEnd, initConfig, initTab, initializeTeams, isSignedIn, signInWithTeamsSso } from "../teams";

describe("teams", function () {
  afterEach(function () {
    document.body.innerHTML = "";
    delete document.body.dataset.target;
    delete document.body.dataset.authEnd;
  });

  const inTeams = () => ({ initialize: vi.fn(() => Promise.resolve()) });
  const outsideOfTeams = () => ({ initialize: vi.fn(() => new Promise(() => {})) });

  function renderTab() {
    document.body.dataset.target = "/web/presence";
    document.body.dataset.authEnd = "/fork/teams/auth-end";
    document.body.innerHTML = `
      <p data-teams-loading></p>
      <div data-teams-login hidden><p data-teams-unknown-person hidden></p><button data-teams-login-button></button><p data-teams-error hidden></p></div>`;
    return document.body;
  }

  it("detects whether it runs in teams", async function () {
    expect(await initializeTeams(inTeams())).toBe(true);
    expect(await initializeTeams(outsideOfTeams(), 10)).toBe(false);
  });

  it("is signed in without redirect", async function () {
    expect(await isSignedIn("/web", () => Promise.resolve({ type: "basic", ok: true }))).toBe(true);
    expect(await isSignedIn("/web", () => Promise.resolve({ type: "opaqueredirect", ok: false }))).toBe(false);
    expect(await isSignedIn("/web", () => Promise.reject(new Error("offline")))).toBe(false);
  });

  it("opens the target directly if signed in", async function () {
    const navigate = vi.fn();
    await initTab(renderTab(), {
      teamsApp: inTeams(),
      fetchFunction: () => Promise.resolve({ type: "basic", ok: true }),
      navigate,
    });
    expect(navigate).toHaveBeenCalledWith("/web/presence");
  });

  it("signs in with the teams popup and opens the target", async function () {
    const navigate = vi.fn();
    const teamsAuthentication = { authenticate: vi.fn(() => Promise.resolve("ok")) };
    const body = renderTab();

    await initTab(body, {
      teamsApp: inTeams(),
      teamsAuthentication,
      fetchFunction: () => Promise.resolve({ type: "opaqueredirect", ok: false }),
      navigate,
    });

    expect(navigate).not.toHaveBeenCalled();
    expect(body.querySelector("[data-teams-login]").hidden).toBe(false);

    body.querySelector("[data-teams-login-button]").click();
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith("/web/presence"));
    expect(teamsAuthentication.authenticate.mock.calls[0][0].url).toContain("/fork/teams/auth-end");
  });

  it("signs in silently with teams sso", async function () {
    const navigate = vi.fn();
    const body = renderTab();
    body.dataset.ssoUrl = "/fork/teams/sso";
    const fetchFunction = vi.fn((url) =>
      Promise.resolve(url === "/fork/teams/sso" ? { ok: true } : { type: "opaqueredirect", ok: false }),
    );
    const teamsAuthentication = { getAuthToken: vi.fn(() => Promise.resolve("token")) };

    await initTab(body, { teamsApp: inTeams(), teamsAuthentication, fetchFunction, navigate });

    expect(navigate).toHaveBeenCalledWith("/web/presence");
    expect(fetchFunction).toHaveBeenCalledWith(
      "/fork/teams/sso",
      expect.objectContaining({ method: "POST", headers: { Authorization: "Bearer token" } }),
    );
    delete body.dataset.ssoUrl;
  });

  it("shows the sign in with a hint if the person is unknown", async function () {
    const navigate = vi.fn();
    const body = renderTab();
    body.dataset.ssoUrl = "/fork/teams/sso";
    const fetchFunction = (url) =>
      Promise.resolve(
        url === "/fork/teams/sso"
          ? { ok: false, json: () => Promise.resolve({ error: "unknown_person" }) }
          : { type: "opaqueredirect", ok: false },
      );
    const teamsAuthentication = { getAuthToken: () => Promise.resolve("token") };

    await initTab(body, { teamsApp: inTeams(), teamsAuthentication, fetchFunction, navigate });

    expect(navigate).not.toHaveBeenCalled();
    expect(body.querySelector("[data-teams-login]").hidden).toBe(false);
    expect(body.querySelector("[data-teams-unknown-person]").hidden).toBe(false);
    delete body.dataset.ssoUrl;
  });

  it("falls back to the popup if teams sso fails", async function () {
    const teamsAuthentication = { getAuthToken: () => Promise.reject(new Error("consent required")) };
    expect(await signInWithTeamsSso("/sso", teamsAuthentication)).toBe("failed");
  });

  it("notifies teams at the end of the sign in", async function () {
    const teamsAuthentication = { notifySuccess: vi.fn() };
    await initAuthEnd(document.body, { teamsApp: inTeams(), teamsAuthentication });
    expect(teamsAuthentication.notifySuccess).toHaveBeenCalled();
  });

  it("configures a channel tab with the selected page", async function () {
    document.body.dataset.contextPath = "";
    document.body.innerHTML = `
      <input type="radio" name="target" value="/web/presence" data-label="Teamkalender" checked />
      <input type="radio" name="target" value="/web/overview" data-label="Übersicht" />`;
    let saveHandler;
    const teamsPages = {
      config: {
        registerOnSaveHandler: vi.fn((handler) => (saveHandler = handler)),
        setConfig: vi.fn(() => Promise.resolve()),
        setValidityState: vi.fn(),
      },
    };

    await initConfig(document.body, { teamsApp: inTeams(), teamsPages });
    expect(teamsPages.config.setValidityState).toHaveBeenCalledWith(true);

    const saveEvent = { notifySuccess: vi.fn() };
    await saveHandler(saveEvent);

    const config = teamsPages.config.setConfig.mock.calls[0][0];
    expect(config.contentUrl).toContain("/fork/teams/tab?target=%2Fweb%2Fpresence");
    expect(config.suggestedDisplayName).toBe("Teamkalender");
    expect(saveEvent.notifySuccess).toHaveBeenCalled();
    delete document.body.dataset.contextPath;
  });
});
