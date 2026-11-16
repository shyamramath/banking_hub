'use client';

import { useCallback, useEffect, useState } from 'react';
import { Alert, ErrorAlert } from '@/components/Alert';
import { Refresh } from '@/components/Icons';
import { ApiError, execute } from '@/lib/api';
import { dateTime } from '@/lib/format';
import { hasRole, useSession } from '@/lib/session';
import type { AuditEntry } from '@/lib/types';

const outcomeTag: Record<string, string> = {
  success: 'tag-green',
  denied: 'tag-red',
  rejected: 'tag-amber',
  error: 'tag-red',
};

export default function AuditPage() {
  const { session } = useSession();
  const [entries, setEntries] = useState<AuditEntry[] | null>(null);
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(() => {
    setError(null);
    execute<AuditEntry[]>('audit.list', { limit: 100 }).then(setEntries).catch((e) => {
      setError(e);
      setEntries([]);
    });
  }, []);

  useEffect(load, [load]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Audit log</h1>
          <p>Every state-changing operation and every denied attempt, newest first.</p>
        </div>
        <button className="btn btn-secondary" onClick={load}>
          <Refresh size={16} /> Refresh
        </button>
      </div>

      {!hasRole(session, 'auditor') && (
        <Alert kind="warn" title="Auditor role required">
          This page calls <code>audit.list</code>; the backend will deny it for your role. Your attempt will itself appear in
          the audit log.
        </Alert>
      )}
      <ErrorAlert error={error} />

      <section className="card">
        {entries === null ? (
          <div className="card-pad"><div className="skeleton" style={{ height: 300 }} /></div>
        ) : entries.length === 0 ? (
          <div className="empty">No audit entries yet. Make a transfer or run the Security center checks.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Time</th>
                <th>User</th>
                <th>Operation</th>
                <th>Outcome</th>
                <th>Detail</th>
                <th>Correlation id</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e) => (
                <tr key={e.id}>
                  <td className="muted" style={{ whiteSpace: 'nowrap' }}>{dateTime(e.at)}</td>
                  <td style={{ fontWeight: 600 }}>{e.username}</td>
                  <td><code>{e.operation}</code></td>
                  <td><span className={`tag ${outcomeTag[e.outcome] ?? ''}`}>{e.outcome}</span></td>
                  <td className="muted" style={{ fontSize: 13 }}>{e.detail || '—'}</td>
                  <td><code>{e.correlationId?.slice(0, 8)}</code></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
