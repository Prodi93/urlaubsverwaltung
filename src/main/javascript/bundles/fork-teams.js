import { initAuthEnd, initConfig, initTab } from "../js/fork/teams";

const pagesByName = { tab: initTab, "auth-end": initAuthEnd, config: initConfig };

document.addEventListener("DOMContentLoaded", () => {
  const init = pagesByName[document.body.dataset.teamsPage];
  init?.(document.body);
});
