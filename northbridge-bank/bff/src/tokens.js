// Server-side token management. The browser never sees an access or refresh token.

const REFRESH_SKEW_SECONDS = 30;

// One refresh per session at a time: with refresh-token rotation, two parallel refreshes
// would make the second one fail (and Keycloak would revoke the session).
const inFlight = new Map();

export class SessionExpiredError extends Error {}

export function storeTokens(session, tokenSet) {
  session.tokens = {
    access_token: tokenSet.access_token,
    refresh_token: tokenSet.refresh_token ?? session.tokens?.refresh_token,
    id_token: tokenSet.id_token ?? session.tokens?.id_token,
    expires_at: tokenSet.expires_at ?? Math.floor(Date.now() / 1000) + (tokenSet.expires_in ?? 60),
  };
}

/** Returns a valid access token, refreshing it first if it is about to expire. */
export async function getAccessToken(req, client) {
  const tokens = req.session.tokens;
  const now = Math.floor(Date.now() / 1000);
  if (tokens.expires_at - REFRESH_SKEW_SECONDS > now) return tokens.access_token;
  if (!tokens.refresh_token) throw new SessionExpiredError('No refresh token');

  const key = req.sessionID;
  let pending = inFlight.get(key);
  if (!pending) {
    pending = (async () => {
      try {
        const refreshed = await client.refresh(tokens.refresh_token);
        req.log?.info('access token refreshed');
        return refreshed;
      } catch (err) {
        req.log?.warn({ err: err.message }, 'token refresh failed');
        throw new SessionExpiredError('Refresh failed');
      } finally {
        inFlight.delete(key);
      }
    })();
    inFlight.set(key, pending);
  }
  // Every concurrent request for this session gets the same refreshed token set.
  const refreshed = await pending;
  storeTokens(req.session, refreshed);
  await new Promise((resolve, reject) => req.session.save((e) => (e ? reject(e) : resolve())));
  return req.session.tokens.access_token;
}
