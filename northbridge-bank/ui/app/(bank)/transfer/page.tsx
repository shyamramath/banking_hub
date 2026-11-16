'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, ErrorAlert } from '@/components/Alert';
import { Check, Lock } from '@/components/Icons';
import { ApiError, execute, newIdempotencyKey } from '@/lib/api';
import { dateTime, money } from '@/lib/format';
import type { AccountSummary, TransferReceipt } from '@/lib/types';

type Step = 'form' | 'review' | 'done';
type Mode = 'own' | 'payee';

function TransferFlow() {
  const params = useSearchParams();
  const [accounts, setAccounts] = useState<AccountSummary[] | null>(null);
  const [step, setStep] = useState<Step>('form');
  const [mode, setMode] = useState<Mode>('own');
  const [fromId, setFromId] = useState('');
  const [toId, setToId] = useState('');
  const [payeeNumber, setPayeeNumber] = useState('');
  const [payeeName, setPayeeName] = useState('');
  const [amount, setAmount] = useState('');
  const [memo, setMemo] = useState('');
  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [receipt, setReceipt] = useState<TransferReceipt | null>(null);
  // One key per intended transfer: retries and double-clicks can't move money twice.
  const idemKey = useRef<string>('');

  useEffect(() => {
    execute<AccountSummary[]>('accounts.list').then((list) => {
      setAccounts(list);
      const sources = list.filter((a) => a.type !== 'CREDIT');
      const preferred = sources.find((a) => a.id === params.get('from')) ?? sources[0];
      if (preferred) setFromId(preferred.id);
      const other = sources.find((a) => a.id !== preferred?.id);
      if (other) setToId(other.id);
    }).catch(setError);
  }, [params]);

  const sources = useMemo(() => (accounts ?? []).filter((a) => a.type !== 'CREDIT'), [accounts]);
  const destinations = useMemo(() => (accounts ?? []).filter((a) => a.id !== fromId), [accounts, fromId]);
  const from = accounts?.find((a) => a.id === fromId);
  const to = accounts?.find((a) => a.id === toId);
  const amt = Number(amount);

  const formErrors: string[] = [];
  if (!(amt > 0)) formErrors.push('Enter an amount greater than $0.00');
  if (from && amt > from.balance) formErrors.push('Amount exceeds the available balance');
  if (!/^\d+(\.\d{1,2})?$/.test(amount || '0')) formErrors.push('Use up to two decimal places');
  if (mode === 'own' && !toId) formErrors.push('Choose a destination account');
  if (mode === 'payee' && !/^\d{16}$/.test(payeeNumber)) formErrors.push('Payee account number must be 16 digits');
  if (mode === 'payee' && !payeeName.trim()) formErrors.push('Enter the payee name');

  const toReview = () => {
    setError(null);
    idemKey.current = newIdempotencyKey();
    setStep('review');
  };

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const payload: Record<string, unknown> = {
        fromAccountId: fromId,
        amount: amt,
        memo: memo.trim() || undefined,
        idempotencyKey: idemKey.current,
      };
      if (mode === 'own') payload.toAccountId = toId;
      else {
        payload.payeeAccountNumber = payeeNumber;
        payload.payeeName = payeeName.trim();
      }
      const r = await execute<TransferReceipt>('transfer.create', payload);
      setReceipt(r);
      setStep('done');
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setSubmitting(false);
    }
  };

  const reset = () => {
    setStep('form');
    setAmount('');
    setMemo('');
    setReceipt(null);
    setError(null);
    execute<AccountSummary[]>('accounts.list').then(setAccounts).catch(() => {});
  };

  const stepClass = (s: Step) => {
    const order: Step[] = ['form', 'review', 'done'];
    return order.indexOf(s) < order.indexOf(step) ? 'step done' : s === step ? 'step on' : 'step';
  };

  return (
    <div className="grid grid-main">
      <section className="card card-pad">
        <div className="steps">
          <div className={stepClass('form')}><span className="dot">{step === 'form' ? 1 : <Check size={14} />}</span>Details</div>
          <div className="step-sep" />
          <div className={stepClass('review')}><span className="dot">{step === 'done' ? <Check size={14} /> : 2}</span>Review</div>
          <div className="step-sep" />
          <div className={stepClass('done')}><span className="dot">3</span>Confirmation</div>
        </div>

        <ErrorAlert error={error} />

        {step === 'form' && (
          <>
            <label className="field">
              <span>From</span>
              <select className="input" value={fromId} onChange={(e) => setFromId(e.target.value)}>
                {sources.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.nickname} {a.maskedNumber} — {money(a.balance)} available
                  </option>
                ))}
              </select>
            </label>

            <div className="segmented" role="tablist">
              <button className={mode === 'own' ? 'active' : ''} onClick={() => setMode('own')}>Between my accounts</button>
              <button className={mode === 'payee' ? 'active' : ''} onClick={() => setMode('payee')}>To someone else</button>
            </div>

            {mode === 'own' ? (
              <label className="field">
                <span>To</span>
                <select className="input" value={toId} onChange={(e) => setToId(e.target.value)}>
                  {destinations.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.nickname} {a.maskedNumber}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <div className="grid grid-2" style={{ gap: 14 }}>
                <label className="field">
                  <span>Payee name</span>
                  <input className="input" value={payeeName} maxLength={60} onChange={(e) => setPayeeName(e.target.value)} placeholder="Bob Nguyen" />
                </label>
                <label className="field">
                  <span>Account number</span>
                  <input
                    className="input num"
                    inputMode="numeric"
                    value={payeeNumber}
                    maxLength={16}
                    onChange={(e) => setPayeeNumber(e.target.value.replace(/\D/g, ''))}
                    placeholder="16 digits"
                  />
                  <div className="hint">Demo: Bob&apos;s checking is 4021009911214455</div>
                </label>
              </div>
            )}

            <label className="field">
              <span>Amount</span>
              <div className="input-money">
                <input className="input num" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0.00" />
              </div>
              <div className="hint">Online transfers are limited to $10,000.00 per transaction.</div>
            </label>

            <label className="field">
              <span>Memo <span className="subtle" style={{ display: 'inline', fontWeight: 400 }}>(optional)</span></span>
              <input className="input" value={memo} maxLength={140} onChange={(e) => setMemo(e.target.value)} placeholder="e.g. Rent share" />
            </label>

            {amount && formErrors.length > 0 && <div className="field-error" style={{ marginBottom: 14 }}>{formErrors[0]}</div>}
            <button className="btn btn-primary btn-lg" disabled={!accounts || formErrors.length > 0} onClick={toReview}>
              Review transfer
            </button>
          </>
        )}

        {step === 'review' && (
          <>
            <dl className="review">
              <div><dt>Amount</dt><dd className="big num">{money(amt)}</dd></div>
              <div><dt>From</dt><dd>{from?.nickname} {from?.maskedNumber}</dd></div>
              <div>
                <dt>To</dt>
                <dd>{mode === 'own' ? `${to?.nickname} ${to?.maskedNumber}` : `${payeeName} •••• ${payeeNumber.slice(-4)}`}</dd>
              </div>
              {memo && <div><dt>Memo</dt><dd>{memo}</dd></div>}
              <div><dt>Delivery</dt><dd>Today · Instant</dd></div>
            </dl>
            <div style={{ display: 'flex', gap: 10 }}>
              <button className="btn btn-secondary btn-lg" onClick={() => setStep('form')} disabled={submitting}>Edit</button>
              <button className="btn btn-primary btn-lg" onClick={submit} disabled={submitting}>
                <Lock size={16} /> {submitting ? 'Sending…' : `Confirm and send ${money(amt)}`}
              </button>
            </div>
            <p className="subtle" style={{ marginTop: 14 }}>
              Request reference <code>{idemKey.current.slice(0, 8)}</code> — if you retry, the bank recognises it and won&apos;t
              send the money twice.
            </p>
          </>
        )}

        {step === 'done' && receipt && (
          <div className="receipt">
            <div className="check"><Check size={30} /></div>
            <h2 style={{ fontSize: 22 }}>{receipt.replayed ? 'Already sent' : 'Transfer complete'}</h2>
            <p className="muted">
              {money(receipt.amount)} to {receipt.toLabel} {receipt.toAccount}
            </p>
            <dl className="review" style={{ textAlign: 'left', marginTop: 18 }}>
              <div><dt>Confirmation</dt><dd className="num"><b>{receipt.transferId}</b></dd></div>
              <div><dt>From</dt><dd>{receipt.fromAccount}</dd></div>
              <div><dt>New balance</dt><dd className="num">{money(receipt.newBalance)}</dd></div>
              <div><dt>Completed</dt><dd>{dateTime(receipt.completedAt)}</dd></div>
            </dl>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
              <button className="btn btn-secondary" onClick={reset}>Make another transfer</button>
              <Link className="btn btn-primary" href="/dashboard">Back to accounts</Link>
            </div>
          </div>
        )}
      </section>

      <aside className="card card-pad">
        <h2 style={{ fontSize: 16, marginBottom: 12 }}>What protects this transfer</h2>
        <Alert kind="info">
          The browser sends only <code>transfer.create</code> + payload to the BFF, with a CSRF token. The BFF attaches your
          access token; the backend checks the <code>transfers:write</code> scope, the <code>customer</code> role and that
          the source account is yours.
        </Alert>
        <ul className="muted" style={{ paddingLeft: 18, margin: 0, fontSize: 14, lineHeight: 1.7 }}>
          <li>Idempotency key prevents double sends</li>
          <li>Per-user rate limit: 5 transfers / minute</li>
          <li>$10,000 per-transfer limit enforced server-side</li>
          <li>Every attempt is written to the audit log</li>
          <li>Unknown payload fields are rejected</li>
        </ul>
      </aside>
    </div>
  );
}

export default function TransferPage() {
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Transfer money</h1>
          <p>Move money between your accounts or to another Northbridge customer.</p>
        </div>
      </div>
      <Suspense>
        <TransferFlow />
      </Suspense>
    </>
  );
}
