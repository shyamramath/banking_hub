'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { logout, onActivity } from '@/lib/api';
import { hasRole, useSession } from '@/lib/session';
import { Clipboard, Home, Lock, Logo, Logout, Shield, Transfer } from './Icons';

const TITLES: Record<string, string> = {
  '/dashboard': 'Accounts overview',
  '/transfer': 'Transfer money',
  '/security': 'Security center',
  '/audit': 'Audit log',
};

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { session, refresh } = useSession();
  const user = session?.user;
  const idleMs = session?.idleTimeoutMs ?? 15 * 60_000;

  // Mirror the BFF's rolling idle timeout so the user is warned before being signed out.
  const [lastActivity, setLastActivity] = useState(() => Date.now());
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => onActivity(() => setLastActivity(Date.now())), []);
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  const remaining = Math.max(0, lastActivity + idleMs - now);
  useEffect(() => {
    if (remaining === 0) window.location.href = '/?error=session_timeout';
  }, [remaining]);
  const mm = Math.floor(remaining / 60_000);
  const ss = String(Math.floor((remaining % 60_000) / 1000)).padStart(2, '0');
  const warn = remaining < 2 * 60_000;

  const stayActive = async () => {
    await refresh();
    setLastActivity(Date.now());
  };

  const initials = (user?.name ?? '?').split(' ').map((p) => p[0]).join('').slice(0, 2).toUpperCase();
  const isCustomer = hasRole(session, 'customer');
  const isAuditor = hasRole(session, 'auditor');
  const title = pathname.startsWith('/accounts/') ? 'Account details' : TITLES[pathname] ?? '';

  const nav = (href: string, label: string, icon: React.ReactNode) => (
    <Link href={href} className={pathname === href || pathname.startsWith(href + '/') ? 'active' : ''}>
      {icon}
      {label}
    </Link>
  );

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <Logo />
          <div>
            Northbridge
            <small>Online Banking</small>
          </div>
        </div>
        <nav className="nav">
          {nav('/dashboard', 'Overview', <Home />)}
          {isCustomer && nav('/transfer', 'Transfer', <Transfer />)}
          {nav('/security', 'Security center', <Shield />)}
          {isAuditor && (
            <>
              <div className="nav-label">Staff</div>
              {nav('/audit', 'Audit log', <Clipboard />)}
            </>
          )}
        </nav>
        <div className="sidebar-foot">
          <strong>Need help?</strong>
          <br />
          1-800-555-0199 · 24/7
          <br />
          <span style={{ opacity: 0.8 }}>Member FDIC (demo)</span>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="topbar-title">{title}</div>
          <div className="topbar-right">
            {warn ? (
              <button className="secure-pill warn" onClick={stayActive} style={{ border: 0, cursor: 'pointer' }}>
                <Lock size={14} /> Signing out in {mm}:{ss} · Stay signed in
              </button>
            ) : (
              <span className="secure-pill" title="Session cookie is HttpOnly; tokens never reach the browser">
                <Lock size={14} /> Secure session · {mm}:{ss}
              </span>
            )}
            <div className="user-chip">
              <div className="avatar">{initials}</div>
              <div className="who">
                <b>{user?.name}</b>
                <span>{isAuditor ? 'Internal audit' : 'Personal banking'}</span>
              </div>
            </div>
            <button className="btn btn-ghost" onClick={() => logout()} title="Sign out">
              <Logout size={18} /> Sign out
            </button>
          </div>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}
