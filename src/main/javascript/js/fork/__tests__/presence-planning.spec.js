import { initPresencePlanning } from "../presence-planning";

describe("presence-planning", function () {
  it("shows the halves only for separate halves", function () {
    document.body.innerHTML = `
      <ul><li data-presence-day>
        <select data-presence-full><option value="">Standard</option><option value="SPLIT">getrennt</option></select>
        <span data-presence-halves class="hidden"></span>
      </li></ul>`;
    initPresencePlanning(document);

    const select = document.querySelector("select");
    const halves = document.querySelector("[data-presence-halves]");
    expect(halves.classList.contains("hidden")).toBe(true);

    select.value = "SPLIT";
    select.dispatchEvent(new Event("change"));
    expect(halves.classList.contains("hidden")).toBe(false);
  });
});
