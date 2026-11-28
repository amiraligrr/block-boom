-- News system (2026-11-28)
-- Additive migration. Not executed by the development session.
-- Rollback: DROP TABLE news;

CREATE TABLE IF NOT EXISTS news (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(160) NOT NULL,
    summary       VARCHAR(320) NOT NULL DEFAULT '',
    body          TEXT NOT NULL,
    image_url     VARCHAR(255) NULL,
    is_published  TINYINT(1) NOT NULL DEFAULT 0,
    published_at  DATETIME NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_news_published (is_published, published_at),
    INDEX idx_news_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed one honest welcome item. It is a draft until an operator publishes it.
INSERT INTO news (title, summary, body, is_published)
SELECT 'به نسل جدید Block Boom خوش آمدی',
       'منوی تازه، حالت بی‌نهایت پیوسته و لیدربورد.',
       'این اولین خبر رسمی است. از پنل ادمین می‌توانی خبر بسازی، ویرایش کنی و منتشر کنی.',
       0
WHERE NOT EXISTS (SELECT 1 FROM news);
