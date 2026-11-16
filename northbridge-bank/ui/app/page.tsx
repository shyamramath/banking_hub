'use client';

import { Suspense, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Alert } from '@/components/Alert';
import { Check, Lock, Logo, Shield } from '@/components/Icons';
import { useSession } from '@/lib/session';

const MESSAGES: Record<string, { kind: 'error' | 'info' | 'warn'; text: string }> = {
  login_failed: { kind: 'error', text: 'We could not sign you in. Please try again.' },
  login_expired: { kind: 'warn', text: 'Your sign-in took too long. Please start again.' },
  idp_unavailable: { kind: 'error', text: 'The sign-in service is starting up or unavailable. Try again in a moment.' },
  session_timeout: { kind: 'warn', text: 'You were signed out after a period of inactivity.' },
};

function SignInPanel() {
  const params = useSearchParams();
  const { session, loading } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (!loading && session?.authenticated) router.replace('/dashboard');
  }, [loading, session, router]);

  const error = params.get('error');
  const msg = error ? MESSAGES[error] : null;

  return (
    <div className="signin">
      <div className="lock"><Lock size={24} /></div>
      <h2>Sign in to Online Banking</h2>
      <p className="muted" style={{ marginTop: 0 }}>
        You&apos;ll be redirected to our secure sign-in page. Your password is never shared with this website.
      </p>

      {params.get('signedOut') && <Alert kind="success" title="You have signed out securely." />}
      {msg && <Alert kind={msg.kind}>{msg.text}</Alert>}

      <a className="btn btn-primary btn-lg btn-block" href="/auth/login?returnTo=/dashboard" style={{ marginTop: 8 }}>
        <Lock size={18} /> Sign in securely
      </a>

      <div className="demo-users">
        <b>Demo users</b> <span className="subtle">(Keycloak realm “northbridge”)</span>
        <table>
          <tbody>
            <tr><td><code>alice</code> / <code>alice123</code></td><td className="subtle">Premier customer</td></tr>
            <tr><td><code>bob</code> / <code>bob123</code></td><td className="subtle">Everyday customer</td></tr>
            <tr><td><code>carol</code> / <code>carol123</code></td><td className="subtle">Auditor (staff)</td></tr>
          </tbody>
        </table>
      </div>

      <div className="fineprint">
        <Shield size={16} />
        <span>
          Protected by OpenID Connect with PKCE. Tokens are held server-side by the BFF; your browser only keeps an
          HttpOnly session cookie.
        </span>
      </div>
    </div>
  );
}

export default function Landing() {
  return (
    <div className="landing">
      <section className="landing-hero">
        <div className="brand">
          <Logo size={38} />
          <div>
            Northbridge Bank
            <small>Est. 1924 · Member FDIC (demo)</small>
          </div>
        </div>
        <h1>
          Banking that&apos;s <em>secure</em> by design.
        </h1>
        <p>Check balances, review activity and move money between accounts — all through a single, audited API.</p>
        <div className="hero-points">
          <div><Check size={18} /> 24/7 account access</div>
          <div><Check size={18} /> Instant transfers</div>
          <div><Check size={18} /> Fraud monitoring</div>
        </div>
        <div className="hero-card" aria-hidden>
          <div className="chip" />
          <div className="digits">•••• •••• •••• 9012</div>
          <div className="holder">NORTHBRIDGE REWARDS</div>
        </div>
      </section>
      <section className="landing-form">
        <Suspense>
          <SignInPanel />
        </Suspense>
      </section>
    </div>
  );
}
