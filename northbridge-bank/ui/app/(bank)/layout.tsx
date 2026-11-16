'use client';

import { useEffect } from 'react';
import { AppShell } from '@/components/AppShell';
import { useSession } from '@/lib/session';

/** Client-side guard. The real enforcement is server-side: BFF session + backend JWT checks. */
export default function BankLayout({ children }: { children: React.ReactNode }) {
  const { session, loading } = useSession();

  useEffect(() => {
    if (!loading && !session?.authenticated) {
      const returnTo = window.location.pathname + window.location.search;
      window.location.href = `/auth/login?returnTo=${encodeURIComponent(returnTo)}`;
    }
  }, [loading, session]);

  if (loading || !session?.authenticated) {
    return (
      <div className="center-screen">
        <div className="spinner" aria-label="Loading" />
      </div>
    );
  }
  return <AppShell>{children}</AppShell>;
}
