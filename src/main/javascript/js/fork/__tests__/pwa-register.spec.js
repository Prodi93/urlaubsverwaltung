import { getContextPath, registerServiceWorker } from "../pwa-register";

describe("pwa-register", function () {
  afterEach(function () {
    document.head.innerHTML = "";
  });

  function fakeNavigator() {
    return { serviceWorker: { register: vi.fn(() => Promise.resolve()) } };
  }

  function fakeWindow({ secure = true, readyState = "complete" } = {}) {
    return {
      isSecureContext: secure,
      document: { readyState, querySelector: (selector) => document.querySelector(selector) },
      addEventListener: vi.fn(),
    };
  }

  it("derives the context path from the turbo-root meta tag", function () {
    document.head.innerHTML = `<meta name="turbo-root" content="/urlaub/web" />`;
    expect(getContextPath(document)).toBe("/urlaub/");
  });

  it("uses the root context path without turbo-root meta tag", function () {
    expect(getContextPath(document)).toBe("/");
  });

  it("registers the service worker with the context path as scope", function () {
    document.head.innerHTML = `<meta name="turbo-root" content="/web" />`;
    const navigatorObject = fakeNavigator();

    registerServiceWorker(navigatorObject, fakeWindow());

    expect(navigatorObject.serviceWorker.register).toHaveBeenCalledWith("/sw.js", { scope: "/" });
  });

  it("registers the service worker after the load event if the document is still loading", function () {
    const navigatorObject = fakeNavigator();
    const windowObject = fakeWindow({ readyState: "loading" });

    registerServiceWorker(navigatorObject, windowObject);

    expect(navigatorObject.serviceWorker.register).not.toHaveBeenCalled();
    expect(windowObject.addEventListener).toHaveBeenCalledWith("load", expect.any(Function), { once: true });

    windowObject.addEventListener.mock.calls[0][1]();
    expect(navigatorObject.serviceWorker.register).toHaveBeenCalled();
  });

  it("does not register the service worker in an insecure context", function () {
    const navigatorObject = fakeNavigator();

    registerServiceWorker(navigatorObject, fakeWindow({ secure: false }));

    expect(navigatorObject.serviceWorker.register).not.toHaveBeenCalled();
  });

  it("does nothing if service workers are not supported", function () {
    expect(() => registerServiceWorker({}, fakeWindow())).not.toThrow();
  });
});
