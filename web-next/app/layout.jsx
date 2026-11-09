import "./globals.css";

export const metadata = {
  title: "بلاک بوم ۱.۱ — بازی ارکید آنلاین و اندروید",
  description:
    "بلاک بوم: یک بازی ارکید سریع و سرگرم‌کننده. بلاک‌ها را بشکن، کمبو بساز و باس بزن. همین حالا آنلاین بازی کن یا نسخه اندروید ۱.۱ را دانلود کن.",
  metadataBase: new URL("https://blockboomapi.amiraligrr.ir"),
  alternates: { canonical: "/" },
  openGraph: {
    title: "بلاک بوم ۱.۱ — بترکون، بالا برو، رکورد بزن",
    description:
      "همون بازی اندروید، حالا کاملاً تحت وب. نسخه PWA آفلاین + دانلود مستقیم APK.",
    url: "https://blockboomapi.amiraligrr.ir/",
    siteName: "Block Boom",
    images: ["/assets/icons/icon-512.png"],
    locale: "fa_IR",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "بلاک بوم ۱.۱ — بازی ارکید آنلاین و اندروید",
    description:
      "همون بازی اندروید، حالا کاملاً تحت وب. نسخه PWA آفلاین + دانلود مستقیم APK.",
    images: ["/assets/icons/icon-512.png"],
  },
  icons: {
    icon: "/assets/icons/icon-192.png",
    apple: "/assets/icons/apple-touch-icon.png",
  },
};

export const viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#04120d",
};

const jsonLd = {
  "@context": "https://schema.org",
  "@type": "VideoGame",
  name: "Block Boom",
  alternateName: "بلاک بوم",
  url: "https://blockboomapi.amiraligrr.ir/",
  applicationCategory: "GameApplication",
  operatingSystem: "Android 7+, Web",
  softwareVersion: "1.1.0",
  offers: { "@type": "Offer", price: "0", priceCurrency: "IRR" },
  description: "بازی ارکید بلاک‌شکن با باس‌فایت، ارتقا و لیدربورد.",
};

export default function RootLayout({ children }) {
  return (
    <html lang="fa" dir="rtl" suppressHydrationWarning>
      <body>
        <script
          dangerouslySetInnerHTML={{
            __html: `try{var t=localStorage.getItem("bb-theme");if(t==="light")document.documentElement.dataset.theme="light";var l=localStorage.getItem("bb-lang");if(l==="en"){document.documentElement.lang="en";document.documentElement.dir="ltr";}}catch(e){}`,
          }}
        />
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
        {children}
      </body>
    </html>
  );
}
