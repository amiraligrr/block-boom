"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  GameEngine,
  MODIFIERS,
  POWERUPS,
  UPGRADE_DEFS,
  formatNumber,
  loadSave,
  persistSave,
  refreshHearts,
  upgradeCost,
  upgradeEffect,
} from "../../lib/game";

const TXT = {
  fa: {
    back: "بازگشت",
    brand: "بلاک بوم",
    online: "نسخه وب",
    hearts: "قلب",
    coins: "سکه",
    soundOn: "صدا روشن",
    soundOff: "صدا خاموش",
    lang: "English",
    pause: "توقف",
    resume: "ادامه",
    level: "مرحله",
    score: "امتیاز",
    best: "بهترین",
    combo: "کمبو",
    boss: "بلاک مادر",
    nextLevel: "مرحله بعد",
    shop: "فروشگاه",
    play: "شروع بازی",
    retry: "دوباره امتحان کن",
    home: "خانه",
    quit: "خروج به منو",
    pausedTitle: "بازی متوقف شد",
    pausedSub: "نفس بگیر. هر وقت آماده بودی ادامه بده.",
    completeKicker: "مرحله کامل شد",
    completeTitle: "عالی بود!",
    completeSub: "مرحله بعد منتظرته.",
    overKicker: "پایان بازی",
    overTitle: "این بار نشد",
    overSub: "اشکالی نداره؛ دوباره امتحان کن.",
    noHearts: "قلب کافی نداری. کمی صبر کن یا بعداً برگرد.",
    heartNext: "قلب بعدی تا",
    runScore: "امتیاز این دور",
    coinsEarned: "سکه این دور",
    playerName: "نام بازیکن",
    namePlaceholder: "مثلاً آمیرعلی",
    install: "نصب بازی",
    installHint: "برای نصب، از منوی مرورگر «افزودن به صفحه اصلی» را بزن.",
    startHint: "انگشتت را روی صفحه بکش تا توپ شلیک شود",
    menuKicker: "BLOCK BOOM · 1.1",
    menuTitle: "بترکون، بالا برو، رکورد بزن",
    menuSub: "همون بازی اندروید، حالا کاملاً تحت وب. بلاک‌ها را بشکن، کمبو بساز و باس بزن.",
    download: "دانلود نسخه اندروید",
    shopTitle: "فروشگاه ارتقا",
    shopSub: "با سکه‌هایی که جمع کردی قوی‌تر شو.",
    close: "بستن",
    maxed: "حداکثر",
    locked: "قفل",
    unlockAt: "از مرحله",
    next: "بعدی",
    buy: "خرید",
    notEnough: "سکه کافی نداری",
    bought: "خرید انجام شد",
    free: "رایگان",
    shield: "سپر",
    bossWarn: "باس فایت! بلاک مادر رسید",
    credit: "made by Amiraligrr, for everyone, everywhere",
    loading: "در حال آماده‌سازی…",
    levelShort: "مرحله",
    bestShort: "رکورد",
    heartsShort: "قلب",
    modifier: "مود",
    progress: "پیشرفت",
    bossHp: "جان باس",
    upgradeLevel: "سطح",
    effect: "اثر",
    installDone: "بازی نصب شد",
    installLater: "نصب را می‌توانی بعداً از منوی مرورگر انجام بدهی",
  },
  en: {
    back: "Back",
    brand: "Block Boom",
    online: "Web build",
    hearts: "Hearts",
    coins: "Coins",
    soundOn: "Sound on",
    soundOff: "Sound off",
    lang: "فارسی",
    pause: "Pause",
    resume: "Resume",
    level: "Level",
    score: "Score",
    best: "Best",
    combo: "Combo",
    boss: "Mother Block",
    nextLevel: "Next level",
    shop: "Shop",
    play: "Play",
    retry: "Try again",
    home: "Home",
    quit: "Quit to menu",
    pausedTitle: "Game paused",
    pausedSub: "Take a breath. Continue whenever you're ready.",
    completeKicker: "Level clear",
    completeTitle: "Nice work!",
    completeSub: "The next level is waiting.",
    overKicker: "Game over",
    overTitle: "Not this time",
    overSub: "No worries — give it another go.",
    noHearts: "No hearts left. Wait a bit or come back later.",
    heartNext: "Next heart in",
    runScore: "Run score",
    coinsEarned: "Run coins",
    playerName: "Player name",
    namePlaceholder: "e.g. Amirali",
    install: "Install game",
    installHint: "To install, use your browser menu and choose “Add to Home screen”.",
    startHint: "Drag anywhere to aim and fire",
    menuKicker: "BLOCK BOOM · 1.1",
    menuTitle: "Break it. Climb. Set a record.",
    menuSub: "The same Android game, now fully on the web. Break blocks, chain combos, beat the boss.",
    download: "Download Android",
    shopTitle: "Upgrade shop",
    shopSub: "Spend the coins you've collected and get stronger.",
    close: "Close",
    maxed: "Maxed",
    locked: "Locked",
    unlockAt: "Unlocks at level",
    next: "Next",
    buy: "Buy",
    notEnough: "Not enough coins",
    bought: "Upgrade purchased",
    free: "Free",
    shield: "Shield",
    bossWarn: "Boss fight! The Mother Block has arrived",
    credit: "made by Amiraligrr, for everyone, everywhere",
    loading: "Getting ready…",
    levelShort: "Level",
    bestShort: "Best",
    heartsShort: "Hearts",
    modifier: "Modifier",
    progress: "Progress",
    bossHp: "Boss HP",
    upgradeLevel: "Level",
    effect: "Effect",
    installDone: "Game installed",
    installLater: "You can install it later from your browser menu",
  },
};

const FALLBACK_SNAP = {
  status: "idle",
  paused: false,
  level: 1,
  score: 0,
  runScore: 0,
  coins: 0,
  coinsInLevel: 0,
  hearts: 25,
  heartTimer: 0,
  bestScore: 0,
  combo: 0,
  comboFraction: 0,
  comboBest: 0,
  shields: 0,
  powerups: {},
  blocksDestroyed: 0,
  totalBlocks: 0,
  modifier: "NONE",
  boss: null,
  bossLevel: false,
  name: "",
  sound: true,
  levelUpgrades: { damage: 1, fireRate: 2, multiBall: 1, pierce: 0, magnet: 0, bulletSpeed: 8 },
  canAfford: {},
};

function formatTime(ms, lang) {
  if (!ms) return "";
  const totalMinutes = Math.max(1, Math.ceil(ms / 60000));
  const h = Math.floor(totalMinutes / 60);
  const m = totalMinutes % 60;
  if (lang === "en") return h ? `${h}h ${m}m` : `${m}m`;
  return h
    ? `${formatNumber(h, lang)} ساعت ${formatNumber(m, lang)} دقیقه`
    : `${formatNumber(m, lang)} دقیقه`;
}

function upgradeName(def, lang) {
  return lang === "en" ? def.nameEn : def.name;
}

function modifierLabel(mod, lang) {
  return lang === "en" ? mod.labelEn : mod.label;
}

function modifierBlurb(mod, lang) {
  return lang === "en" ? mod.blurbEn : mod.blurb;
}

export default function PlayPage() {
  const canvasRef = useRef(null);
  const stageRef = useRef(null);
  const engineRef = useRef(null);
  const [snap, setSnap] = useState(FALLBACK_SNAP);
  const [lang, setLang] = useState("fa");
  const [name, setName] = useState("");
  const [toast, setToast] = useState("");
  const [shopOpen, setShopOpen] = useState(false);
  const [completeInfo, setCompleteInfo] = useState(null);
  const [overInfo, setOverInfo] = useState(null);
  const [installPrompt, setInstallPrompt] = useState(null);
  const [standalone, setStandalone] = useState(false);
  const [hasMoved, setHasMoved] = useState(false);
  const t = TXT[lang];
  const tRef = useRef(t);
  tRef.current = t;

  useEffect(() => {
    const stored = localStorage.getItem("bb-lang");
    if (stored === "en") setLang("en");
    setStandalone(window.matchMedia?.("(display-mode: standalone)")?.matches || false);
  }, []);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "fa" ? "rtl" : "ltr";
    if (engineRef.current) engineRef.current.lang = lang;
  }, [lang]);

  useEffect(() => {
    if (!toast) return undefined;
    const id = setTimeout(() => setToast(""), 2600);
    return () => clearTimeout(id);
  }, [toast]);

  useEffect(() => {
    const saved = loadSave();
    refreshHearts(saved);
    persistSave(saved);
    setName(saved.name || "");

    const canvas = canvasRef.current;
    if (!canvas) return undefined;

    const engine = new GameEngine(canvas, {
      save: saved,
      lang: "fa",
      onState: (state) => setSnap(state),
      onSave: (next) => persistSave(next),
      onEvent: (event) => {
        if (event.type === "levelComplete") {
          setCompleteInfo(event);
        } else if (event.type === "hit") {
          setOverInfo(event);
        } else if (event.type === "noHearts") {
          setToast(tRef.current.noHearts);
        } else if (event.type === "boss") {
          setToast(tRef.current.bossWarn);
        } else if (event.type === "levelStarted") {
          setHasMoved(false);
        } else if (event.type === "runStarted") {
          setHasMoved(false);
        }
      },
    });
    engineRef.current = engine;
    if (typeof window !== "undefined") window.__bbEngine = engine;
    setSnap(engine.snapshot());

    const stage = stageRef.current;
    const observer = stage && "ResizeObserver" in window
      ? new ResizeObserver(() => engine.resize())
      : null;
    if (observer && stage) observer.observe(stage);

    const onVisibility = () => {
      if (document.hidden) engine.pause();
      else engine.refreshHearts();
    };
    document.addEventListener("visibilitychange", onVisibility);
    const heartInterval = setInterval(() => engine.refreshHearts(), 30_000);

    const onInstallPrompt = (event) => {
      event.preventDefault();
      setInstallPrompt(event);
    };
    window.addEventListener("beforeinstallprompt", onInstallPrompt);

    if ("serviceWorker" in navigator) {
      const register = () => {
        navigator.serviceWorker.register("/play/sw.js", { scope: "/play/" }).catch(() => {});
      };
      if (document.readyState === "complete") register();
      else window.addEventListener("load", register, { once: true });
    }

    return () => {
      clearInterval(heartInterval);
      observer?.disconnect();
      document.removeEventListener("visibilitychange", onVisibility);
      window.removeEventListener("beforeinstallprompt", onInstallPrompt);
      engine.destroy();
      engineRef.current = null;
      if (typeof window !== "undefined") delete window.__bbEngine;
    };
  }, []);

  const handlePointerMove = useCallback((event) => {
    engineRef.current?.pointerMove(event.clientX);
    setHasMoved(true);
  }, []);

  const handlePointerDown = useCallback((event) => {
    event.currentTarget.setPointerCapture?.(event.pointerId);
    engineRef.current?.initAudio?.();
    engineRef.current?.pointerMove(event.clientX);
    setHasMoved(true);
  }, []);

  const handlePlay = useCallback(() => {
    const engine = engineRef.current;
    if (!engine) return;
    engine.initAudio?.();
    if (!engine.startRun()) setToast(tRef.current.noHearts);
  }, []);

  const handlePause = useCallback(() => {
    engineRef.current?.pause();
  }, []);

  const handleResume = useCallback(() => {
    engineRef.current?.resume();
  }, []);

  const handleQuit = useCallback(() => {
    engineRef.current?.quitToMenu();
    setCompleteInfo(null);
    setOverInfo(null);
    setShopOpen(false);
  }, []);

  const handleNext = useCallback(() => {
    engineRef.current?.nextLevel();
    setCompleteInfo(null);
  }, []);

  const handleRetry = useCallback(() => {
    const engine = engineRef.current;
    if (!engine) return;
    engine.initAudio?.();
    if (!engine.startRun()) setToast(tRef.current.noHearts);
  }, []);

  const openShop = useCallback(() => {
    const engine = engineRef.current;
    if (engine?.status === "playing") engine.pause();
    setShopOpen(true);
  }, []);

  const handleBuy = useCallback((id) => {
    const engine = engineRef.current;
    if (!engine) return;
    const def = UPGRADE_DEFS.find((u) => u.id === id);
    const current = snap?.levelUpgrades?.[id] ?? 0;
    if (!def) return;
    if ((snap?.level || 1) < def.unlock) {
      setToast(`${tRef.current.locked} — ${tRef.current.unlockAt} ${formatNumber(def.unlock, lang)}`);
      return;
    }
    if (current >= def.max) {
      setToast(tRef.current.maxed);
      return;
    }
    const cost = upgradeCost(id, current);
    if ((snap?.coins || 0) < cost) {
      setToast(tRef.current.notEnough);
      return;
    }
    if (engine.buyUpgrade(id)) setToast(tRef.current.bought);
    else setToast(tRef.current.notEnough);
  }, [snap, lang]);

  const handleSound = useCallback(() => {
    const engine = engineRef.current;
    if (!engine) return;
    engine.initAudio?.();
    engine.setSound(!snap?.sound);
  }, [snap?.sound]);

  const handleInstall = useCallback(async () => {
    if (!installPrompt) {
      setToast(tRef.current.installHint);
      return;
    }
    installPrompt.prompt();
    try {
      const choice = await installPrompt.userChoice;
      setToast(choice?.outcome === "accepted" ? tRef.current.installDone : tRef.current.installLater);
    } catch {
      setToast(tRef.current.installLater);
    }
    setInstallPrompt(null);
  }, [installPrompt]);

  const toggleLang = useCallback(() => {
    const next = lang === "fa" ? "en" : "fa";
    setLang(next);
    localStorage.setItem("bb-lang", next);
  }, [lang]);

  const handleName = useCallback((event) => {
    const value = event.target.value.slice(0, 16);
    setName(value);
    engineRef.current?.setName(value);
  }, []);

  const mod = MODIFIERS[snap.modifier] || MODIFIERS.NONE;
  const bossPercent = snap.boss ? Math.max(0, Math.min(100, (snap.boss.hp / Math.max(1, snap.boss.maxHp)) * 100)) : 0;
  const progress = snap.totalBlocks > 0
    ? Math.max(0, Math.min(100, (snap.blocksDestroyed / snap.totalBlocks) * 100))
    : 0;
  const powerups = Object.entries(snap.powerups || {}).filter(([, seconds]) => seconds > 0);
  const showHud = snap.status !== "idle";
  const isPlaying = snap.status === "playing";
  const isPaused = snap.status === "paused";
  const isComplete = snap.status === "complete";
  const isOver = snap.status === "hit";

  return (
    <main className="play-page" dir={lang === "fa" ? "rtl" : "ltr"}>
      <div className="play-ambient" aria-hidden="true">
        <span />
        <span />
      </div>

      <header className="play-top">
        <a className="play-icon" href="/" aria-label={t.back} title={t.back}>←</a>
        <a className="play-brand" href="/" aria-label={t.brand}>
          <img src="/assets/icons/icon-192.png" alt="" width="32" height="32" />
          <span>
            <strong>{t.brand}</strong>
            <small>BLOCK BOOM · 1.1</small>
          </span>
        </a>
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
        <button
          className="play-icon"
          type="button"
          onClick={handleSound}
          title={snap.sound ? t.soundOn : t.soundOff}
          aria-label={snap.sound ? t.soundOn : t.soundOff}
        >
          {snap.sound ? "🔊" : "🔇"}
        </button>
        {(isPlaying || isPaused) && (
          <button
            className="play-icon pause"
            type="button"
            onClick={isPaused ? handleResume : handlePause}
            title={isPaused ? t.resume : t.pause}
            aria-label={isPaused ? t.resume : t.pause}
          >
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
                <span>{t.level}</span>
                <strong>{formatNumber(snap.level, lang)}</strong>
              </div>
              <div className="hud-mod" title={modifierBlurb(mod, lang)}>
                <span>{mod.icon}</span>
                <b>{modifierLabel(mod, lang)}</b>
              </div>
              <div className="hud-chip score">
                <span>{t.score}</span>
                <strong>{formatNumber(snap.score, lang)}</strong>
              </div>
            </div>

            {snap.boss ? (
              <div className="boss-bar">
                <div className="boss-bar-head">
                  <span>☠️ {t.boss}</span>
                  <b>{formatNumber(Math.ceil(snap.boss.hp), lang)} / {formatNumber(snap.boss.maxHp, lang)}</b>
                </div>
                <div className="boss-track"><i style={{ width: `${bossPercent}%` }} /></div>
              </div>
            ) : (
              <div className="hud-progress" title={t.progress}>
                <i style={{ width: `${progress}%` }} />
              </div>
            )}

            <div className="hud-powerups">
              {powerups.map(([key, seconds]) => {
                const info = POWERUPS[key];
                return (
                  <span className="pu-chip" key={key} style={{ borderColor: info.color, color: info.color }}>
                    {info.icon} {Math.ceil(seconds)}s
                  </span>
                );
              })}
              {snap.shields > 0 && (
                <span className="pu-chip shield">🛡️ {formatNumber(snap.shields, lang)}</span>
              )}
            </div>

            {snap.combo > 1 && (
              <div className="combo-pill">
                <span>{t.combo} ×{formatNumber(snap.combo, lang)}</span>
                <i style={{ transform: `scaleX(${Math.max(0, Math.min(1, snap.comboFraction))})` }} />
              </div>
            )}

            {isPlaying && !hasMoved && (
              <div className="stage-hint">{t.startHint}</div>
            )}
          </div>
        )}

        {snap.status === "idle" && (
          <div className="play-overlay">
            <div className="play-card menu-card">
              <div className="play-kicker">{t.menuKicker}</div>
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
                  <strong>{formatNumber(snap.hearts, lang)} / {formatNumber(25, lang)}</strong>
                </div>
              </div>

              {snap.hearts < 25 && (
                <div className="heart-note">
                  ❤️ {t.heartNext} {formatTime(snap.heartTimer, lang)}
                </div>
              )}

              <label className="name-field">
                <span>{t.playerName}</span>
                <input
                  value={name}
                  onChange={handleName}
                  maxLength={16}
                  placeholder={t.namePlaceholder}
                  autoComplete="off"
                  spellCheck="false"
                />
              </label>

              <div className="play-actions">
                <button
                  className="play-btn primary"
                  type="button"
                  onClick={handlePlay}
                  disabled={snap.hearts <= 0}
                >
                  {snap.hearts > 0 ? t.play : t.noHearts}
                </button>
                <button className="play-btn" type="button" onClick={openShop}>
                  🛒 {t.shop}
                </button>
              </div>

              <div className="menu-links">
                <button type="button" onClick={handleInstall}>📲 {t.install}</button>
                <a href="/download/BlockBoom-1.1.0.apk" download>⬇️ {t.download}</a>
                {!standalone && <span className="menu-dot" aria-hidden="true">•</span>}
                {!standalone && <span className="menu-muted">{lang === "fa" ? "قابل نصب" : "Installable"}</span>}
              </div>

              <p className="play-credit">{t.credit}</p>
            </div>
          </div>
        )}

        {isPaused && (
          <div className="play-overlay">
            <div className="play-card small-card">
              <div className="play-kicker">PAUSED</div>
              <h2>{t.pausedTitle}</h2>
              <p className="play-lede">{t.pausedSub}</p>
              <div className="pause-stats">
                <span>{t.level} <b>{formatNumber(snap.level, lang)}</b></span>
                <span>{t.score} <b>{formatNumber(snap.score, lang)}</b></span>
                <span>{t.coins} <b>{formatNumber(snap.coins, lang)}</b></span>
              </div>
              <div className="play-actions">
                <button className="play-btn primary" type="button" onClick={handleResume}>▶ {t.resume}</button>
                <button className="play-btn" type="button" onClick={openShop}>🛒 {t.shop}</button>
                <button className="play-btn ghost" type="button" onClick={handleQuit}>🏠 {t.quit}</button>
              </div>
            </div>
          </div>
        )}

        {isComplete && (
          <div className="play-overlay">
            <div className="play-card small-card">
              <div className="play-kicker">{t.completeKicker}</div>
              <h2>{t.completeTitle}</h2>
              <p className="play-lede">
                {t.level} {formatNumber(completeInfo?.level ?? snap.level, lang)} — {t.completeSub}
              </p>
              <div className="menu-stats">
                <div>
                  <span>{t.score}</span>
                  <strong>{formatNumber(completeInfo?.score ?? snap.score, lang)}</strong>
                </div>
                <div>
                  <span>{t.coinsEarned}</span>
                  <strong>{formatNumber(completeInfo?.coins ?? snap.coins, lang)}</strong>
                </div>
                <div>
                  <span>{t.best}</span>
                  <strong>{formatNumber(completeInfo?.best ?? snap.bestScore, lang)}</strong>
                </div>
              </div>
              <div className="play-actions">
                <button className="play-btn primary" type="button" onClick={handleNext}>🚀 {t.nextLevel}</button>
                <button className="play-btn" type="button" onClick={openShop}>🛒 {t.shop}</button>
                <button className="play-btn ghost" type="button" onClick={handleQuit}>🏠 {t.home}</button>
              </div>
            </div>
          </div>
        )}

        {isOver && (
          <div className="play-overlay">
            <div className="play-card small-card">
              <div className="play-kicker">{t.overKicker}</div>
              <h2>{t.overTitle}</h2>
              <p className="play-lede">{t.overSub}</p>
              <div className="menu-stats">
                <div>
                  <span>{t.runScore}</span>
                  <strong>{formatNumber(overInfo?.score ?? snap.runScore, lang)}</strong>
                </div>
                <div>
                  <span>{t.best}</span>
                  <strong>{formatNumber(overInfo?.best ?? snap.bestScore, lang)}</strong>
                </div>
                <div>
                  <span>{t.coinsEarned}</span>
                  <strong>{formatNumber(overInfo?.coins ?? snap.coins, lang)}</strong>
                </div>
              </div>
              {snap.hearts < 25 && (
                <div className="heart-note">
                  ❤️ {t.heartNext} {formatTime(snap.heartTimer, lang)}
                </div>
              )}
              <div className="play-actions">
                <button
                  className="play-btn primary"
                  type="button"
                  onClick={handleRetry}
                  disabled={snap.hearts <= 0}
                >
                  {snap.hearts > 0 ? `❤️ ${t.retry}` : t.noHearts}
                </button>
                <button className="play-btn" type="button" onClick={openShop}>🛒 {t.shop}</button>
                <button className="play-btn ghost" type="button" onClick={handleQuit}>🏠 {t.home}</button>
              </div>
              <p className="play-version">Block Boom · 1.1</p>
            </div>
          </div>
        )}

        {shopOpen && (
          <div className="play-overlay shop-overlay">
            <div className="play-card shop-card">
              <div className="shop-head">
                <div>
                  <div className="play-kicker">UPGRADES</div>
                  <h2>{t.shopTitle}</h2>
                  <p className="play-lede">{t.shopSub}</p>
                </div>
                <div className="shop-head-right">
                  <div className="shop-coins">🪙 {formatNumber(snap.coins, lang)}</div>
                  <button className="play-icon" type="button" onClick={() => setShopOpen(false)} aria-label={t.close}>✕</button>
                </div>
              </div>

              <div className="shop-grid">
                {UPGRADE_DEFS.map((def) => {
                  const current = snap.levelUpgrades?.[def.id] ?? 0;
                  const locked = snap.level < def.unlock;
                  const maxed = current >= def.max;
                  const cost = upgradeCost(def.id, current);
                  const canBuy = !locked && !maxed && snap.coins >= cost;
                  return (
                    <div className={`shop-item${locked ? " is-locked" : ""}${maxed ? " is-maxed" : ""}`} key={def.id}>
                      <div className="shop-icon" aria-hidden="true">{def.icon}</div>
                      <div className="shop-info">
                        <div className="shop-name">
                          <span>{upgradeName(def, lang)}</span>
                          <small>{t.upgradeLevel} {formatNumber(current, lang)}/{formatNumber(def.max, lang)}</small>
                        </div>
                        <div className="shop-effect">{upgradeEffect(def.id, current, lang)}</div>
                        <div className="shop-next">
                          {maxed
                            ? t.maxed
                            : locked
                              ? `${t.unlockAt} ${formatNumber(def.unlock, lang)}`
                              : `${t.next}: ${upgradeEffect(def.id, current + 1, lang)}`}
                        </div>
                      </div>
                      <button
                        className="buy-btn"
                        type="button"
                        disabled={!canBuy}
                        onClick={() => handleBuy(def.id)}
                      >
                        {maxed
                          ? t.maxed
                          : locked
                            ? `🔒 ${t.locked}`
                            : cost === 0
                              ? t.free
                              : `${formatNumber(cost, lang)} 🪙`}
                      </button>
                    </div>
                  );
                })}
              </div>

              <button className="play-btn ghost shop-close" type="button" onClick={() => setShopOpen(false)}>
                {t.close}
              </button>
            </div>
          </div>
        )}
      </section>

      {toast && <div className="play-toast" role="status" aria-live="polite">{toast}</div>}
    </main>
  );
}
