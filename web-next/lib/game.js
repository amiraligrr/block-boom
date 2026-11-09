/* ==========================================================================
   Block Boom — web game engine
   A canvas reimplementation of the Android 1.1 loop: drag the cannon, break
   numbered blocks, chain combos, fight the Mother every tenth level.
   ========================================================================== */

export const PALETTE = [
  "#ff3b30", "#ff7a00", "#ffb300", "#ffd400", "#a8e10c", "#3ed400",
  "#00d98f", "#00d6c2", "#00c2ff", "#2e9bff", "#5b6bff", "#8b5cf6",
  "#c13bff", "#f03be0", "#ff3b9e", "#ff3b6b",
];

export const UPGRADE_DEFS = [
  { id: "damage", name: "قدرت ضربه", nameEn: "Impact", icon: "💥", base: 40, max: 2, unlock: 1, flat: 3000 },
  { id: "fireRate", name: "نواخت شلیک", nameEn: "Fire rate", icon: "🔫", base: 60, max: 15, unlock: 2 },
  { id: "multiBall", name: "تیر چندگانه", nameEn: "Multi shot", icon: "🎱", base: 90, max: 5, unlock: 4 },
  { id: "pierce", name: "نفوذ", nameEn: "Pierce", icon: "🏹", base: 140, max: 7, unlock: 8 },
  { id: "magnet", name: "آهنربای سکه", nameEn: "Coin magnet", icon: "🧲", base: 70, max: 15, unlock: 1 },
  { id: "bulletSpeed", name: "سرعت گلوله", nameEn: "Bullet speed", icon: "🚀", base: 50, max: 18, unlock: 1 },
];

export const MODIFIERS = {
  NONE: { id: "NONE", icon: "✨", label: "سطح عادی", labelEn: "Standard", blurb: "همه‌چیز مثل همیشه", blurbEn: "Everything as usual" },
  WIND: { id: "WIND", icon: "🌬️", label: "باد", labelEn: "Wind", blurb: "بلاک‌ها را باد می‌راند", blurbEn: "The wind pushes blocks around" },
  ICE: { id: "ICE", icon: "❄️", label: "یخ", labelEn: "Ice", blurb: "بعضی بلاک‌ها یخ‌اند؛ دوبار بزن", blurbEn: "Some blocks are frozen — hit twice" },
  ARMOR: { id: "ARMOR", icon: "🛡️", label: "زره", labelEn: "Armor", blurb: "زرهِ بعضی بلاک‌ها اول باید بشکند", blurbEn: "Break the armor plating first" },
  RUSH: { id: "RUSH", icon: "⚡", label: "شتاب", labelEn: "Rush", blurb: "بلاک‌ها خیلی سریع‌تر می‌آیند", blurbEn: "Blocks arrive much faster" },
  BOULDER: { id: "BOULDER", icon: "🪨", label: "صخره", labelEn: "Boulder", blurb: "چند بلاک خیلی سفت‌اند", blurbEn: "A few blocks are extra tough" },
  LIFT: { id: "LIFT", icon: "🕊️", label: "بالابر", labelEn: "Lift", blurb: "باد بلاک‌ها را بالا می‌برد", blurbEn: "The wind lifts blocks upward" },
  BATTLE: { id: "BATTLE", icon: "☠️", label: "باس فایت", labelEn: "Boss fight", blurb: "بلاک مادر — تنها هدف این مرحله", blurbEn: "The Mother Block — the only target" },
};

export const POWERUPS = {
  FREEZE: { icon: "❄️", label: "FREEZE!", duration: 5, color: "#22d3ee" },
  DOUBLE_DAMAGE: { icon: "⚡", label: "DOUBLE DAMAGE!", duration: 8, color: "#fbbf24" },
  RAPID_FIRE: { icon: "🔥", label: "RAPID FIRE!", duration: 6, color: "#fb7185" },
  COIN_RUSH: { icon: "🌟", label: "COIN RUSH!", duration: 10, color: "#fbbf24" },
  GHOST: { icon: "👻", label: "GHOST MODE!", duration: 7, color: "#b39dff" },
  SHIELD: { icon: "🛡️", label: "SHIELD!", duration: 0, color: "#7dd3fc" },
  NUKE: { icon: "💥", label: "NUKE!", duration: 0, color: "#fb7185" },
};

const POWERUP_KEYS = Object.keys(POWERUPS);
const MAX_BLOCK_HP = 240;
const MAX_BLOCKS_PER_LEVEL = 72;
const MAX_GRAVITY = 290;
const MAX_FALL_SPEED = 1400;
const MIN_SPAWN_INTERVAL = 0.16;
const BOSS_EVERY = 10;
const COMBO_WINDOW = 1;
const BOUNCE_HEIGHT_FRACTION = 0.32;
const HEART_MAX = 25;
const HEART_REGEN_MS = 90 * 60 * 1000;
const HEART_REGEN_AMOUNT = 5;

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const lerp = (a, b, t) => a + (b - a) * t;
const rand = (a = 0, b = 1) => a + Math.random() * (b - a);
const randInt = (a, b) => Math.floor(rand(a, b + 1));

export function defaultSave() {
  return {
    version: 1,
    coins: 0,
    level: 1,
    bestScore: 0,
    hearts: HEART_MAX,
    lastHeartAt: Date.now(),
    name: "",
    sound: true,
    totalRuns: 0,
    totalBlocks: 0,
    upgrades: {
      damage: 1,
      fireRate: 2,
      multiBall: 1,
      pierce: 0,
      magnet: 0,
      bulletSpeed: 8,
    },
  };
}

export function loadSave() {
  try {
    const raw = localStorage.getItem("bb_web_save_v1");
    if (!raw) return defaultSave();
    const parsed = JSON.parse(raw);
    const base = defaultSave();
    return {
      ...base,
      ...parsed,
      upgrades: { ...base.upgrades, ...(parsed.upgrades || {}) },
    };
  } catch {
    return defaultSave();
  }
}

export function persistSave(save) {
  try {
    localStorage.setItem("bb_web_save_v1", JSON.stringify(save));
  } catch {
    /* storage can be full or blocked; the run still works */
  }
}

export function refreshHearts(save) {
  const now = Date.now();
  if (save.hearts >= HEART_MAX) {
    save.hearts = HEART_MAX;
    save.lastHeartAt = now;
    return false;
  }
  const elapsed = Math.max(0, now - Number(save.lastHeartAt || now));
  const grants = Math.floor(elapsed / HEART_REGEN_MS);
  if (grants <= 0) return false;
  save.hearts = Math.min(HEART_MAX, save.hearts + grants * HEART_REGEN_AMOUNT);
  save.lastHeartAt += grants * HEART_REGEN_MS;
  if (save.hearts >= HEART_MAX) save.lastHeartAt = now;
  return true;
}

export function heartTimerMs(save) {
  if (save.hearts >= HEART_MAX) return 0;
  return Math.max(0, Number(save.lastHeartAt || Date.now()) + HEART_REGEN_MS - Date.now());
}

export function upgradeCost(id, level) {
  const def = UPGRADE_DEFS.find((u) => u.id === id);
  if (!def || level >= def.max) return Infinity;
  if (def.flat) return def.flat;
  if (level <= 0) return 0;
  return Math.ceil(def.base * Math.pow(level, 1.65) * 1.35);
}

export function upgradeEffect(id, level, lang = "fa") {
  const raw = Math.max(0, Number(level) || 0);
  const lv = Math.max(1, raw);
  const en = lang === "en";
  switch (id) {
    case "damage": return en ? `Impact ${lv}` : `ضربه ${lv}`;
    case "fireRate": {
      const rate = formatFloat(2.6 + Math.max(0, lv - 2) * 0.55);
      return en ? `${rate} shots/s` : `${rate} شلیک در ثانیه`;
    }
    case "multiBall": return en ? `${lv} balls` : `${lv} توپ`;
    case "pierce": return raw === 0
      ? (en ? "No pierce" : "بدون نفوذ")
      : (en ? `Pierce ${raw} blocks` : `نفوذ از ${raw} مانع`);
    case "magnet": return raw === 0
      ? (en ? "No magnet" : "بدون آهنربا")
      : (en ? `Magnet radius ${raw * 22}` : `شعاع جذب ${raw * 22}`);
    case "bulletSpeed": return en ? `Speed ${lv * 38}` : `سرعت ${lv * 38}`;
    default: return "";
  }
}

function formatFloat(v) {
  return Number.isInteger(v) ? String(v) : v.toFixed(2).replace(/0+$/, "").replace(/\.$/, "");
}

export function cannonStats(upgrades) {
  const damage = clamp(Number(upgrades.damage) || 1, 1, 2);
  const fireRateLevel = clamp(Number(upgrades.fireRate) || 2, 0, 15);
  const fireRate = 2.6 + Math.max(0, fireRateLevel - 2) * 0.55;
  const multiBall = Math.max(1, clamp(Number(upgrades.multiBall) || 1, 0, 5));
  const pierce = clamp(Number(upgrades.pierce) || 0, 0, 7);
  const bulletSpeedLevel = Math.max(1, clamp(Number(upgrades.bulletSpeed) || 8, 0, 18));
  const magnet = clamp(Number(upgrades.magnet) || 0, 0, 15);
  const powerIndex = (fireRate * multiBall * damage * (pierce + 1)) / 2.6;
  return {
    damage,
    fireRate,
    multiBall,
    pierce,
    bulletSpeed: 38 * bulletSpeedLevel,
    magnetRadius: 22 * magnet,
    powerIndex,
    buildArmourFactor: 1 + 0.05 * Math.pow(Math.max(0, powerIndex - 1), 0.8),
  };
}

export function generateBlockHP(level) {
  let base;
  if (level <= 5) base = 4 + (level - 1) * 2;
  else if (level <= 15) base = 12 + (level - 5) * 3;
  else if (level <= 30) base = 42 + (level - 15) * 4;
  else if (level <= 50) base = 102 + (level - 30) * 5;
  else base = 222 + (level - 50) * 16;
  const variance = rand(0.9, 1.1);
  return clamp(Math.floor(base * variance), 1, MAX_BLOCK_HP);
}

export function levelConfig(level, upgrades) {
  const extra = Math.max(0, level - 1);
  const tier = Math.floor(extra / 10);
  const lateCreep = Math.log(1 + extra / 22) * 8;
  const stats = cannonStats(upgrades);
  const gravity = Math.min(52 + extra * 1.45 + tier * 3 + lateCreep, MAX_GRAVITY);
  const maxFall = Math.min(280 + extra * 4.5 + tier * 14 + lateCreep * 5, MAX_FALL_SPEED);
  const spawnInterval = clamp(1.2 - extra * 0.022, MIN_SPAWN_INTERVAL, 1.2);
  const hpMultiplier = Math.min(1 + extra * 0.0085 + tier * 0.009, 2.3);
  const blockCount = Math.min(Math.min(8 + extra * 2, 40) + Math.floor(extra / 5) * 3, MAX_BLOCKS_PER_LEVEL);
  const armourBudget = extra < 24
    ? 0
    : Math.floor(Math.pow((extra - 24) / 4.2, 1.2) * stats.buildArmourFactor);
  const boss = level % BOSS_EVERY === 0;
  return {
    blockCount,
    gravity,
    maxFall,
    spawnInterval,
    hpMultiplier,
    armourBudget,
    boss,
    isMotherLevel: boss,
    stats,
  };
}

export function modifierFor(level, boss) {
  if (boss) return MODIFIERS.BATTLE;
  if (level <= 2) return MODIFIERS.NONE;
  const pool = [MODIFIERS.WIND, MODIFIERS.ICE, MODIFIERS.ARMOR, MODIFIERS.RUSH, MODIFIERS.BOULDER, MODIFIERS.LIFT];
  return pool[hashLevel(level) % pool.length];
}

function hashLevel(level) {
  let x = Math.imul(level, 2654435761);
  x ^= x >>> 13;
  x = Math.imul(x, 1274126177);
  x ^= x >>> 16;
  return x & 0x7fffffff;
}

function blockSizeFor(hp, boss = false, boulder = false) {
  if (boss) return 104;
  if (boulder) return 92;
  if (hp <= 5) return 52;
  if (hp <= 10) return 58;
  if (hp <= 20) return 64;
  if (hp <= 50) return 72;
  if (hp <= 100) return 80;
  return 88;
}

function comboMultiplier(count) {
  if (count >= 30) return 5;
  if (count >= 20) return 4;
  if (count >= 10) return 3;
  if (count >= 5) return 2;
  return 1;
}

function comboBonus(count) {
  return count * 5 * comboMultiplier(count);
}

function powerUpForRoll(roll) {
  if (roll < 4) return "NUKE";
  if (roll < 8) return "SHIELD";
  if (roll < 26) return "FREEZE";
  if (roll < 48) return "DOUBLE_DAMAGE";
  if (roll < 70) return "RAPID_FIRE";
  if (roll < 88) return "GHOST";
  return "COIN_RUSH";
}

function randomPowerUp() {
  return powerUpForRoll(Math.random() * 100);
}

function randomShape() {
  const roll = Math.random() * 100;
  if (roll < 38) return "square";
  if (roll < 60) return "circle";
  if (roll < 80) return "hexagon";
  if (roll < 91) return "triangle";
  return "star";
}

function shapeCanSplit(shape) {
  return shape === "square" || shape === "circle" || shape === "hexagon";
}

function roundRectPath(ctx, x, y, w, h, r) {
  const rr = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + rr, y);
  ctx.arcTo(x + w, y, x + w, y + h, rr);
  ctx.arcTo(x + w, y + h, x, y + h, rr);
  ctx.arcTo(x, y + h, x, y, rr);
  ctx.arcTo(x, y, x + w, y, rr);
  ctx.closePath();
}

function circleRect(cx, cy, r, x, y, w, h) {
  const nx = clamp(cx, x, x + w);
  const ny = clamp(cy, y, y + h);
  const dx = cx - nx;
  const dy = cy - ny;
  return dx * dx + dy * dy <= r * r;
}

function lighten(hex, amount) {
  const n = parseInt(hex.slice(1), 16);
  let r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
  r = clamp(Math.round(r + (255 - r) * amount), 0, 255);
  g = clamp(Math.round(g + (255 - g) * amount), 0, 255);
  b = clamp(Math.round(b + (255 - b) * amount), 0, 255);
  return `rgb(${r},${g},${b})`;
}

function darken(hex, amount) {
  const n = parseInt(hex.slice(1), 16);
  let r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
  r = clamp(Math.round(r * (1 - amount)), 0, 255);
  g = clamp(Math.round(g * (1 - amount)), 0, 255);
  b = clamp(Math.round(b * (1 - amount)), 0, 255);
  return `rgb(${r},${g},${b})`;
}

export class GameEngine {
  constructor(canvas, options = {}) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.save = options.save || defaultSave();
    this.onState = options.onState || (() => {});
    this.onEvent = options.onEvent || (() => {});
    this.onSave = options.onSave || (() => {});
    this.lang = options.lang || "fa";

    this.status = "idle"; // idle | playing | paused | complete | hit
    this.paused = false;
    this.level = 1;
    this.score = 0;
    this.runScore = 0;
    this.coinsInLevel = 0;
    this.runCoins = 0;
    this.blocksDestroyed = 0;
    this.totalBlocks = 0;
    this.levelModifier = MODIFIERS.NONE;
    this.cfg = levelConfig(1, this.save.upgrades);
    this.blocksToSpawn = 0;
    this.spawnTicking = false;
    this.nextSpawnIn = 1.4;
    this.spawnInterval = 1;
    this.motherPendingSpawn = false;
    this.gameTime = 0;
    this.idleTime = 0;
    this.levelElapsed = 0;
    this.fireCooldown = 0;
    this.hitGrace = 0;
    this.shake = 0;
    this.flash = 0;
    this.flashColor = "#34d399";
    this.coinSaveTimer = 0;
    this.lastStateAt = 0;
    this.lastFrame = 0;
    this.raf = 0;

    this.blocks = [];
    this.balls = [];
    this.coins = [];
    this.bonuses = [];
    this.drops = [];
    this.particles = [];
    this.popups = [];
    this.explosions = [];
    this.stars = [];

    this.combo = { count: 0, lastKill: -100, fraction: 0, best: 0 };
    this.powerups = {};
    for (const key of POWERUP_KEYS) this.powerups[key] = 0;
    this.shields = 0;

    this.cannon = {
      x: 0,
      targetX: 0,
      radius: 26,
      baseY: 0,
      recoil: 0,
      pulse: 0,
    };

    this.stats = cannonStats(this.save.upgrades);
    this.fieldW = 0;
    this.fieldH = 0;
    this.dpr = 1;

    refreshHearts(this.save);
    this.resize();
    this.bindAudio();
    this.startLoop();
    this.emitState(true);
  }

  destroy() {
    cancelAnimationFrame(this.raf);
    this.audio?.close?.();
    this.audio = null;
  }

  /* ── layout ─────────────────────────────────────────────────────────── */

  resize() {
    const parent = this.canvas.parentElement;
    if (!parent) return;
    const rect = parent.getBoundingClientRect();
    const w = Math.max(280, Math.round(rect.width));
    const h = Math.max(360, Math.round(rect.height));
    this.dpr = Math.min(2, window.devicePixelRatio || 1);
    this.canvas.width = Math.round(w * this.dpr);
    this.canvas.height = Math.round(h * this.dpr);
    this.canvas.style.width = `${w}px`;
    this.canvas.style.height = `${h}px`;
    this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    const first = this.fieldW === 0;
    this.fieldW = w;
    this.fieldH = h;
    this.cannon.radius = 26;
    this.cannon.baseY = h - 96;
    if (first) {
      this.cannon.x = w / 2;
      this.cannon.targetX = w / 2;
    } else {
      this.cannon.x = clamp(this.cannon.x, this.cannon.radius, w - this.cannon.radius);
      this.cannon.targetX = clamp(this.cannon.targetX, this.cannon.radius, w - this.cannon.radius);
    }
    this.generateStars();
  }

  generateStars() {
    this.stars = Array.from({ length: 54 }, () => ({
      x: Math.random() * this.fieldW,
      y: Math.random() * (this.fieldH - 120),
      r: rand(0.8, 2.2),
      phase: Math.random() * Math.PI * 2,
    }));
  }

  pointerMove(clientX) {
    const rect = this.canvas.getBoundingClientRect();
    this.setCannonTarget(clientX - rect.left);
  }

  setCannonTarget(x) {
    if (this.status !== "playing") return;
    this.cannon.targetX = clamp(x, this.cannon.radius, this.fieldW - this.cannon.radius);
  }

  /* ── run / level lifecycle ─────────────────────────────────────────── */

  startRun() {
    refreshHearts(this.save);
    if (this.save.hearts <= 0) {
      this.emit("noHearts");
      this.emitState(true);
      return false;
    }
    if (this.save.hearts >= HEART_MAX) this.save.lastHeartAt = Date.now();
    this.save.hearts -= 1;
    this.save.totalRuns = (this.save.totalRuns || 0) + 1;
    this.runScore = 0;
    this.runCoins = 0;
    this.runBlocks = 0;
    this.runBestCombo = 0;
    this.startLevel(clamp(Number(this.save.level) || 1, 1, 9999));
    this.persist();
    this.emit("runStarted");
    return true;
  }

  startLevel(level) {
    this.level = clamp(Number(level) || 1, 1, 9999);
    this.save.level = this.level;
    this.status = "playing";
    this.paused = false;
    this.score = 0;
    this.coinsInLevel = 0;
    this.blocksDestroyed = 0;
    this.blocksToSpawn = 0;
    this.spawnTicking = false;
    this.nextSpawnIn = 1.4;
    this.gameTime = 0;
    this.levelElapsed = 0;
    this.fireCooldown = 0;
    this.hitGrace = 0;
    this.shake = 0;
    this.flash = 0;
    this.blocks = [];
    this.balls = [];
    this.coins = [];
    this.bonuses = [];
    this.drops = [];
    this.particles = [];
    this.popups = [];
    this.explosions = [];
    this.combo = { count: 0, lastKill: -100, fraction: 0, best: 0 };
    for (const key of POWERUP_KEYS) this.powerups[key] = 0;
    this.shields = 0;
    this.stats = cannonStats(this.save.upgrades);
    this.cfg = levelConfig(this.level, this.save.upgrades);
    this.levelModifier = modifierFor(this.level, this.cfg.isMotherLevel);
    this.blocksToSpawn = this.cfg.isMotherLevel ? 0 : this.cfg.blockCount;
    this.totalBlocks = this.cfg.isMotherLevel ? 1 : this.cfg.blockCount;
    this.spawnInterval = this.levelModifier.id === "RUSH"
      ? this.cfg.spawnInterval * (0.55 - 0.21 * Math.min(1, this.level / 400))
      : this.cfg.spawnInterval;
    this.spawnTicking = !this.cfg.isMotherLevel;
    this.motherPendingSpawn = this.cfg.isMotherLevel;
    this.cannon.x = this.fieldW / 2;
    this.cannon.targetX = this.fieldW / 2;
    this.lastFrame = performance.now();
    this.persist();
    this.emitState(true);
    this.emit("levelStarted", { level: this.level, modifier: this.levelModifier.id });
  }

  nextLevel() {
    if (this.status !== "complete") return;
    this.startLevel(clamp(this.save.level, 1, 9999));
  }

  retry() {
    return this.startRun();
  }

  pause() {
    if (this.status !== "playing") return;
    this.paused = true;
    this.status = "paused";
    this.emitState(true);
  }

  resume() {
    if (this.status !== "paused") return;
    this.paused = false;
    this.status = "playing";
    this.lastFrame = performance.now();
    this.emitState(true);
  }

  togglePause() {
    if (this.status === "playing") this.pause();
    else if (this.status === "paused") this.resume();
  }

  quitToMenu() {
    this.status = "idle";
    this.paused = false;
    this.blocks = [];
    this.balls = [];
    this.persist();
    this.emitState(true);
  }

  refreshStats() {
    this.stats = cannonStats(this.save.upgrades);
  }

  refreshHearts() {
    if (refreshHearts(this.save)) this.persist();
    this.emitState(true);
  }

  buyUpgrade(id) {
    const def = UPGRADE_DEFS.find((u) => u.id === id);
    if (!def) return false;
    const level = clamp(Number(this.save.upgrades[id]) || 0, 0, def.max);
    if (this.save.level < def.unlock) return false;
    if (level >= def.max) return false;
    const cost = upgradeCost(id, level);
    if (!Number.isFinite(cost) || this.save.coins < cost) return false;
    this.save.coins -= cost;
    this.save.upgrades[id] = level + 1;
    this.refreshStats();
    this.persist();
    this.emit("upgradeBought", { id, level: level + 1 });
    this.emitState(true);
    return true;
  }

  setName(name) {
    this.save.name = String(name || "").trim().slice(0, 16);
    this.persist();
    this.emitState(true);
  }

  setSound(on) {
    this.save.sound = !!on;
    if (on) this.initAudio();
    this.persist();
    this.emitState(true);
  }

  /* ── audio ─────────────────────────────────────────────────────────── */

  bindAudio() {
    this.audio = null;
    this.initAudio = () => {
      if (this.audio) return;
      try {
        const Ctx = window.AudioContext || window.webkitAudioContext;
        if (Ctx) this.audio = new Ctx();
      } catch {
        this.audio = null;
      }
    };
  }

  sound(name) {
    if (!this.save.sound) return;
    this.initAudio?.();
    if (!this.audio) return;
    const ctx = this.audio;
    if (ctx.state === "suspended") ctx.resume().catch(() => {});
    const now = ctx.currentTime;
    const tone = (freq, dur, type = "sine", vol = 0.04, slide = 0) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type;
      osc.frequency.setValueAtTime(freq, now);
      if (slide) osc.frequency.exponentialRampToValueAtTime(Math.max(30, freq + slide), now + dur);
      gain.gain.setValueAtTime(vol, now);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + dur);
      osc.connect(gain).connect(ctx.destination);
      osc.start(now);
      osc.stop(now + dur + 0.02);
    };
    switch (name) {
      case "fire": tone(520, 0.045, "square", 0.018, -180); break;
      case "hit": tone(190, 0.06, "triangle", 0.035, -60); break;
      case "destroy": tone(120, 0.12, "sawtooth", 0.045, -60); break;
      case "coin": tone(880, 0.07, "sine", 0.03, 220); break;
      case "power": tone(520, 0.1, "triangle", 0.04, 380); break;
      case "level": tone(523, 0.12, "sine", 0.05, 0); setTimeout(() => tone(784, 0.18, "sine", 0.05, 0), 110); break;
      case "heart": tone(180, 0.25, "sawtooth", 0.06, -100); break;
      case "boss": tone(90, 0.4, "sawtooth", 0.06, 40); break;
      default: break;
    }
  }

  /* ── loop ──────────────────────────────────────────────────────────── */

  startLoop() {
    const frame = (now) => {
      const dt = Math.min(0.05, Math.max(0, (now - this.lastFrame) / 1000 || 0.016));
      this.lastFrame = now;
      if (this.status === "playing" && !this.paused) this.update(dt);
      this.render(dt);
      if (now - this.lastStateAt > 100) {
        this.lastStateAt = now;
        this.emitState();
      }
      this.raf = requestAnimationFrame(frame);
    };
    this.lastFrame = performance.now();
    this.raf = requestAnimationFrame(frame);
  }

  emit(type, payload = {}) {
    this.onEvent({ type, ...payload });
  }

  persist() {
    persistSave(this.save);
    this.onSave(this.save);
  }

  emitState(force = false) {
    if (!force && this.lastStateAt && performance.now() - this.lastStateAt < 80) return;
    this.lastStateAt = performance.now();
    this.onState(this.snapshot());
  }

  snapshot() {
    const mother = this.blocks.find((b) => b.isMother);
    return {
      status: this.status,
      paused: this.paused,
      level: this.level,
      score: this.score,
      runScore: this.runScore,
      coins: this.save.coins,
      coinsInLevel: this.coinsInLevel,
      hearts: this.save.hearts,
      heartTimer: heartTimerMs(this.save),
      bestScore: this.save.bestScore,
      combo: this.combo.count,
      comboFraction: this.combo.fraction,
      comboBest: this.combo.best,
      shields: this.shields,
      powerups: { ...this.powerups },
      blocksDestroyed: this.blocksDestroyed,
      totalBlocks: this.totalBlocks,
      modifier: this.levelModifier.id,
      boss: mother ? { hp: mother.hp, maxHp: mother.maxHp } : null,
      bossLevel: this.cfg?.isMotherLevel || false,
      name: this.save.name,
      sound: this.save.sound,
      levelUpgrades: { ...this.save.upgrades },
      canAfford: Object.fromEntries(UPGRADE_DEFS.map((d) => [d.id, this.save.coins >= upgradeCost(d.id, this.save.upgrades[d.id] || 0)])),
    };
  }

  /* ── update ────────────────────────────────────────────────────────── */

  update(dt) {
    this.gameTime += dt;
    this.levelElapsed += dt;
    if (this.hitGrace > 0) this.hitGrace = Math.max(0, this.hitGrace - dt);

    if (this.motherPendingSpawn && this.fieldW > 0) {
      this.motherPendingSpawn = false;
      this.spawnMother();
    }

    this.updateCannon(dt);
    this.updateSpawning(dt);
    this.updateBalls(dt);
    this.updateBlocks(dt);
    this.updateBonuses(dt);
    this.updateCoins(dt);
    this.updateDrops(dt);
    this.updateParticles(dt);
    this.updatePopups(dt);
    this.updatePowerups(dt);
    this.combo.fraction = this.combo.count > 0
      ? clamp(1 - (this.gameTime - this.combo.lastKill) / COMBO_WINDOW, 0, 1)
      : 0;
    if (this.combo.count > 0 && this.gameTime - this.combo.lastKill > COMBO_WINDOW) {
      this.combo.count = 0;
      this.combo.fraction = 0;
    }
    if (this.shake > 0) this.shake = Math.max(0, this.shake - dt * 3.5);
    if (this.flash > 0) this.flash = Math.max(0, this.flash - dt * 2);
    this.coinSaveTimer += dt;
    if (this.coinSaveTimer > 1) {
      this.coinSaveTimer = 0;
      this.persist();
    }
    this.checkLevelComplete();
  }

  updateCannon(dt) {
    const c = this.cannon;
    c.pulse += dt;
    c.recoil *= Math.exp(-10 * dt);
    const t = 1 - Math.exp(-18 * dt);
    c.x += (c.targetX - c.x) * t;
    c.x = clamp(c.x, c.radius, this.fieldW - c.radius);

    this.fireCooldown -= dt;
    if (this.fireCooldown <= 0) {
      this.fire();
      const rate = this.stats.fireRate * (this.hasPowerUp("RAPID_FIRE") ? 2 : 1);
      this.fireCooldown = 1 / Math.max(0.1, rate);
    }
  }

  fire() {
    const double = this.hasPowerUp("DOUBLE_DAMAGE") ? 2 : 1;
    const damage = this.stats.damage * double;
    const multi = this.stats.multiBall;
    const speed = this.stats.bulletSpeed;
    this.cannon.recoil = 1;
    this.sound("fire");
    const muzzleY = this.cannon.baseY - this.cannon.radius;
    for (let i = 0; i < multi; i++) {
      const spread = multi === 1 ? 0 : (i - (multi - 1) / 2) * (0.05 + multi * 0.004);
      this.balls.push({
        x: this.cannon.x,
        y: muzzleY,
        vx: Math.sin(spread) * speed,
        vy: -Math.cos(spread) * speed,
        radius: 5,
        damage,
        pierce: this.stats.pierce,
        age: 0,
        trail: [],
        isFlaming: this.hasPowerUp("RAPID_FIRE"),
        isSpectral: this.hasPowerUp("GHOST"),
        lastHitBlock: null,
      });
    }
    this.spawnParticles(this.cannon.x, muzzleY, "#22d3ee", 3, 40, 0.18);
  }

  updateSpawning(dt) {
    if (this.blocksToSpawn <= 0) return;
    if (this.blocks.length >= this.maxBlocksAlive()) {
      this.nextSpawnIn = Math.min(this.nextSpawnIn, 0.12);
      return;
    }
    this.nextSpawnIn -= dt;
    if (this.nextSpawnIn > 0) return;
    this.nextSpawnIn = this.spawnInterval * rand(0.82, 1.12);
    if (Math.random() < 0.03) {
      this.spawnBonus();
      return;
    }
    this.spawnBlockSmart();
    this.blocksToSpawn -= 1;
    if (this.blocksToSpawn <= 0) this.spawnTicking = false;
  }

  maxBlocksAlive() {
    return Math.min(8 + Math.floor(this.level / 25), 16);
  }

  spawnBlockSmart() {
    const hp = Math.floor(generateBlockHP(this.level) * this.cfg.hpMultiplier);
    const size = blockSizeFor(hp);
    let chosenX = 0;
    for (let attempt = 0; attempt < 7; attempt++) {
      chosenX = rand(0, Math.max(0, this.fieldW - size));
      let ok = true;
      for (const b of this.blocks) {
        if (b.y < 140 && Math.abs((b.x + b.w / 2) - (chosenX + size / 2)) < (b.w + size) * 0.55) {
          ok = false;
          break;
        }
      }
      if (ok) break;
    }
    this.spawnBlockAt(chosenX, hp, size);
  }

  spawnBlockAt(x, hp, size, opts = {}) {
    const block = {
      x,
      y: opts.y ?? -size,
      w: opts.w ?? size,
      h: opts.h ?? size,
      vx: opts.vx ?? 0,
      vy: opts.vy ?? 0,
      hp,
      maxHp: hp,
      scoreValue: hp,
      shape: opts.shape || randomShape(),
      colorIndex: opts.colorIndex ?? randInt(0, PALETTE.length - 1),
      colorSeed: Math.random(),
      shakePhase: Math.random() * Math.PI * 2,
      isLucky: opts.isLucky ?? (Math.random() < 0.11),
      isBoss: false,
      isMother: false,
      isSatellite: false,
      isFragment: !!opts.isFragment,
      isIce: false,
      hasArmor: false,
      armorHp: 0,
      armorMaxHp: 0,
      isBoulder: false,
      bounceCount: 0,
      isHoming: false,
      lastStandTimer: 0,
      chargeDir: 1,
      chargeElapsed: 0,
      isCharging: false,
      hitFlash: 0,
      shakeTime: 0,
    };

    const depthShare = Math.min(1, this.level / 300);
    if (this.levelModifier.id === "ICE" && Math.random() < 0.34 + 0.22 * depthShare) {
      block.isIce = true;
    }
    if (this.levelModifier.id === "ARMOR" && Math.random() < 0.3 + 0.24 * depthShare) {
      block.hasArmor = true;
      block.armorHp = this.armourFor(hp);
      block.armorMaxHp = block.armorHp;
    }
    if (this.levelModifier.id === "BOULDER" && Math.random() < 0.16) {
      block.isBoulder = true;
      block.maxHp = Math.min(hp * 3, MAX_BLOCK_HP);
      block.hp = block.maxHp;
      block.scoreValue = block.maxHp;
      block.w = block.h = blockSizeFor(block.maxHp, false, true);
    }
    if (this.cfg.armourBudget > 0 && !block.hasArmor && Math.random() < 0.55) {
      block.hasArmor = true;
      block.armorHp = this.armourFor(hp) + this.cfg.armourBudget;
      block.armorMaxHp = block.armorHp;
    }
    this.blocks.push(block);
  }

  armourFor(hp) {
    return Math.max(2, Math.min(24, Math.floor(hp / 6))) + this.cfg.armourBudget;
  }

  spawnMother() {
    const w = Math.min(this.fieldW * 0.46, 150);
    const h = w * 0.82;
    const dps = this.stats.fireRate * this.stats.multiBall * this.stats.damage * (this.stats.pierce + 1);
    const ttk = Math.min(30, 3 + 0.55 * Math.sqrt(this.level));
    const hp = clamp(Math.floor(dps * 0.85 * ttk), 24, 60000);
    const mother = {
      x: (this.fieldW - w) / 2,
      y: -h,
      w,
      h,
      vx: 0,
      vy: 0,
      hp,
      maxHp: hp,
      scoreValue: hp * 3,
      shape: "hexagon",
      colorIndex: 14,
      colorSeed: Math.random(),
      shakePhase: Math.random() * Math.PI * 2,
      isLucky: false,
      isBoss: false,
      isMother: true,
      isSatellite: false,
      isFragment: false,
      isIce: false,
      hasArmor: false,
      armorHp: 0,
      armorMaxHp: 0,
      isBoulder: false,
      bounceCount: 0,
      isHoming: false,
      lastStandTimer: 0,
      chargeDir: 1,
      chargeElapsed: 0,
      isCharging: false,
      hitFlash: 0,
      shakeTime: 0,
    };
    this.blocks.push(mother);
    this.sound("boss");
    this.pushPopup(this.fieldW / 2, this.fieldH * 0.34, "☠️ باس فایت!", "#fb7185", 1.8, true);
    this.pushPopup(this.fieldW / 2, this.fieldH * 0.34 + 34, "بلاک مادر — هر بار به کف می‌خورد بچه می‌سازد", "#fbbf24", 2.6, false);
    this.emit("boss", { level: this.level });
    this.emitState(true);
  }

  motherAlive() {
    return this.blocks.some((b) => b.isMother);
  }

  spawnSatellites(mother) {
    let alive = 0;
    for (const b of this.blocks) if (b.isSatellite) alive++;
    const room = 6 - alive;
    if (room <= 0) return;
    const size = Math.min(this.fieldW * 0.13, 38);
    const levelHp = generateBlockHP(this.level);
    for (let i = 0; i < Math.min(2, room); i++) {
      const side = i % 2 === 0 ? -1 : 1;
      const margin = 6;
      const minX = Math.min(margin, this.fieldW * 0.5);
      const maxX = Math.max(minX, this.fieldW - size - margin);
      const x = clamp(mother.x + mother.w / 2 + side * (mother.w * 0.62) - size / 2 + rand(-10, 10), minX, maxX);
      const dir = x <= minX + 0.5 ? 1 : x >= maxX - 0.5 ? -1 : side;
      const hp = Math.max(1, Math.floor(levelHp * 0.3));
      this.blocks.push({
        x, y: this.cannon.baseY - size, w: size, h: size,
        vx: dir * rand(30, 70), vy: 14,
        hp, maxHp: hp, scoreValue: hp,
        shape: randomShape(), colorIndex: mother.colorIndex,
        colorSeed: Math.random(), shakePhase: Math.random() * Math.PI * 2,
        isLucky: false, isBoss: false, isMother: false, isSatellite: true,
        isFragment: false, isIce: false, hasArmor: false, armorHp: 0, armorMaxHp: 0,
        isBoulder: false, bounceCount: 0, isHoming: false, lastStandTimer: 0,
        chargeDir: 1, chargeElapsed: 0, isCharging: false, hitFlash: 0, shakeTime: 0,
      });
    }
    this.sound("hit");
  }

  spawnBonus() {
    const size = 34;
    this.bonuses.push({
      x: rand(0, Math.max(0, this.fieldW - size)),
      y: -size,
      vx: rand(-40, 40),
      vy: 26 + rand(0, 12),
      size,
      spin: rand(150, 320),
      rotation: Math.random() * Math.PI * 2,
      type: randomPowerUp(),
      age: 0,
    });
  }

  spawnDrop(bonus) {
    this.drops.push({
      x: bonus.x + bonus.size / 2 - 16,
      y: bonus.y + bonus.size / 2 - 16,
      vx: rand(-80, 80),
      vy: -rand(100, 200),
      size: 32,
      type: bonus.type,
      rotation: Math.random() * Math.PI * 2,
      spin: rand(180, 360),
      age: 0,
    });
  }

  /* ── balls ─────────────────────────────────────────────────────────── */

  updateBalls(dt) {
    for (let i = this.balls.length - 1; i >= 0; i--) {
      const b = this.balls[i];
      b.age += dt;
      const prevX = b.x;
      const prevY = b.y;
      b.x += b.vx * dt;
      b.y += b.vy * dt;
      b.trail.push({ x: b.x, y: b.y });
      if (b.trail.length > 7) b.trail.shift();

      const bonusConsumed = this.hitBonus(b);
      const out = b.y < -40 || b.y > this.fieldH + 40 || b.x < -40 || b.x > this.fieldW + 40;
      const hitResult = this.hitBlocks(b, prevX, prevY);
      if (out || bonusConsumed || !hitResult) {
        this.balls.splice(i, 1);
      }
    }
  }

  hitBonus(ball) {
    for (let i = this.bonuses.length - 1; i >= 0; i--) {
      const bonus = this.bonuses[i];
      if (!circleRect(ball.x, ball.y, ball.radius, bonus.x, bonus.y, bonus.size, bonus.size)) continue;
      this.spawnDrop(bonus);
      this.bonuses.splice(i, 1);
      this.sound("power");
      this.spawnParticles(bonus.x + bonus.size / 2, bonus.y + bonus.size / 2, POWERUPS[bonus.type].color, 10, 160, 0.45);
      return ball.pierce <= 0;
    }
    return false;
  }

  hitBlocks(ball, prevX, prevY) {
    const guarded = ball.lastHitBlock;
    if (guarded && !circleRect(ball.x, ball.y, ball.radius, guarded.x, guarded.y, guarded.w, guarded.h)) {
      ball.lastHitBlock = null;
    }
    for (let i = this.blocks.length - 1; i >= 0; i--) {
      const block = this.blocks[i];
      if (block === ball.lastHitBlock) continue;
      let hit = circleRect(ball.x, ball.y, ball.radius, block.x, block.y, block.w, block.h);
      if (!hit) {
        for (let s = 1; s <= 3 && !hit; s++) {
          const t = s / 4;
          hit = circleRect(lerp(prevX, ball.x, t), lerp(prevY, ball.y, t), ball.radius, block.x, block.y, block.w, block.h);
        }
      }
      if (!hit) continue;
      ball.lastHitBlock = block;

      if (block.hasArmor && block.armorHp > 0) {
        block.armorHp -= ball.damage;
        block.hitFlash = 0.2;
        this.spawnParticles(ball.x, ball.y, "#22d3ee", 3, 70, 0.25);
        if (block.armorHp <= 0) {
          block.hasArmor = false;
          block.armorHp = 0;
          this.pushPopup(block.x + block.w / 2, block.y + block.h * 0.2, "زره شکست!", "#22d3ee", 0.6);
        }
        return !this.consumedForHit(ball);
      }

      if (block.isIce) {
        block.isIce = false;
        block.hitFlash = 0.2;
        this.spawnParticles(ball.x, ball.y, "#bae6fd", 8, 150, 0.4);
        this.sound("hit");
        this.shake = Math.max(this.shake, 0.3);
        return !this.consumedForHit(ball);
      }

      block.hp -= ball.damage;
      block.hitFlash = 0.2;
      this.spawnParticles(ball.x, ball.y, "#ffffff", 3, 60, 0.25);
      const consumed = this.consumedForHit(ball);

      if (block.hp <= 0) {
        const count = this.comboOnKill();
        this.destroyBlock(block, false, count);
        this.sound("destroy");
      } else {
        this.sound("hit");
        this.shake = Math.max(this.shake, 0.28);
        this.pushPopup(block.x + block.w / 2 + rand(-8, 8), block.y + 6, `-${ball.damage}`, "#ffffff", 0.45);
      }
      return !consumed;
    }
    return true;
  }

  consumedForHit(ball) {
    if (this.hasPowerUp("GHOST")) return false;
    if (ball.pierce > 0) {
      ball.pierce -= 1;
      return false;
    }
    return true;
  }

  comboOnKill() {
    this.combo.count = this.gameTime - this.combo.lastKill <= COMBO_WINDOW ? this.combo.count + 1 : 1;
    this.combo.lastKill = this.gameTime;
    this.combo.fraction = 1;
    this.combo.best = Math.max(this.combo.best, this.combo.count);
    return this.combo.count;
  }

  /* ── blocks ────────────────────────────────────────────────────────── */

  updateBlocks(dt) {
    const groundY = this.cannon.baseY;
    const freeze = this.hasPowerUp("FREEZE") ? 0.28 : 1;

    let idx = 0;
    while (idx < this.blocks.length) {
      const block = this.blocks[idx];
      block.shakeTime = Math.max(0, block.shakeTime - dt);
      block.hitFlash = Math.max(0, block.hitFlash - dt);

      if (block.isHoming) {
        if (this.updateFloorThreat(block, dt, groundY)) continue;
      } else {
        const gravScale = block.isLucky ? 0.78 : 1;
        block.vy += this.cfg.gravity * dt * freeze * gravScale;
        block.vy = Math.min(block.vy, this.cfg.maxFall * (freeze < 1 ? 0.42 : 1));
        block.vx -= block.vx * dt * 0.5;
        block.vx += Math.sin(this.gameTime * 0.9 + block.shakePhase) * 8 * dt;
        const push = 1 + 0.85 * Math.min(1, this.level / 400);
        if (this.levelModifier.id === "WIND") block.vx += 26 * push * dt * (1 + 0.22 * Math.sin(this.gameTime * 0.5));
        if (this.levelModifier.id === "LIFT") block.vy -= 30 * push * dt * (1 + 0.25 * Math.sin(this.gameTime * 0.7));
        block.x += block.vx * dt * freeze;
        block.y += block.vy * dt * freeze;
        this.keepBlockInField(block);

        if (block.y + block.h >= groundY) {
          block.y = groundY - block.h;
          if (block.isMother) {
            block.vy = -this.bounceSpeed();
            block.vx = rand(-46, 46);
            block.bounceCount++;
            block.shakeTime = 0.16;
            block.hitFlash = 0.25;
            this.spawnSatellites(block);
          } else if (block.bounceCount >= this.bounceBudget()) {
            const anotherThreat = this.blocks.some((b) => b !== block && b.isHoming);
            if (anotherThreat) {
              this.shatterFloorBlock(block);
              this.blocks.splice(idx, 1);
              continue;
            }
            block.isHoming = true;
            block.lastStandTimer = 0.78;
            const desired = Math.abs(this.cannon.x - (block.x + block.w / 2)) > 4
              ? (this.cannon.x > block.x + block.w / 2 ? 1 : -1)
              : (block.vx >= 0 ? 1 : -1);
            block.chargeDir = desired;
            if (desired < 0 && block.x <= 1 && block.x + block.w < this.fieldW - 1) block.chargeDir = 1;
            if (desired > 0 && block.x + block.w >= this.fieldW - 1 && block.x > 1) block.chargeDir = -1;
            block.chargeElapsed = 0;
            block.isCharging = false;
            block.vy = 0;
            block.vx = 0;
            block.y = groundY - block.h;
            block.shakeTime = 0.2;
            block.hitFlash = 0.35;
          } else {
            block.vy = -this.bounceSpeed();
            block.vx = rand(-46, 46);
            block.bounceCount++;
            block.shakeTime = 0.16;
            block.hitFlash = 0.25;
          }
        }
      }
      idx++;
    }

    this.separateBlocks();

    for (let i = this.blocks.length - 1; i >= 0; i--) {
      const block = this.blocks[i];
      if (!this.blockHitsCannon(block)) continue;
      if (block.isMother) {
        this.motherSlam(block);
      } else {
        this.handleCannonHit(block, i);
      }
      if (this.status !== "playing") break;
    }
  }

  bounceBudget() {
    if (this.level < 300) return 3;
    if (this.level < 1200) return 2;
    return 1;
  }

  keepBlockInField(block) {
    const maxX = Math.max(0, this.fieldW - block.w);
    if (block.x < 0) {
      block.x = 0;
      if (block.vx < 0) block.vx = -block.vx * 0.85;
      return true;
    }
    if (block.x > maxX) {
      block.x = maxX;
      if (block.vx > 0) block.vx = -block.vx * 0.85;
      return true;
    }
    return false;
  }

  updateFloorThreat(block, dt, groundY) {
    block.vy = 0;
    block.y = groundY - block.h;
    if (block.lastStandTimer > 0) {
      block.vx = 0;
      block.lastStandTimer = Math.max(0, block.lastStandTimer - dt);
      block.x = clamp(block.x + Math.sin(this.gameTime * 46 + block.shakePhase) * 18 * dt, 0, Math.max(0, this.fieldW - block.w));
      return false;
    }
    if (!block.isCharging) {
      block.isCharging = true;
      block.shakeTime = 0.2;
    }
    block.vx = block.chargeDir * 250;
    block.x += block.vx * dt;
    block.chargeElapsed += dt;
    const hitWall = this.keepBlockInField(block);
    if (hitWall || block.chargeElapsed >= 2.6) {
      this.shatterFloorBlock(block);
      const idx = this.blocks.indexOf(block);
      if (idx >= 0) this.blocks.splice(idx, 1);
      return true;
    }
    return false;
  }

  shatterFloorBlock(block) {
    this.spawnExplosion(block);
    this.sound("destroy");
    this.pushPopup(block.x + block.w / 2, block.y + block.h / 2, "خنثی شد", "#22d3ee", 0.65);
  }

  separateBlocks() {
    if (this.blocks.length < 2) return;
    const margin = 1.5;
    for (let i = 0; i < this.blocks.length; i++) {
      const a = this.blocks[i];
      if (a.isMother) continue;
      for (let j = i + 1; j < this.blocks.length; j++) {
        const b = this.blocks[j];
        if (b.isMother) continue;
        if (a.y >= b.y + b.h || b.y >= a.y + a.h) continue;
        const overlap = (a.w + b.w) * 0.5 + margin - Math.abs((a.x + a.w / 2) - (b.x + b.w / 2));
        if (overlap <= 0) continue;
        const dir = (a.x + a.w / 2) < (b.x + b.w / 2) ? -1 : 1;
        const push = overlap * 0.5;
        a.x = clamp(a.x + dir * push, 0, Math.max(0, this.fieldW - a.w));
        b.x = clamp(b.x - dir * push, 0, Math.max(0, this.fieldW - b.w));
      }
    }
  }

  blockHitsCannon(block) {
    if (this.hitGrace > 0 && !block.isMother) return false;
    const insetX = block.w * 0.12;
    const insetY = block.h * 0.12;
    return circleRect(
      this.cannon.x,
      this.cannon.baseY,
      this.cannon.radius * 0.68,
      block.x + insetX,
      block.y + insetY,
      Math.max(1, block.w - insetX * 2),
      Math.max(1, block.h - insetY * 2),
    );
  }

  handleCannonHit(block, index) {
    this.spawnExplosion(block);
    this.blocks.splice(index, 1);
    this.combo.count = 0;
    this.combo.fraction = 0;
    if (this.hasPowerUp("GHOST")) {
      this.sound("destroy");
      this.pushPopup(this.fieldW / 2, this.fieldH * 0.55, "👻 ازش گذشت!", POWERUPS.GHOST.color, 0.9, true);
      return;
    }
    if (this.shields > 0) {
      this.shields -= 1;
      this.sound("destroy");
      this.pushPopup(this.fieldW / 2, this.fieldH * 0.55, "🛡️ سپر مصرف شد", POWERUPS.SHIELD.color, 0.9, true);
      return;
    }
    this.sound("heart");
    this.status = "hit";
    this.paused = false;
    this.save.bestScore = Math.max(this.save.bestScore, this.runScore);
    this.persist();
    this.emit("hit", {
      level: this.level,
      score: this.runScore,
      levelScore: this.score,
      coins: this.save.coins,
      best: this.save.bestScore,
      reason: "block",
    });
    this.emitState(true);
  }

  motherSlam(mother) {
    this.spawnExplosion(mother);
    this.shake = Math.max(this.shake, 0.55);
    this.combo.count = 0;
    this.combo.fraction = 0;
    if (this.hasPowerUp("GHOST")) {
      this.sound("destroy");
      this.pushPopup(this.fieldW / 2, this.fieldH * 0.55, "👻 رد شد!", POWERUPS.GHOST.color, 0.9, true);
    } else if (this.shields > 0) {
      this.shields -= 1;
      this.sound("destroy");
      this.pushPopup(this.fieldW / 2, this.fieldH * 0.55, "🛡️ سپر مصرف شد", POWERUPS.SHIELD.color, 0.9, true);
    } else {
      this.sound("heart");
      this.pushPopup(this.fieldW / 2, this.fieldH * 0.55, "💥 رئیس از کف رد شد!", "#fb7185", 1.1, true);
      this.status = "hit";
      this.paused = false;
      this.save.bestScore = Math.max(this.save.bestScore, this.runScore);
      this.persist();
      this.emit("hit", {
        level: this.level,
        score: this.runScore,
        levelScore: this.score,
        coins: this.save.coins,
        best: this.save.bestScore,
        reason: "mother",
      });
      this.emitState(true);
    }
    if (this.status === "playing") {
      mother.bounceCount = 0;
      mother.vy = -this.bounceSpeed();
      mother.vx = rand(-46, 46);
    }
  }

  bounceSpeed() {
    const height = Math.max(24, this.fieldH * BOUNCE_HEIGHT_FRACTION);
    return Math.min(Math.sqrt(2 * this.cfg.gravity * height), this.cfg.maxFall);
  }

  /* ── rewards / splitting ───────────────────────────────────────────── */

  addScore(delta) {
    if (delta <= 0) return;
    this.score += delta;
    this.runScore += delta;
    this.save.bestScore = Math.max(this.save.bestScore, this.runScore);
  }

  addCoins(n) {
    if (n <= 0) return;
    this.save.coins += n;
    this.coinsInLevel += n;
    this.runCoins += n;
  }

  destroyBlock(block, forceNoSplit = false, comboCount = 0) {
    this.addScore(block.scoreValue);
    if (block.isLucky) this.triggerLucky(block);
    this.spawnExplosion(block);
    this.shake = Math.max(this.shake, block.isBoss ? 1 : block.isLucky ? 0.65 : 0.45);

    if (comboCount >= 2) {
      const bonus = comboBonus(comboCount);
      this.addScore(bonus);
      if (comboCount >= 3) this.addCoins(1);
      const mult = comboMultiplier(comboCount);
      this.pushPopup(
        this.fieldW / 2,
        this.fieldH * 0.35,
        mult > 1 ? `کمبو ×${comboCount}  +${bonus}  (${mult}×)` : `کمبو ×${comboCount}  +${bonus}`,
        "#fbbf24",
        0.9,
        true,
      );
      this.sound("coin");
    }

    if (block.isBoss) this.addScore(block.scoreValue * 2);
    if (block.isMother) this.addScore(block.scoreValue * 2);

    const idx = this.blocks.indexOf(block);
    if (idx >= 0) this.blocks.splice(idx, 1);

    if (forceNoSplit || !this.trySplitBlock(block)) {
      this.spawnCoins(block);
      this.blocksDestroyed += 1;
      this.save.totalBlocks = (this.save.totalBlocks || 0) + 1;
    }
  }

  trySplitBlock(block) {
    if (block.isFragment || block.isMother || block.isBoss) return false;
    const threshold = Math.max(14, 40 - this.level * 0.9);
    if (block.maxHp < threshold) return false;
    if (!shapeCanSplit(block.shape)) return false;
    if (block.w < 38) return false;
    const pieces = this.level >= 12 && block.maxHp >= 120 ? 4
      : this.level >= 7 && block.maxHp >= 80 ? 3
      : 2;
    const childHp = Math.max(1, Math.floor(block.maxHp / pieces));
    const childW = Math.max(22, block.w * (pieces === 2 ? 0.56 : 0.44));
    const childH = Math.max(22, block.h * (pieces === 2 ? 0.56 : 0.44));
    this.totalBlocks += pieces - 1;
    for (let i = 0; i < pieces; i++) {
      const angle = (i * (360 / pieces) + rand(-9, 9)) * (Math.PI / 180);
      const speed = rand(80, 170);
      const plate = block.hasArmor ? Math.max(1, Math.floor(block.armorHp / 3)) : 0;
      this.blocks.push({
        x: block.x + block.w / 2 - childW / 2 + Math.cos(angle) * block.w * 0.22,
        y: block.y + block.h / 2 - childH / 2 + Math.sin(angle) * block.h * 0.22,
        w: childW, h: childH,
        vx: block.vx * 0.35 + Math.cos(angle) * speed,
        vy: block.vy * 0.3 + Math.sin(angle) * speed - rand(20, 70),
        hp: childHp, maxHp: childHp, scoreValue: childHp,
        shape: block.shape, colorIndex: block.colorIndex,
        colorSeed: Math.random(), shakePhase: Math.random() * Math.PI * 2,
        isLucky: false, isBoss: false, isMother: false, isSatellite: false,
        isFragment: true, isIce: false,
        hasArmor: plate > 0, armorHp: plate, armorMaxHp: plate,
        isBoulder: false, bounceCount: 0, isHoming: false, lastStandTimer: 0,
        chargeDir: 1, chargeElapsed: 0, isCharging: false, hitFlash: 0, shakeTime: 0,
      });
    }
    this.sound("hit");
    this.shake = Math.max(this.shake, 0.38);
    return true;
  }

  spawnCoins(block) {
    const rush = this.hasPowerUp("COIN_RUSH") ? 3 : 1;
    const boss = block.isBoss ? 3 : 1;
    const count = block.isMother
      ? clamp(this.cfg.blockCount * 4, 1, 110)
      : clamp(randInt(2, 4) * rush * boss, 1, 14);
    this.spawnCoinBurst(block.x + block.w / 2, block.y + block.h / 2, count);
  }

  spawnCoinBurst(x, y, count) {
    for (let i = 0; i < count; i++) {
      const angle = Math.random() * Math.PI * 2;
      const speed = rand(40, 200);
      this.coins.push({
        x, y,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed * 0.7,
        rotation: Math.random() * Math.PI * 2,
        spin: rand(240, 520),
        age: 0,
        state: "free",
      });
    }
  }

  triggerLucky(anchor) {
    const type = randomPowerUp();
    this.spawnCoinBurst(anchor.x + anchor.w / 2, anchor.y + anchor.h / 2, 2);
    this.applyPowerUp(type);
    this.pushPopup(anchor.x + anchor.w / 2, anchor.y + anchor.h * 0.2, `🍀 ${POWERUPS[type].label}!`, "#fbbf24", 1.2, true);
    this.sound("power");
  }

  /* ── bonuses / drops / powerups ────────────────────────────────────── */

  updateBonuses(dt) {
    const groundY = this.cannon.baseY;
    for (let i = this.bonuses.length - 1; i >= 0; i--) {
      const b = this.bonuses[i];
      b.age += dt;
      b.rotation += b.spin * dt;
      b.vy += 28 * dt;
      b.vy = Math.min(b.vy, 130);
      b.x += b.vx * dt;
      b.y += b.vy * dt;
      if (b.x <= 0) { b.x = 0; b.vx = -b.vx; }
      else if (b.x + b.size >= this.fieldW) { b.x = this.fieldW - b.size; b.vx = -b.vx; }
      if (b.y + b.size >= groundY) {
        b.y = groundY - b.size;
        b.vy = -this.bounceSpeed();
        b.vx = -b.vx * 0.85;
      }
      if (b.age > 25) this.bonuses.splice(i, 1);
    }
  }

  updateDrops(dt) {
    const groundY = this.cannon.baseY;
    for (let i = this.drops.length - 1; i >= 0; i--) {
      const d = this.drops[i];
      d.age += dt;
      d.rotation += d.spin * dt;
      d.vy += 300 * dt;
      d.vy = Math.min(d.vy, 400);
      d.vx -= d.vx * dt * 1.5;
      d.x += d.vx * dt;
      d.y += d.vy * dt;
      if (d.x - d.size / 2 < 0) { d.x = d.size / 2; d.vx = -d.vx * 0.6; }
      else if (d.x + d.size / 2 > this.fieldW) { d.x = this.fieldW - d.size / 2; d.vx = -d.vx * 0.6; }
      if (d.y >= groundY - d.size / 2) {
        d.y = groundY - d.size / 2;
        if (d.vy > 30) { d.vy = -d.vy * 0.3; d.vx *= 0.7; }
        else { d.vy = 0; d.vx = 0; }
      }
      const dist = Math.hypot(d.x - this.cannon.x, d.y - this.cannon.baseY);
      if (dist < this.cannon.radius * 1.5) {
        this.applyPowerUp(d.type);
        this.drops.splice(i, 1);
      } else if (d.age > 15) {
        this.drops.splice(i, 1);
      }
    }
  }

  hasPowerUp(type) {
    return (this.powerups[type] || 0) > 0;
  }

  applyPowerUp(type) {
    if (type === "SHIELD") {
      this.shields += 1;
      this.sound("power");
    } else if (type === "NUKE") {
      const had = this.blocks.length > 0;
      for (let i = this.blocks.length - 1; i >= 0; i--) {
        this.destroyBlock(this.blocks[i], true, 0);
      }
      this.coins = [];
      if (had) {
        for (let i = 0; i < 12; i++) this.spawnCoinBurst(this.cannon.x, this.cannon.baseY, 3);
        this.shake = 1;
      }
      this.sound("destroy");
    } else {
      this.powerups[type] = POWERUPS[type].duration;
      this.sound("power");
    }
    this.flash = 1;
    this.flashColor = POWERUPS[type].color;
    this.pushPopup(this.fieldW / 2, this.fieldH * 0.4, POWERUPS[type].label, POWERUPS[type].color, 1.1, true);
  }

  updatePowerups(dt) {
    for (const key of POWERUP_KEYS) {
      if (this.powerups[key] > 0) this.powerups[key] = Math.max(0, this.powerups[key] - dt);
    }
  }

  /* ── coins / fx ────────────────────────────────────────────────────── */

  updateCoins(dt) {
    const groundY = this.cannon.baseY;
    const coinR = 11;
    const manualRadius = this.cannon.radius * 1.8;
    const magnetRadius = this.stats.magnetRadius;
    for (let i = this.coins.length - 1; i >= 0; i--) {
      const c = this.coins[i];
      c.age += dt;
      c.rotation += c.spin * dt;
      if (c.state === "free" && magnetRadius > 0 &&
        Math.hypot(c.x - this.cannon.x, c.y - this.cannon.baseY) < magnetRadius) {
        c.state = "magnet";
      }
      if (c.state === "free") {
        c.vy += 420 * dt;
        c.vy = Math.min(c.vy, 280);
        c.vx -= c.vx * dt * 1.5;
        c.x += c.vx * dt;
        c.y += c.vy * dt;
        if (c.x - coinR < 0) { c.x = coinR; c.vx = -c.vx * 0.6; }
        else if (c.x + coinR > this.fieldW) { c.x = this.fieldW - coinR; c.vx = -c.vx * 0.6; }
        if (c.y >= groundY - coinR) {
          c.y = groundY - coinR;
          if (c.vy > 30) { c.vy = -c.vy * 0.35; c.vx *= 0.7; }
          else { c.vy = 0; c.vx = 0; }
        }
      } else {
        const dx = this.cannon.x - c.x;
        const dy = this.cannon.baseY - c.y;
        const dist = Math.hypot(dx, dy);
        if (dist > 1) {
          c.vx += (dx / dist) * 1400 * dt;
          c.vy += (dy / dist) * 1400 * dt;
          const speed = Math.hypot(c.vx, c.vy);
          const maxSpeed = 520;
          if (speed > maxSpeed) { c.vx = (c.vx / speed) * maxSpeed; c.vy = (c.vy / speed) * maxSpeed; }
          c.x += c.vx * dt;
          c.y += c.vy * dt;
        }
      }
      const dist = Math.hypot(c.x - this.cannon.x, c.y - this.cannon.baseY);
      const collected = dist < manualRadius
        || (c.state === "magnet" && dist < 26)
        || (c.state === "free" && magnetRadius <= 0 && c.age >= 45);
      if (collected) {
        this.addCoins(1);
        this.sound("coin");
        this.coins.splice(i, 1);
      }
    }
  }

  collectAllCoins() {
    const n = this.coins.length;
    this.coins = [];
    for (let i = 0; i < n; i++) {
      this.addCoins(1);
      this.sound("coin");
    }
  }

  spawnParticles(x, y, color, count, speed, life) {
    for (let i = 0; i < count; i++) {
      const a = Math.random() * Math.PI * 2;
      const s = rand(speed * 0.3, speed);
      this.particles.push({
        x, y,
        vx: Math.cos(a) * s,
        vy: Math.sin(a) * s,
        life: rand(life * 0.6, life),
        max: life,
        size: rand(1.5, 4),
        color,
      });
    }
  }

  spawnExplosion(block) {
    this.spawnParticles(block.x + block.w / 2, block.y + block.h / 2, PALETTE[block.colorIndex] || "#34d399", 18, 240, 0.6);
    this.explosions.push({
      x: block.x + block.w / 2,
      y: block.y + block.h / 2,
      r: block.w * 0.45,
      endR: block.w * 1.9,
      life: block.isBoss ? 0.62 : 0.42,
      age: 0,
      color: PALETTE[block.colorIndex] || "#34d399",
    });
  }

  updateParticles(dt) {
    const decay = Math.max(0, 1 - 2.6 * dt);
    for (let i = this.particles.length - 1; i >= 0; i--) {
      const p = this.particles[i];
      p.vy += 900 * dt;
      p.vx *= decay;
      p.vy *= decay;
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.life -= dt;
      if (p.life <= 0) this.particles.splice(i, 1);
    }
    for (let i = this.explosions.length - 1; i >= 0; i--) {
      const e = this.explosions[i];
      e.age += dt;
      if (e.age >= e.life) this.explosions.splice(i, 1);
    }
  }

  pushPopup(x, y, text, color, life, big = false) {
    if (this.popups.length >= 24) this.popups.shift();
    this.popups.push({ x, y, text, color, life, age: 0, big });
  }

  updatePopups(dt) {
    for (let i = this.popups.length - 1; i >= 0; i--) {
      const p = this.popups[i];
      p.age += dt;
      p.y -= (p.big ? 12 : 28) * dt;
      if (p.age >= p.life) this.popups.splice(i, 1);
    }
  }

  checkLevelComplete() {
    if (this.status !== "playing") return;
    if (this.motherAlive()) return;
    if (this.motherPendingSpawn) return;
    if (this.blocksToSpawn !== 0 || this.blocks.length !== 0 || this.spawnTicking) return;
    this.status = "complete";
    this.collectAllCoins();
    this.save.level = clamp(this.level + 1, 1, 9999);
    this.save.bestScore = Math.max(this.save.bestScore, this.runScore);
    this.persist();
    this.sound("level");
    this.emit("levelComplete", {
      level: this.level,
      nextLevel: this.save.level,
      score: this.score,
      runScore: this.runScore,
      coins: this.save.coins,
      best: this.save.bestScore,
    });
    this.emitState(true);
  }

  /* ── render ────────────────────────────────────────────────────────── */

  render(dt) {
    const ctx = this.ctx;
    const w = this.fieldW;
    const h = this.fieldH;
    if (!w || !h) return;

    ctx.save();
    if (this.shake > 0) {
      const s = this.shake * 9;
      ctx.translate(rand(-s, s), rand(-s, s));
    }

    const bg = ctx.createLinearGradient(0, 0, 0, h);
    bg.addColorStop(0, "#03120c");
    bg.addColorStop(0.55, "#04180f");
    bg.addColorStop(1, "#020b07");
    ctx.fillStyle = bg;
    ctx.fillRect(-20, -20, w + 40, h + 40);

    ctx.strokeStyle = "rgba(52,211,153,0.055)";
    ctx.lineWidth = 1;
    for (let x = 20; x < w; x += 40) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke();
    }
    for (let y = 20; y < h; y += 40) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
    }

    for (const star of this.stars) {
      const tw = 0.2 + 0.6 * (0.5 + 0.5 * Math.sin(this.gameTime * 1.4 + star.phase));
      ctx.globalAlpha = tw;
      ctx.fillStyle = star.phase % 2 < 1 ? "#a7f3d0" : "#fde68a";
      ctx.beginPath(); ctx.arc(star.x, star.y, star.r, 0, Math.PI * 2); ctx.fill();
    }
    ctx.globalAlpha = 1;

    const floor = ctx.createLinearGradient(0, h - 140, 0, h);
    floor.addColorStop(0, "rgba(52,211,153,0)");
    floor.addColorStop(1, "rgba(52,211,153,0.12)");
    ctx.fillStyle = floor;
    ctx.fillRect(0, h - 140, w, 140);
    ctx.strokeStyle = "rgba(52,211,153,0.26)";
    ctx.beginPath(); ctx.moveTo(0, this.cannon.baseY); ctx.lineTo(w, this.cannon.baseY); ctx.stroke();

    if (this.status === "idle") {
      this.idleTime += dt;
      this.drawIdleBlocks();
    }

    for (const coin of this.coins) this.drawCoin(coin);
    for (const bonus of this.bonuses) this.drawBonus(bonus);
    for (const drop of this.drops) this.drawDrop(drop);
    for (const block of this.blocks) this.drawBlock(block);
    for (const ball of this.balls) this.drawBall(ball);
    this.drawCannon();

    for (const p of this.particles) {
      ctx.globalAlpha = Math.max(0, p.life / p.max);
      ctx.fillStyle = p.color;
      ctx.beginPath(); ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2); ctx.fill();
    }
    ctx.globalAlpha = 1;

    for (const e of this.explosions) {
      const t = e.age / e.life;
      ctx.globalAlpha = Math.max(0, 1 - t) * 0.5;
      ctx.strokeStyle = e.color;
      ctx.lineWidth = 3 * (1 - t) + 1;
      ctx.beginPath();
      ctx.arc(e.x, e.y, lerp(e.r, e.endR, t), 0, Math.PI * 2);
      ctx.stroke();
    }
    ctx.globalAlpha = 1;

    for (const p of this.popups) {
      const t = p.age / p.life;
      ctx.globalAlpha = Math.max(0, 1 - t);
      ctx.fillStyle = p.color;
      ctx.font = `900 ${p.big ? 18 : 13}px Vazirmatn, sans-serif`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.shadowColor = "rgba(0,0,0,0.65)";
      ctx.shadowBlur = 6;
      ctx.fillText(p.text, p.x, p.y);
      ctx.shadowBlur = 0;
    }
    ctx.globalAlpha = 1;

    if (this.flash > 0) {
      ctx.globalAlpha = this.flash * 0.22;
      ctx.fillStyle = this.flashColor;
      ctx.fillRect(0, 0, w, h);
      ctx.globalAlpha = 1;
    }

    ctx.restore();
  }

  drawIdleBlocks() {
    const ctx = this.ctx;
    const t = this.idleTime || 0;
    const specs = [
      { x: 0.12, y: 0.18, size: 48, color: 8, label: "32", phase: 0.2 },
      { x: 0.86, y: 0.28, size: 58, color: 2, label: "64", phase: 1.4 },
      { x: 0.18, y: 0.58, size: 42, color: 5, label: "16", phase: 2.1 },
      { x: 0.82, y: 0.66, size: 52, color: 10, label: "128", phase: 3.3 },
      { x: 0.5, y: 0.12, size: 36, color: 12, label: "8", phase: 4.2 },
    ];
    for (const spec of specs) {
      const x = this.fieldW * spec.x + Math.sin(t * 0.5 + spec.phase) * 16;
      const y = this.fieldH * spec.y + Math.cos(t * 0.42 + spec.phase) * 12;
      const color = PALETTE[spec.color];
      ctx.save();
      ctx.translate(x, y);
      ctx.rotate(Math.sin(t * 0.35 + spec.phase) * 0.12);
      ctx.globalAlpha = 0.18;
      ctx.shadowColor = color;
      ctx.shadowBlur = 18;
      ctx.fillStyle = color;
      roundRectPath(ctx, -spec.size / 2, -spec.size / 2, spec.size, spec.size, spec.size * 0.22);
      ctx.fill();
      ctx.shadowBlur = 0;
      ctx.globalAlpha = 0.4;
      ctx.strokeStyle = "rgba(255,255,255,0.45)";
      ctx.lineWidth = 1;
      ctx.stroke();
      ctx.globalAlpha = 0.5;
      ctx.fillStyle = "#04120d";
      ctx.font = `900 ${spec.size * 0.28}px Vazirmatn, sans-serif`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(spec.label, 0, 1);
      ctx.restore();
    }
    ctx.globalAlpha = 1;
  }

  drawBlock(block) {
    const ctx = this.ctx;
    const cx = block.x + block.w / 2;
    const cy = block.y + block.h / 2;
    const color = block.isMother ? "#ff4d8d" : PALETTE[block.colorIndex] || "#34d399";
    const size = Math.min(block.w, block.h);
    const wobble = block.shakeTime > 0 ? Math.sin(this.gameTime * 50 + block.shakePhase) * 2.4 : 0;

    ctx.save();
    ctx.translate(cx + wobble, cy);
    if (block.isMother) {
      const pulse = 1 + 0.015 * Math.sin(this.gameTime * 2.4);
      ctx.scale(pulse, pulse);
    }

    ctx.shadowColor = color;
    ctx.shadowBlur = block.isMother ? 28 : 14;
    const grad = ctx.createLinearGradient(-block.w / 2, -block.h / 2, block.w / 2, block.h / 2);
    grad.addColorStop(0, lighten(color, 0.22));
    grad.addColorStop(0.55, color);
    grad.addColorStop(1, darken(color, 0.34));
    ctx.fillStyle = grad;

    this.traceShape(block.shape, block.w, block.h);
    ctx.fill();
    ctx.shadowBlur = 0;

    ctx.strokeStyle = block.hitFlash > 0 ? "rgba(255,255,255,0.95)" : "rgba(255,255,255,0.34)";
    ctx.lineWidth = block.isMother ? 2.4 : 1.3;
    this.traceShape(block.shape, block.w, block.h);
    ctx.stroke();

    if (block.hasArmor && block.armorHp > 0) {
      const ratio = clamp(block.armorHp / Math.max(1, block.armorMaxHp), 0, 1);
      ctx.strokeStyle = `rgba(34,211,238,${0.45 + ratio * 0.45})`;
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.arc(0, 0, size * 0.55, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * ratio);
      ctx.stroke();
    }

    if (block.isIce) {
      ctx.fillStyle = "rgba(186,230,253,0.3)";
      this.traceShape(block.shape, block.w, block.h);
      ctx.fill();
      ctx.fillStyle = "rgba(224,242,254,0.9)";
      ctx.font = `700 ${Math.max(10, size * 0.2)}px Vazirmatn, sans-serif`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText("❄", 0, 0);
    }

    ctx.fillStyle = block.isMother ? "#2b0615" : "#04120d";
    ctx.font = `900 ${Math.max(10, size * (block.isMother ? 0.22 : 0.3))}px Vazirmatn, sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(String(Math.max(0, Math.ceil(block.hp))), 0, 1);

    if (block.isLucky) {
      ctx.font = `${Math.max(10, size * 0.2)}px serif`;
      ctx.fillText("🍀", size * 0.3, -size * 0.3);
    }

    if (block.isMother) {
      const ratio = clamp(block.hp / Math.max(1, block.maxHp), 0, 1);
      ctx.strokeStyle = "rgba(251,191,36,0.9)";
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.arc(0, 0, Math.max(block.w, block.h) * 0.56, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * ratio);
      ctx.stroke();
    }

    ctx.restore();
  }

  traceShape(shape, w, h) {
    const ctx = this.ctx;
    const rx = w / 2;
    const ry = h / 2;
    ctx.beginPath();
    if (shape === "circle") {
      ctx.ellipse(0, 0, rx, ry, 0, 0, Math.PI * 2);
    } else if (shape === "hexagon") {
      for (let i = 0; i < 6; i++) {
        const a = -Math.PI / 2 + (i * Math.PI) / 3;
        const x = Math.cos(a) * rx;
        const y = Math.sin(a) * ry;
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.closePath();
    } else if (shape === "triangle") {
      ctx.moveTo(0, -ry);
      ctx.lineTo(rx, ry * 0.78);
      ctx.lineTo(-rx, ry * 0.78);
      ctx.closePath();
    } else if (shape === "star") {
      for (let i = 0; i < 10; i++) {
        const a = -Math.PI / 2 + (i * Math.PI) / 5;
        const r = i % 2 === 0 ? 1 : 0.48;
        const x = Math.cos(a) * rx * r;
        const y = Math.sin(a) * ry * r;
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.closePath();
    } else {
      roundRectPath(ctx, -rx, -ry, w, h, Math.min(w, h) * 0.2);
    }
  }

  drawBall(ball) {
    const ctx = this.ctx;
    for (let i = 0; i < ball.trail.length; i++) {
      const p = ball.trail[i];
      const t = i / ball.trail.length;
      ctx.globalAlpha = t * 0.35;
      ctx.fillStyle = ball.isFlaming ? "#fb7185" : ball.isSpectral ? "#b39dff" : "#22d3ee";
      ctx.beginPath(); ctx.arc(p.x, p.y, ball.radius * (0.4 + t * 0.5), 0, Math.PI * 2); ctx.fill();
    }
    ctx.globalAlpha = 1;
    ctx.fillStyle = "#ecfeff";
    ctx.shadowColor = ball.isFlaming ? "#fb7185" : "#22d3ee";
    ctx.shadowBlur = 12;
    ctx.beginPath(); ctx.arc(ball.x, ball.y, ball.radius, 0, Math.PI * 2); ctx.fill();
    ctx.shadowBlur = 0;
  }

  drawCannon() {
    const ctx = this.ctx;
    const c = this.cannon;
    const y = c.baseY;
    const recoil = c.recoil * 5;
    const glow = ctx.createRadialGradient(c.x, y, 2, c.x, y, 40);
    glow.addColorStop(0, "rgba(110,231,183,0.5)");
    glow.addColorStop(1, "rgba(110,231,183,0)");
    ctx.fillStyle = glow;
    ctx.beginPath(); ctx.arc(c.x, y, 40, 0, Math.PI * 2); ctx.fill();

    const grad = ctx.createRadialGradient(c.x - 6, y - 8 - recoil, 2, c.x, y - recoil, c.radius);
    grad.addColorStop(0, "#d1fae5");
    grad.addColorStop(0.45, "#34d399");
    grad.addColorStop(1, "#047857");
    ctx.fillStyle = grad;
    ctx.beginPath(); ctx.arc(c.x, y - recoil, c.radius, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = "rgba(255,255,255,0.42)";
    ctx.lineWidth = 1.3;
    ctx.stroke();
    ctx.fillStyle = "rgba(4,18,13,0.78)";
    ctx.beginPath(); ctx.arc(c.x, y - recoil, c.radius * 0.32, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#a7f3d0";
    ctx.beginPath(); ctx.arc(c.x, y - recoil - c.radius * 0.55, 3.4, 0, Math.PI * 2); ctx.fill();
  }

  drawCoin(coin) {
    const ctx = this.ctx;
    ctx.save();
    ctx.translate(coin.x, coin.y);
    const squash = Math.abs(Math.cos(coin.rotation));
    ctx.scale(0.55 + squash * 0.45, 1);
    ctx.fillStyle = "#fbbf24";
    ctx.shadowColor = "#fbbf24";
    ctx.shadowBlur = 10;
    ctx.beginPath(); ctx.arc(0, 0, 7, 0, Math.PI * 2); ctx.fill();
    ctx.shadowBlur = 0;
    ctx.strokeStyle = "rgba(255,255,255,0.6)";
    ctx.lineWidth = 1.2;
    ctx.beginPath(); ctx.arc(0, 0, 4.4, 0, Math.PI * 2); ctx.stroke();
    ctx.restore();
  }

  drawBonus(bonus) {
    const ctx = this.ctx;
    const info = POWERUPS[bonus.type];
    ctx.save();
    ctx.translate(bonus.x + bonus.size / 2, bonus.y + bonus.size / 2);
    ctx.rotate(bonus.rotation);
    ctx.shadowColor = "#fbbf24";
    ctx.shadowBlur = 18;
    const grad = ctx.createLinearGradient(-bonus.size / 2, -bonus.size / 2, bonus.size / 2, bonus.size / 2);
    grad.addColorStop(0, "#fde68a");
    grad.addColorStop(0.5, "#fbbf24");
    grad.addColorStop(1, "#b45309");
    ctx.fillStyle = grad;
    roundRectPath(ctx, -bonus.size / 2, -bonus.size / 2, bonus.size, bonus.size, 9);
    ctx.fill();
    ctx.shadowBlur = 0;
    ctx.strokeStyle = "rgba(255,255,255,0.55)";
    ctx.lineWidth = 1.2;
    ctx.stroke();
    ctx.rotate(-bonus.rotation);
    ctx.fillStyle = "#2a1a00";
    ctx.font = `900 ${bonus.size * 0.42}px Vazirmatn, sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(info.icon, 0, 1);
    ctx.restore();
  }

  drawDrop(drop) {
    const ctx = this.ctx;
    const info = POWERUPS[drop.type];
    ctx.save();
    ctx.translate(drop.x, drop.y);
    ctx.rotate(drop.rotation);
    ctx.shadowColor = info.color;
    ctx.shadowBlur = 16;
    ctx.fillStyle = "rgba(4,18,13,0.86)";
    roundRectPath(ctx, -drop.size / 2, -drop.size / 2, drop.size, drop.size, 9);
    ctx.fill();
    ctx.strokeStyle = info.color;
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.shadowBlur = 0;
    ctx.rotate(-drop.rotation);
    ctx.font = `${drop.size * 0.48}px serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(info.icon, 0, 1);
    ctx.restore();
  }
}

export function formatNumber(n, lang = "fa") {
  try {
    return Number(n).toLocaleString(lang === "en" ? "en-US" : "fa-IR");
  } catch {
    return String(n);
  }
}
