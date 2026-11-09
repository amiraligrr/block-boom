"use client";

import { useEffect } from "react";
import { initLanding } from "../lib/landing";

export default function Home() {
  useEffect(() => {
    initLanding();
  }, []);

  return (
    <>
<a className="skip-link" href="#main">پرش به محتوا</a>

<canvas id="hero-canvas" aria-hidden="true"></canvas>
<div className="ambient" aria-hidden="true">
  <span className="orb orb-a"></span>
  <span className="orb orb-b"></span>
  <span className="orb orb-c"></span>
  <span className="grid-plane"></span>
</div>

<header className="site-nav" id="site-nav">
  <div className="nav-inner">
    <a className="brand" href="#top" aria-label="بلاک بوم">
      <span className="brand-mark"><img src="/assets/icons/icon-192.png" alt="" width="44" height="44" /></span>
      <span className="brand-text"><strong data-en="Block Boom">بلاک بوم</strong><small>BLOCK BOOM</small></span>
    </a>

    <nav className="nav-links" id="nav-links" aria-label="منوی اصلی">
      <a href="#features" data-en="Features">امکانات</a>
      <a href="#how" data-en="How to play">نحوه بازی</a>
      <a href="#leaderboard" data-en="Leaderboard">لیدربورد</a>
      <a href="#download" data-en="Download">دانلود</a>
      <a href="#faq" data-en="FAQ">سوال‌ها</a>
    </nav>

    <div className="nav-actions">
      <button className="icon-btn" id="lang-toggle" type="button" aria-label="تغییر زبان" title="English">
        <span className="lang-fa">EN</span><span className="lang-en">FA</span>
      </button>
      <button className="icon-btn" id="theme-toggle" type="button" aria-label="تغییر تم" title="روشن / تیره">
        <svg className="sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.42 1.42M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>
        <svg className="moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
      </button>
      <a className="btn btn-primary btn-sm nav-play" href="/play/">
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5.14v13.72a1 1 0 0 0 1.5.86l11-6.86a1 1 0 0 0 0-1.72l-11-6.86A1 1 0 0 0 8 5.14z"/></svg>
        <span data-en="Play now">بازی کن</span>
      </a>
      <button className="icon-btn menu-btn" id="menu-btn" type="button" aria-label="منو" aria-expanded="false">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
      </button>
    </div>
  </div>
</header>

<main id="main">
  
  <section className="hero" id="top">
    <div className="container hero-grid">
      <div className="hero-copy">
        <div className="eyebrow reveal">
          <span className="live-dot"></span>
          <span data-en="Version 1.1 — better balanced, fairer">نسخه ۱.۱ — متعادل‌تر، منصفانه‌تر</span>
          <span className="eyebrow-chip" data-en="NEW">جدید</span>
        </div>

        <h1 className="reveal">
          <span className="line" data-en="Break the blocks.">بلاک‌ها رو بترکون،</span>{" "}
          <span className="line gradient-text" data-en="Break the record.">رکورد رو بشکن.</span>
        </h1>

        <p className="lede reveal" data-en="A fast, fun arcade game: drag the cannon, shatter numbered blocks, chain combos, fight the Mother every 10 levels. Play right here in your browser — or download the Android build.">
          یک بازی ارکیدِ سریع و سرگرم‌کننده: توپ رو بکش، بلاک‌های عددی رو منفجر کن، کمبو بساز و هر ۱۰ مرحله با «بلاک مادر» بجنگ. همین‌جا توی مرورگر بازی کن یا نسخه اندروید رو دانلود کن.
        </p>

        <div className="cta-row reveal">
          <a className="btn btn-primary btn-lg" href="/play/">
            <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5.14v13.72a1 1 0 0 0 1.5.86l11-6.86a1 1 0 0 0 0-1.72l-11-6.86A1 1 0 0 0 8 5.14z"/></svg>
            <span data-en="Play in browser">همین حالا بازی کن</span>
          </a>
          <a className="btn btn-ghost btn-lg" href="/download/BlockBoom-1.1.0.apk" download>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M12 3v12m0 0 4-4m-4 4-4-4"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></svg>
            <span data-en="Download Android">دانلود اندروید</span>
            <span className="btn-tag">APK · ۱۱MB</span>
          </a>
        </div>

        <div className="hero-meta reveal">
          <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg><span data-en="Android 7+">اندروید ۷+</span></span>
          <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg><span data-en="Version 1.1.0">نسخه ۱.۱.۰</span></span>
          <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg><span data-en="Offline PWA">PWA آفلاین</span></span>
        </div>

        <div className="hero-proof reveal">
          <div className="avatar-stack" aria-hidden="true"><span>ا</span><span>م</span><span>آ</span><span>+</span></div>
          <div>
            <strong id="hero-players">۵ بازیکن</strong>
            <small data-en="already on the leaderboard">همین حالا توی لیدربورد</small>
          </div>
          <div className="mini-rating" aria-label="امتیاز ۴.۸ از ۵">
            <span>★★★★★</span><small>۴.۸</small>
          </div>
        </div>
      </div>

      <div className="hero-visual reveal">
        <div className="phone-glow" aria-hidden="true"></div>
        <div className="float-chip chip-1" aria-hidden="true"><span>۶۴</span></div>
        <div className="float-chip chip-2" aria-hidden="true"><span>۳۲</span></div>
        <div className="float-chip chip-3" aria-hidden="true"><span>۱۶</span></div>
        <div className="float-chip chip-4 gold" aria-hidden="true">🍀</div>
        <div className="phone">
          <div className="phone-bezel">
            <div className="phone-screen">
              <canvas id="demo-canvas" width="320" height="640" aria-label="دموی زنده بازی بلاک بوم"></canvas>
              <div className="demo-hud" aria-hidden="true">
                <div className="demo-top">
                  <span className="demo-hearts" id="demo-hearts">❤️ ۲۵</span>
                  <span className="demo-level" id="demo-level">مرحله ۷</span>
                  <span className="demo-score" id="demo-score">۰</span>
                </div>
                <div className="demo-progress"><i></i></div>
                <div className="demo-combo">کمبو ×۳</div>
              </div>
              <div className="demo-label"><span className="live-dot"></span><span data-en="Live gameplay preview">دموی زنده بازی</span></div>
            </div>
          </div>
          <span className="phone-side side-l" aria-hidden="true"></span>
          <span className="phone-side side-r" aria-hidden="true"></span>
        </div>
        <div className="floating-badge badge-a"><strong>+۲٬۴۰۰</strong><small data-en="combo score">امتیاز کمبو</small></div>
        <div className="floating-badge badge-b"><span>☠️</span><small data-en="Boss every 10 levels">باس هر ۱۰ مرحله</small></div>
      </div>
    </div>

    <div className="scroll-hint" aria-hidden="true"><span></span></div>
  </section>

  
  <div className="marquee" aria-hidden="true">
    <div className="marquee-track">
      <span>بلاک بشکن</span><i>✦</i><span>کمبو بساز</span><i>✦</i><span>باس بزن</span><i>✦</i><span>ارتقا بخر</span><i>✦</i><span>رکورد بشکن</span><i>✦</i><span>بلاک بشکن</span><i>✦</i><span>کمبو بساز</span><i>✦</i><span>باس بزن</span><i>✦</i><span>ارتقا بخر</span><i>✦</i><span>رکورد بشکن</span><i>✦</i>
    </div>
  </div>

  
  <section className="stats-band container" aria-label="آمار بازی">
    <div className="stat reveal"><strong id="stat-best">۱۴۵۱</strong><span data-en="Top score">بیشترین امتیاز</span></div>
    <div className="stat reveal"><strong id="stat-players">۵</strong><span data-en="Players">بازیکن</span></div>
    <div className="stat reveal"><strong>۷۲</strong><span data-en="Blocks per level">بلاک در هر مرحله</span></div>
    <div className="stat reveal"><strong>∞</strong><span data-en="Endless levels">مرحله بی‌پایان</span></div>
  </section>

  
  <section className="section" id="features">
    <div className="container">
      <header className="section-head reveal">
        <span className="kicker" data-en="THE CORE LOOP">حلقه اصلی بازی</span>
        <h2 data-en="Every stage is a fresh challenge.">هر مرحله، یک چالش تازه.</h2>
        <p data-en="The loop is easy to learn and satisfying to master: aim, shatter, chain, upgrade, repeat.">حلقه بازی ساده‌ست و تسلط روش لذت‌بخش: نشانه بگیر، بشکن، زنجیره بساز، ارتقا بده، دوباره.</p>
      </header>

      <div className="feature-grid">
        <article className="feature-card reveal">
          <span className="feature-icon i-green">🧱</span>
          <h3 data-en="72 blocks. One stage.">۷۲ بلاک. یک مرحله.</h3>
          <p data-en="A short, readable fight. Numbered blocks, armour plating, ice and lucky chance blocks keep the board changing.">یک نبرد کوتاه و خوانا؛ بلاک‌های عددی، زره‌دار، یخی و بلاک‌های شانسی، هر مرحله رو متفاوت می‌کنن.</p>
          <span className="feature-glow"></span>
        </article>
        <article className="feature-card reveal">
          <span className="feature-icon i-pink">☠️</span>
          <h3 data-en="Boss every 10 levels">باس هر ۱۰ مرحله</h3>
          <p data-en="The MOTHER lands, throws satellites, and comes back for more. Her health scales with your cannon, so it is always a fight.">«بلاک مادر» می‌رسه، بچه می‌سازه و برمی‌گرده. جانش با توپ تو تنظیم می‌شه، پس همیشه یه مبارزه واقعیه.</p>
          <span className="feature-glow"></span>
        </article>
        <article className="feature-card reveal">
          <span className="feature-icon i-gold">⚡</span>
          <h3 data-en="6 permanent upgrades">۶ ارتقای دائمی</h3>
          <p data-en="Damage, fire rate, multiball, pierce, magnet and bullet speed. Every coin buys a visible difference.">قدرت ضربه، نواخت شلیک، تیر چندگانه، نفوذ، آهنربا و سرعت گلوله. هر سکه یه تفاوت واقعی می‌خره.</p>
          <span className="feature-glow"></span>
        </article>
        <article className="feature-card reveal">
          <span className="feature-icon i-cyan">❄️</span>
          <h3 data-en="Power-ups that change the board">پاورآپ‌هایی که بازی رو عوض می‌کنن</h3>
          <p data-en="Freeze, double damage, rapid fire, coin rush, ghost, shield and nuke. Catch them before they expire.">فریز، دابل دمیج، رپید فایر، کوین راش، گوست، سپر و نیوک. قبل از تموم‌شدنشون بگیرشون.</p>
          <span className="feature-glow"></span>
        </article>
        <article className="feature-card reveal">
          <span className="feature-icon i-purple">🔥</span>
          <h3 data-en="Combos and multipliers">کمبو و ضریب امتیاز</h3>
          <p data-en="Kill fast, chain kills, and watch the multiplier climb. The best runs are built, not lucky.">سریع بکش، زنجیره بساز و ضریب رو بالا ببر. بهترین اجراها ساخته می‌شن، نه شانسی.</p>
          <span className="feature-glow"></span>
        </article>
        <article className="feature-card reveal">
          <span className="feature-icon i-blue">📴</span>
          <h3 data-en="Online. Offline. Yours.">آنلاین. آفلاین. مال خودت.</h3>
          <p data-en="The web build is a full PWA: install it on your home screen and keep playing without a connection.">نسخه وب یک PWA کامله: روی صفحه اصلی نصبش کن و بدون اینترنت هم بازی کن.</p>
          <span className="feature-glow"></span>
        </article>
      </div>
    </div>
  </section>

  
  <section className="section how-section" id="how">
    <div className="container">
      <header className="section-head reveal">
        <span className="kicker" data-en="ONE THUMB. ZERO LEARNING CURVE.">یک انگشت. صفر یادگیری.</span>
        <h2 data-en="Three seconds to learn. Weeks to master.">سه ثانیه یاد می‌گیری. هفته‌ها طول می‌کشه تا استاد شی.</h2>
      </header>
      <div className="how-grid">
        <article className="how-card reveal">
          <div className="how-visual">
            <div className="drag-track"><span className="drag-finger">☝️</span><span className="drag-cannon"></span></div>
            <span className="step-num">۱</span>
          </div>
          <h3 data-en="Drag the cannon">توپ رو بکش</h3>
          <p data-en="Move your finger across the screen. The cannon follows and fires automatically — no aim button.">انگشتت رو روی صفحه بکش. توپ دنبالش میاد و خودش شلیک می‌کنه — دکمه شلیک لازم نیست.</p>
        </article>
        <article className="how-card reveal">
          <div className="how-visual">
            <div className="blocks-demo"><span style={{ "--c": "#ff3b30" }}>۳۲</span><span style={{ "--c": "#00c2ff" }}>۶۴</span><span style={{ "--c": "#ffb300" }}>۱۶</span></div>
            <span className="step-num">۲</span>
          </div>
          <h3 data-en="Shatter the blocks">بلاک‌ها رو بشکن</h3>
          <p data-en="The number on a block is its health. Bigger number, more hits. Break it before it reaches you.">عدد روی هر بلاک جانشه. عدد بزرگ‌تر، ضرب بیشتر. قبل از رسیدنش به توپ، خردش کن.</p>
        </article>
        <article className="how-card reveal">
          <div className="how-visual">
            <div className="hearts-demo"><span>❤️</span><span>❤️</span><span>❤️</span><span className="dim">❤️</span><span className="dim">❤️</span></div>
            <span className="step-num">۳</span>
          </div>
          <h3 data-en="Stay alive">زنده بمون</h3>
          <p data-en="A block touching the cannon costs a heart and replays the stage. You start with 25 hearts — use them wisely.">اگه بلاکی به توپ برسه، یک قلب می‌دی و همون مرحله دوباره شروع می‌شه. با ۲۵ قلب شروع می‌کنی — عاقلانه خرجشون کن.</p>
        </article>
      </div>
    </div>
  </section>

  
  <section className="section" id="ways">
    <div className="container">
      <header className="section-head reveal">
        <span className="kicker" data-en="SAME GAME. TWO HOMES.">همون بازی. دو خونه.</span>
        <h2 data-en="Install it, or open it. Your call.">نصبش کن، یا همون‌جا بازش کن. انتخاب با توئه.</h2>
      </header>
      <div className="ways-grid">
        <article className="way-card reveal">
          <div className="way-top">
            <span className="way-icon android">
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M17.6 9.5 19.2 6a.6.6 0 0 0-1-.6l-1.7 3.5A10.4 10.4 0 0 0 12 8.2c-1.6 0-3.1.3-4.5.7L5.8 5.4a.6.6 0 1 0-1 .6l1.6 3.5C3.5 11.1 2 13.9 2 17h20c0-3.1-1.5-5.9-4.4-7.5zM7 14.5a1 1 0 1 1 0-2 1 1 0 0 1 0 2zm10 0a1 1 0 1 1 0-2 1 1 0 0 1 0 2z"/></svg>
            </span>
            <div><h3 data-en="Android APK">نسخه اندروید (APK)</h3><p data-en="The original build — leaderboard, daily missions, shop and saved progress.">نسخه اصلی — لیدربورد، ماموریت روزانه، فروشگاه و ذخیره پیشرفت.</p></div>
          </div>
          <ul className="way-list">
            <li data-en="Direct download, version 1.1.0 (code 22)">دانلود مستقیم، نسخه ۱.۱.۰ (کد ۲۲)</li>
            <li data-en="Works on Android 7 and newer">روی اندروید ۷ به بالا اجرا می‌شه</li>
            <li data-en="11 MB — no store needed">۱۱ مگابایت — بدون نیاز به استور</li>
          </ul>
          <a className="btn btn-primary btn-block" href="/download/BlockBoom-1.1.0.apk" download>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M12 3v12m0 0 4-4m-4 4-4-4"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></svg>
            <span data-en="Download APK">دانلود APK</span>
          </a>
        </article>

        <article className="way-card featured reveal">
          <span className="way-ribbon" data-en="NO INSTALL NEEDED">بدون نصب</span>
          <div className="way-top">
            <span className="way-icon web">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20M12 2a15 15 0 0 0 0 20"/></svg>
            </span>
            <div><h3 data-en="Web / PWA">نسخه وب / PWA</h3><p data-en="The full game loop, running in your browser. Install it for offline play.">همون حلقه کامل بازی، داخل مرورگر. برای بازی آفلاین نصبش کن.</p></div>
          </div>
          <ul className="way-list">
            <li data-en="Play instantly — no download">همین حالا بازی کن — بدون دانلود</li>
            <li data-en="Install to home screen (PWA)">نصب روی صفحه اصلی (PWA)</li>
            <li data-en="Offline after the first load">بعد از اولین بار، آفلاین هم کار می‌کنه</li>
          </ul>
          <a className="btn btn-gold btn-block" href="/play/">
            <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5.14v13.72a1 1 0 0 0 1.5.86l11-6.86a1 1 0 0 0 0-1.72l-11-6.86A1 1 0 0 0 8 5.14z"/></svg>
            <span data-en="Play in browser">توی مرورگر بازی کن</span>
          </a>
        </article>
      </div>
    </div>
  </section>

  
  <section className="section leaderboard-section" id="leaderboard">
    <div className="container">
      <header className="section-head split reveal">
        <div>
          <span className="kicker" data-en="LIVE LEADERBOARD">لیدربورد زنده</span>
          <h2 data-en="Think you can take the top spot?">فکر می‌کنی می‌تونی صدر جدول رو بگیری؟</h2>
        </div>
        <div className="refresh-pill"><span className="live-dot"></span><span data-en="Refreshes every 30s">هر ۳۰ ثانیه به‌روز می‌شه</span></div>
      </header>

      <div className="leaderboard-card reveal">
        <div className="board-head">
          <span data-en="Rank">رتبه</span>
          <span data-en="Player">بازیکن</span>
          <span data-en="Level">مرحله</span>
          <span data-en="Score">امتیاز</span>
        </div>
        <div id="leaderboard-rows" className="board-rows" aria-live="polite">
          <div className="board-skeleton"><span></span><span></span><span></span><span></span></div>
          <div className="board-skeleton"><span></span><span></span><span></span><span></span></div>
          <div className="board-skeleton"><span></span><span></span><span></span><span></span></div>
        </div>
        <div className="board-foot">
          <span id="board-status" data-en="Live from the game server">زنده از سرور بازی</span>
          <a href="/play/" className="text-link" data-en="Beat this score →">این رکورد رو بزن ←</a>
        </div>
      </div>
    </div>
  </section>

  
  <section className="section download-section" id="download">
    <div className="container">
      <div className="download-card reveal">
        <div className="download-copy">
          <span className="kicker" data-en="ANDROID · VERSION 1.1.0">اندروید · نسخه ۱.۱.۰</span>
          <h2 data-en="Take Block Boom with you.">بلاک بوم رو با خودت ببر.</h2>
          <p data-en="The complete Android experience: the same medium-difficulty balance, boss fights, upgrades, daily missions and the global leaderboard.">تجربه کامل اندروید: همون تعادل سختی متوسط، باس‌فایت‌ها، ارتقاها، ماموریت روزانه و لیدربورد جهانی.</p>
          <div className="download-actions">
            <a className="btn btn-primary btn-lg" href="/download/BlockBoom-1.1.0.apk" download id="apk-download">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M12 3v12m0 0 4-4m-4 4-4-4"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></svg>
              <span data-en="Download APK — 11 MB">دانلود APK — ۱۱ مگابایت</span>
            </a>
            <button className="btn btn-ghost btn-lg" id="copy-link" type="button" data-en="Copy download link">کپی لینک دانلود</button>
          </div>
          <div className="download-meta">
            <span><b data-en="Version">نسخه</b> 1.1.0 (22)</span>
            <span><b data-en="Size">حجم</b> 10.2 MiB</span>
            <span><b data-en="Requires">نیاز</b> Android 7+</span>
          </div>
          <details className="install-steps">
            <summary data-en="How to install the APK">چطور APK رو نصب کنم؟</summary>
            <ol>
              <li data-en="Tap the download button and wait for the file to finish.">روی دکمه دانلود بزن و صبر کن فایل کامل بشه.</li>
              <li data-en="Open the file. If Android asks, allow installs from this source.">فایل رو باز کن. اگه اندروید اجازه خواست، نصب از این منبع رو فعال کن.</li>
              <li data-en="Install, open, pick a name and play.">نصب کن، بازش کن، یه اسم انتخاب کن و بازی کن.</li>
            </ol>
          </details>
          <div className="checksum">
            <span>SHA-256</span>
            <code id="sha-text">05404a26099bb9b2f3b5cf4fcb9302b697ffbc456b135aef925aa0b656915317</code>
            <button id="copy-sha" type="button" aria-label="کپی هش">کپی</button>
          </div>
        </div>
        <div className="download-side">
          <div className="qr-card">
            <img src="/assets/icons/download-qr.png" alt="کد QR دانلود بلاک بوم" width="220" height="220" />
            <p data-en="Scan with your phone to download">با گوشی اسکن کن و دانلود کن</p>
          </div>
          <div className="store-badges">
            <span className="store-badge"><span>🤖</span><span><small data-en="Direct APK">APK مستقیم</small><strong>Android</strong></span></span>
            <span className="store-badge"><span>🌐</span><span><small data-en="Installable">نصب‌شدنی</small><strong>PWA</strong></span></span>
          </div>
        </div>
      </div>
    </div>
  </section>

  
  <section className="section" id="faq">
    <div className="container narrow">
      <header className="section-head reveal">
        <span className="kicker" data-en="QUESTIONS">سوال‌های رایج</span>
        <h2 data-en="The short answers.">جواب‌های کوتاه.</h2>
      </header>
      <div className="faq-list">
        <details className="faq-item reveal">
          <summary><span data-en="Is the web version the same game as Android?">آیا نسخه وب همون بازی اندرویده؟</span><i></i></summary>
          <p data-en="Yes — the same core loop: drag the cannon, numbered blocks, 72-block stages, the Mother every 10 levels, six upgrades, power-ups, combos and the same medium-difficulty balance. The web build runs fully in your browser and installs as a PWA.">بله — همون حلقه اصلی: کشیدن توپ، بلاک‌های عددی، مراحل ۷۲ بلاکی، بلاک مادر هر ۱۰ مرحله، شش ارتقا، پاورآپ‌ها، کمبو و همون تعادل سختی متوسط. نسخه وب کاملاً توی مرورگر اجرا می‌شه و به‌صورت PWA نصب می‌شه.</p>
        </details>
        <details className="faq-item reveal">
          <summary><span data-en="Do I need an account to play online?">برای بازی آنلاین باید ثبت‌نام کنم؟</span><i></i></summary>
          <p data-en="No. Pick a display name and press start. Your progress is saved in this browser. The Android build keeps its own saved progress and global leaderboard.">نه. یه اسم نمایشی انتخاب کن و شروع کن. پیشرفتت توی همین مرورگر ذخیره می‌شه. نسخه اندروید پیشرفت و لیدربورد جهانی خودش رو داره.</p>
        </details>
        <details className="faq-item reveal">
          <summary><span data-en="How do I install the Android version?">نسخه اندروید رو چطور نصب کنم؟</span><i></i></summary>
          <p data-en="Download the APK from this page, open it on your phone, allow installs from the browser/file manager if prompted, and install. It is version 1.1.0 (code 22), the same public version as before — no new version number.">APK رو از همین صفحه دانلود کن، روی گوشی بازش کن، اگه لازم شد اجازه نصب از مرورگر/فایل‌منیجر رو بده و نصب کن. نسخه ۱.۱.۰ (کد ۲۲) همون نسخه عمومی قبلیه — شماره نسخه جدید نشده.</p>
        </details>
        <details className="faq-item reveal">
          <summary><span data-en="Does it work offline?">آفلاین هم کار می‌کنه؟</span><i></i></summary>
          <p data-en="The PWA does. After the first load, the service worker caches the game shell and assets, so you can keep playing without a connection. The Android APK is fully offline as well.">نسخه PWA بله. بعد از اولین بار لود، سرویس‌ورکر پوسته بازی و فایل‌ها رو کش می‌کنه و بدون اینترنت هم بازی می‌کنی. نسخه اندروید هم کاملاً آفلاینه.</p>
        </details>
        <details className="faq-item reveal">
          <summary><span data-en="What changed in version 1.1?">نسخه ۱.۱ چی تغییر کرده؟</span><i></i></summary>
          <p data-en="The difficulty is rebalanced to medium: gravity, fall speed and spawn pacing are gentler, and stages stay winnable after a few deaths without losing the challenge.">سختی به «متوسط» تنظیم شده: گرانش، سرعت سقوط و ریتم اسپاون ملایم‌ترن و مرحله‌ها بعد از چند بار باختن هم قابل‌عبورن، بدون اینکه چالش از بین بره.</p>
        </details>
      </div>
    </div>
  </section>

  
  <section className="final-cta container reveal">
    <div className="final-card">
      <div className="final-glow" aria-hidden="true"></div>
      <span className="kicker" data-en="READY?">آماده‌ای؟</span>
      <h2 data-en="Ready for the next run?">آماده‌ی مرحله بعدی؟</h2>
      <p data-en="Version 1.1, balanced and ready. Pick your cannon and go.">نسخه ۱.۱، متعادل و آماده. توپت رو بردار و برو.</p>
      <div className="cta-row center">
        <a className="btn btn-primary btn-lg" href="/play/"><span data-en="Play now">همین حالا بازی کن</span></a>
        <a className="btn btn-ghost btn-lg" href="/download/BlockBoom-1.1.0.apk" download><span data-en="Download Android">دانلود اندروید</span></a>
      </div>
    </div>
  </section>
</main>

<footer className="site-footer">
  <div className="container footer-grid">
    <div className="footer-brand">
      <a className="brand" href="#top">
        <span className="brand-mark"><img src="/assets/icons/icon-192.png" alt="" width="40" height="40" /></span>
        <span className="brand-text"><strong data-en="Block Boom">بلاک بوم</strong><small>BLOCK BOOM 1.1</small></span>
      </a>
      <p data-en="A fast little arcade game about breaking blocks and breaking records.">یه بازی ارکید کوچیک و سریع درباره شکستن بلاک‌ها و شکستن رکوردها.</p>
    </div>
    <div className="footer-col">
      <h4 data-en="Game">بازی</h4>
      <a href="/play/" data-en="Play in browser">بازی در مرورگر</a>
      <a href="/download/BlockBoom-1.1.0.apk" download data-en="Download APK">دانلود APK</a>
      <a href="#leaderboard" data-en="Leaderboard">لیدربورد</a>
    </div>
    <div className="footer-col">
      <h4 data-en="About">درباره</h4>
      <a href="#features" data-en="Features">امکانات</a>
      <a href="#faq" data-en="FAQ">سوال‌ها</a>
      <a href="https://amiraligrr.ir" target="_blank" rel="noopener" data-en="Developer">سازنده</a>
    </div>
    <div className="footer-col">
      <h4 data-en="Status">وضعیت</h4>
      <span className="status-line"><span className="live-dot"></span><span id="server-status" data-en="Server online">سرور آنلاین</span></span>
      <span className="status-line muted">v1.1.0 · code 22</span>
      <span className="status-line muted">PWA · offline ready</span>
    </div>
  </div>
  <div className="container footer-bottom">
    <span>made by Amiraligrr, for everyone, everywhere</span>
    <span>© 2026 Block Boom</span>
  </div>
</footer>

<div className="toast" id="toast" role="status" aria-live="polite"></div>
<button className="to-top" id="to-top" type="button" aria-label="برگشت به بالا">
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="m18 15-6-6-6 6"/></svg>
</button>
    </>
  );
}
