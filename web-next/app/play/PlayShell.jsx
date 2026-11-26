"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { usePlay } from "./PlayContext";

const NAV_ITEMS = [
  { href: "/play/", fa: "خانه", en: "Home", icon: "🏠" },
  { href: "/play/game/", fa: "بازی", en: "Play", icon: "🎮" },
  { href: "/play/shop/", fa: "فروشگاه", en: "Shop", icon: "🛒" },
  { href: "/play/stats/", fa: "رکورد", en: "Stats", icon: "📊" },
  { href: "/play/settings/", fa: "تنظیمات", en: "Settings", icon: "⚙️" },
];

export default function PlayShell({ children }) {
  const pathname = usePathname() || "";
  const { snap, lang, t, toggleLang, handleSound, toast } = usePlay();
  const isGame = /^\/play\/game(\/|$)/.test(pathname);

  const items = NAV_ITEMS.map((item) => ({
    ...item,
    label: lang === "en" ? item.en : item.fa,
  }));

  return (
    <>
      {children}
      {!isGame ? (
        <nav className="play-bottom-nav" aria-label="منوی اصلی بازی">
          {items.map((item) => {
            const active =
              item.href === "/play/"
                ? pathname === "/play/"
                : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`play-bottom-link${active ? " is-active" : ""}`}
                aria-current={active ? "page" : undefined}
              >
                <span className="play-bottom-icon" aria-hidden="true">
                  {item.icon}
                </span>
                <span className="play-bottom-label">{item.label}</span>
              </Link>
            );
          })}
        </nav>
      ) : null}
      {toast ? (
        <div className="play-toast" role="status" aria-live="polite">
          {toast}
        </div>
      ) : null}
    </>
  );
}
