import type { Metadata } from "next";
import "./globals.css";

const productionHost = process.env.VERCEL_PROJECT_PRODUCTION_URL;

export const metadata: Metadata = {
  metadataBase: new URL(productionHost ? `https://${productionHost}` : "http://localhost:3000"),
  title: "오늘 뭐먹지 - Android APK 다운로드",
  description: "오늘 뭐먹지 최신 Android APK를 다운로드하세요.",
  applicationName: "오늘 뭐먹지",
  icons: {
    icon: "/app-icon.png",
    apple: "/app-icon.png",
  },
  openGraph: {
    title: "오늘 뭐먹지 - Android APK 다운로드",
    description: "오늘 뭐먹지 최신 Android APK를 다운로드하세요.",
    type: "website",
    locale: "ko_KR",
    images: [{ url: "/app-icon.png", width: 1024, height: 1024, alt: "오늘 뭐먹지 앱 아이콘" }],
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
