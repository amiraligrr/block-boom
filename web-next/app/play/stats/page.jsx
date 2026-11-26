"use client";

import Link from "next/link";
import { formatNumber } from "../../../lib/game";
import { usePlay } from "../PlayContext";

export default function PlayStatsPage() {
  const { snap, runs, lang, t, handlePlay, openShop } = usePlay();

  const sortedRuns = [...runs].sort((a, b) => (b.score || 0) - (a.score || 0));

  return (
    <main className="play-page play-page-stats">
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
        <div className="play-resource" title={t.hearts}>
          <span className="play-resource-icon">❤️</span>
          <b>{formatNumber(snap.hearts, lang)}</b>
        </div>
        <div className="play-resource gold" title={t.coins}>
          <span className="play-resource-icon">🪙</span>
          <b>{formatNumber(snap.coins, lang)}</b>
        </div>
      </header>

      <div className="play-content">
        <section className="play-card menu-card">
          <div className="play-kicker">STATS</div>
          <h1>{t.homeStats}</h1>
          <p className="play-lede">Block Boom · 1.2</p>
          <div className="menu-stats">
            <div>
              <span>{t.best}</span>
              <strong>{formatNumber(snap.bestScore, lang)}</strong>
            </div>
            <div>
              <span>{t.level}</span>
              <strong>{formatNumber(snap.level, lang)}</strong>
            </div>
            <div>
              <span>{t.coins}</span>
              <strong>{formatNumber(snap.coins, lang)}</strong>
            </div>
          </div>

          <div className="play-note">
            🏆 {t.recordBest}: {formatNumber(snap.recordBestScore || 0, lang)} {t.score} ·{" "}
            {t.wave} {formatNumber(snap.recordBestLevel || 0, lang)}
          </div>

          <h2>{t.recentRuns}</h2>
          <div className="play-list">
            {sortedRuns.length ? (
              sortedRuns.map((run, index) => (
                <div key={`${run.at}-${index}`} className="play-list-row">
                  <span className="play-list-rank">{index + 1}</span>
                  <span className="play-list-title">
                    {run.mode === "record" ? t.record : t.stage} ·{" "}
                    {run.type === "complete" ? t.completeKicker : t.overKicker}
                  </span>
                  <span className="play-list-score">{formatNumber(run.score, lang)}</span>
                </div>
              ))
            ) : (
              <p className="play-lede">{t.loading}</p>
            )}
          </div>

          <div className="play-actions">
            <button className="play-btn primary" type="button" onClick={() => handlePlay("record")}>
              ❤️ {t.play}
            </button>
            <button className="play-btn" type="button" onClick={openShop}>
              🛒 {t.shop}
            </button>
          </div>
        </section>
      </div>
    </main>
  );
}
