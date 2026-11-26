"use client";

import Link from "next/link";
import {
  chestRewardFor,
  formatNumber,
  nextChestLevel,
  UPGRADE_DEFS,
  upgradeCost,
  upgradeEffect,
} from "../../../lib/game";
import { upgradeName } from "../play-i18n";
import { usePlay } from "../PlayContext";

export default function PlayUpgradePage() {
  const {
    snap,
    lang,
    t,
    completeInfo,
    openShop,
    handleBuy,
    handleNext,
    handleQuit,
  } = usePlay();

  const nextChest = nextChestLevel(snap, snap.mode);
  const chestCoins = chestRewardFor(nextChest);
  const progressionLevel = snap.mode === "record"
    ? Math.max(snap.level || 1, snap.recordBestLevel || 0)
    : snap.level || 1;

  return (
    <main className="play-page play-page-upgrade">
      <header className="play-top">
        <Link className="play-icon" href="/play/game/" aria-label={t.back}>
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
          <div className="play-kicker">LEVEL CLEAR</div>
          <h1>{t.nextLevel}</h1>
          <p className="play-lede">{t.completeSub}</p>

          {completeInfo && (completeInfo.chest || completeInfo.perfectBonus > 0) ? (
            <div className="reward-stack">
              {completeInfo.chest ? (
                <div className="reward-chip gold">
                  🎁 {t.chestTitle} {formatNumber(completeInfo.chest.level, lang)} · +
                  {formatNumber(completeInfo.chest.coins, lang)} 🪙
                </div>
              ) : null}
              {completeInfo.perfectBonus > 0 ? (
                <div className="reward-chip green">
                  ✨ {t.perfectTitle} · +{formatNumber(completeInfo.perfectBonus, lang)} 🪙
                </div>
              ) : null}
            </div>
          ) : null}

          <div className="play-note gold">
            🎁 {t.nextChest} {formatNumber(nextChest, lang)} · +{formatNumber(chestCoins, lang)} 🪙
          </div>

          <div className="play-actions">
            <button className="play-btn primary" type="button" onClick={handleNext}>
              🚀 {t.next}
            </button>
            <button className="play-btn" type="button" onClick={() => openShop()}>
              🛒 {t.shop}
            </button>
            <button className="play-btn ghost" type="button" onClick={handleQuit}>
              🏠 {t.home}
            </button>
          </div>
        </section>

        <section className="play-card menu-card">
          <div className="play-kicker">UPGRADES</div>
          <h1>{t.shopTitle}</h1>
          <p className="play-lede">{t.shopSub}</p>
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
        </section>
      </div>
    </main>
  );
}
