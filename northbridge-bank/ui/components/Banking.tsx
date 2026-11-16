import Link from 'next/link';
import { accountIcon } from './Icons';
import { accountTypeLabel, money, shortDate, signedMoney } from '@/lib/format';
import type { AccountSummary, Transaction } from '@/lib/types';

export function AccountCard({ account }: { account: AccountSummary }) {
  const credit = account.type === 'CREDIT';
  return (
    <Link href={`/accounts/${account.id}`} className={`card account-card${credit ? ' credit' : ''}`}>
      <div className="top">
        <div className="kind">
          <div className="icon">{accountIcon(account.type)}</div>
          <div>
            <div className="name">{account.nickname}</div>
            <div className="mask">
              {accountTypeLabel[account.type]} {account.maskedNumber}
            </div>
          </div>
        </div>
      </div>
      <div>
        <div className="bal-label">{credit ? 'Current balance' : 'Available balance'}</div>
        <div className="bal num">{money(credit ? Math.abs(account.balance) : account.balance)}</div>
        {credit && account.availableCredit != null && (
          <div className="subtle">Available credit {money(account.availableCredit)}</div>
        )}
      </div>
    </Link>
  );
}

const initialsOf = (s: string) => s.replace(/[^A-Za-z ]/g, '').split(' ').filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase() || '•';

export function TransactionList({
  items,
  accounts,
  showBalance = false,
}: {
  items: Transaction[];
  accounts?: AccountSummary[];
  showBalance?: boolean;
}) {
  if (!items.length) return <div className="empty">No transactions yet.</div>;
  const nameOf = (id: string) => accounts?.find((a) => a.id === id)?.nickname;
  return (
    <ul className="tx-list">
      {items.map((t) => (
        <li key={t.id}>
          <div className="tx-icon">{initialsOf(t.description)}</div>
          <div className="tx-desc">
            <b>{t.description}</b>
            <span>
              {shortDate(t.postedAt)} · {t.category}
              {nameOf(t.accountId) ? ` · ${nameOf(t.accountId)}` : ''}
            </span>
          </div>
          <div className={`tx-amt num${t.amount > 0 ? ' in' : ''}`}>
            {signedMoney(t.amount)}
            {showBalance && <small>Bal {money(t.balanceAfter)}</small>}
          </div>
        </li>
      ))}
    </ul>
  );
}

export function SkeletonCards({ n = 3 }: { n?: number }) {
  return (
    <div className="grid grid-3">
      {Array.from({ length: n }).map((_, i) => (
        <div key={i} className="skeleton" style={{ height: 150 }} />
      ))}
    </div>
  );
}
