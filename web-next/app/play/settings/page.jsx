"use client";

import Link from "next/link";
import { usePlay } from "../PlayContext";

export default function PlaySettingsPage() {
  const { lang, t, toggleLang, handleSound, handleName, handleInstall, installPrompt, standalone, snap, name } = usePlay();

  return (
    <main className="play-page play-page-settings">
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
          <b>{snap.hearts}</b>
        </div>
        <div className="play-resource gold" title={t.coins}>
          <span className="play-resource-icon">🪙</span>
          <b>{snap.coins}</b>
        </div>
      </header>

      <div className="play-content">
        <section className="play-card menu-card">
          <div className="play-kicker">SETTINGS</div>
          <h1>{t.homeSettings}</h1>
          <p className="play-lede">Block Boom · 1.2</p>

          <div className="play-list">
            <div className="play-list-row">
              <span className="play-list-title">{t.lang}</span>
              <button className="play-btn" type="button" onClick={toggleLang}>
                {lang === "fa" ? "EN" : "FA"}
              </button>
            </div>
            <div className="play-list-row">
              <span className="play-list-title">{t.soundOn}</span>
              <button className="play-btn" type="button" onClick={handleSound}>
                {snap.sound ? "🔊" : "🔇"}
              </button>
            </div>
            <div className="play-list-row">
              <span className="play-list-title">{t.playerName}</span>
              <input
                value={name || ""}
                onChange={(event) => handleName(event.target.value.slice(0, 16))}
                maxLength={16}
                placeholder={t.namePlaceholder}
                autoComplete="off"
                spellCheck={false}
              />
            </div>
          </div>

          <div className="play-actions">
            <button className="play-btn primary" type="button" onClick={handleInstall}>
              📲 {t.install}
            </button>
            <Link className="play-btn" href="/download/BlockBoom-1.2.0.apk" download>
              ⬇️ {t.downloadAndroid}
            </Link>
          </div>
        </section>
      </div>
    </main>
  );
}
