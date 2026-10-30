import type {
  Account,
  AccountRequest,
  HealthStatus,
  LinkedItem,
  PlaidAccount,
  PlaidTransaction,
} from "./types";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8082";

interface GraphQlError {
  message: string;
}

interface GraphQlResponse<T> {
  data: T | null;
  errors?: GraphQlError[];
}

async function graphql<T>(
  query: string,
  variables?: Record<string, unknown>,
): Promise<T> {
  const res = await fetch(`${API_BASE_URL}/graphql`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query, variables }),
  });

  if (!res.ok) {
    const message = await res.text();
    throw new Error(`GraphQL request failed (${res.status}): ${message}`);
  }

  const body = (await res.json()) as GraphQlResponse<T>;
  if (body.errors?.length) {
    throw new Error(body.errors.map((e) => e.message).join("; "));
  }
  return body.data as T;
}

const ACCOUNT_FIELDS = `
  id
  accountNumber
  ownerName
  accountType
  balance
  createdAt
  updatedAt
`;

export async function getHealth(): Promise<HealthStatus> {
  const res = await fetch(`${API_BASE_URL}/actuator/health`);
  if (!res.ok) {
    throw new Error(`Unable to reach the ToyotaFSBank GraphQL API (${res.status})`);
  }
  const body = (await res.json()) as { status: string };
  return { status: body.status, service: "ToyotaFSBank GraphQL" };
}

export async function getAccounts(): Promise<Account[]> {
  const data = await graphql<{ accounts: Account[] }>(
    `query { accounts { ${ACCOUNT_FIELDS} } }`,
  );
  return data.accounts;
}

export async function getAccount(id: string): Promise<Account> {
  const data = await graphql<{ account: Account }>(
    `query($id: ID!) { account(id: $id) { ${ACCOUNT_FIELDS} } }`,
    { id },
  );
  return data.account;
}

export async function createAccount(payload: AccountRequest): Promise<Account> {
  const data = await graphql<{ createAccount: Account }>(
    `mutation($input: AccountInput!) { createAccount(input: $input) { ${ACCOUNT_FIELDS} } }`,
    { input: payload },
  );
  return data.createAccount;
}

export async function deleteAccount(id: string): Promise<void> {
  await graphql<{ deleteAccount: boolean }>(
    `mutation($id: ID!) { deleteAccount(id: $id) }`,
    { id },
  );
}

export async function createPlaidLinkToken(): Promise<{ linkToken: string }> {
  const data = await graphql<{ createPlaidLinkToken: string }>(
    `mutation { createPlaidLinkToken }`,
  );
  return { linkToken: data.createPlaidLinkToken };
}

export async function exchangePublicToken(payload: {
  publicToken: string;
  institutionId?: string | null;
  institutionName?: string | null;
}): Promise<LinkedItem> {
  const data = await graphql<{ exchangePlaidPublicToken: LinkedItem }>(
    `mutation($input: ExchangePublicTokenInput!) {
      exchangePlaidPublicToken(input: $input) {
        itemId
        institutionId
        institutionName
        linkedAt
      }
    }`,
    { input: payload },
  );
  return data.exchangePlaidPublicToken;
}

export async function getLinkedItems(): Promise<LinkedItem[]> {
  const data = await graphql<{ plaidItems: LinkedItem[] }>(
    `query {
      plaidItems {
        itemId
        institutionId
        institutionName
        linkedAt
      }
    }`,
  );
  return data.plaidItems;
}

export async function getPlaidAccounts(): Promise<PlaidAccount[]> {
  const data = await graphql<{ plaidAccounts: PlaidAccount[] }>(
    `query {
      plaidAccounts {
        itemId
        institutionName
        accountId
        name
        officialName
        mask
        type
        subtype
        availableBalance
        currentBalance
        isoCurrencyCode
      }
    }`,
  );
  return data.plaidAccounts;
}

export async function getPlaidTransactions(): Promise<PlaidTransaction[]> {
  const data = await graphql<{ plaidTransactions: PlaidTransaction[] }>(
    `query {
      plaidTransactions {
        itemId
        institutionName
        transactionId
        accountId
        name
        merchantName
        amount
        isoCurrencyCode
        date
        pending
        category
      }
    }`,
  );
  return data.plaidTransactions;
}

export async function unlinkPlaidItem(itemId: string): Promise<void> {
  await graphql<{ unlinkPlaidItem: boolean }>(
    `mutation($itemId: ID!) { unlinkPlaidItem(itemId: $itemId) }`,
    { itemId },
  );
}
