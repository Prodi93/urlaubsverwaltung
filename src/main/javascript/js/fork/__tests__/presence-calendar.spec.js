import { createPresenceCache, decorateDay, PRESENCE_COLORS, withPresences } from "../presence-calendar";

describe("presence-calendar", function () {
  afterEach(function () {
    document.body.innerHTML = "";
  });

  it("decorates a day with the colors of morning and noon", function () {
    const element = document.createElement("div");

    decorateDay(element, { morning: "OFFICE", noon: "HOME_OFFICE", explicit: true });

    expect(element.dataset.forkPresence).toBe("planned");
    expect(element.style.getPropertyValue("--fork-presence-morning")).toBe(PRESENCE_COLORS.OFFICE);
    expect(element.style.getPropertyValue("--fork-presence-noon")).toBe(PRESENCE_COLORS.HOME_OFFICE);

    decorateDay(element, { morning: "MOBILE_WORK", explicit: false });
    expect(element.dataset.forkPresence).toBe("pattern");
    expect(element.style.getPropertyValue("--fork-presence-noon")).toBe("transparent");

    decorateDay(element);
    expect(element.dataset.forkPresence).toBeUndefined();
  });

  it("fetches the presences of a year only once and ignores errors", async function () {
    const fetchJson = vi.fn(() =>
      Promise.resolve([{ date: "2026-10-12", morning: "OFFICE", noon: "OFFICE", explicit: true }]),
    );
    const cache = createPresenceCache("/api", 42, fetchJson);

    await cache.fetch(2026);
    await cache.fetch(2026);

    expect(fetchJson).toHaveBeenCalledTimes(1);
    expect(fetchJson).toHaveBeenCalledWith("/api/persons/42/presences?from=2026-01-01&to=2026-12-31");
    expect(cache.get("2026-10-12").morning).toBe("OFFICE");

    const failingCache = createPresenceCache("/api", 42, () => Promise.reject(new Error("403")));
    await expect(failingCache.fetch(2026)).resolves.toBeUndefined();
  });

  it("opens the presence menu instead of booking a holiday directly", function () {
    document.body.innerHTML = `
      <div id="datepicker"></div>
      <form id="form" data-label-absence="Abwesenheit" data-label-office="Büro" data-label-home-office="Homeoffice"
            data-label-mobile="Mobil" data-label-remove="Entfernen">
        <input name="from" /><input name="to" /><input name="type" />
      </form>`;
    const form = document.querySelector("#form");
    form.submit = vi.fn();
    const holidayService = { bookHoliday: vi.fn(), name: "service" };

    const service = withPresences(holidayService, {
      apiPrefix: "/api",
      personId: 42,
      root: document.querySelector("#datepicker"),
      form,
      canApplyForLeave: true,
    });

    expect(service.name).toBe("service");

    service.bookHoliday(new Date(2026, 9, 12), new Date(2026, 9, 14));
    const actions = [...document.querySelectorAll(".fork-presence-menu button")].map(
      (button) => button.dataset.forkPresenceAction,
    );
    expect(actions).toEqual(["absence", "OFFICE", "HOME_OFFICE", "MOBILE_WORK", "remove"]);

    document.querySelector("[data-fork-presence-action=OFFICE]").click();
    expect(form.elements.from.value).toBe("2026-10-12");
    expect(form.elements.to.value).toBe("2026-10-14");
    expect(form.elements.type.value).toBe("OFFICE");
    expect(form.submit).toHaveBeenCalled();
    expect(holidayService.bookHoliday).not.toHaveBeenCalled();

    service.bookHoliday(new Date(2026, 9, 12), new Date(2026, 9, 12));
    document.querySelector("[data-fork-presence-action=absence]").click();
    expect(holidayService.bookHoliday).toHaveBeenCalled();
  });

  it("books the holiday directly without presence form", function () {
    const holidayService = { bookHoliday: vi.fn() };
    const service = withPresences(holidayService, { apiPrefix: "/api", personId: 1, root: document.body });

    service.bookHoliday(new Date(2026, 9, 12));

    expect(holidayService.bookHoliday).toHaveBeenCalled();
  });
});
