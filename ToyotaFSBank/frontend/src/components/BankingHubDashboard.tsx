"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { PlaidLinkOnSuccess } from "react-plaid-link";
import {
  createAccount,
  deleteAccount,
  exchangePublicToken,
  getAccounts,
  getLinkedItems,
  getPlaidAccounts,
  getPlaidTransactions,
  unlinkPlaidItem,
} from "@/lib/api";
import type {
  Account,
  AccountType,
  LinkedItem,
  PlaidAccount,
  PlaidTransaction,
} from "@/lib/types";
import PlaidLinkButton from "./PlaidLinkButton";

const ACCOUNT_TYPES: AccountType[] = ["CHECKING", "SAVINGS", "AUTO_LOAN"];

function formatCurrency(value: number | null | undefined, currency = "USD"): string {
  if (value === null || value === undefined) return "—";
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value);
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" }).format(
    new Date(value),
  );
}

export default function BankingHubDashboard() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [linkedItems, setLinkedItems] = useState<LinkedItem[]>([]);
  const [plaidAccounts, setPlaidAccounts] = useState<PlaidAccount[]>([]);
  const [transactions, setTransactions] = useState<PlaidTransaction[]>([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const [ownerName, setOwnerName] = useState("");
  const [accountType, setAccountType] = useState<AccountType>("CHECKING");
  const [balance, setBalance] = useState("0");
  const [submittingAccount, setSubmittingAccount] = useState(false);

  const loadAll = useCallback(async () => {
    try {
      const [accountsResult, itemsResult] = await Promise.all([getAccounts(), getLinkedItems()]);
      setAccounts(accountsResult);
      setLinkedItems(itemsResult);

      if (itemsResult.length > 0) {
        const [plaidAccountsResult, transactionsResult] = await Promise.all([
          getPlaidAccounts(),
          getPlaidTransactions(),
        ]);
        setPlaidAccounts(plaidAccountsResult);
        setTransactions(transactionsResult);
      } else {
        setPlaidAccounts([]);
        setTransactions([]);
      }
      setError(null);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to reach the ToyotaFSBank API. Is the backend running?",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Fetch-on-mount: the documented exception to "you might not need an effect"
    // (https://react.dev/learn/you-might-not-need-an-effect#fetching-data).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadAll();
  }, [loadAll]);

  const handlePlaidSuccess: PlaidLinkOnSuccess = useCallback(
    async (publicToken, metadata) => {
      if (!publicToken) return;
      setError(null);
      try {
        await exchangePublicToken({
          publicToken,
          institutionId: metadata.institution?.institution_id ?? null,
          institutionName: metadata.institution?.name ?? null,
        });
        await loadAll();
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to link account.");
      }
    },
    [loadAll],
  );

  async function handleUnlink(itemId: string) {
    setError(null);
    try {
      await unlinkPlaidItem(itemId);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to unlink account.");
    }
  }

  async function handleCreateAccount(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmittingAccount(true);
    setError(null);
    try {
      await createAccount({ ownerName, accountType, balance: Number(balance) });
      setOwnerName("");
      setBalance("0");
      setAccountType("CHECKING");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create account.");
    } finally {
      setSubmittingAccount(false);
    }
  }

  async function handleDeleteAccount(id: string) {
    setError(null);
    try {
      await deleteAccount(id);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete account.");
    }
  }

  const totalPrimaryBalance = useMemo(
    () => accounts.reduce((sum, account) => sum + account.balance, 0),
    [accounts],
  );
  const totalLinkedBalance = useMemo(
    () => plaidAccounts.reduce((sum, account) => sum + (account.currentBalance ?? 0), 0),
    [plaidAccounts],
  );
  const netWorth = totalPrimaryBalance + totalLinkedBalance;

  const filteredTransactions = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return transactions;
    return transactions.filter(
      (t) =>
        t.name.toLowerCase().includes(query) ||
        (t.merchantName ?? "").toLowerCase().includes(query) ||
        (t.institutionName ?? "").toLowerCase().includes(query),
    );
  }, [transactions, search]);

  const accountsByItem = useMemo(() => {
    const map = new Map<string, PlaidAccount[]>();
    for (const account of plaidAccounts) {
      const list = map.get(account.itemId) ?? [];
      list.push(account);
      map.set(account.itemId, list);
    }
    return map;
  }, [plaidAccounts]);

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-8 px-6 py-10">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-red-700">Banking Hub</p>
          <h1 className="text-3xl font-bold text-gray-900">Toyota FS Bank</h1>
          <p className="mt-1 text-sm text-gray-500">
            See every account in one place. Connect external banks with Plaid.
          </p>
        </div>
      </header>

      {error && (
        <div className="rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <section className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-400">Net worth</p>
          <p className="mt-2 text-2xl font-semibold text-gray-900">{formatCurrency(netWorth)}</p>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-400">ToyotaFSBank accounts</p>
          <p className="mt-2 text-2xl font-semibold text-gray-900">{accounts.length}</p>
          <p className="text-xs text-gray-400">{formatCurrency(totalPrimaryBalance)} total</p>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-400">Linked institutions</p>
          <p className="mt-2 text-2xl font-semibold text-gray-900">{linkedItems.length}</p>
          <p className="text-xs text-gray-400">{formatCurrency(totalLinkedBalance)} total</p>
        </div>
      </section>

      <section className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-gray-900">Linked accounts</h2>
            <PlaidLinkButton
              label="+ Add account"
              onSuccess={handlePlaidSuccess}
              className="rounded-md border border-red-700 px-3 py-1.5 text-xs font-medium text-red-700 transition hover:bg-red-50"
            />
          </div>

          {loading ? (
            <p className="text-sm text-gray-500">Loading…</p>
          ) : linkedItems.length === 0 ? (
            <p className="text-sm text-gray-500">
              No external accounts linked yet. Use Plaid to connect a bank in seconds.
            </p>
          ) : (
            <div className="flex flex-col gap-4">
              {linkedItems.map((item) => (
                <div key={item.itemId} className="rounded-lg border border-gray-100 p-4">
                  <div className="mb-2 flex items-center justify-between">
                    <p className="font-medium text-gray-900">{item.institutionName ?? "Linked institution"}</p>
                    <button
                      onClick={() => handleUnlink(item.itemId)}
                      className="text-xs font-medium text-red-600 hover:underline"
                    >
                      Unlink
                    </button>
                  </div>
                  <ul className="flex flex-col gap-1">
                    {(accountsByItem.get(item.itemId) ?? []).map((account) => (
                      <li key={account.accountId} className="flex items-center justify-between text-sm">
                        <span className="text-gray-600">
                          {account.name}
                          {account.mask ? ` •••${account.mask}` : ""}
                        </span>
                        <span className="font-medium text-gray-900">
                          {formatCurrency(account.currentBalance, account.isoCurrencyCode ?? "USD")}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-gray-900">ToyotaFSBank accounts</h2>
          <form onSubmit={handleCreateAccount} className="mb-4 flex flex-wrap items-end gap-3">
            <label className="flex flex-col gap-1 text-xs text-gray-600">
              Owner name
              <input
                required
                value={ownerName}
                onChange={(e) => setOwnerName(e.target.value)}
                className="rounded-md border border-gray-300 px-2 py-1.5 text-sm"
                placeholder="Jane Doe"
              />
            </label>
            <label className="flex flex-col gap-1 text-xs text-gray-600">
              Type
              <select
                value={accountType}
                onChange={(e) => setAccountType(e.target.value as AccountType)}
                className="rounded-md border border-gray-300 px-2 py-1.5 text-sm"
              >
                {ACCOUNT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type.replace("_", " ")}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-xs text-gray-600">
              Opening balance
              <input
                required
                type="number"
                min={0}
                step="0.01"
                value={balance}
                onChange={(e) => setBalance(e.target.value)}
                className="w-28 rounded-md border border-gray-300 px-2 py-1.5 text-sm"
              />
            </label>
            <button
              type="submit"
              disabled={submittingAccount}
              className="rounded-md bg-gray-900 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-gray-800 disabled:opacity-50"
            >
              {submittingAccount ? "Creating…" : "Open account"}
            </button>
          </form>

          {accounts.length === 0 ? (
            <p className="text-sm text-gray-500">No ToyotaFSBank accounts yet.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {accounts.map((account) => (
                <li
                  key={account.id}
                  className="flex items-center justify-between rounded-lg border border-gray-100 p-3 text-sm"
                >
                  <div>
                    <p className="font-medium text-gray-900">{account.ownerName}</p>
                    <p className="font-mono text-xs text-gray-400">
                      {account.accountNumber} · {account.accountType.replace("_", " ")}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-medium text-gray-900">{formatCurrency(account.balance)}</span>
                    <button
                      onClick={() => handleDeleteAccount(account.id)}
                      className="text-xs font-medium text-red-600 hover:underline"
                    >
                      Close
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <section className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-semibold text-gray-900">Recent transactions</h2>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search transactions…"
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm"
          />
        </div>

        {linkedItems.length === 0 ? (
          <p className="text-sm text-gray-500">Link a bank account to see transactions here.</p>
        ) : filteredTransactions.length === 0 ? (
          <p className="text-sm text-gray-500">No transactions match your search.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-gray-200 text-gray-500">
                  <th className="py-2">Date</th>
                  <th className="py-2">Description</th>
                  <th className="py-2">Institution</th>
                  <th className="py-2 text-right">Amount</th>
                </tr>
              </thead>
              <tbody>
                {filteredTransactions.map((transaction) => (
                  <tr key={transaction.transactionId} className="border-b border-gray-100">
                    <td className="py-2 text-gray-500">{formatDate(transaction.date)}</td>
                    <td className="py-2">
                      {transaction.merchantName ?? transaction.name}
                      {transaction.pending && (
                        <span className="ml-2 rounded bg-yellow-100 px-1.5 py-0.5 text-xs text-yellow-700">
                          Pending
                        </span>
                      )}
                    </td>
                    <td className="py-2 text-gray-500">{transaction.institutionName ?? "—"}</td>
                    <td className="py-2 text-right font-medium text-gray-900">
                      {formatCurrency(transaction.amount, transaction.isoCurrencyCode ?? "USD")}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
