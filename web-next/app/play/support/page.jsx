"use client";

import Link from "next/link";
import { usePlay } from "../PlayContext";

export default function PlaySupportPage() {
  const { t, handlePlay, openShop } = usePlay();

  return (
    <main className="play-page play-page-support">
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
          <div className="play-kicker">SUPPORT</div>
          <h1>{t.homeSupport}</h1>
          <p className="play-lede">{t.credit}</p>
          <div className="play-list">
            <div className="play-list-row">
              <span className="play-list-title">GitHub</span>
              <Link href="https://github.com/amiraligrr/block-boom" target="_blank" rel="noreferrer">
                {t.back}
              </Link>
            </div>
            <div className="play-list-row">
              <span className="play-list-title">Play Store</span>
              <Link href="/download/BlockBoom-1.2.0.apk" download>
                {t.downloadAndroid}
              </Link>
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
