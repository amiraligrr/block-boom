"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { formatNumber, UPGRADE_DEFS, upgradeCost, upgradeEffect } from "../../../lib/game";
import { upgradeName } from "../play-i18n";
import { usePlay } from "../PlayContext";

export default function PlayShopPage() {
  const router = useRouter();
  const { snap, lang, t, handleBuy } = usePlay();
  const progressionLevel = snap.mode === "record"
    ? Math.max(snap.level || 1, snap.recordBestLevel || 0)
    : snap.level || 1;
  const displayLevel = snap.mode === "record" ? snap.recordBestLevel || 0 : snap.level || 1;
  const displayBest = snap.mode === "record" ? snap.recordBestScore || 0 : snap.bestScore || 0;

  return (
    <main className="play-page play-page-shop">
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
          <div className="play-kicker">UPGRADES</div>
          <h1>{t.shopTitle}</h1>
          <p className="play-lede">{t.shopSub}</p>
          <div className="menu-stats">
            <div>
              <span>{t.coins}</span>
              <strong>{formatNumber(snap.coins, lang)}</strong>
            </div>
            <div>
              <span>{t.level}</span>
              <strong>{formatNumber(displayLevel, lang)}</strong>
            </div>
            <div>
              <span>{t.best}</span>
              <strong>{formatNumber(displayBest, lang)}</strong>
            </div>
          </div>

          <div className="play-home-grid">
            {UPGRADE_DEFS.map((def) => {
              const current = snap.levelUpgrades?.[def.id] ?? 0;
              const locked = progressionLevel < def.unlock;
              const maxed = current >= def.max;
              const cost = upgradeCost(def.id, current);
              const canBuy = !locked && !maxed && snap.coins >= cost;
              return (
                <div key={def.id} className={`play-card play-card-link shop-item${locked ? " is-locked" : ""}${maxed ? " is-maxed" : ""}`}>
                  <div className="play-home-icon" aria-hidden="true">
                    {def.icon}
                  </div>
                  <div className="play-home-body">
                    <h2>{upgradeName(def, lang)}</h2>
                    <p className="play-lede">{upgradeEffect(def.id, current, lang)}</p>
                    <span className="play-home-cta">
                      {maxed ? t.maxed : locked ? `${t.locked} · ${t.unlockAt} ${formatNumber(def.unlock, lang)}` : `${formatNumber(cost, lang)} ${t.coins}`}
                    </span>
                  </div>
                  <button className="buy-btn" type="button" disabled={!canBuy} onClick={() => handleBuy(def.id)}>
                    {maxed ? t.maxed : locked ? `🔒 ${t.locked}` : cost === 0 ? t.free : `${formatNumber(cost, lang)} 🪙`}
                  </button>
                </div>
              );
            })}
          </div>

          <div className="play-actions">
            <button className="play-btn primary" type="button" onClick={() => router.push("/play/")}>
              {t.close}
            </button>
          </div>
        </section>
      </div>
    </main>
  );
}
