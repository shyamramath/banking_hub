'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { Alert, ErrorAlert } from '@/components/Alert';
import { Back, Transfer, accountIcon } from '@/components/Icons';
import { ApiError, execute } from '@/lib/api';
import { accountTypeLabel, dateTime, money, signedMoney } from '@/lib/format';
import type { AccountDetails, Transaction } from '@/lib/types';

export default function AccountPage() {
  const { id } = useParams<{ id: string }>();
  const [account, setAccount] = useState<AccountDetails | null>(null);
  const [txs, setTxs] = useState<Transaction[] | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [query, setQuery] = useState('');

  useEffect(() => {
    setAccount(null);
    setTxs(null);
    setError(null);
    Promise.all([
      execute<AccountDetails>('account.details', { accountId: id }),
      execute<Transaction[]>('transactions.list', { accountId: id, limit: 50 }),
    ])
      .then(([a, t]) => {
        setAccount(a);
        setTxs(t);
      })
      .catch(setError);
  }, [id]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (txs ?? []).filter((t) => !q || t.description.toLowerCase().includes(q) || t.category.toLowerCase().includes(q));
  }, [txs, query]);

  if (error?.code === 'NOT_FOUND') {
    return (
      <>
        <Link href="/dashboard" className="btn btn-ghost" style={{ marginBottom: 14 }}><Back size={16} /> Back to accounts</Link>
        <Alert kind="warn" title="Account not found">
          We couldn&apos;t find that account in your profile. (The backend answers the same way whether an account doesn&apos;t
          exist or belongs to someone else.)
        </Alert>
      </>
    );
  }

  const credit = account?.type === 'CREDIT';

  return (
    <>
      <Link href="/dashboard" className="btn btn-ghost" style={{ marginBottom: 14, paddingLeft: 8 }}>
        <Back size={16} /> All accounts
      </Link>
      <ErrorAlert error={error} />

      <div className="card card-pad" style={{ marginBottom: 22 }}>
        {account ? (
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 24, flexWrap: 'wrap', alignItems: 'center' }}>
            <div style={{ display: 'flex', gap: 14, alignItems: 'center' }}>
              <div className="tx-icon" style={{ width: 52, height: 52, background: 'var(--brand-50)', color: 'var(--brand)' }}>
                {accountIcon(account.type, 24)}
              </div>
              <div>
                <h1 style={{ fontSize: 22 }}>{account.nickname}</h1>
                <div className="muted">
                  {accountTypeLabel[account.type]} {account.maskedNumber} · Routing {account.routingNumber}{' '}
                  <span className="tag tag-green" style={{ marginLeft: 6 }}>{account.status}</span>
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 36 }}>
              <div>
                <div className="subtle">{credit ? 'Current balance' : 'Balance'}</div>
                <div className="num" style={{ fontSize: 26, fontWeight: 700 }}>
                  {money(credit ? Math.abs(account.balance) : account.balance)}
                </div>
              </div>
              <div>
                <div className="subtle">{credit ? 'Available credit' : 'Available'}</div>
                <div className="num" style={{ fontSize: 26, fontWeight: 700, color: 'var(--positive)' }}>
                  {money(account.availableBalance)}
                </div>
              </div>
              {!credit && (
                <Link href={`/transfer?from=${account.id}`} className="btn btn-primary" style={{ alignSelf: 'center' }}>
                  <Transfer size={18} /> Transfer
                </Link>
              )}
            </div>
          </div>
        ) : (
          <div className="skeleton" style={{ height: 64 }} />
        )}
      </div>

      <section className="card">
        <div className="card-head">
          <h2>Transactions</h2>
          <div className="toolbar">
            <input
              className="input search"
              placeholder="Search description or category"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Search transactions"
            />
          </div>
        </div>
        {txs === null ? (
          <div className="card-pad"><div className="skeleton" style={{ height: 260 }} /></div>
        ) : filtered.length === 0 ? (
          <div className="empty">No matching transactions.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Category</th>
                <th className="right">Amount</th>
                <th className="right">Balance</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((t) => (
                <tr key={t.id}>
                  <td className="muted" style={{ whiteSpace: 'nowrap' }}>{dateTime(t.postedAt)}</td>
                  <td style={{ fontWeight: 600 }}>{t.description}</td>
                  <td><span className="tag">{t.category}</span></td>
                  <td className="right num" style={{ fontWeight: 600, color: t.amount > 0 ? 'var(--positive)' : undefined }}>
                    {signedMoney(t.amount)}
                  </td>
                  <td className="right num muted">{money(t.balanceAfter)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
