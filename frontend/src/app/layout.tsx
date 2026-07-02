import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Orders Service",
  description: "Open Liberty orders platform with Kafka and PostgreSQL",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
