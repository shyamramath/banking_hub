'use client';

import { useEffect, useState } from 'react';
import { Alert } from '@/components/Alert';
import { Shield } from '@/components/Icons';
import { rawPost, type RawResult } from '@/lib/api';
import { hasRole, useSession } from '@/lib/session';
import { dateTime } from '@/lib/format';

interface Probe {
  id: string;
  title: string;
  description: string;
  expect: string;
  run: () => Promise<RawResult>;
  customerOnly?: boolean;
}

const BOB_ACCOUNT = 'acc-bob-chk';
const ALICE_ACCOUNT = 'acc-alice-chk';

export default function SecurityCenter() {
  const { session } = useSession();
  const user = session?.user;
  const isCustomer = hasRole(session, 'customer');
  const [results, setResults] = useState<Record<string, RawResult & { ok: boolean }>>({});
  const [cookieVisible, setCookieVisible] = useState('');

  useEffect(() => setCookieVisible(document.cookie), []);

  const someoneElse = user?.username === 'alice' ? BOB_ACCOUNT : ALICE_ACCOUNT;
  const mine = user?.username === 'bob' ? BOB_ACCOUNT : ALICE_ACCOUNT;

  const probes: Probe[] = [
    {
      id: 'bola',
      title: "Read another customer's account",
      description: `account.details for ${someoneElse}. Ownership is checked against the JWT subject, not the payload.`,
      expect: '404 NOT_FOUND',
      run: () => rawPost('/bff/execute', { operation: 'account.details', payload: { accountId: someoneElse } }),
      customerOnly: true,
    },
    {
      id: 'role',
      title: 'Call a staff-only operation',
      description: 'audit.list needs the audit:read scope AND the auditor role.',
      expect: isCustomer ? '403 OPERATION_NOT_PERMITTED' : '200 OK (you are an auditor)',
      run: () => rawPost('/bff/execute', { operation: 'audit.list', payload: { limit: 1 } }),
    },
    {
      id: 'unknown',
      title: 'Call an operation that is not exposed',
      description: 'admin.users.delete is not on the BFF allow-list, so it never reaches the backend.',
      expect: '400 UNSUPPORTED_OPERATION',
      run: () => rawPost('/bff/execute', { operation: 'admin.users.delete', payload: {} }),
    },
    {
      id: 'mass',
      title: 'Smuggle an extra field (mass assignment)',
      description: 'Adds "ownerId" to the payload. The strict binder rejects unknown fields.',
      expect: '400 INVALID_PAYLOAD',
      run: () => rawPost('/bff/execute', { operation: 'account.details', payload: { accountId: mine, ownerId: 'someone-else' } }),
      customerOnly: true,
    },
    {
      id: 'csrf',
      title: 'Send a request without the CSRF token',
      description: 'Simulates a forged cross-site request riding on the session cookie.',
      expect: '403 CSRF',
      run: () => rawPost('/bff/execute', { operation: 'profile.get' }, { csrf: false }),
    },
    {
      id: 'batchwrite',
      title: 'Hide a transfer inside a batch',
      description: 'State-changing operations cannot be batched.',
      expect: '400 WRITE_NOT_BATCHABLE',
      run: () =>
        rawPost('/bff/execute/batch', {
          requests: [{ operation: 'profile.get' }, { operation: 'transfer.create', payload: {} }],
        }),
    },
  ];

  const run = async (p: Probe) => {
    const r = await p.run();
    const ok = p.expect.startsWith(String(r.status));
    setResults((prev) => ({ ...prev, [p.id]: { ...r, ok } }));
  };

  const runAll = async () => {
    for (const p of probes) {
      if (!p.customerOnly || isCustomer) await run(p);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Security center</h1>
          <p>See the protections on the single API endpoint for yourself. Each check sends a real request.</p>
        </div>
        <button className="btn btn-primary" onClick={runAll}>
          <Shield size={18} /> Run all checks
        </button>
      </div>

      <div className="grid grid-main">
        <section className="card">
          <div className="card-head">
            <h2>Attack simulations</h2>
            <span className="subtle">Expected result in green means the control worked</span>
          </div>
          {probes.map((p) => {
            const r = results[p.id];
            const disabled = p.customerOnly && !isCustomer;
            return (
              <div className="check-row" key={p.id}>
                <div>
                  <h3>
                    {p.title}{' '}
                    {r && <span className={`tag ${r.ok ? 'tag-green' : 'tag-red'}`}>{r.ok ? 'Blocked as expected' : 'Unexpected'}</span>}
                  </h3>
                  <p>
                    {p.description} Expected: <code>{p.expect}</code>
                  </p>
                </div>
                <button className="btn btn-secondary" onClick={() => run(p)} disabled={disabled} title={disabled ? 'Customer accounts only' : ''}>
                  Run
                </button>
                {r && (
                  <pre className="check-result">
                    <span className={r.ok ? 'ok' : 'bad'}>HTTP {r.status}</span>
                    {r.correlationId ? `   correlation-id: ${r.correlationId}` : ''}
                    {'\n'}
                    {JSON.stringify(r.body, null, 2)}
                  </pre>
                )}
              </div>
            );
          })}
        </section>

        <section className="card card-pad">
          <h2 style={{ fontSize: 16, marginBottom: 14 }}>Your session</h2>
          <dl className="kv">
            <dt>Signed in as</dt>
            <dd>{user?.name} ({user?.username})</dd>
            <dt>Roles</dt>
            <dd>{user?.roles.map((r) => <span key={r} className="tag tag-blue" style={{ marginRight: 4 }}>{r}</span>)}</dd>
            <dt>Token scopes</dt>
            <dd>{user?.scopes.filter((s) => s !== 'openid').map((s) => <span key={s} className="tag" style={{ marginRight: 4 }}>{s}</span>)}</dd>
            <dt>Session ends by</dt>
            <dd>{session?.absoluteExpiresAt ? dateTime(new Date(session.absoluteExpiresAt).toISOString()) : '—'}</dd>
            <dt>Cookies JS can read</dt>
            <dd><code>{cookieVisible || '(none — session cookie is HttpOnly)'}</code></dd>
            <dt>Access token in browser</dt>
            <dd><span className="tag tag-green">None</span> held by the BFF only</dd>
          </dl>
          <div style={{ marginTop: 18 }}>
            <Alert kind="info" title="Layers on every call">
              <ol style={{ margin: '4px 0 0', paddingLeft: 18, lineHeight: 1.7 }}>
                <li>BFF: session, CSRF + origin check, allow-list, rate limit</li>
                <li>Backend: JWT signature, issuer, audience, expiry</li>
                <li>Dispatcher: scopes + roles per operation</li>
                <li>Service: ownership of every record</li>
                <li>Strict payload binding + validation</li>
                <li>Audit log + metrics per operation</li>
              </ol>
            </Alert>
          </div>
        </section>
      </div>
    </>
  );
}
