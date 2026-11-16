export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT';

export interface SessionUser {
  sub: string;
  username: string;
  name: string;
  email?: string;
  roles: string[];
  scopes: string[];
}

export interface Session {
  authenticated: boolean;
  user?: SessionUser;
  csrfToken?: string;
  absoluteExpiresAt?: number;
  idleTimeoutMs?: number;
}

export interface CustomerProfile {
  userId: string;
  displayName: string;
  segment: string;
  memberSince: string;
  maskedEmail: string;
  relationshipManager?: string | null;
}

export interface AccountSummary {
  id: string;
  type: AccountType;
  nickname: string;
  maskedNumber: string;
  balance: number;
  availableCredit?: number | null;
  currency: string;
}

export interface AccountDetails {
  id: string;
  type: AccountType;
  nickname: string;
  maskedNumber: string;
  routingNumber: string;
  balance: number;
  availableBalance: number;
  creditLimit?: number | null;
  currency: string;
  status: string;
}

export interface Transaction {
  id: string;
  accountId: string;
  postedAt: string;
  description: string;
  category: string;
  amount: number;
  balanceAfter: number;
  status: string;
}

export interface TransferReceipt {
  transferId: string;
  status: string;
  amount: number;
  currency: string;
  fromAccount: string;
  toAccount: string;
  toLabel: string;
  newBalance: number;
  completedAt: string;
  replayed: boolean;
}

export interface AuditEntry {
  id: string;
  at: string;
  userId: string;
  username: string;
  operation: string;
  outcome: string;
  correlationId: string;
  detail: string;
}

export interface FieldIssue {
  field: string;
  issue: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: FieldIssue[];
}

export interface BatchItem<T = unknown> {
  operation: string;
  success: boolean;
  data?: T;
  error?: ApiErrorBody;
}
