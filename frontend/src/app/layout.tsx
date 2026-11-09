import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { Toaster } from "sonner";
import { TickStreamConnector } from "@/components/TickStreamConnector";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "TickLock",
  description: "실시간 시세와 동시성 보장 자동 체결",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="ko"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <TickStreamConnector />
        {children}
        {/* 토스트 알림 영역. 요구사항대로 화면 우측 상단에 띄운다. */}
        <Toaster theme="dark" position="top-right" />
      </body>
    </html>
  );
}
