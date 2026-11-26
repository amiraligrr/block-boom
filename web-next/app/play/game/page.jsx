"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import { formatNumber } from "../../../lib/game";
import { usePlay } from "../PlayContext";

export default function PlayGamePage() {
  const {
    engine,
    snap,
    lang,
    t,
    handlePlay,
    openShop,
    handleQuit,
    handleSound,
    setHasMoved,
  } = usePlay();

  const canvasRef = useRef(null);
  const stageRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !engine) return;

    engine.attachCanvas(canvas);
    const stage = stageRef.current;
    const observer = stage && "ResizeObserver" in window ? new ResizeObserver(() => engine.resize()) : null;
    if (observer && stage) observer.observe(stage);

    return () => {
      observer?.disconnect();
      engine.setActive(false);
      engine.pause();
    };
  }, [engine]);

  const handlePointerMove = (event) => {
    engine?.pointerMove(event.clientX);
  };

  const handlePointerDown = (event) => {
    event.currentTarget.setPointerCapture?.(event.pointerId);
    engine?.initAudio?.();
    engine?.pointerMove(event.clientX);
    setHasMoved(true);
  };

  const isPlaying = snap.status === "playing";
  const isPaused = snap.status === "paused";
  const showHud = snap.status !== "idle";

  return (
    <main className="play-page play-page-game">
      <header className="play-top">
        <button className="play-icon" type="button" onClick={handleQuit} aria-label={t.back}>
          ←
        </button>
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
        <button className="play-icon" type="button" onClick={handleSound} title={snap.sound ? t.soundOn : t.soundOff}>
          {snap.sound ? "🔊" : "🔇"}
        </button>
        {(isPlaying || isPaused) && (
          <button className="play-icon pause" type="button" onClick={isPaused ? () => engine?.resume() : () => engine?.pause()}>
            {isPaused ? "▶" : "⏸"}
          </button>
        )}
      </header>

      <section className={`play-stage${isPlaying ? " is-live" : ""}`} ref={stageRef}>
        <canvas
          ref={canvasRef}
          className="play-canvas"
          onPointerMove={handlePointerMove}
          onPointerDown={handlePointerDown}
          onContextMenu={(event) => event.preventDefault()}
          aria-label={t.startHint}
        />

        {showHud && (
          <div className="play-hud" aria-hidden="true">
            <div className="hud-shade" />
            <div className="hud-top">
              <div className="hud-chip">
                <span>{snap.mode === "record" ? t.wave : t.level}</span>
                <strong>{formatNumber(snap.level, lang)}</strong>
              </div>
              <div className="hud-mod" title={t.modifier}>
                <span>{snap.modifier}</span>
                <b>{t.modifier}</b>
              </div>
              <div className="hud-chip score">
                <span>{t.score}</span>
                <strong>{formatNumber(snap.mode === "record" ? snap.runScore : snap.score, lang)}</strong>
              </div>
            </div>
            {snap.boss ? (
              <div className="boss-bar">
                <div className="boss-bar-head">
                  <span>☠️ {t.boss}</span>
                  <b>
                    {formatNumber(Math.ceil(snap.boss.hp), lang)} / {formatNumber(snap.boss.maxHp, lang)}
                  </b>
                </div>
                <div className="boss-track">
                  <i style={{ width: `${Math.max(0, Math.min(100, (snap.boss.hp / Math.max(1, snap.boss.maxHp)) * 100))}%` }} />
                </div>
              </div>
            ) : (
              <div className="hud-progress" title={t.progress}>
                <i style={{ width: `${snap.blocksDestroyed && snap.totalBlocks ? Math.max(0, Math.min(100, (snap.blocksDestroyed / snap.totalBlocks) * 100)) : 0}%` }} />
              </div>
            )}
            {snap.mode === "record" && Number(snap.recordBaseline) > 0 ? (
              <div className={`record-chase${snap.runScore > snap.recordBaseline ? " is-beaten" : ""}`}>
                <div className="record-chase-head">
                  <span>🏆 {t.recordBest}</span>
                  <b>
                    {snap.runScore > snap.recordBaseline
                      ? t.newRecord
                      : `${formatNumber(snap.runScore, lang)} / ${formatNumber(snap.recordBaseline, lang)}`}
                  </b>
                </div>
                <div className="record-chase-track">
                  <i
                    style={{
                      width: `${Math.max(
                        0,
                        Math.min(100, (Number(snap.runScore) / Math.max(1, Number(snap.recordBaseline))) * 100),
                      )}%`,
                    }}
                  />
                </div>
              </div>
            ) : null}
            <div className="hud-powerups">
              {Object.entries(snap.powerups || {}).filter(([, seconds]) => seconds > 0).map(([key, seconds]) => (
                <span className="pu-chip" key={key}>
                  {key} {Math.ceil(seconds)}s
                </span>
              ))}
              {snap.shields > 0 ? <span className="pu-chip shield">🛡️ {snap.shields}</span> : null}
            </div>
            {snap.combo > 1 ? (
              <div className="combo-pill">
                <span>
                  {t.combo} ×{formatNumber(snap.combo, lang)}
                </span>
                <i style={{ transform: `scaleX(${Math.max(0, Math.min(1, snap.comboFraction))})` }} />
              </div>
            ) : null}
            {isPlaying && !hasMoved ? <div className="stage-hint">{t.startHint}</div> : null}
          </div>
        )}

        {snap.status === "idle" ? (
          <div className="play-overlay">
            <div className="play-card menu-card">
              <div className="play-kicker">{t.menuKicker}</div>
              <h1>{t.menuTitle}</h1>
              <p className="play-lede">{t.menuSub}</p>
              <div className="play-actions">
                <button className="play-btn primary" type="button" onClick={() => handlePlay(engine?.mode || snap.mode || "stage")} disabled={snap.hearts <= 0}>
                  {snap.hearts > 0 ? `❤️ ${t.play}` : t.noHearts}
                </button>
                <button className="play-btn" type="button" onClick={openShop}>
                  🛒 {t.shop}
                </button>
                <button className="play-btn ghost" type="button" onClick={() => (window.location.href = "/play/")}>
                  🏠 {t.home}
                </button>
              </div>
            </div>
          </div>
        ) : null}

        {isPaused ? (
          <div className="play-overlay">
            <div className="play-card small-card">
              <div className="play-kicker">PAUSED</div>
              <h2>{t.pausedTitle}</h2>
              <p className="play-lede">{t.pausedSub}</p>
              <div className="play-actions">
                <button className="play-btn primary" type="button" onClick={() => engine?.resume()}>
                  ▶ {t.resume}
                </button>
                <button className="play-btn" type="button" onClick={openShop}>
                  🛒 {t.shop}
                </button>
                <button className="play-btn ghost" type="button" onClick={handleQuit}>
                  🏠 {t.quit}
                </button>
              </div>
            </div>
          </div>
        ) : null}
      </section>
    </main>
  );
}
