'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { Alert, ErrorAlert } from '@/components/Alert';
import { AccountCard, SkeletonCards, TransactionList } from '@/components/Banking';
import { Bill, Clipboard, Shield, Statement, Transfer } from '@/components/Icons';
import { ApiError, batch } from '@/lib/api';
import { firstName, greeting, longDate, money } from '@/lib/format';
import { hasRole, useSession } from '@/lib/session';
import type { AccountSummary, CustomerProfile, Transaction } from '@/lib/types';

interface Dashboard {
  profile?: CustomerProfile;
  accounts?: AccountSummary[];
  recent?: Transaction[];
}

export default function DashboardPage() {
  const { session } = useSession();
  const isCustomer = hasRole(session, 'customer');
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    // One round trip for the whole screen: three operations through the batch endpoint.
    const requests = isCustomer
      ? [{ operation: 'profile.get' }, { operation: 'accounts.list' }, { operation: 'transactions.recent', payload: { limit: 8 } }]
      : [{ operation: 'profile.get' }];
    batch(requests)
      .then((items) => {
        const failed = items.find((i) => !i.success);
        if (failed?.error) setError(new ApiError(400, failed.error.code, failed.error.message));
        setData({
          profile: items[0]?.data as CustomerProfile,
          accounts: items[1]?.data as AccountSummary[],
          recent: items[2]?.data as Transaction[],
        });
      })
      .catch(setError);
  }, [isCustomer]);

  const accounts = data?.accounts ?? [];
  const cash = accounts.filter((a) => a.type !== 'CREDIT').reduce((s, a) => s + a.balance, 0);
  const owed = accounts.filter((a) => a.type === 'CREDIT').reduce((s, a) => s + Math.abs(a.balance), 0);
  const savings = accounts.filter((a) => a.type === 'SAVINGS').reduce((s, a) => s + a.balance, 0);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>
            {greeting()}, {firstName(data?.profile?.displayName ?? session?.user?.name)}
          </h1>
          <p>
            {data?.profile ? (
              <>
                <span className="tag tag-gold">{data.profile.segment}</span>&nbsp; Member since{' '}
                {longDate(data.profile.memberSince)}
                {data.profile.relationshipManager && <> · Relationship manager: {data.profile.relationshipManager}</>}
              </>
            ) : (
              'Loading your profile…'
            )}
          </p>
        </div>
        {isCustomer && (
          <Link href="/transfer" className="btn btn-primary">
            <Transfer size={18} /> Transfer money
          </Link>
        )}
      </div>

      <ErrorAlert error={error} />

      {!isCustomer ? (
        <div className="card card-pad">
          <Alert kind="info" title="Staff account">
            You&apos;re signed in with the <b>auditor</b> role. Customer operations such as <code>accounts.list</code> are
            denied for this role by the backend policy, even though your token carries the <code>accounts:read</code>{' '}
            scope.
          </Alert>
          <Link href="/audit" className="btn btn-primary">
            <Clipboard size={18} /> Open audit log
          </Link>
        </div>
      ) : (
        <>
          <div className="summary-band">
            <div>
              <div className="label">Total cash</div>
              <div className="value num">{data ? money(cash) : '—'}</div>
              <div className="foot">Checking and savings</div>
            </div>
            <div>
              <div className="label">Savings</div>
              <div className="value num">{data ? money(savings) : '—'}</div>
              <div className="foot">Earning 4.10% APY</div>
            </div>
            <div>
              <div className="label">Card balance</div>
              <div className="value num">{data ? money(owed) : '—'}</div>
              <div className="foot">Statement due on the 21st</div>
            </div>
          </div>

          {data ? (
            <div className="grid grid-3" style={{ marginBottom: 22 }}>
              {accounts.map((a) => (
                <AccountCard key={a.id} account={a} />
              ))}
            </div>
          ) : (
            <div style={{ marginBottom: 22 }}>
              <SkeletonCards />
            </div>
          )}

          <div className="grid grid-main">
            <section className="card">
              <div className="card-head">
                <h2>Recent activity</h2>
                <span className="subtle">All accounts</span>
              </div>
              {data ? (
                <TransactionList items={data.recent ?? []} accounts={accounts} />
              ) : (
                <div className="card-pad">
                  <div className="skeleton" style={{ height: 240 }} />
                </div>
              )}
            </section>

            <section className="card">
              <div className="card-head">
                <h2>Quick actions</h2>
              </div>
              <div className="quick">
                <Link href="/transfer"><span className="qi"><Transfer /></span>Transfer</Link>
                <button disabled title="Not part of this demo"><span className="qi"><Bill /></span>Pay bills</button>
                <button disabled title="Not part of this demo"><span className="qi"><Statement /></span>Statements</button>
                <Link href="/security"><span className="qi"><Shield /></span>Security center</Link>
              </div>
              <div className="card-pad" style={{ paddingTop: 0 }}>
                <Alert kind="info" title="How this page loaded">
                  <span className="subtle" style={{ color: 'inherit' }}>
                    One batch call → <code>profile.get</code>, <code>accounts.list</code>,{' '}
                    <code>transactions.recent</code>. Each was authorized separately by the dispatcher.
                  </span>
                </Alert>
              </div>
            </section>
          </div>
        </>
      )}
    </>
  );
}
