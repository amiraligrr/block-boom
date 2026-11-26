"use client";

import { usePlay } from "../PlayContext";

export default function PlayInvitePage() {
  const { t, handlePlay, openShop } = usePlay();

  const shareUrl = typeof window === "undefined" ? "/play/" : `${window.location.origin}/play/`;

  return (
    <main className="play-page play-page-invite">
      <header className="play-top">
        <div className="play-top-spacer" />
        <div className="play-top-spacer" />
        <div className="play-top-spacer" />
      </header>

      <div className="play-content">
        <section className="play-card menu-card">
          <div className="play-kicker">INVITE</div>
          <h1>{t.homeInvite}</h1>
          <p className="play-lede">{t.menuSub}</p>
          <div className="play-list">
            <div className="play-list-row">
              <span className="play-list-title">{shareUrl}</span>
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
