/**
 * Registers the service worker of the progressive web app (see PwaController).
 * Service workers are only available in secure contexts (https or localhost).
 */
export function registerServiceWorker(navigatorObject = globalThis.navigator, windowObject = globalThis.window) {
  if (!navigatorObject || !("serviceWorker" in navigatorObject) || !windowObject?.isSecureContext) {
    return;
  }

  const contextPath = getContextPath(windowObject.document);
  const register = () => {
    navigatorObject.serviceWorker
      .register(`${contextPath}sw.js`, { scope: contextPath })
      .catch((error) => console.warn("could not register service worker", error));
  };

  if (windowObject.document.readyState === "complete") {
    register();
  } else {
    windowObject.addEventListener("load", register, { once: true });
  }
}

// the turbo-root meta tag contains the context path, e.g. "/web" or "/urlaub/web"
export function getContextPath(documentObject) {
  const turboRoot = documentObject.querySelector("meta[name='turbo-root']")?.getAttribute("content") ?? "/web";
  return turboRoot.replace(/web\/?$/, "");
}
