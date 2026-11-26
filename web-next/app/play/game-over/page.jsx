"use client";

import Link from "next/link";
import { formatNumber } from "../../../lib/game";
import { usePlay } from "../PlayContext";

export default function PlayGameOverPage() {
  const {
    snap,
    lang,
    t,
    handleRetry,
    openShop,
    handleQuit,
    overInfo,
    completeInfo,
    submitInfo,
    retrySubmit,
  } = usePlay();

  const info = overInfo || completeInfo || snap;
  const isOver = info?.type === "over" || snap.status === "hit";
  const isRecord = (info?.mode || snap.mode) === "record";

  return (
    <main className="play-page play-page-game-over">
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
          <b>{formatNumber(snap.coins, lang)}</b>
        </div>
      </header>

      <div className="play-content">
        <section className="play-card menu-card">
          <div className="play-kicker">{isOver ? t.overKicker : t.completeKicker}</div>
          <h1>{isOver ? t.overTitle : t.completeTitle}</h1>
          <p className="play-lede">{isOver ? t.overSub : t.completeSub}</p>
          <div className="menu-stats">
            <div>
              <span>{isRecord ? t.wave : t.level}</span>
              <strong>{formatNumber(info?.level ?? snap.level, lang)}</strong>
            </div>
            <div>
              <span>{t.runScore}</span>
              <strong>{formatNumber(info?.runScore ?? snap.runScore, lang)}</strong>
            </div>
            <div>
              <span>{t.coinsEarned}</span>
              <strong>{formatNumber(info?.runCoins ?? snap.runCoins ?? 0, lang)}</strong>
            </div>
          </div>

          {isRecord ? (
            <div className="play-note">
              🏆 {t.recordBest}: {formatNumber(snap.recordBestScore || 0, lang)} {t.score} ·{" "}
              {t.wave} {formatNumber(snap.recordBestLevel || 0, lang)}
            </div>
          ) : null}

          {isRecord && submitInfo ? (
            <div
              className={`play-note${
                submitInfo.status === "done"
                  ? " success"
                  : submitInfo.status === "failed"
                    ? " error"
                    : ""
              }`}
            >
              <span>
                {submitInfo.status === "submitting"
                  ? t.submitting
                  : submitInfo.message}
              </span>
              {submitInfo.status === "failed" ? (
                <button className="play-inline-btn" type="button" onClick={retrySubmit}>
                  {t.submitRetry}
                </button>
              ) : null}
            </div>
          ) : null}

          <div className="play-actions">
            <button className="play-btn primary" type="button" onClick={() => handleRetry(snap.mode || "stage")} disabled={snap.hearts <= 0}>
              {snap.hearts > 0 ? `❤️ ${t.retry}` : t.noHearts}
            </button>
            <button className="play-btn" type="button" onClick={openShop}>
              🛒 {t.shop}
            </button>
            <button className="play-btn ghost" type="button" onClick={handleQuit}>
              🏠 {t.home}
            </button>
          </div>
        </section>
      </div>
    </main>
  );
}
