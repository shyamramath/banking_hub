'use client';

import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { getSession } from './api';
import type { Session } from './types';

interface SessionState {
  session: Session | null;
  loading: boolean;
  refresh: () => Promise<Session>;
}

const SessionContext = createContext<SessionState>({
  session: null,
  loading: true,
  refresh: async () => ({ authenticated: false }),
});

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const s = await getSession();
    setSession(s);
    setLoading(false);
    return s;
  }, []);

  useEffect(() => {
    refresh().catch(() => {
      setSession({ authenticated: false });
      setLoading(false);
    });
  }, [refresh]);

  return <SessionContext.Provider value={{ session, loading, refresh }}>{children}</SessionContext.Provider>;
}

export const useSession = () => useContext(SessionContext);

export const hasRole = (s: Session | null, role: string) => !!s?.user?.roles.includes(role);
