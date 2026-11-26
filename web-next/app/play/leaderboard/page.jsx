"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { formatNumber } from "../../../lib/game";
import { fetchLeaderboard, getDeviceId } from "../../../lib/api";
import { usePlay } from "../PlayContext";

const PERIODS = ["all", "daily", "weekly", "monthly"];

export default function PlayLeaderboardPage() {
  const { lang, t, handlePlay, snap } = usePlay();
  const [period, setPeriod] = useState("all");
  const [reloadKey, setReloadKey] = useState(0);
  const [state, setState] = useState({
    loading: true,
    error: false,
    data: [],
    userRank: null,
    userEntry: null,
  });

  useEffect(() => {
    let active = true;
    setState((previous) => ({ ...previous, loading: true, error: false }));
    fetchLeaderboard({ deviceId: getDeviceId(), period, page: 1, limit: 20 })
      .then((result) => {
        if (!active) return;
        if (!result.ok) {
          // Keep the last good board on a refresh failure. Blanking a screen
          // the player was reading because one poll timed out is worse than a
          // stale row; only a first load with no data should look empty.
          setState((previous) => ({
            ...previous,
            loading: false,
            error: true,
          }));
          return;
        }
        setState({
          loading: false,
          error: false,
          data: Array.isArray(result.data?.data) ? result.data.data : [],
          userRank: result.data?.userRank ?? null,
          userEntry: result.data?.userEntry ?? null,
        });
      })
      .catch(() => {
        if (active) {
          setState((previous) => ({ ...previous, loading: false, error: true }));
        }
      });
    return () => {
      active = false;
    };
  }, [period, reloadKey]);

  // A record can be submitted from another tab or from the phone while this
  // page is open. Refresh on focus and on a slow timer instead of showing a
  // frozen board; the same problem made "my new record" look like it was lost.
  useEffect(() => {
    const refresh = () => setReloadKey((key) => key + 1);
    const onVisibility = () => {
      if (!document.hidden) refresh();
    };
    const timer = window.setInterval(refresh, 25000);
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, []);

  const periodLabel = (value) => {
    if (value === "daily") return t.periodDaily;
    if (value === "weekly") return t.periodWeekly;
    if (value === "monthly") return t.periodMonthly;
    return t.periodAll;
  };

  return (
    <main className="play-page play-page-leaderboard">
      <header className="play-top">
        <Link className="play-icon" href="/play/" aria-label={t.back}>
          ←
        </Link>
        <Link className="play-brand" href="/play/">
          <img src="/assets/icons/icon-192.png" alt="" width="32" height="32" />
          <span>
            <strong>{t.brand}</strong>
            <small>BLOCK BOOM · 1.2</small>
          </span>
        </Link>
        <div className="play-top-spacer" />
        <div className="play-resource gold" title={t.coins}>
          <span className="play-resource-icon">🪙</span>
          <b>{formatNumber(snap.coins || 0, lang)}</b>
        </div>
      </header>

      <div className="play-content">
        <section className="play-card menu-card">
          <div className="play-kicker">{t.leaderboardGlobal}</div>
          <h1>{t.homeLeaderboard}</h1>
          <p className="play-lede">{t.leaderboardSub}</p>

          <div className="play-note gold play-notice">
            <span>🏆 {t.leaderboardNotice}</span>
          </div>

          <div className="play-tabs-row">
            <div className="play-tabs" role="tablist" aria-label={t.homeLeaderboard}>
              {PERIODS.map((value) => (
                <button
                  key={value}
                  type="button"
                  role="tab"
                  aria-selected={period === value}
                  className={`play-tab${period === value ? " is-active" : ""}`}
                  onClick={() => setPeriod(value)}
                >
                  {periodLabel(value)}
                </button>
              ))}
            </div>
            <button
              className="play-refresh"
              type="button"
              onClick={() => setReloadKey((key) => key + 1)}
              disabled={state.loading}
              aria-label={t.refresh}
              title={t.refresh}
            >
              ↻
            </button>
          </div>

          <div className="play-list" aria-live="polite">
            {state.loading && state.data.length === 0 ? (
              <div className="play-skeleton" aria-label={t.leaderboardLoading}>
                {[0, 1, 2, 3, 4].map((i) => <span key={i} className="play-skeleton-row" />)}
              </div>
            ) : null}
            {state.error && state.data.length === 0 ? (
              <div className="play-note error">
                <span>{t.leaderboardError}</span>
                <button className="play-inline-btn" type="button" onClick={() => setReloadKey((key) => key + 1)}>
                  {t.leaderboardRetry}
                </button>
              </div>
            ) : null}
            {!state.loading && !state.error && state.data.length === 0 ? (
              <div className="play-empty">
                <div className="play-empty-icon">🏆</div>
                <p>{t.leaderboardEmpty}</p>
                <button className="play-btn primary" type="button" onClick={() => handlePlay("record")}>
                  🎮 {t.play}
                </button>
              </div>
            ) : null}
            {state.data.map((entry, index) => {
              const rank = Number(entry.rank) || index + 1;
              const isMe = Boolean(state.userRank) && rank === state.userRank;
              const medal = rank === 1 ? "🥇" : rank === 2 ? "🥈" : rank === 3 ? "🥉" : "";
              return (
                <div
                  key={`${entry.name}-${rank}-${index}`}
                  className={`play-list-row${rank <= 3 ? " is-top" : ""}${isMe ? " is-me" : ""}`}
                >
                  <span className="play-list-rank">{medal || formatNumber(rank, lang)}</span>
                  <span className="play-list-title">
                    <b>{entry.name}{entry.crown ? ` ${entry.crown}` : ""}</b>
                    <small>
                      {t.wave} {formatNumber(entry.level, lang)}
                      {isMe ? ` · ${t.you}` : ""}
                    </small>
                  </span>
                  <span className="play-list-score">{formatNumber(entry.score, lang)}</span>
                </div>
              );
            })}
          </div>

          {state.userRank ? (
            <div className="play-note success">
              <span>
                {t.yourRank}: {formatNumber(state.userRank, lang)}
                {state.userEntry ? ` · ${t.level} ${formatNumber(state.userEntry.level, lang)} · ${formatNumber(state.userEntry.score, lang)}` : ""}
              </span>
              <span className="play-rank-badge">#{formatNumber(state.userRank, lang)}</span>
            </div>
          ) : null}

          <div className="play-actions">
            <button className="play-btn primary" type="button" onClick={() => handlePlay("record")}>
              🏆 {t.play}
            </button>
            <Link className="play-btn ghost" href="/play/">
              🏠 {t.home}
            </Link>
          </div>
        </section>
      </div>
    </main>
  );
}
