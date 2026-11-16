import type { AccountType } from './types';

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export const money = (n: number | null | undefined) => (n == null ? '—' : usd.format(n));

export const signedMoney = (n: number) => (n > 0 ? `+${usd.format(n)}` : usd.format(n));

export const shortDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });

// Date-only values (e.g. "2016-04-12") are calendar dates: parse them as local, not UTC midnight.
const parseDate = (iso: string) => (/^\d{4}-\d{2}-\d{2}$/.test(iso) ? new Date(`${iso}T00:00:00`) : new Date(iso));

export const longDate = (iso: string) =>
  parseDate(iso).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });

export const dateTime = (iso: string) =>
  new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });

export const accountTypeLabel: Record<AccountType, string> = {
  CHECKING: 'Checking',
  SAVINGS: 'Savings',
  CREDIT: 'Credit card',
};

export function greeting(date = new Date()) {
  const h = date.getHours();
  if (h < 12) return 'Good morning';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
}

export const firstName = (name?: string) => (name ?? '').split(' ')[0] || 'there';
