/* ==========================================================================
   Block Boom — small client for the record leaderboard API.

   The PWA is served from the same origin as the API, so every call is a
   relative URL. Keeping that assumption in one place makes it easy to move the
   game to a separate static host later without hunting through components.
   ========================================================================== */

const DEVICE_KEY = "bb-web-device-id";

/**
 * A stable per-browser id, in the same shape the Android client sends.
 *
 * The server keys leaderboard rows on device_id, so this must not change
 * between sessions or the player silently loses their row. localStorage is the
 * only durable storage available here; if it is disabled (private mode, some
 * embedded webviews) the game still runs, it just cannot submit a record.
 */
export function getDeviceId() {
  if (typeof window === "undefined") return "";
  try {
    let id = window.localStorage.getItem(DEVICE_KEY);
    if (!id) {
      const random =
        typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
          ? crypto.randomUUID().replace(/-/g, "")
          : `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
      id = `web_${random}`.slice(0, 64);
      window.localStorage.setItem(DEVICE_KEY, id);
    }
    return id.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 64);
  } catch {
    return "";
  }
}

/**
 * Fetch with a hard wall-clock timeout.
 *
 * A captive portal or a dead mobile radio can leave `fetch` pending for a very
 * long time. The leaderboard then sits on its loading skeleton and a record
 * submit looks like it is still in flight. Abort it, surface the normal
 * network-failure path, and let the UI retry.
 */
async function fetchWithTimeout(url, options = {}, timeoutMs = 12000) {
  let controller = null;
  let timer = null;
  if (typeof AbortController !== "undefined") {
    controller = new AbortController();
    timer = setTimeout(() => controller.abort(), timeoutMs);
  }
  try {
    return await fetch(url, controller ? { ...options, signal: controller.signal } : options);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

async function postJson(path, body) {
  try {
    const response = await fetchWithTimeout(path, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(body),
    }, 15000);
    let data = null;
    try {
      data = await response.json();
    } catch {
      data = null;
    }
    return { ok: response.ok && data?.ok === true, status: response.status, data };
  } catch {
    return { ok: false, status: 0, data: null };
  }
}

/** Opens a server-signed run. Returns the token, or "" when unavailable. */
export async function openRecordRun(deviceId) {
  if (!deviceId) return "";
  const result = await postJson("/start_run.php", {
    device_id: deviceId,
    level: 1,
    mode: "record",
  });
  const token = result.data?.run_token;
  return typeof token === "string" ? token : "";
}

/** Submits a finished record run. The server upserts monotonically. */
export async function submitRecordScore({
  name,
  level,
  score,
  deviceId,
  token,
  events,
  chain,
  durationMs,
}) {
  if (!deviceId) {
    return { ok: false, status: 0, data: null, error: "no-device" };
  }
  const result = await postJson("/submit_score.php", {
    name,
    level,
    score,
    is_premium: 0,
    device_id: deviceId,
    mode: "record",
    run_token: token || "",
    events: Math.max(0, Number(events) || 0),
    chain: chain || "",
    duration_ms: Math.max(0, Number(durationMs) || 0),
  });
  return {
    ...result,
    error: result.data?.error || "",
    message: result.data?.message || "",
  };
}

/** Fetches a page of the global record-only board. */
export async function fetchLeaderboard({ deviceId, period = "all", page = 1, limit = 20 }) {
  const params = new URLSearchParams({
    page: String(page),
    limit: String(limit),
    period,
    mode: "record",
  });
  if (deviceId) params.set("device_id", deviceId);
  try {
    const response = await fetch(`/leaderboard.php?${params.toString()}`, {
      headers: { Accept: "application/json" },
      cache: "no-store",
    });
    const data = await response.json();
    if (!response.ok || data?.ok !== true) {
      return { ok: false, status: response.status, data: null };
    }
    return { ok: true, status: response.status, data };
  } catch {
    return { ok: false, status: 0, data: null };
  }
}
