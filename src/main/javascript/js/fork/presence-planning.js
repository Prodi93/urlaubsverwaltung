/**
 * Month planning: shows the selects for morning and noon when "separate halves" is chosen.
 */
export function initPresencePlanning(root = document) {
  for (const select of root.querySelectorAll("[data-presence-full]")) {
    const halves = select.closest("[data-presence-day]")?.querySelector("[data-presence-halves]");
    if (!halves) {
      continue;
    }
    const update = () => halves.classList.toggle("hidden", select.value !== "SPLIT");
    select.addEventListener("change", update);
    update();
  }
}
