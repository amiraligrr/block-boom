"use client";

import { useEffect } from "react";

/**
 * Registers the /play/ service worker.
 *
 * The manifest makes the game installable, but without this registration the
 * browser never activates the offline shell. Registration is scoped to /play/
 * so the landing page and API stay outside the worker's control.
 */
export default function PwaRegister() {
  useEffect(() => {
    if (typeof window === "undefined" || !("serviceWorker" in navigator)) return;

    const register = () => {
      navigator.serviceWorker.register("/play/sw.js", { scope: "/play/" }).catch(() => {
        // Offline support is a bonus; a failed registration must not stop play.
      });
    };

    if (document.readyState === "complete") {
      register();
      return undefined;
    }

    window.addEventListener("load", register, { once: true });
    return () => window.removeEventListener("load", register);
  }, []);

  return null;
}
