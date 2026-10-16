import { getJSON } from "../fetch";

/**
 * Shows the presences (office, home office, mobile) in the calendar of the person overview and offers to plan them
 * for a selected period. The upstream calendar is not changed: the holiday service is wrapped and the rendered days
 * are decorated.
 */
export const PRESENCE_COLORS = {
  OFFICE: "var(--color-sky-500, #0ea5e9)",
  HOME_OFFICE: "var(--color-emerald-500, #10b981)",
  MOBILE_WORK: "var(--color-violet-500, #8b5cf6)",
};

const STYLE_ID = "fork-presence-calendar-style";
const STYLE = `
.datepicker-day[data-fork-presence]::before {
  content: "";
  position: absolute;
  top: 0.2rem;
  left: 0.3rem;
  right: 0.3rem;
  height: 0.3rem;
  border-radius: 9999px;
  background: linear-gradient(to right, var(--fork-presence-morning, transparent) 50%, var(--fork-presence-noon, transparent) 50%);
}
.datepicker-day[data-fork-presence="pattern"]::before {
  opacity: 0.5;
}
.fork-presence-menu {
  position: fixed;
  z-index: 50;
  min-width: 14rem;
}
`;

export function createPresenceCache(apiPrefix, personId, fetchJson = getJSON) {
  const presences = {};
  const loadedYears = new Set();

  return {
    async fetch(year) {
      if (loadedYears.has(year)) {
        return;
      }
      loadedYears.add(year);
      try {
        const data = await fetchJson(`${apiPrefix}/persons/${personId}/presences?from=${year}-01-01&to=${year}-12-31`);
        for (const presence of data) {
          presences[presence.date] = presence;
        }
      } catch {
        // no permission or not available: the calendar works without presences
      }
    },
    get(isoDate) {
      return presences[isoDate];
    },
  };
}

export function decorateDay(element, presence) {
  if (!presence) {
    delete element.dataset.forkPresence;
    element.style.removeProperty("--fork-presence-morning");
    element.style.removeProperty("--fork-presence-noon");
    return;
  }
  element.dataset.forkPresence = presence.explicit ? "planned" : "pattern";
  element.style.setProperty("--fork-presence-morning", PRESENCE_COLORS[presence.morning] ?? "transparent");
  element.style.setProperty("--fork-presence-noon", PRESENCE_COLORS[presence.noon] ?? "transparent");
}

function decorateAll(root, cache) {
  for (const element of root.querySelectorAll(".datepicker-day[data-datepicker-date]")) {
    decorateDay(element, cache.get(element.dataset.datepickerDate));
  }
}

function formatIso(date) {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function openMenu(form, position, from, to, bookHoliday) {
  document.querySelector(".fork-presence-menu")?.remove();

  const labels = form.dataset;
  const menu = document.createElement("div");
  menu.className =
    "fork-presence-menu rounded-lg border border-zinc-200 bg-white p-2 shadow-lg dark:border-zinc-700 dark:bg-zinc-900";
  menu.setAttribute("role", "menu");

  const addItem = (action, label, color, onClick) => {
    const button = document.createElement("button");
    button.type = "button";
    button.dataset.forkPresenceAction = action;
    button.setAttribute("role", "menuitem");
    button.className =
      "flex w-full items-center gap-2 rounded px-3 py-2 text-left text-sm hover:bg-zinc-100 dark:hover:bg-zinc-800";
    if (color) {
      const swatch = document.createElement("span");
      swatch.className = "inline-block h-3 w-3 rounded-sm";
      swatch.style.background = color;
      button.append(swatch);
    }
    button.append(document.createTextNode(label));
    button.addEventListener("click", () => {
      menu.remove();
      onClick();
    });
    menu.append(button);
  };

  const submit = (type) => {
    form.elements.from.value = formatIso(from);
    form.elements.to.value = formatIso(to ?? from);
    form.elements.type.value = type;
    form.submit();
  };

  if (bookHoliday) {
    addItem("absence", labels.labelAbsence, undefined, bookHoliday);
  }
  addItem("OFFICE", labels.labelOffice, PRESENCE_COLORS.OFFICE, () => submit("OFFICE"));
  addItem("HOME_OFFICE", labels.labelHomeOffice, PRESENCE_COLORS.HOME_OFFICE, () => submit("HOME_OFFICE"));
  addItem("MOBILE_WORK", labels.labelMobile, PRESENCE_COLORS.MOBILE_WORK, () => submit("MOBILE_WORK"));
  addItem("remove", labels.labelRemove, undefined, () => submit(""));

  document.body.append(menu);
  const { innerWidth, innerHeight } = globalThis;
  const rect = menu.getBoundingClientRect();
  menu.style.left = `${Math.max(8, Math.min(position.x, innerWidth - rect.width - 8))}px`;
  menu.style.top = `${Math.max(8, Math.min(position.y, innerHeight - rect.height - 8))}px`;
  menu.querySelector("button")?.focus();

  const close = (event) => {
    if (event.type === "keydown" && event.key !== "Escape") {
      return;
    }
    if (event.type === "mousedown" && menu.contains(event.target)) {
      return;
    }
    menu.remove();
    document.removeEventListener("mousedown", close, true);
    document.removeEventListener("keydown", close, true);
  };
  setTimeout(() => {
    document.addEventListener("mousedown", close, true);
    document.addEventListener("keydown", close, true);
  });
}

/**
 * @param holidayService the holiday service of the calendar
 * @param {{apiPrefix: string, personId: number, root: Element, form: HTMLFormElement | null, canApplyForLeave: boolean}} options
 * @return the wrapped holiday service to pass to the calendar
 */
export function withPresences(holidayService, { apiPrefix, personId, root, form, canApplyForLeave }) {
  const cache = createPresenceCache(apiPrefix, personId);

  if (!document.querySelector(`#${STYLE_ID}`)) {
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = STYLE;
    document.head.append(style);
  }

  const position = { x: 0, y: 0 };
  document.addEventListener("mouseup", (event) => {
    position.x = event.clientX;
    position.y = event.clientY;
  });

  new MutationObserver(() => decorateAll(root, cache)).observe(root, { childList: true, subtree: true });

  return new Proxy(holidayService, {
    get(target, property) {
      if (property === "fetchAbsences") {
        return (year) =>
          Promise.all([target.fetchAbsences(year), cache.fetch(year)]).then(([absences]) => {
            decorateAll(root, cache);
            return absences;
          });
      }
      if (property === "bookHoliday" && form) {
        return (from, to) =>
          openMenu(form, position, from, to, canApplyForLeave ? () => target.bookHoliday(from, to) : undefined);
      }
      const value = target[property];
      return typeof value === "function" ? value.bind(target) : value;
    },
  });
}
