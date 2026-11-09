import "./play.css";

export const metadata = {
  title: "بلاک بوم ۱.۱ — بازی آنلاین",
  description:
    "همون بازی بلاک بوم، حالا کاملاً تحت وب. بلاک‌ها را بشکن، کمبو بساز، باس بزن و رکورد خودت را جابه‌جا کن.",
  manifest: "/play/manifest.webmanifest",
  alternates: { canonical: "/play/" },
  appleWebApp: {
    capable: true,
    statusBarStyle: "black-translucent",
    title: "Block Boom",
  },
  icons: {
    icon: "/assets/icons/icon-192.png",
    apple: "/assets/icons/apple-touch-icon.png",
  },
};

export const viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: "cover",
  themeColor: "#04120d",
};

export default function PlayLayout({ children }) {
  return children;
}
