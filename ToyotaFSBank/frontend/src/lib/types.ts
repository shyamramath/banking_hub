export type AccountType = "CHECKING" | "SAVINGS" | "AUTO_LOAN";

export interface Account {
  id: string;
  accountNumber: string;
  ownerName: string;
  accountType: AccountType;
  balance: number;
  createdAt: string;
  updatedAt: string;
}

export interface AccountRequest {
  ownerName: string;
  accountType: AccountType;
  balance: number;
}

export interface HealthStatus {
  status: string;
  service?: string;
  timestamp?: string;
}

export interface LinkedItem {
  itemId: string;
  institutionId: string | null;
  institutionName: string | null;
  linkedAt: string;
}

export interface PlaidAccount {
  itemId: string;
  institutionName: string | null;
  accountId: string;
  name: string;
  officialName: string | null;
  mask: string | null;
  type: string | null;
  subtype: string | null;
  availableBalance: number | null;
  currentBalance: number | null;
  isoCurrencyCode: string | null;
}

export interface PlaidTransaction {
  itemId: string;
  institutionName: string | null;
  transactionId: string;
  accountId: string;
  name: string;
  merchantName: string | null;
  amount: number;
  isoCurrencyCode: string | null;
  date: string;
  pending: boolean;
  category: string[] | null;
}
