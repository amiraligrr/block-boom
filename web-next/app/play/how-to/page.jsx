"use client";

import Link from "next/link";
import { usePlay } from "../PlayContext";

export default function PlayHowToPage() {
  const { t, handlePlay, openShop } = usePlay();

  return (
    <main className="play-page play-page-how-to">
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
      </header>

      <div className="play-content">
        <section className="play-card menu-card">
          <div className="play-kicker">HOW TO PLAY</div>
          <h1>{t.homeHow}</h1>
          <p className="play-lede">{t.menuSub}</p>
          <div className="play-list">
            <div className="play-list-row">
              <span className="play-list-title">{t.startHint}</span>
            </div>
            <div className="play-list-row">
              <span className="play-list-title">{t.noHearts}</span>
            </div>
            <div className="play-list-row">
              <span className="play-list-title">{t.bossWarn}</span>
            </div>
          </div>
          <div className="play-actions">
            <button className="play-btn primary" type="button" onClick={handlePlay}>
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
