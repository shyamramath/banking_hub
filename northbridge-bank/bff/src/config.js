// All configuration comes from the environment. Defaults are for local development only.

const env = (name, fallback) => {
  const v = process.env[name];
  if (v === undefined || v === '') {
    if (fallback === undefined) throw new Error(`Missing required env var ${name}`);
    return fallback;
  }
  return v;
};

const production = process.env.NODE_ENV === 'production';

export const config = {
  port: Number(env('PORT', '4000')),
  production,

  // The browser-facing origin of the UI. Login callbacks and CSRF origin checks use it.
  appOrigin: env('APP_ORIGIN', 'http://localhost:3000'),

  oidc: {
    // Public issuer (what the browser sees, what tokens carry in "iss").
    issuer: env('OIDC_ISSUER', 'http://localhost:8080/realms/northbridge'),
    // Optional: where the BFF itself reaches the IdP (e.g. http://keycloak:8080/realms/northbridge in Docker).
    internalIssuer: process.env.OIDC_INTERNAL_ISSUER || '',
    clientId: env('OIDC_CLIENT_ID', 'bank-bff'),
    clientSecret: env('OIDC_CLIENT_SECRET', 'bff-dev-secret-change-me'),
    scopes: env('OIDC_SCOPES', 'openid accounts:read transfers:write audit:read'),
  },

  backendUrl: env('BACKEND_URL', 'http://localhost:8081'),
  backendTimeoutMs: Number(env('BACKEND_TIMEOUT_MS', '10000')),

  session: {
    secret: env('SESSION_SECRET', production ? undefined : 'dev-only-session-secret-change-me'),
    // __Host- prefix = Secure, Path=/, no Domain. Requires HTTPS, so only in production.
    cookieName: env('SESSION_COOKIE_NAME', production ? '__Host-nb.sid' : 'nb.sid'),
    cookieSecure: env('SESSION_COOKIE_SECURE', production ? 'true' : 'false') === 'true',
    idleTimeoutMs: Number(env('SESSION_IDLE_MINUTES', '15')) * 60_000,
    absoluteTimeoutMs: Number(env('SESSION_ABSOLUTE_HOURS', '8')) * 3_600_000,
    redisUrl: process.env.REDIS_URL || '',
  },

  rateLimit: {
    readsPerMinute: Number(env('RATE_LIMIT_READS_PER_MIN', '120')),
    writesPerMinute: Number(env('RATE_LIMIT_WRITES_PER_MIN', '10')),
  },

  batchMaxSize: 10,
};

export const redirectUri = () => `${config.appOrigin}/auth/callback`;
export const postLogoutRedirectUri = () => `${config.appOrigin}/?signedOut=1`;
