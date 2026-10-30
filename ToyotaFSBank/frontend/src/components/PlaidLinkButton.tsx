"use client";

import { useCallback, useEffect, useState } from "react";
import { usePlaidLink, type PlaidLinkOnSuccess } from "react-plaid-link";
import { createPlaidLinkToken } from "@/lib/api";

interface PlaidLinkButtonProps {
  label?: string;
  className?: string;
  onSuccess: PlaidLinkOnSuccess;
}

export default function PlaidLinkButton({ label = "Connect a bank account", className, onSuccess }: PlaidLinkButtonProps) {
  const [linkToken, setLinkToken] = useState<string | null>(null);
  const [fetchingToken, setFetchingToken] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { open, ready } = usePlaidLink({
    token: linkToken,
    onSuccess,
  });

  const fetchLinkToken = useCallback(async () => {
    setFetchingToken(true);
    setError(null);
    try {
      const { linkToken: token } = await createPlaidLinkToken();
      setLinkToken(token);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to start Plaid Link.");
    } finally {
      setFetchingToken(false);
    }
  }, []);

  useEffect(() => {
    if (linkToken && ready) {
      open();
    }
  }, [linkToken, ready, open]);

  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        onClick={fetchLinkToken}
        disabled={fetchingToken}
        className={
          className ??
          "rounded-md bg-red-700 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-800 disabled:opacity-50"
        }
      >
        {fetchingToken ? "Connecting…" : label}
      </button>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}
