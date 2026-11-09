-- Block Boom Leaderboard - اجرا کن در phpMyAdmin
CREATE TABLE IF NOT EXISTS leaderboard (
    id INT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    name VARCHAR(32) NOT NULL,
    level INT NOT NULL,
    score BIGINT NOT NULL DEFAULT 0,
    is_premium TINYINT(1) NOT NULL DEFAULT 0,
    ip VARCHAR(45) NOT NULL,
    banned TINYINT(1) NOT NULL DEFAULT 0,
    ban_reason VARCHAR(64) NOT NULL DEFAULT '',
    -- سرعت امتیاز واقعی ثبت‌شده (امتیاز بر ثانیه) برای رصد در پنل
    score_rate DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_device (device_id),
    KEY idx_level_score (level DESC, score DESC),
    KEY idx_updated (updated_at),
    KEY idx_score_rate (score_rate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────
-- ضدتقلب: توکن اجرای بازی
-- سرور هنگام شروع هر اجرا یک توکن امضاشده صادر می‌کند. کلید امضا
-- (RUN_TOKEN_SECRET) هرگز داخل اپلیکیشن نیست، پس جعل آن ممکن نیست.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS run_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    run_id CHAR(32) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    level INT NOT NULL,
    started_at INT NOT NULL,          -- unix ثانیه، سمت سرور
    expires_at INT NOT NULL,
    consumed TINYINT(1) NOT NULL DEFAULT 0,
    play_ms INT NOT NULL DEFAULT 0,   -- مدت واقعی بازی (میلی‌ثانیه)
    events INT NOT NULL DEFAULT 0,    -- تعداد رویدادهای زنجیره‌دار
    chain VARCHAR(64) NOT NULL DEFAULT '', -- انتهای زنجیره هش رویدادها
    result TEXT DEFAULT NULL,          -- JSON تصمیم ضدتقلب
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_run (run_id),
    KEY idx_device (device_id),
    KEY idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────
-- ریت‌لیمیت نرخ ارسال. جدول rate_limit از قبل روی سرور با ستون `ip`
-- ساخته شده؛ ستون‌های جدید در بخش MIGRATION پایین فایل ADD می‌شوند.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS rate_limit (
    ip VARCHAR(64) PRIMARY KEY,
    last_submit INT NOT NULL,
    strikes INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ─────────────────────────────────────────────────────────────────────────
-- لاگ رد شدن‌ها (برای پنل ضدتقلب و بررسی بعدی)
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cheat_flags (
    id INT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL DEFAULT '',
    ip VARCHAR(45) NOT NULL DEFAULT '',
    reason VARCHAR(48) NOT NULL,
    detail VARCHAR(255) NOT NULL DEFAULT '',
    level INT NOT NULL DEFAULT 0,
    score BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_device (device_id),
    KEY idx_reason_day (reason, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────
-- تنظیمات پنل (به‌جای بازنویسی فایل config.php روی دیسک)
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(64) NOT NULL PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- برای ریت‌لیمیت ساده با فایل هم میشه ولی با جدول هم میشه
-- (نکته: تعریف rate_limit بالای همین فایل آمده است)

-- خریدهای تاییدشده مایکت
CREATE TABLE IF NOT EXISTS purchases (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL,
    purchase_token VARCHAR(255) NOT NULL,
    signature TEXT NOT NULL,
    -- JSON اصلی خرید. برای اطمینان از اینکه امضا واقعاً مربوط به همین
    -- productId است لازم است (وگرنه امضای یک خرید ارزان قابل استفاده
    -- برای محصول گران‌تر است).
    original_json MEDIUMTEXT DEFAULT NULL,
    -- بدون این ستون معلوم نبود خرید به کدام بازیکن تعلق دارد، پس تایید
    -- خرید هیچ حقی به کسی نمی‌داد.
    device_id VARCHAR(64) DEFAULT NULL,
    coins INT NOT NULL DEFAULT 0,
    is_premium TINYINT(1) NOT NULL DEFAULT 0,
    price_rial INT NOT NULL DEFAULT 0,
    ip VARCHAR(45) NOT NULL,
    verified TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_token (purchase_token),
    KEY idx_created (created_at),
    KEY idx_product (product_id),
    KEY idx_purchase_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- رویدادهای تبلیغات برای پنل مدیریت
CREATE TABLE IF NOT EXISTS ad_events (
    id INT AUTO_INCREMENT PRIMARY KEY,
    -- 32 not 16: 'interstitial_closed' is 19 chars and silently failed to
    -- insert against the old VARCHAR(16).
    event_type VARCHAR(32) NOT NULL,
    placement VARCHAR(64) NOT NULL DEFAULT '',
    device_id VARCHAR(64) NOT NULL DEFAULT '',
    ip VARCHAR(45) NOT NULL DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_type_day (event_type, created_at),
    -- Needed on its own, not just as the tail of idx_type_day: every dashboard
    -- and ads-page aggregate filters on `created_at >= ?` with no
    -- event_type. An index that leads with event_type cannot serve that range
    -- scan, so without this the fastest-growing table in the database is fully
    -- scanned on every page load. The MIGRATION block added this index later
    -- and the fresh-install CREATE TABLE never caught up.
    KEY idx_created (created_at),
    KEY idx_placement (placement),
    KEY idx_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- پنل‌های جایزه و اخطارات
CREATE TABLE IF NOT EXISTS notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    message TEXT NOT NULL,
    target_type ENUM('all', 'specific') NOT NULL DEFAULT 'all',
    target_id VARCHAR(64) DEFAULT NULL,
    -- فقط برای اعلان‌های هدفمند معتبر است (مخاطب یک دستگاه است، پس وقتی آن
    -- دستگاه بخواند، سطر کاملاً خوانده‌شده است). برای برودکست‌ها صفر می‌ماند؛
    -- «خوانده» برای برودکست معنی درست و قابل محاسبه‌ای ندارد. شمارش خواننده‌ها
    -- از notification_reads خوانده می‌شود.
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_created (created_at),
    KEY idx_target (target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- رسید خواندن اعلان، به‌ازای هر دستگاه.
--
-- notifications.is_read یک فلگ روی خود سطر بود، پس اولین دستگاهی که یک
-- برودکست را با mark_read=1 می‌گرفت، آن را برای کل بازیکن‌ها «خوانده» می‌کرد و
-- بقیه هیچ‌وقت اعلان را نمی‌دیدند. این جدول وضعیت خواندن را به (اعلان، دستگاه)
-- می‌برد، پس ذاتاً هر دستگاهی فقط وضعیت خودش را می‌بیند.
CREATE TABLE IF NOT EXISTS notification_reads (
    notification_id INT NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id, device_id),
    KEY idx_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- کدهای دعوتہ دوستان (تیز)
CREATE TABLE IF NOT EXISTS referrals (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    used_by VARCHAR(64) DEFAULT NULL,
    coins_awarded TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_code (code),
    KEY idx_used_by (used_by),
    -- Covers the daily-cap check in referral.php, which now runs inside the
    -- transaction with FOR UPDATE so two concurrent redemptions cannot both
    -- pass the limit check. Without an index that leads with used_by, that
    -- lock is taken by scanning the whole table.
    KEY idx_used_day (used_by, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────
-- جایزه‌های سکه که سرور ثبت کرده ولی کلاینت هنوز نگرفته است.
-- سکه در این بازی ارز محلی است، پس سرور موجودی نگه نمی‌دارد؛ فقط
-- «حق برداشت» ثبت می‌کند و کلاینت یک‌بار می‌گیردش.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coin_grants (
    id INT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    coins INT NOT NULL,
    reason VARCHAR(32) NOT NULL DEFAULT 'referral',
    ref_code VARCHAR(32) DEFAULT NULL,
    claimed_at DATETIME DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_device_unclaimed (device_id, claimed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────
--  پریمیوم هدیه‌شده توسط اپراتور
--
--  چرا این جدول لازم است:
--  دکمهٔ «پریمیوم» در پنل فقط `leaderboard.is_premium` را عوض می‌کرد، و آن
--  ستون فقط یک تاج کنار اسم در جدول امتیازهاست. هیچ‌وقت به گوشی بازیکن
--  گفته نمی‌شد، پس دکمه هیچ اثری روی بازی نداشت — و «حذف پریمیوم» هم به همان
--  دلیل بی‌اثر بود.
--
--  اینجا همان چیزی است که واقعاً به گوشی می‌رسد: app_config.php در هر بار
--  باز شدن و هر بار برگشتن اپ به foreground این جدول را می‌خواند و وضعیت
--  پریمیوم را می‌فرستد، و کلاینت آن را اعمال می‌کند.
--
--  تایم‌اوت لازم است وگرنه «حذف پریمیوم» فقط یعنی «فعلاً پریمیوم هست».
--  until_ts زمان پایان بر حسب ثانیهٔ یونیکس است؛ مقدار 0 یعنی «همیشگی».
--
--  account_id اختیاری است: اگر بازیکن کد بازیابی داشته باشد، پریمیوم به
--  حساب گره می‌خورد تا با عوض کردن گوشی از بین نرود؛ وگرنه فقط به همان
--  دستگاه می‌چسبد.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS premium_grants (
    id INT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    account_id INT DEFAULT NULL,
    -- 1 = فعال، 0 = لغوشده (ردیف نگه داشته می‌شود تا تاریخچه بماند)
    is_premium TINYINT(1) NOT NULL DEFAULT 1,
    -- پایان اعتبار بر حسب ثانیهٔ یونیکس؛ 0 = بدون انقضا
    until_ts BIGINT NOT NULL DEFAULT 0,
    note VARCHAR(255) NOT NULL DEFAULT '',
    granted_by VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- یک ردیف به ازای هر دستگاه؛ دکمهٔ پنل باید دوباره‌نویسی کند نه اینکه
    -- سطر دوم بسازد و بازیکن دو بار پریمیوم ببیند.
    UNIQUE KEY uniq_device (device_id),
    KEY idx_account (account_id),
    KEY idx_until (until_ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────
-- ─────────────────────────────────────────────────────────────────────────
--  مدیریت کاربر از راه دور
--
--  app_bans جداست چون leaderboard فقط برای بازیکنانی است که امتیاز ثبت
--  کرده‌اند. کسی که تازه نصب کرده و هرگز بازی نکرده ردیفی در لیدربرد
--  ندارد، ولی باز هم باید بتوانیم مسدودش کنیم.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app_bans (
    device_id VARCHAR(64) NOT NULL PRIMARY KEY,
    reason VARCHAR(255) NOT NULL DEFAULT '',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    banned_by VARCHAR(64) NOT NULL DEFAULT 'admin',
    banned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    lifted_at DATETIME DEFAULT NULL,
    KEY idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────
-- ─────────────────────────────────────────────────────────────────────────
--  تله‌متری بازی — ورودی admin/monitor.php
--
--  سه جدول، عمداً با سه کار متفاوت، چون نرخ رشدشان یکی نیست:
--
--  telemetry_presence : یک سطر به ازای هر دستگاهِ «الان فعال». این جدول
--      اندازه‌اش برابر تعداد بازیکن‌های یکتای تاریخی است، نه تعداد درخواست‌ها،
--      پس رشدش کند است. کاشی «آنلاین الان» از همین خوانده می‌شود و به همین
--      دلیل هر flush یک upsert اینجا می‌زند.
--
--  telemetry_rollups : جمع هر ساعت برای هر دستگاه. نمودارها از این می‌آیند.
--      یک سطر در ساعت به‌ازای هر بازیکنِ فعال — بزرگ‌ترین جدول، ولی چون ساعتی
--      است رشدش خطی و قابل پیش‌بینی می‌ماند (برخلاف رویداد خام).
--
--  telemetry_events  : رویدادهای نقطه‌ای (شروع/پایان اجرا، اتمام لول، عبور
--      از کمبوی ۵/۱۰/۲۰). کم‌تعداد ولی همان چیزی است که آدم واقعاً می‌خواند.
--      دلیلِ نگه‌داشتن جدا از rollup این است که میانگین‌ها گلوگاه کمبو را پنهان
--      می‌کنند؛ کمبوی x۴۰ روزی یک‌بار اتفاق می‌افتد و در میانگین گم می‌شود.
--
--  چرا سه‌تا و نه یکی: جدول رویداد خام با یک بازیکن فعال در هر دقیقه، چند صد
--  سطر در ساعت می‌سازد. روی هاست اشتراکی با سقف حجم دیتابیس این یعنی پنل
--  بعد از چند هفته کند می‌شود و تمیزکردنش دستی و فراموش‌شدنی است.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS telemetry_presence (
    device_id VARCHAR(64) NOT NULL PRIMARY KEY,
    -- زمان آخرین packet دریافتی. عمداً ستون تاریخ نیست: پنل «۵ دقیقهٔ اخیر»
    -- را با مقایسهٔ عددی حساب می‌کند و تبدیل TIMESTAMP به وقت PHP منبع
    -- دائمی اختلاف یک‌روزه در نمودارها می‌شد.
    last_seen INT UNSIGNED NOT NULL DEFAULT 0,
    last_level INT NOT NULL DEFAULT 0,
    app_version VARCHAR(24) NOT NULL DEFAULT '',
    packets INT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS telemetry_rollups (
    -- ابتدای ساعت به وقت ایران (Asia/Tehran). عدد است تا bucketing سمت PHP و پنل یکی بماند.
    bucket_ts INT UNSIGNED NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    runs INT UNSIGNED NOT NULL DEFAULT 0,
    levels INT UNSIGNED NOT NULL DEFAULT 0,
    blocks INT UNSIGNED NOT NULL DEFAULT 0,
    coins INT UNSIGNED NOT NULL DEFAULT 0,
    shots INT UNSIGNED NOT NULL DEFAULT 0,
    combos INT UNSIGNED NOT NULL DEFAULT 0,
    max_combo SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    active_ms INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket_ts, device_id),
    -- بدون این، همهٔ نمودارهای «۲۴ ساعت اخیر» یک full scan روی کل تاریخ می‌زنند.
    KEY idx_bucket (bucket_ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS telemetry_events (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL DEFAULT '',
    event_type VARCHAR(24) NOT NULL,
    level INT NOT NULL DEFAULT 0,
    -- عدد اصلی رویداد: امتیاز، اندازهٔ کمبو، و…
    value INT NOT NULL DEFAULT 0,
    duration_ms INT UNSIGNED NOT NULL DEFAULT 0,
    meta VARCHAR(191) NOT NULL DEFAULT '',
    -- زمان دریافت روی سرور. نه زمان کلاینت: ساعت گوشی قابل دستکاری است و
    -- برای «الان چند نفر آنلاین‌اند» بی‌فایده.
    at_ts INT UNSIGNED NOT NULL,
    client_ts BIGINT UNSIGNED NOT NULL DEFAULT 0,
    KEY idx_at (at_ts),
    KEY idx_type_at (event_type, at_ts),
    KEY idx_device_at (device_id, at_ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────
--  نسخه‌های اپلیکیشن
--
--  برای اینکه از پنل بتوانی نسخه‌ای را خاموش کنی و کاربران مجبور شوند
--  آپدیت کنند. اپلیکیشن موقع اجرا app_config.php را می‌خواند و اگر
--  نسخه‌اش غیرفعال یا قدیمی باشد، قفل می‌شود.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app_versions (
    version_code INT NOT NULL PRIMARY KEY,
    version_name VARCHAR(32) NOT NULL DEFAULT '',
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    is_latest TINYINT(1) NOT NULL DEFAULT 0,
    force_message TEXT DEFAULT NULL,
    store_url VARCHAR(255) NOT NULL DEFAULT '',
    note VARCHAR(255) NOT NULL DEFAULT '',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────
--  تیکت پشتیبانی
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tickets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    player_name VARCHAR(32) NOT NULL DEFAULT '',
    subject VARCHAR(160) NOT NULL,
    category VARCHAR(24) NOT NULL DEFAULT 'other',
    status ENUM('open','answered','closed') NOT NULL DEFAULT 'open',
    last_admin_reply_at DATETIME DEFAULT NULL,
    -- When the player last opened the thread. The "unread" badge compares
    -- last_admin_reply_at against this, because comparing against `updated_at`
    -- cannot work: the player's own replies move updated_at too, so a ticket
    -- stayed flagged as unread after the player answered it.
    last_read_at DATETIME DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_device (device_id),
    KEY idx_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id INT NOT NULL,
    from_admin TINYINT(1) NOT NULL DEFAULT 0,
    body TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ticket (ticket_id, id),
    CONSTRAINT fk_msg_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────
--  حساب بازیکن + بازیابی بعد از ریست گوشی
--
--  چرا این جدول اضافه شد
--  ────────────────
--  تا پیش از این، «هویت بازیکن» فقط device_id بود — یعنی ANDROID_ID.
--  این مقدار با ریست فکتوری، عوض کردن گوشی، یا حتی نصب دوباره روی دستگاه
--  دیگر عوض می‌شود. نتیجه‌اش این بود که بازیکنی که گوشیش را ریست می‌کرد:
--    * لیدربردش خالی می‌شد (سطرش روی device_id قبلی می‌ماند)،
--    * سکه و لِویلش برمی‌گشت به صفر،
--    * و تیکت‌هایش دیگر قابل دسترسی نبود، چون tickets.php هم با device_id
--      اسکوپ می‌کرد.
--  یعنی دقیقاً همان چیزی که بازیکن گزارش می‌داد: «هیچی ثبت نمی‌شه و تیکت
--  هم نمی‌تونم بفرستم».
--
--  حالا هر بازیکن یک account دارد با یک «کد بازیابی» که خودش می‌سازد.
--  device_id فقط یک «دستگاهِ متصل» است، نه هویت. کد بازیابی را بازیکن
--  ذخیره می‌کند (یا از تنظیمات می‌بیند) و روی گوشی جدید وارد می‌کند.
--
--  progress  هم یک snapshot از وضعیت بازی است تا بعد از بازیابی چیزی از
--  دست نرود. این نسخه پشتیبان است، نه منبع حقیقت: merge همیشه یک‌طرفه
--  و صعودی است (بیشینه)، پس هیچ‌وقت از داده‌ای که هنوز به سرور نرسیده
--  کم نمی‌شود.
--
--  device_links  می‌گوید کدام دستگاه‌ها به این حساب وصل‌اند. بدون آن،
--  یک بازیکن می‌توانست بی‌نهایت بار حسابش را روی دستگاه‌های مختلف بازیابی
--  کند و عملاً روزی یک بار از محدودیت نرخ عبور کند.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS accounts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    -- کد بازیابی: ۱۰ کاراکتر از الفبای بدون ابهام (بدون 0/O و 1/I).
    -- ذخیره‌سازی هش است نه خودِ کد؛ بنابراین دیتابیس لو رفتنش برای تصاحب
    -- همهٔ حساب‌ها کافی نیست.
    recovery_hash CHAR(64) NOT NULL,
    -- چند رقم آخر کد، فقط برای اینکه بازیکن بتواند کدش را تشخیص دهد.
    recovery_hint CHAR(4) NOT NULL DEFAULT '',
    player_name VARCHAR(32) NOT NULL DEFAULT '',
    -- شناسهٔ نصب کلاینت. ANDROID_ID معمولی با نصب دوباره عوض نمی‌شود، اما
    -- با ریست کارخانه یا گوشی جدید عوض می‌شود؛ این ستون «همان بازیکن،
    -- نصب جدید» را قابل تشخیص می‌کند.
    install_id VARCHAR(64) NOT NULL DEFAULT '',
    app_version VARCHAR(24) NOT NULL DEFAULT '',
    install_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_ip VARCHAR(45) NOT NULL DEFAULT '',
    -- خلاصهٔ سریع برای صفحهٔ بازیکنان؛ منبع اصلی account_progress است.
    last_level INT NOT NULL DEFAULT 1,
    last_score BIGINT NOT NULL DEFAULT 0,
    -- ردیف‌های آزمایشی/توسعه‌دهنده که نباید در آمار بازیکنان حساب شوند.
    is_test TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_recovery (recovery_hash),
    KEY idx_seen (last_seen_at),
    KEY idx_install (install_id),
    KEY idx_app_version (app_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- snapshot وضعیت بازی برای هر حساب
CREATE TABLE IF NOT EXISTS account_progress (
    account_id INT NOT NULL PRIMARY KEY,
    level INT NOT NULL DEFAULT 1,
    total_score BIGINT NOT NULL DEFAULT 0,
    coins BIGINT NOT NULL DEFAULT 0,
    -- ارتقاها و اسکین‌ها به صورت JSON نگه‌داری می‌شوند تا با اضافه شدن
    -- ارتقای جدید نیازی به مهاجرت اسکیما نباشد.
    upgrades JSON DEFAULT NULL,
    meta JSON DEFAULT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_prog_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- کدام دستگاه به کدام حساب وصل است
CREATE TABLE IF NOT EXISTS device_links (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_id INT NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- آخرین زمانی که این دستگاه چیزی را همگام کرده؛ برای نمایش وضعیت
    -- «همگام‌شده در ...» و برای محدود کردن بازیابی پرتکرار.
    last_sync_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_device (device_id),
    KEY idx_account (account_id),
    CONSTRAINT fk_link_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- هر نصب بازی. حساب با ANDROID_ID پیدا می‌شود، اما این جدول تعداد نصب‌های
-- واقعی یک بازیکن را نگه می‌دارد و نصب دوباره را از نصب اول جدا می‌کند.
CREATE TABLE IF NOT EXISTS player_installs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_id INT NOT NULL,
    install_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    app_version VARCHAR(24) NOT NULL DEFAULT '',
    first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_install (install_id),
    KEY idx_account (account_id),
    KEY idx_device (device_id),
    CONSTRAINT fk_install_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- عکس روزانهٔ هر بازیکن. برخلاف لیدربرد، اینجا هر حسابی یک ردیف دارد،
-- حتی اگر هیچ‌وقت امتیازی ثبت نکرده باشد. پنل بازیکنان و نمودارهای
-- «بازیکنان فعال» از همین جدول می‌خوانند.
CREATE TABLE IF NOT EXISTS player_daily (
    account_id INT NOT NULL,
    day DATE NOT NULL,
    level INT NOT NULL DEFAULT 1,
    total_score BIGINT NOT NULL DEFAULT 0,
    coins BIGINT NOT NULL DEFAULT 0,
    runs INT UNSIGNED NOT NULL DEFAULT 0,
    levels_cleared INT UNSIGNED NOT NULL DEFAULT 0,
    blocks INT UNSIGNED NOT NULL DEFAULT 0,
    shots INT UNSIGNED NOT NULL DEFAULT 0,
    best_combo SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    play_seconds INT UNSIGNED NOT NULL DEFAULT 0,
    coins_earned BIGINT NOT NULL DEFAULT 0,
    app_version VARCHAR(24) NOT NULL DEFAULT '',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, day),
    KEY idx_day (day),
    KEY idx_day_level (day, level),
    CONSTRAINT fk_daily_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- MIGRATION — دفتر خرید
-- بدون این دو ستون، تایید خرید نه می‌تواند امضا را به محصول گره بزند و نه
-- می‌تواند بفهمد خرید به کدام بازیکن تعلق دارد.
-- ALTER TABLE purchases ADD COLUMN original_json MEDIUMTEXT DEFAULT NULL AFTER signature;
-- ALTER TABLE purchases ADD COLUMN device_id VARCHAR(64) DEFAULT NULL AFTER original_json;
-- ALTER TABLE purchases ADD KEY idx_purchase_device (device_id);
-- قیمت‌های قدیمی اشتباه بودند (۴۹۰۰۰ به‌جای ۱۹۰۰۰ و ...). اصلاح:
-- UPDATE purchases SET price_rial = 190000 WHERE product_id = 'blockboom_coins_small';
-- UPDATE purchases SET price_rial = 390000 WHERE product_id = 'blockboom_coins_medium';
-- UPDATE purchases SET price_rial = 690000 WHERE product_id = 'blockboom_coins_large';
-- UPDATE purchases SET price_rial = 990000 WHERE product_id = 'blockboom_coins_very_larg';

-- ═════════════════════════════════════════════════════════════════════════
--  MIGRATION — فقط برای دیتابیسی که از قبل نصب بوده
--  این بخش را روی دیتابیس فعال یک‌بار اجرا کن. اگر خطای «Duplicate column»
--  یا «Duplicate key name» گرفتی، یعنی از قبل اجرا شده — بی‌خیال شو.
--  (روی نصب تازه، CREATE TABLEهای بالا همه‌چیز را ساخته‌اند.)
-- ═════════════════════════════════════════════════════════════════════════

-- تله‌متری بازی (ورودی admin/monitor.php). روی نصب تازه بالای فایل ساخته شده؛
-- این سه تا را برای دیتابیسی که از قبل نصب بوده یک‌بار اجرا کن.
-- CREATE TABLE IF NOT EXISTS telemetry_presence (
--     device_id VARCHAR(64) NOT NULL PRIMARY KEY,
--     last_seen INT UNSIGNED NOT NULL DEFAULT 0,
--     last_level INT NOT NULL DEFAULT 0,
--     app_version VARCHAR(24) NOT NULL DEFAULT '',
--     packets INT UNSIGNED NOT NULL DEFAULT 0
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- CREATE TABLE IF NOT EXISTS telemetry_rollups (
--     bucket_ts INT UNSIGNED NOT NULL,
--     device_id VARCHAR(64) NOT NULL,
--     runs INT UNSIGNED NOT NULL DEFAULT 0,
--     levels INT UNSIGNED NOT NULL DEFAULT 0,
--     blocks INT UNSIGNED NOT NULL DEFAULT 0,
--     coins INT UNSIGNED NOT NULL DEFAULT 0,
--     shots INT UNSIGNED NOT NULL DEFAULT 0,
--     combos INT UNSIGNED NOT NULL DEFAULT 0,
--     max_combo SMALLINT UNSIGNED NOT NULL DEFAULT 0,
--     active_ms INT UNSIGNED NOT NULL DEFAULT 0,
--     PRIMARY KEY (bucket_ts, device_id),
--     KEY idx_bucket (bucket_ts)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- CREATE TABLE IF NOT EXISTS telemetry_events (
--     id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
--     device_id VARCHAR(64) NOT NULL DEFAULT '',
--     event_type VARCHAR(24) NOT NULL,
--     level INT NOT NULL DEFAULT 0,
--     value INT NOT NULL DEFAULT 0,
--     duration_ms INT UNSIGNED NOT NULL DEFAULT 0,
--     meta VARCHAR(191) NOT NULL DEFAULT '',
--     at_ts INT UNSIGNED NOT NULL,
--     client_ts BIGINT UNSIGNED NOT NULL DEFAULT 0,
--     KEY idx_at (at_ts),
--     KEY idx_type_at (event_type, at_ts),
--     KEY idx_device_at (device_id, at_ts)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ستون‌های ضدتقلب روی لیدربرد
-- ALTER TABLE leaderboard ADD COLUMN banned TINYINT(1) NOT NULL DEFAULT 0 AFTER ip;
-- ALTER TABLE leaderboard ADD COLUMN ban_reason VARCHAR(64) NOT NULL DEFAULT '' AFTER banned;
-- ALTER TABLE leaderboard ADD COLUMN score_rate DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER ban_reason;
-- ALTER TABLE leaderboard MODIFY score BIGINT NOT NULL DEFAULT 0;

-- ایندکس‌ها (فیلتر «امروز/این هفته» روی updated_at است، نه created_at)
-- ALTER TABLE leaderboard ADD KEY idx_updated (updated_at);

-- ─────────────────────────────────────────────────────────────────────────
--  MIGRATION — حساب بازیکن (بازیابی بعد از ریست گوشی)
--
--  روی دیتابیسی که از قبل نصب بوده، سه جدول بالا (accounts,
--  account_progress, device_links) با CREATE TABLE IF NOT EXISTS ساخته
--  نمی‌شوند چون این فایل روی نصب تازه اجرا می‌شود. این بخش را یک‌بار روی
--  دیتابیس فعال اجرا کن (phpMyAdmin → SQL، یا از پنل هاست):
-- ═════════════════════════════════════════════════════════════════════════
-- CREATE TABLE IF NOT EXISTS accounts (
--     id INT AUTO_INCREMENT PRIMARY KEY,
--     recovery_hash CHAR(64) NOT NULL,
--     recovery_hint CHAR(4) NOT NULL DEFAULT '',
--     player_name VARCHAR(32) NOT NULL DEFAULT '',
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     UNIQUE KEY uniq_recovery (recovery_hash),
--     KEY idx_seen (last_seen_at)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
--
-- CREATE TABLE IF NOT EXISTS account_progress (
--     account_id INT NOT NULL PRIMARY KEY,
--     level INT NOT NULL DEFAULT 1,
--     total_score BIGINT NOT NULL DEFAULT 0,
--     coins BIGINT NOT NULL DEFAULT 0,
--     upgrades JSON DEFAULT NULL,
--     meta JSON DEFAULT NULL,
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     CONSTRAINT fk_prog_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
--
-- CREATE TABLE IF NOT EXISTS device_links (
--     id INT AUTO_INCREMENT PRIMARY KEY,
--     account_id INT NOT NULL,
--     device_id VARCHAR(64) NOT NULL,
--     linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     last_sync_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     UNIQUE KEY uniq_device (device_id),
--     KEY idx_account (account_id),
--     CONSTRAINT fk_link_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
--
--  لیدربرد و تیکت‌ها هم به حساب گره می‌خورند تا از گوشی جدید قابل دسترسی
--  باشند. این ستون‌ها اختیاری‌اند: اگر نبودند، کد از device_id قدیمی
--  استفاده می‌کند و بازیکن چیزی از دست نمی‌دهد.
--    ALTER TABLE leaderboard ADD COLUMN account_id INT DEFAULT NULL AFTER device_id;
--    ALTER TABLE leaderboard ADD KEY idx_account (account_id);
--    ALTER TABLE tickets   ADD COLUMN account_id INT DEFAULT NULL AFTER device_id;
--    ALTER TABLE tickets   ADD KEY idx_ticket_account (account_id);
--
--  بک‌فیل: حساب‌سازی برای بازیکنانی که قبلاً این جدول‌ها نبودند. بدون این
--  کار، لیدربرد فعلی بی‌صاحب می‌ماند و بازیابی‌شان غیرممکن است. یک‌بار
--  اجرا کن — برای هر سطر یک حساب می‌سازد و آن را وصل می‌کند.
--    INSERT INTO accounts (recovery_hash, recovery_hint, player_name)
--    SELECT SHA2(CONCAT('bb-migrate-', device_id), 256), RIGHT(device_id, 4), name
--      FROM leaderboard
--     WHERE device_id <> ''
--    ON DUPLICATE KEY UPDATE player_name = VALUES(player_name);
--
--    INSERT INTO device_links (account_id, device_id)
--    SELECT a.id, l.device_id
--      FROM leaderboard l
--      JOIN accounts a ON a.recovery_hash = SHA2(CONCAT('bb-migrate-', l.device_id), 256)
--    ON DUPLICATE KEY UPDATE account_id = VALUES(account_id);
--
--    UPDATE leaderboard l
--      JOIN accounts a ON a.recovery_hash = SHA2(CONCAT('bb-migrate-', l.device_id), 256)
--       SET l.account_id = a.id
--     WHERE l.account_id IS NULL;
-- ═════════════════════════════════════════════════════════════════════════
-- ALTER TABLE leaderboard ADD KEY idx_score_rate (score_rate);

-- شمارش تخلف برای ریت‌لیمیت
-- ALTER TABLE rate_limit ADD COLUMN strikes INT NOT NULL DEFAULT 0 AFTER last_submit;

-- ad_events بدون ایندکس created_at هر بار اسکن کامل می‌شد
-- ALTER TABLE ad_events ADD KEY idx_created (created_at);
-- ALTER TABLE ad_events MODIFY event_type VARCHAR(32) NOT NULL;

-- ستون «آخرین بار باز کردن تیکت». قبلاً با UPDATE ... SET updated_at =
-- updated_at اشتباه پیاده شده بود که هیچ کاری نمی‌کرد، و پرچم unread هم
-- last_admin_reply_at را با updated_at مقایسه می‌کرد که خودِ بازیکن هم با
-- ریپلی‌اش آن را جابه‌جا می‌کند.
-- ALTER TABLE tickets ADD COLUMN last_read_at DATETIME DEFAULT NULL AFTER last_admin_reply_at;

-- ایندکس برای سقف روزانهٔ رفرال که حالا داخل تراکنش و با FOR UPDATE اجرا می‌شود
-- ALTER TABLE referrals ADD KEY idx_used_day (used_by, created_at);

-- رسید خواندن اعلان به‌ازای دستگاه. بدون این جدول، اولین دستگاهی که یک
-- برودکست را می‌گیرد آن را برای همه «خوانده» می‌کند و بقیه بازیکن‌ها اعلان را
-- هرگز نمی‌بینند.
-- CREATE TABLE IF NOT EXISTS notification_reads (
--     notification_id INT NOT NULL,
--     device_id VARCHAR(64) NOT NULL,
--     read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     PRIMARY KEY (notification_id, device_id),
--     KEY idx_device (device_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- ALTER TABLE notifications ADD KEY idx_target (target_id);

-- نرخ‌لیمیت روی start_run.php کلیدهای 'run:' و 'iprun:' می‌سازد که با
-- bb_throttle_key() به ۶۴ بایت محدود می‌شوند؛ پس ستون ip دست‌نخورده می‌ماند.
-- فقط اگر قبلاً خطای «Data too long» دیده‌ای، این را یک‌بار اجرا کن:
-- ALTER TABLE rate_limit MODIFY ip VARCHAR(64) NOT NULL;

-- ═════════════════════════════════════════════════════════════════════════
--  MIGRATION — پریمیوم هدیه‌شده توسط اپراتور
--
--  روی دیتابیسی که از قبل نصب بوده، بخش CREATE بالا اجرا نمی‌شود. این را
--  یک‌بار روی دیتابیس فعال اجرا کن (phpMyAdmin → SQL یا اسکریپت یک‌بارمصرف):
-- ═════════════════════════════════════════════════════════════════════════
-- CREATE TABLE IF NOT EXISTS premium_grants (
--     id INT AUTO_INCREMENT PRIMARY KEY,
--     device_id VARCHAR(64) NOT NULL,
--     account_id INT DEFAULT NULL,
--     is_premium TINYINT(1) NOT NULL DEFAULT 1,
--     until_ts BIGINT NOT NULL DEFAULT 0,
--     note VARCHAR(255) NOT NULL DEFAULT '',
--     granted_by VARCHAR(64) NOT NULL DEFAULT '',
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     UNIQUE KEY uniq_device (device_id),
--     KEY idx_account (account_id),
--     KEY idx_until (until_ts)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
--
--  app_config.php اگر این جدول را نبیند، بی‌سروصدا پریمیوم را «ندارد» گزارش
--  می‌کند و کلاینت پریمیوم هدیه‌شده را پاک می‌کند. برای همین وجود جدول در
--  health.php بررسی می‌شود و نبودش یعنی حالت خراب.
-- ═════════════════════════════════════════════════════════════════════════
-- DELETE FROM premium_grants WHERE until_ts > 0 AND until_ts < UNIX_TIMESTAMP();

-- ═════════════════════════════════════════════════════════════════════════
--  نگهداری: ad_events هر روز بزرگ‌تر می‌شود. یک CRON روزانه بگذار:
--    DELETE FROM ad_events WHERE created_at < NOW() - INTERVAL 90 DAY;
--    DELETE FROM run_tokens WHERE expires_at < UNIX_TIMESTAMP() - INTERVAL 7 DAY;
--    DELETE FROM cheat_flags WHERE created_at < NOW() - INTERVAL 90 DAY;
--    DELETE FROM telemetry_events  WHERE at_ts < UNIX_TIMESTAMP() - 7 * 86400;
--    DELETE FROM telemetry_rollups WHERE bucket_ts < UNIX_TIMESTAMP() - 90 * 86400;
--  و مناسبتاً:
--    DELETE FROM rate_limit WHERE last_submit < UNIX_TIMESTAMP() - INTERVAL 1 DAY;
--
--  دربارهٔ تله‌متری: telemetry.php خودش هم هر ۵۰ درخواست یک‌بار پاک‌سازی
--  می‌کند (احتمالی، تا به CRON وابسته نباشد) — ولی آن فقط یک تور ایمنی است.
--  اگر CRON داری، خط بالا را جدی بگیر. نگه‌داشتن ۹۰ روز rollup و ۷ روز
--  رویداد خام روی هاست اشتراکی تفاوت بین «پنل سریع» و «پنلی که ۳۰ ثانیه
--  طول می‌کشد تا باز شود» است.
--
--  telemetry_presence را پاک نکن: یک سطر به ازای هر بازیکن یکتاست و
--  همان «چه کسی تا حالا بازی کرده» است. اگر خواستی پاکش کنی، بازیکن
--  غیرفعال از تاریخچهٔ آنلاین پنل حذف می‌شود.
--
--  rate_limit را هم پاک کن: کلیدهای ریت‌لیمیت ایستا ('run:'، 'iprun:'،
--  'sub:') بعد از هر دورهٔ پنجره از خودشان عوض می‌شوند ولی سطرشان می‌ماند.
--    DELETE FROM rate_limit WHERE ip LIKE 'run:%'   AND last_submit < UNIX_TIMESTAMP() - INTERVAL 1 DAY;
--    DELETE FROM rate_limit WHERE ip LIKE 'iprun:%' AND last_submit < UNIX_TIMESTAMP() - INTERVAL 1 DAY;
--    DELETE FROM rate_limit WHERE ip LIKE 'sub:%'   AND last_submit < UNIX_TIMESTAMP() - INTERVAL 1 DAY;
--
--  app_settings دیگر هیچ کدی نمی‌خواند یا نمی‌نویسد (اسرار به متغیر محیطی
--  منتقل شدند)، پس می‌توانی حذفش کنی. health.php دیگر وجودش را گزارش نمی‌کند.
--    DROP TABLE IF EXISTS app_settings;
-- ═════════════════════════════════════════════════════════════════════════

-- ═════════════════════════════════════════════════════════════════════════
--  MIGRATION — بازیکنان سرور، نصب‌ها و دادهٔ روزانه
--
--  روی دیتابیس فعال یک‌بار اجرا کن. اگر خطای Duplicate column / Duplicate
--  key گرفتی یعنی قبلاً اجرا شده؛ رد کن. روی نصب تازه، CREATE TABLEهای
--  بالای فایل همه‌چیز را ساخته‌اند و این بخش لازم نیست.
-- ═════════════════════════════════════════════════════════════════════════
-- ALTER TABLE accounts ADD COLUMN install_id VARCHAR(64) NOT NULL DEFAULT '';
-- ALTER TABLE accounts ADD COLUMN app_version VARCHAR(24) NOT NULL DEFAULT '';
-- ALTER TABLE accounts ADD COLUMN install_count INT UNSIGNED NOT NULL DEFAULT 0;
-- ALTER TABLE accounts ADD COLUMN last_ip VARCHAR(45) NOT NULL DEFAULT '';
-- ALTER TABLE accounts ADD COLUMN last_level INT NOT NULL DEFAULT 1;
-- ALTER TABLE accounts ADD COLUMN last_score BIGINT NOT NULL DEFAULT 0;
-- ALTER TABLE accounts ADD COLUMN is_test TINYINT(1) NOT NULL DEFAULT 0;
-- ALTER TABLE accounts ADD KEY idx_install (install_id);
-- ALTER TABLE accounts ADD KEY idx_app_version (app_version);
--
-- CREATE TABLE IF NOT EXISTS player_installs (
--     id INT AUTO_INCREMENT PRIMARY KEY,
--     account_id INT NOT NULL,
--     install_id VARCHAR(64) NOT NULL,
--     device_id VARCHAR(64) NOT NULL,
--     app_version VARCHAR(24) NOT NULL DEFAULT '',
--     first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     UNIQUE KEY uniq_install (install_id),
--     KEY idx_account (account_id),
--     KEY idx_device (device_id),
--     CONSTRAINT fk_install_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
--
-- CREATE TABLE IF NOT EXISTS player_daily (
--     account_id INT NOT NULL,
--     day DATE NOT NULL,
--     level INT NOT NULL DEFAULT 1,
--     total_score BIGINT NOT NULL DEFAULT 0,
--     coins BIGINT NOT NULL DEFAULT 0,
--     runs INT UNSIGNED NOT NULL DEFAULT 0,
--     levels_cleared INT UNSIGNED NOT NULL DEFAULT 0,
--     blocks INT UNSIGNED NOT NULL DEFAULT 0,
--     shots INT UNSIGNED NOT NULL DEFAULT 0,
--     best_combo SMALLINT UNSIGNED NOT NULL DEFAULT 0,
--     play_seconds INT UNSIGNED NOT NULL DEFAULT 0,
--     coins_earned BIGINT NOT NULL DEFAULT 0,
--     app_version VARCHAR(24) NOT NULL DEFAULT '',
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     PRIMARY KEY (account_id, day),
--     KEY idx_day (day),
--     KEY idx_day_level (day, level),
--     CONSTRAINT fk_daily_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
--
--  نسخه‌ها: پیش‌نویس در app_config دیده نمی‌شود تا زمانی که از پنل
--  «منتشر» شود. مقدار پیش‌فرض ۱ است تا نسخه‌های موجود بلافاصله از دست
--  نروند.
-- ALTER TABLE app_versions ADD COLUMN is_published TINYINT(1) NOT NULL DEFAULT 1;
-- ALTER TABLE app_versions ADD COLUMN published_at TIMESTAMP NULL DEFAULT NULL;
-- ═════════════════════════════════════════════════════════════════════════
