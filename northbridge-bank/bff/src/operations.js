// Edge allow-list: the BFF only relays operations the web channel is meant to use.
// This is a coarse first filter (and keeps junk away from the backend); the backend still
// enforces scopes, roles and ownership for every call. Never rely on this list alone.

export const OPERATIONS = Object.freeze({
  'profile.get': { write: false },
  'accounts.list': { write: false },
  'account.details': { write: false },
  'transactions.list': { write: false },
  'transactions.recent': { write: false },
  'transfer.create': { write: true },
  'audit.list': { write: false },
});

const NAME = /^[a-z]+(\.[a-z]+)+$/;

export function isAllowed(operation) {
  return typeof operation === 'string' && NAME.test(operation) && Object.hasOwn(OPERATIONS, operation);
}

export function isWrite(operation) {
  return isAllowed(operation) && OPERATIONS[operation].write;
}
