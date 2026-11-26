"use client";

import Link from "next/link";
import {
  chestRewardFor,
  formatNumber,
  nextChestLevel,
} from "../../lib/game";
import { formatTime } from "./play-i18n";
import { usePlay } from "./PlayContext";

export default function PlayHomePage() {
  const {
    engine,
    snap,
    lang,
    t,
    toggleLang,
    handleSound,
    handleInstall,
    installPrompt,
    standalone,
    name,
    setName,
    handlePlay,
    openShop,
  } = usePlay();

  const hearts = snap.hearts || 0;
  const playDisabled = hearts <= 0;
  const mode = engine?.mode || snap.mode || "stage";
  const nextChest = nextChestLevel(snap, mode);
  const chestCoins = chestRewardFor(nextChest);

  const startInMode = (runMode) => {
    if (playDisabled) return;
    handlePlay(runMode);
  };

  const homeCards = [
    { href: "/play/game/", icon: "🎮", title: t.homePlay, sub: t.menuSub, cta: t.play },
    { href: "/play/shop/", icon: "🛒", title: t.homeShop, sub: t.shopSub, cta: t.shop },
    { href: "/play/stats/", icon: "📊", title: t.homeStats, sub: t.best, cta: t.best },
    { href: "/play/leaderboard/", icon: "🏆", title: t.homeLeaderboard, sub: t.leaderboardSub, cta: t.best },
    { href: "/play/how-to/", icon: "🕹️", title: t.homeHow, sub: t.startHint, cta: t.play },
    { href: "/play/support/", icon: "🛟", title: t.homeSupport, sub: t.credit, cta: t.homeSupport },
    { href: "/play/invite/", icon: "📣", title: t.homeInvite, sub: t.credit, cta: t.homeInvite },
    { href: "/play/settings/", icon: "⚙️", title: t.homeSettings, sub: t.soundOn, cta: t.soundOn },
  ];

  return (
    <main className="play-page play-page-home">
      <header className="play-top">
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
        <button className="play-icon" type="button" onClick={toggleLang} title={t.lang} aria-label={t.lang}>
          {lang === "fa" ? "EN" : "FA"}
        </button>
        <button className="play-icon" type="button" onClick={handleSound} title={snap.sound ? t.soundOn : t.soundOff} aria-label={snap.sound ? t.soundOn : t.soundOff}>
          {snap.sound ? "🔊" : "🔇"}
        </button>
        <button className="play-icon" type="button" onClick={handleInstall} title={t.install} aria-label={t.install}>
          📲
        </button>
      </header>

      <div className="play-content">
        <section className="play-card menu-card">
          <div className="play-kicker">{t.online}</div>
          <h1>{t.menuTitle}</h1>
          <p className="play-lede">{t.menuSub}</p>

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
              <span>{t.hearts}</span>
              <strong>
                {formatNumber(snap.hearts, lang)} / {formatNumber(25, lang)}
              </strong>
            </div>
          </div>

          {snap.hearts < 25 ? (
            <div className="heart-note">
              ❤️ {t.heartNext} {formatTime(snap.heartTimer, lang)}
            </div>
          ) : null}

          <div className="play-note gold">
            🎁 {t.nextChest} {formatNumber(nextChest, lang)} · +{formatNumber(chestCoins, lang)} 🪙
          </div>
          {mode === "record" ? (
            <div className="play-note">
              🏆 {t.recordBest}: {formatNumber(snap.recordBestScore || 0, lang)} {t.score} ·{" "}
              {t.wave} {formatNumber(snap.recordBestLevel || 0, lang)}
            </div>
          ) : null}
          <div className="play-note gold play-notice">
            🏆 {t.leaderboardNoticeShort}
          </div>

          <label className="name-field">
            <span>{t.playerName}</span>
            <input
              value={name || ""}
              onChange={(event) => setName(event.target.value.slice(0, 16))}
              maxLength={16}
              placeholder={t.namePlaceholder}
              autoComplete="off"
              spellCheck={false}
            />
          </label>

          <div className="play-actions">
            <button className="play-btn primary" type="button" onClick={() => startInMode(mode)} disabled={playDisabled}>
              {playDisabled ? t.noHearts : `❤️ ${t.play}`}
            </button>
            <button className="play-btn" type="button" onClick={() => startInMode("stage")} disabled={playDisabled}>
              🎮 {t.stage}
            </button>
            <button className="play-btn ghost" type="button" onClick={() => startInMode("record")} disabled={playDisabled}>
              🏆 {t.record}
            </button>
            <button className="play-btn" type="button" onClick={openShop}>
              🛒 {t.shop}
            </button>
            <button className="play-btn ghost" type="button" onClick={() => window.location.assign("/play/how-to/")}>
              🕹️ {t.homeHow}
            </button>
          </div>

          <div className="menu-links">
            <Link href="/download/BlockBoom-1.2.0.apk" download>
              ⬇️ {t.downloadAndroid}
            </Link>
            {!standalone ? <span className="menu-muted">{lang === "fa" ? "قابل نصب" : "Installable"}</span> : null}
          </div>

          <p className="play-credit">{t.credit}</p>
        </section>

        <section className="play-home-grid">
          {homeCards.map((card) => (
            <Link key={card.href} href={card.href} className="play-card play-card-link">
              <div className="play-home-icon" aria-hidden="true">
                {card.icon}
              </div>
              <div className="play-home-body">
                <h2>{card.title}</h2>
                <p className="play-lede">{card.sub}</p>
                <span className="play-home-cta">{card.cta}</span>
              </div>
            </Link>
          ))}
        </section>
      </div>

      {installPrompt ? (
        <div className="play-overlay">
          <div className="play-card">
            <div className="play-kicker">INSTALL</div>
            <h2>{t.install}</h2>
            <p className="play-lede">{t.installHint}</p>
            <div className="play-actions">
              <button className="play-btn primary" type="button" onClick={handleInstall}>
                {t.install}
              </button>
              <button className="play-btn ghost" type="button" onClick={() => setInstallPrompt(null)}>
                {t.close}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  );
}
