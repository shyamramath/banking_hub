import type { Metadata } from 'next';
import './globals.css';
import { SessionProvider } from '@/lib/session';

export const metadata: Metadata = {
  title: 'Northbridge Bank — Online Banking',
  description: 'Demo online banking app: Next.js UI, Express BFF, Spring Boot single-endpoint dispatcher.',
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <SessionProvider>{children}</SessionProvider>
      </body>
    </html>
  );
}
