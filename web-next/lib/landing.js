/* ==========================================================================
   Block Boom 1.1 — landing interactions
   Theme + language, scroll reveal, animated hero, live demo, leaderboard.
   ========================================================================== */
export function initLanding() {
  "use strict";
  if (typeof window === "undefined") return;
  if (window.__bbLandingStarted) return;
  window.__bbLandingStarted = true;

  const root = document.documentElement;
  const $ = (sel, scope = document) => scope.querySelector(sel);
  const $$ = (sel, scope = document) => Array.from(scope.querySelectorAll(sel));
  const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const API = "";
  const APK_URL = `${location.origin}/download/BlockBoom-1.1.0.apk`;
  const APK_SHA = "b7583ce6dc5a622cbfb96774e157b53a61185559744a442bbe6bf0c8942ff1fb";

  const state = {
    lang: localStorage.getItem("bb-lang") === "en" ? "en" : "fa",
    theme: localStorage.getItem("bb-theme") === "light" ? "light" : "dark",
    leaderboard: null,
    leaderboardError: false,
    lastBoardAt: 0,
  };

  const copy = {
    fa: {
      title: "بلاک بوم ۱.۱ — بازی ارکید آنلاین و اندروید",
      desc: "بلاک بوم: یک بازی ارکید سریع و سرگرم‌کننده. بلاک‌ها را بشکن، کمبو بساز و باس بزن. همین حالا آنلاین بازی کن یا نسخه اندروید ۱.۱ را دانلود کن.",
      copied: "لینک کپی شد",
      shaCopied: "هش SHA-256 کپی شد",
      copyFailed: "کپی نشد — دستی انتخاب کن",
      online: "زنده از سرور بازی",
      offline: "آفلاین — آخرین رکوردهای ذخیره‌شده",
      updated: "به‌روزرسانی",
      players: (n) => `${format(n, "fa")} بازیکن`,
      serverOnline: "سرور آنلاین",
      serverOffline: "حالت آفلاین",
      empty: "هنوز کسی روی این جدول نیست. اولین نفر باش!",
      langBtn: "تغییر زبان",
      themeBtn: "تغییر تم",
    },
    en: {
      title: "Block Boom 1.1 — Arcade game for Android and the web",
      desc: "Block Boom: a fast, fun arcade game. Shatter blocks, chain combos and fight the Mother. Play online now or download the Android 1.1 build.",
      copied: "Download link copied",
      shaCopied: "SHA-256 copied",
      copyFailed: "Copy failed — select it manually",
      online: "Live from the game server",
      offline: "Offline — showing saved scores",
      updated: "updated",
      players: (n) => `${format(n, "en")} player${n === 1 ? "" : "s"}`,
      serverOnline: "Server online",
      serverOffline: "Offline mode",
      empty: "Nobody on this board yet. Be the first!",
      langBtn: "Change language",
      themeBtn: "Toggle theme",
    },
  };

  function format(n, lang = state.lang) {
    try {
      return Number(n).toLocaleString(lang === "fa" ? "fa-IR" : "en-US");
    } catch {
      return String(n);
    }
  }

  /* ── Theme ─────────────────────────────────────────────────────────────── */

  function applyTheme(theme) {
    state.theme = theme;
    root.dataset.theme = theme;
    localStorage.setItem("bb-theme", theme);
    const meta = $('meta[name="theme-color"]');
    if (meta) meta.content = theme === "light" ? "#f2fbf6" : "#04120d";
    const btn = $("#theme-toggle");
    if (btn) {
      btn.setAttribute("aria-label", copy[state.lang].themeBtn);
      btn.title = copy[state.lang].themeBtn;
    }
  }

  /* ── Language ──────────────────────────────────────────────────────────── */

  const i18nNodes = $$("[data-en]");
  for (const el of i18nNodes) {
    if (!el.dataset.fa) el.dataset.fa = el.innerHTML;
  }

  function applyLang(lang) {
    state.lang = lang;
    localStorage.setItem("bb-lang", lang);
    root.lang = lang;
    root.dir = lang === "fa" ? "rtl" : "ltr";

    for (const el of i18nNodes) {
      const value = lang === "en" ? el.dataset.en : el.dataset.fa;
      if (typeof value === "string") el.innerHTML = value;
    }

    document.title = copy[lang].title;
    const desc = $('meta[name="description"]');
    if (desc) desc.content = copy[lang].desc;
    const ogTitle = $('meta[property="og:title"]');
    if (ogTitle) ogTitle.content = copy[lang].title;
    const ogDesc = $('meta[property="og:description"]');
    if (ogDesc) ogDesc.content = copy[lang].desc;

    const langBtn = $("#lang-toggle");
    if (langBtn) {
      langBtn.title = lang === "fa" ? "English" : "فارسی";
      langBtn.setAttribute("aria-label", copy[lang].langBtn);
    }
    const themeBtn = $("#theme-toggle");
    if (themeBtn) themeBtn.setAttribute("aria-label", copy[lang].themeBtn);

    const server = $("#server-status");
    if (server) server.textContent = server.dataset.online === "1" ? copy[lang].serverOnline : copy[lang].serverOffline;

    const demoHearts = $("#demo-hearts");
    if (demoHearts) demoHearts.textContent = `❤️ ${format(25, lang)}`;
    const demoLevel = $("#demo-level");
    if (demoLevel) demoLevel.textContent = lang === "fa" ? `مرحله ${format(7, lang)}` : `Level ${format(7, lang)}`;
    const demoScore = $("#demo-score");
    if (demoScore) demoScore.textContent = format(demo.score, lang);

    if (state.leaderboard) renderBoard(state.leaderboard);
    else if (state.leaderboardError) renderBoardError();
  }

  /* ── Toast ─────────────────────────────────────────────────────────────── */

  let toastTimer = 0;
  function toast(message) {
    const el = $("#toast");
    if (!el) return;
    el.textContent = message;
    el.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("show"), 2400);
  }

  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      const area = document.createElement("textarea");
      area.value = text;
      area.style.position = "fixed";
      area.style.opacity = "0";
      document.body.appendChild(area);
      area.select();
      let ok = false;
      try { ok = document.execCommand("copy"); } catch { ok = false; }
      area.remove();
      return ok;
    }
  }

  /* ── Navigation ────────────────────────────────────────────────────────── */

  const nav = $("#site-nav");
  const navLinks = $("#nav-links");
  const menuBtn = $("#menu-btn");

  function closeMenu() {
    navLinks?.classList.remove("open");
    menuBtn?.setAttribute("aria-expanded", "false");
  }

  menuBtn?.addEventListener("click", () => {
    const open = navLinks.classList.toggle("open");
    menuBtn.setAttribute("aria-expanded", String(open));
  });
  navLinks?.addEventListener("click", (e) => {
    if (e.target.closest("a")) closeMenu();
  });
  document.addEventListener("click", (e) => {
    if (!navLinks?.classList.contains("open")) return;
    if (!e.target.closest("#nav-links") && !e.target.closest("#menu-btn")) closeMenu();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closeMenu();
  });

  function onScroll() {
    nav?.classList.toggle("scrolled", scrollY > 12);
    $("#to-top")?.classList.toggle("show", scrollY > 700);
  }
  addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  $("#to-top")?.addEventListener("click", () => scrollTo({ top: 0, behavior: reduceMotion ? "auto" : "smooth" }));

  /* ── Reveal on scroll ──────────────────────────────────────────────────── */

  if (!reduceMotion && "IntersectionObserver" in window) {
    const groups = new Map();
    const io = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        const el = entry.target;
        const parent = el.parentElement;
        const index = groups.get(parent)?.indexOf(el) ?? 0;
        el.style.setProperty("--reveal-delay", `${Math.min(index, 7) * 65}ms`);
        el.classList.add("is-visible");
        io.unobserve(el);
      }
    }, { threshold: 0.12, rootMargin: "0px 0px -8% 0px" });

    for (const el of $$(".reveal")) {
      const parent = el.parentElement;
      if (!groups.has(parent)) groups.set(parent, []);
      groups.get(parent).push(el);
      io.observe(el);
    }
  } else {
    for (const el of $$(".reveal")) el.classList.add("is-visible");
  }

  /* ── Stat count-up ─────────────────────────────────────────────────────── */

  const counted = new WeakSet();
  function countUp(el, target) {
    if (!el) return;
    if (reduceMotion || counted.has(el)) {
      el.textContent = format(target);
      return;
    }
    counted.add(el);
    const duration = 900;
    const start = performance.now();
    const from = 0;
    const tick = (now) => {
      const p = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - p, 3);
      el.textContent = format(Math.round(from + (target - from) * eased));
      if (p < 1) requestAnimationFrame(tick);
      else el.textContent = format(target);
    };
    requestAnimationFrame(tick);
  }

  const statObserver = "IntersectionObserver" in window
    ? new IntersectionObserver((entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const el = entry.target;
          countUp(el, Number(el.dataset.value || 0));
          statObserver.unobserve(el);
        }
      }, { threshold: 0.5 })
    : null;

  function watchStat(el, value) {
    if (!el) return;
    el.dataset.value = String(value);
    if (statObserver) statObserver.observe(el);
    else countUp(el, value);
  }

  /* ── Hero background canvas ────────────────────────────────────────────── */

  const heroCanvas = $("#hero-canvas");
  const heroCtx = heroCanvas?.getContext("2d");
  const hero = {
    w: 0, h: 0, dpr: 1, blocks: [], sparks: [], pointerX: 0.5, pointerY: 0.5, raf: 0,
  };
  const PALETTE = ["#34d399", "#22d3ee", "#fbbf24", "#fb7185", "#a78bfa", "#60a5fa"];
  const NUMBERS = [8, 16, 32, 64, 128];

  function sizeCanvas(canvas, ctx, w, h, dpr) {
    canvas.width = Math.max(1, Math.round(w * dpr));
    canvas.height = Math.max(1, Math.round(h * dpr));
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  function initHero() {
    if (!heroCanvas || !heroCtx) return;
    hero.dpr = Math.min(2, devicePixelRatio || 1);
    hero.w = innerWidth;
    hero.h = innerHeight;
    sizeCanvas(heroCanvas, heroCtx, hero.w, hero.h, hero.dpr);

    const count = hero.w < 640 ? 8 : hero.w < 1100 ? 12 : 17;
    hero.blocks = Array.from({ length: count }, (_, i) => {
      const size = 16 + Math.random() * 34;
      return {
        x: Math.random() * hero.w,
        y: Math.random() * hero.h,
        size,
        vy: 7 + Math.random() * 20,
        vx: (Math.random() - 0.5) * 8,
        rot: Math.random() * Math.PI,
        vr: (Math.random() - 0.5) * 0.24,
        color: PALETTE[i % PALETTE.length],
        number: NUMBERS[Math.floor(Math.random() * NUMBERS.length)],
        alpha: 0.05 + Math.random() * 0.15,
        depth: 0.25 + Math.random() * 0.75,
      };
    });
  }

  function drawHero(dt) {
    if (!heroCtx) return;
    const { w, h } = hero;
    heroCtx.clearRect(0, 0, w, h);

    const parallaxX = (hero.pointerX - 0.5) * 22;
    const parallaxY = (hero.pointerY - 0.5) * 14;

    for (const b of hero.blocks) {
      b.x += b.vx * dt * b.depth;
      b.y += b.vy * dt * b.depth;
      b.rot += b.vr * dt;
      if (b.y - b.size > h + 30) {
        b.y = -b.size - 20;
        b.x = Math.random() * w;
      }
      if (b.x < -b.size - 20) b.x = w + b.size;
      if (b.x > w + b.size + 20) b.x = -b.size;

      const px = b.x + parallaxX * b.depth;
      const py = b.y + parallaxY * b.depth;

      heroCtx.save();
      heroCtx.translate(px, py);
      heroCtx.rotate(b.rot);
      heroCtx.globalAlpha = b.alpha;
      heroCtx.fillStyle = b.color;
      heroCtx.shadowColor = b.color;
      heroCtx.shadowBlur = 16;
      roundRect(heroCtx, -b.size / 2, -b.size / 2, b.size, b.size, b.size * 0.24);
      heroCtx.fill();
      heroCtx.shadowBlur = 0;
      heroCtx.globalAlpha = Math.min(0.32, b.alpha * 1.7);
      heroCtx.fillStyle = "#03110b";
      heroCtx.font = `800 ${Math.max(9, b.size * 0.34)}px Vazirmatn, sans-serif`;
      heroCtx.textAlign = "center";
      heroCtx.textBaseline = "middle";
      heroCtx.fillText(String(b.number), 0, 1);
      heroCtx.restore();
    }

    // Slow drifting sparks
    if (hero.sparks.length < 30 && Math.random() < 0.35) {
      hero.sparks.push({
        x: Math.random() * w,
        y: h + 10,
        vx: (Math.random() - 0.5) * 10,
        vy: -(12 + Math.random() * 30),
        life: 1,
        size: 1 + Math.random() * 2,
        color: PALETTE[Math.floor(Math.random() * PALETTE.length)],
      });
    }
    for (let i = hero.sparks.length - 1; i >= 0; i--) {
      const s = hero.sparks[i];
      s.x += s.vx * dt;
      s.y += s.vy * dt;
      s.life -= dt * 0.08;
      if (s.life <= 0 || s.y < -20) {
        hero.sparks.splice(i, 1);
        continue;
      }
      heroCtx.globalAlpha = s.life * 0.55;
      heroCtx.fillStyle = s.color;
      heroCtx.beginPath();
      heroCtx.arc(s.x, s.y, s.size, 0, Math.PI * 2);
      heroCtx.fill();
    }
    heroCtx.globalAlpha = 1;
  }

  function roundRect(ctx, x, y, w, h, r) {
    const rr = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + rr, y);
    ctx.arcTo(x + w, y, x + w, y + h, rr);
    ctx.arcTo(x + w, y + h, x, y + h, rr);
    ctx.arcTo(x, y + h, x, y, rr);
    ctx.arcTo(x, y, x + w, y, rr);
    ctx.closePath();
  }

  let lastHeroTime = 0;
  function heroLoop(now) {
    const dt = Math.min(0.05, (now - lastHeroTime) / 1000 || 0.016);
    lastHeroTime = now;
    drawHero(dt);
    hero.raf = requestAnimationFrame(heroLoop);
  }

  if (heroCanvas && heroCtx && !reduceMotion) {
    initHero();
    hero.raf = requestAnimationFrame(heroLoop);
    addEventListener("resize", () => {
      initHero();
    }, { passive: true });
    addEventListener("pointermove", (e) => {
      hero.pointerX = e.clientX / Math.max(1, innerWidth);
      hero.pointerY = e.clientY / Math.max(1, innerHeight);
    }, { passive: true });
    document.addEventListener("visibilitychange", () => {
      if (document.hidden) cancelAnimationFrame(hero.raf);
      else {
        lastHeroTime = performance.now();
        hero.raf = requestAnimationFrame(heroLoop);
      }
    });
  } else if (heroCanvas && heroCtx) {
    initHero();
    drawHero(0.016);
  }

  /* ── Phone demo ────────────────────────────────────────────────────────── */

  const demoCanvas = $("#demo-canvas");
  const demoCtx = demoCanvas?.getContext("2d");
  const DEMO_W = 320;
  const DEMO_H = 660;
  const demo = {
    blocks: [], bullets: [], particles: [], coins: [],
    cannonX: DEMO_W / 2, targetX: DEMO_W / 2, cooldown: 0,
    score: 0, combo: 0, comboTimer: 0, level: 7, time: 0,
    lastHud: 0, raf: 0, lastTime: 0,
  };
  const DEMO_COLORS = ["#34d399", "#22d3ee", "#fbbf24", "#fb7185", "#a78bfa", "#60a5fa"];

  function demoBlock() {
    const size = 44 + Math.random() * 14;
    return {
      x: 18 + Math.random() * (DEMO_W - size - 36),
      y: -size - Math.random() * 90,
      w: size,
      h: size,
      hp: [8, 16, 32, 64][Math.floor(Math.random() * 4)],
      maxHp: 0,
      color: DEMO_COLORS[Math.floor(Math.random() * DEMO_COLORS.length)],
      rot: (Math.random() - 0.5) * 0.16,
      vy: 16 + Math.random() * 12,
      vx: (Math.random() - 0.5) * 14,
      phase: Math.random() * Math.PI * 2,
    };
  }

  function initDemo() {
    if (!demoCanvas || !demoCtx) return;
    const dpr = Math.min(2, devicePixelRatio || 1);
    demoCanvas.width = Math.round(DEMO_W * dpr);
    demoCanvas.height = Math.round(DEMO_H * dpr);
    demoCtx.setTransform(dpr, 0, 0, dpr, 0, 0);
    demo.blocks = Array.from({ length: 7 }, () => {
      const b = demoBlock();
      b.y = 80 + Math.random() * 300;
      return b;
    });
    demo.bullets.length = 0;
    demo.particles.length = 0;
    demo.coins.length = 0;
    demo.score = 0;
    demo.combo = 0;
    demo.comboTimer = 0;
    demo.time = 0;
  }

  function demoFire() {
    demo.bullets.push({
      x: demo.cannonX,
      y: DEMO_H - 130,
      vy: -560,
      r: 3.2,
    });
  }

  function demoExplode(x, y, color, amount = 12) {
    for (let i = 0; i < amount; i++) {
      const a = Math.random() * Math.PI * 2;
      const s = 30 + Math.random() * 160;
      demo.particles.push({
        x, y,
        vx: Math.cos(a) * s,
        vy: Math.sin(a) * s,
        life: 0.4 + Math.random() * 0.35,
        max: 0.75,
        size: 1.5 + Math.random() * 3.2,
        color,
      });
    }
  }

  function demoTick(dt) {
    demo.time += dt;
    demo.targetX = DEMO_W / 2 + Math.sin(demo.time * 0.65) * (DEMO_W * 0.3);
    demo.cannonX += (demo.targetX - demo.cannonX) * (1 - Math.exp(-7 * dt));

    demo.cooldown -= dt;
    if (demo.cooldown <= 0) {
      demoFire();
      demo.cooldown = 0.2;
    }

    for (let i = demo.blocks.length - 1; i >= 0; i--) {
      const b = demo.blocks[i];
      b.y += b.vy * dt;
      b.x += Math.sin(demo.time * 1.2 + b.phase) * 12 * dt;
      b.x = Math.max(8, Math.min(DEMO_W - b.w - 8, b.x));
      if (b.y > DEMO_H - 100) {
        demo.blocks.splice(i, 1);
        demo.combo = 0;
        demoExplode(b.x + b.w / 2, DEMO_H - 90, "#fb7185", 8);
      }
    }

    for (let i = demo.bullets.length - 1; i >= 0; i--) {
      const p = demo.bullets[i];
      p.y += p.vy * dt;
      if (p.y < -20) { demo.bullets.splice(i, 1); continue; }
      let hit = false;
      for (let j = demo.blocks.length - 1; j >= 0; j--) {
        const b = demo.blocks[j];
        if (p.x > b.x && p.x < b.x + b.w && p.y > b.y && p.y < b.y + b.h) {
          b.hp -= 8;
          demoExplode(p.x, p.y, b.color, 4);
          if (b.hp <= 0) {
            demo.score += b.maxHp || 32;
            demo.combo = demo.comboTimer > 0 ? demo.combo + 1 : 1;
            demo.comboTimer = 1;
            demoExplode(b.x + b.w / 2, b.y + b.h / 2, b.color, 18);
            demo.coins.push({ x: b.x + b.w / 2, y: b.y + b.h / 2, vy: -120, life: 1.4 });
            demo.blocks.splice(j, 1);
          }
          hit = true;
          break;
        }
      }
      if (hit) demo.bullets.splice(i, 1);
    }

    for (let i = demo.coins.length - 1; i >= 0; i--) {
      const c = demo.coins[i];
      c.vy += 420 * dt;
      c.y += c.vy * dt;
      c.life -= dt;
      if (c.life <= 0 || c.y > DEMO_H - 110) demo.coins.splice(i, 1);
    }

    for (let i = demo.particles.length - 1; i >= 0; i--) {
      const p = demo.particles[i];
      p.vy += 420 * dt;
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.life -= dt;
      if (p.life <= 0) demo.particles.splice(i, 1);
    }

    demo.comboTimer = Math.max(0, demo.comboTimer - dt);
    if (demo.comboTimer === 0) demo.combo = 0;

    while (demo.blocks.length < 7) {
      const b = demoBlock();
      demo.blocks.push(b);
    }

    // Update the HUD a few times a second, not every frame.
    demo.lastHud += dt;
    if (demo.lastHud > 0.18) {
      demo.lastHud = 0;
      const score = $(".demo-score");
      if (score) score.textContent = format(demo.score);
      const combo = $(".demo-combo");
      if (combo) {
        combo.textContent = demo.combo > 1 ? `${state.lang === "fa" ? "کمبو" : "COMBO"} ×${format(demo.combo)}` : (state.lang === "fa" ? "کمبو آماده" : "COMBO READY");
        combo.style.opacity = demo.combo > 1 ? "1" : "0.55";
      }
    }
  }

  function demoDraw() {
    if (!demoCtx) return;
    const ctx = demoCtx;
    ctx.clearRect(0, 0, DEMO_W, DEMO_H);

    const bg = ctx.createLinearGradient(0, 0, 0, DEMO_H);
    bg.addColorStop(0, "#03120c");
    bg.addColorStop(0.55, "#04180f");
    bg.addColorStop(1, "#020c08");
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, DEMO_W, DEMO_H);

    // Grid + stars
    ctx.strokeStyle = "rgba(52,211,153,0.055)";
    ctx.lineWidth = 1;
    for (let x = 20; x < DEMO_W; x += 40) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, DEMO_H); ctx.stroke();
    }
    for (let y = 20; y < DEMO_H; y += 40) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(DEMO_W, y); ctx.stroke();
    }
    for (let i = 0; i < 18; i++) {
      const x = (i * 97 + 31) % DEMO_W;
      const y = (i * 173 + 47) % DEMO_H;
      const tw = 0.25 + 0.55 * (0.5 + 0.5 * Math.sin(demo.time * 1.4 + i));
      ctx.globalAlpha = tw;
      ctx.fillStyle = i % 3 === 0 ? "#fde68a" : "#a7f3d0";
      ctx.beginPath(); ctx.arc(x, y, i % 4 === 0 ? 1.4 : 0.9, 0, Math.PI * 2); ctx.fill();
    }
    ctx.globalAlpha = 1;

    // Floor glow
    const floor = ctx.createLinearGradient(0, DEMO_H - 140, 0, DEMO_H);
    floor.addColorStop(0, "rgba(52,211,153,0)");
    floor.addColorStop(1, "rgba(52,211,153,0.12)");
    ctx.fillStyle = floor;
    ctx.fillRect(0, DEMO_H - 140, DEMO_W, 140);
    ctx.strokeStyle = "rgba(52,211,153,0.26)";
    ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(0, DEMO_H - 96); ctx.lineTo(DEMO_W, DEMO_H - 96); ctx.stroke();

    // Blocks
    for (const b of demo.blocks) {
      ctx.save();
      ctx.translate(b.x + b.w / 2, b.y + b.h / 2);
      ctx.rotate(b.rot);
      ctx.shadowColor = b.color;
      ctx.shadowBlur = 14;
      const grad = ctx.createLinearGradient(-b.w / 2, -b.h / 2, b.w / 2, b.h / 2);
      grad.addColorStop(0, b.color);
      grad.addColorStop(1, shade(b.color, -0.32));
      ctx.fillStyle = grad;
      roundRect(ctx, -b.w / 2, -b.h / 2, b.w, b.h, 10);
      ctx.fill();
      ctx.shadowBlur = 0;
      ctx.strokeStyle = "rgba(255,255,255,0.3)";
      ctx.lineWidth = 1;
      roundRect(ctx, -b.w / 2 + 0.5, -b.h / 2 + 0.5, b.w - 1, b.h - 1, 10);
      ctx.stroke();
      ctx.fillStyle = "#05130d";
      ctx.font = "900 15px Vazirmatn, sans-serif";
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(String(Math.max(0, b.hp)), 0, 1);
      ctx.restore();
    }

    // Coins
    for (const c of demo.coins) {
      ctx.globalAlpha = Math.min(1, c.life * 2);
      ctx.fillStyle = "#fbbf24";
      ctx.shadowColor = "#fbbf24";
      ctx.shadowBlur = 10;
      ctx.beginPath(); ctx.arc(c.x, c.y, 4, 0, Math.PI * 2); ctx.fill();
      ctx.shadowBlur = 0;
    }
    ctx.globalAlpha = 1;

    // Bullets
    for (const p of demo.bullets) {
      ctx.strokeStyle = "rgba(34,211,238,0.45)";
      ctx.lineWidth = 3;
      ctx.beginPath(); ctx.moveTo(p.x, p.y + 12); ctx.lineTo(p.x, p.y); ctx.stroke();
      ctx.fillStyle = "#ecfeff";
      ctx.shadowColor = "#22d3ee";
      ctx.shadowBlur = 12;
      ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.fill();
      ctx.shadowBlur = 0;
    }

    // Cannon
    const cx = demo.cannonX;
    const cy = DEMO_H - 96;
    const glow = ctx.createRadialGradient(cx, cy, 2, cx, cy, 34);
    glow.addColorStop(0, "rgba(110,231,183,0.5)");
    glow.addColorStop(1, "rgba(110,231,183,0)");
    ctx.fillStyle = glow;
    ctx.beginPath(); ctx.arc(cx, cy, 34, 0, Math.PI * 2); ctx.fill();
    const cg = ctx.createRadialGradient(cx - 6, cy - 8, 2, cx, cy, 19);
    cg.addColorStop(0, "#d1fae5");
    cg.addColorStop(0.45, "#34d399");
    cg.addColorStop(1, "#047857");
    ctx.fillStyle = cg;
    ctx.beginPath(); ctx.arc(cx, cy, 19, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = "rgba(255,255,255,0.4)";
    ctx.lineWidth = 1.2;
    ctx.stroke();
    ctx.fillStyle = "rgba(4,18,13,0.72)";
    ctx.beginPath(); ctx.arc(cx, cy, 6.5, 0, Math.PI * 2); ctx.fill();

    // Particles
    for (const p of demo.particles) {
      ctx.globalAlpha = Math.max(0, p.life / p.max);
      ctx.fillStyle = p.color;
      ctx.beginPath(); ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2); ctx.fill();
    }
    ctx.globalAlpha = 1;
  }

  function shade(hex, amount) {
    const n = parseInt(hex.slice(1), 16);
    let r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
    r = Math.max(0, Math.min(255, Math.round(r * (1 + amount))));
    g = Math.max(0, Math.min(255, Math.round(g * (1 + amount))));
    b = Math.max(0, Math.min(255, Math.round(b * (1 + amount))));
    return `rgb(${r},${g},${b})`;
  }

  let lastDemoTime = 0;
  function demoLoop(now) {
    const dt = Math.min(0.04, (now - lastDemoTime) / 1000 || 0.016);
    lastDemoTime = now;
    demoTick(dt);
    demoDraw();
    demo.raf = requestAnimationFrame(demoLoop);
  }

  if (demoCanvas && demoCtx) {
    initDemo();
    if (!reduceMotion) demo.raf = requestAnimationFrame(demoLoop);
    else { demoTick(0.016); demoDraw(); }
    document.addEventListener("visibilitychange", () => {
      if (!demoCanvas || reduceMotion) return;
      if (document.hidden) cancelAnimationFrame(demo.raf);
      else {
        lastDemoTime = performance.now();
        demo.raf = requestAnimationFrame(demoLoop);
      }
    });
  }

  /* ── Leaderboard ───────────────────────────────────────────────────────── */

  const FALLBACK = [
    { name: "امیرعلییییی", level: 23, score: 1451, rank: 1 },
    { name: "امیر", level: 10, score: 898, rank: 2 },
    { name: "محمد", level: 2, score: 0, rank: 3 },
    { name: "fhc", level: 1, score: 0, rank: 4 },
    { name: "آبتین", level: 1, score: 0, rank: 5 },
  ];

  function playerInitial(name) {
    const clean = String(name || "?").trim();
    return clean ? Array.from(clean)[0] : "?";
  }

  function renderBoard(payload) {
    const rowsEl = $("#leaderboard-rows");
    if (!rowsEl) return;
    const rows = Array.isArray(payload) ? payload : (payload?.data || []);
    state.leaderboard = payload;
    state.leaderboardError = false;

    const list = rows.length ? rows : [];
    const t = copy[state.lang];
    rowsEl.replaceChildren();

    if (!list.length) {
      const empty = document.createElement("div");
      empty.className = "board-empty";
      empty.textContent = t.empty;
      rowsEl.appendChild(empty);
    } else {
      list.forEach((row, index) => {
        const rank = Number(row.rank || index + 1);
        const el = document.createElement("div");
        el.className = `board-row${rank <= 3 ? ` top-${rank}` : ""}`;

        const rankCell = document.createElement("div");
        rankCell.className = "rank-cell";
        const medal = rank === 1 ? "🥇" : rank === 2 ? "🥈" : rank === 3 ? "🥉" : "";
        if (medal) {
          const m = document.createElement("span");
          m.className = "rank-medal";
          m.textContent = medal;
          rankCell.appendChild(m);
        }
        const rn = document.createElement("span");
        rn.textContent = format(rank);
        rankCell.appendChild(rn);

        const player = document.createElement("div");
        player.className = "player-cell";
        const avatar = document.createElement("span");
        avatar.className = "player-avatar";
        avatar.textContent = playerInitial(row.name);
        const name = document.createElement("span");
        name.className = "player-name";
        name.textContent = row.name || "—";
        const crown = document.createElement("span");
        crown.className = "player-crown";
        crown.textContent = row.crown || (row.is_premium ? "👑" : "");
        player.append(avatar, name, crown);

        const level = document.createElement("div");
        level.className = "level-cell";
        level.append(document.createTextNode(`${state.lang === "fa" ? "مرحله" : "Level"} `));
        const b = document.createElement("b");
        b.textContent = format(Number(row.level || 0));
        level.appendChild(b);

        const score = document.createElement("div");
        score.className = "score-cell";
        score.textContent = format(Number(row.score || 0));

        el.append(rankCell, player, level, score);
        rowsEl.appendChild(el);
      });
    }

    const total = Number(payload?.pagination?.total ?? list.length);
    const best = list.reduce((max, row) => Math.max(max, Number(row.score || 0)), 0);

    const statBest = $("#stat-best");
    if (statBest) statBest.dataset.value = String(best);
    countUp(statBest, best);

    const statPlayers = $("#stat-players");
    if (statPlayers) statPlayers.dataset.value = String(total);
    countUp(statPlayers, total);

    const heroPlayers = $("#hero-players");
    if (heroPlayers) heroPlayers.textContent = copy[state.lang].players(total);

    const avatars = $$(".avatar-stack span");
    list.slice(0, 4).forEach((row, i) => {
      if (avatars[i]) avatars[i].textContent = playerInitial(row.name);
    });

    const status = $("#board-status");
    if (status) {
      const time = new Date();
      const stamp = time.toLocaleTimeString(state.lang === "fa" ? "fa-IR" : "en-US", { hour: "2-digit", minute: "2-digit" });
      status.textContent = `${copy[state.lang].online} · ${copy[state.lang].updated} ${stamp}`;
    }
    state.lastBoardAt = Date.now();
  }

  function renderBoardError() {
    state.leaderboardError = true;
    const rowsEl = $("#leaderboard-rows");
    if (rowsEl && !rowsEl.children.length) {
      renderBoard({ data: FALLBACK, pagination: { total: FALLBACK.length } });
      state.leaderboardError = true;
    }
    const status = $("#board-status");
    if (status) status.textContent = copy[state.lang].offline;
  }

  async function loadLeaderboard() {
    try {
      const res = await fetch(`${API}/leaderboard.php?limit=8&period=all`, {
        headers: { Accept: "application/json" },
        cache: "no-store",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      if (!data || data.ok !== true) throw new Error("Bad payload");
      renderBoard(data);
    } catch {
      if (!state.leaderboard) renderBoard({ data: FALLBACK, pagination: { total: FALLBACK.length } });
      renderBoardError();
    }
  }

  /* ── Server status ─────────────────────────────────────────────────────── */

  async function loadHealth() {
    const el = $("#server-status");
    if (!el) return;
    try {
      const res = await fetch(`${API}/health.php`, { headers: { Accept: "application/json" }, cache: "no-store" });
      if (!res.ok) throw new Error("down");
      const data = await res.json();
      if (!data || data.healthy !== true) throw new Error("unhealthy");
      el.dataset.online = "1";
      el.textContent = copy[state.lang].serverOnline;
    } catch {
      el.dataset.online = "0";
      el.textContent = copy[state.lang].serverOffline;
    }
  }

  /* ── Wire up ───────────────────────────────────────────────────────────── */

  applyTheme(state.theme);
  applyLang(state.lang);

  $("#theme-toggle")?.addEventListener("click", () => {
    applyTheme(state.theme === "dark" ? "light" : "dark");
  });
  $("#lang-toggle")?.addEventListener("click", () => {
    applyLang(state.lang === "fa" ? "en" : "fa");
    closeMenu();
  });

  $("#copy-link")?.addEventListener("click", async () => {
    const ok = await copyText(APK_URL);
    toast(ok ? copy[state.lang].copied : copy[state.lang].copyFailed);
  });
  $("#copy-sha")?.addEventListener("click", async () => {
    const ok = await copyText(APK_SHA);
    toast(ok ? copy[state.lang].shaCopied : copy[state.lang].copyFailed);
  });
  $("#apk-download")?.addEventListener("click", () => {
    // The anchor handles the download; this is just a tiny acknowledgement.
    toast(state.lang === "fa" ? "دانلود نسخه ۱.۱.۰ شروع شد" : "Version 1.1.0 download started");
  });

  loadHealth();
  loadLeaderboard();

  setInterval(() => {
    if (document.hidden) return;
    if (Date.now() - state.lastBoardAt > 28_000) loadLeaderboard();
  }, 30_000);

  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && Date.now() - state.lastBoardAt > 28_000) loadLeaderboard();
  });

  // Expose a tiny debug hook for automated checks.
  window.__blockBoom = { state, loadLeaderboard, renderBoard };
}
