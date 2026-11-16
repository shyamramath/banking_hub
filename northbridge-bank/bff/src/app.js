import crypto from 'node:crypto';
import express from 'express';
import session from 'express-session';
import helmet from 'helmet';
import { rateLimit } from 'express-rate-limit';
import { pinoHttp } from 'pino-http';
import { generators } from 'openid-client';

import { config, redirectUri, postLogoutRedirectUri } from './config.js';
import { isAllowed, isWrite } from './operations.js';
import { requireAuth, verifyCsrf, newCsrfToken, safeReturnTo } from './security.js';
import { getAccessToken, storeTokens, SessionExpiredError } from './tokens.js';
import { callBackend } from './backend.js';
import { decodeJwtPayload } from './oidc.js';

const APP_ROLES = new Set(['customer', 'auditor']);

const fail = (res, status, code, message) => res.status(status).json({ success: false, error: { code, message } });

/**
 * @param {object} deps
 * @param {() => Promise<import('openid-client').BaseClient>} deps.getOidcClient
 * @param {import('express-session').Store} [deps.sessionStore]
 * @param {import('pino').Logger} [deps.logger]
 */
export function createApp({ getOidcClient, sessionStore, logger }) {
  const app = express();
  app.set('trust proxy', 1); // behind the Next.js proxy / a load balancer

  app.use(helmet());
  app.use(
    pinoHttp({
      logger,
      genReqId: (req, res) => {
        const id = crypto.randomUUID();
        res.setHeader('x-correlation-id', id);
        return id;
      },
      redact: ['req.headers.cookie', 'req.headers.authorization', 'req.headers["x-csrf-token"]', 'res.headers["set-cookie"]'],
      autoLogging: { ignore: (req) => req.url === '/healthz' },
    }),
  );
  app.use(express.json({ limit: '32kb', strict: true }));

  app.use(
    session({
      name: config.session.cookieName,
      secret: config.session.secret,
      store: sessionStore,
      resave: false,
      saveUninitialized: false,
      rolling: true, // idle timeout: every request pushes expiry forward
      cookie: {
        httpOnly: true, // not readable from JavaScript (XSS can't steal it)
        secure: config.session.cookieSecure,
        sameSite: 'lax', // not sent on cross-site POSTs
        path: '/',
        maxAge: config.session.idleTimeoutMs,
      },
    }),
  );

  app.get('/healthz', (req, res) => res.json({ status: 'ok' }));

  // ---------------------------------------------------------------------------------------
  // Authentication: OIDC Authorization Code + PKCE, BFF is a confidential client
  // ---------------------------------------------------------------------------------------

  app.get('/auth/login', async (req, res, next) => {
    try {
      const client = await getOidcClient();
      const verifier = generators.codeVerifier();
      const state = generators.state();
      const nonce = generators.nonce();
      req.session.auth = { verifier, state, nonce, returnTo: safeReturnTo(req.query.returnTo) };
      const url = client.authorizationUrl({
        scope: config.oidc.scopes,
        state,
        nonce,
        code_challenge: generators.codeChallenge(verifier),
        code_challenge_method: 'S256',
      });
      req.session.save((err) => (err ? next(err) : res.redirect(url)));
    } catch (err) {
      req.log.error({ err }, 'login could not start');
      res.redirect('/?error=idp_unavailable');
    }
  });

  app.get('/auth/callback', async (req, res) => {
    const pending = req.session.auth;
    if (!pending) return res.redirect('/?error=login_expired');
    try {
      const client = await getOidcClient();
      const params = client.callbackParams(req);
      const tokenSet = await client.callback(redirectUri(), params, {
        state: pending.state,
        nonce: pending.nonce,
        code_verifier: pending.verifier,
      });

      const id = tokenSet.claims(); // ID token: signature, iss, aud, nonce, exp already verified
      const access = decodeJwtPayload(tokenSet.access_token);
      const user = {
        sub: id.sub,
        username: id.preferred_username,
        name: id.name || id.preferred_username,
        email: id.email,
        roles: (access.realm_access?.roles || []).filter((r) => APP_ROLES.has(r)),
        scopes: String(access.scope || tokenSet.scope || '').split(' ').filter(Boolean),
      };
      const returnTo = pending.returnTo;

      // New session id after login: prevents session fixation.
      req.session.regenerate((err) => {
        if (err) return res.redirect('/?error=login_failed');
        req.session.user = user;
        req.session.createdAt = Date.now();
        req.session.csrfToken = newCsrfToken();
        storeTokens(req.session, tokenSet);
        req.session.save(() => {
          req.log.info({ user: user.username }, 'login success');
          res.redirect(returnTo);
        });
      });
    } catch (err) {
      req.log.warn({ err: err.message }, 'login callback failed');
      res.redirect('/?error=login_failed');
    }
  });

  app.post('/auth/logout', verifyCsrf, async (req, res) => {
    const tokens = req.session?.tokens;
    let logoutUrl = postLogoutRedirectUri();
    try {
      const client = await getOidcClient();
      if (tokens?.refresh_token) {
        await client.revoke(tokens.refresh_token, 'refresh_token').catch(() => {});
      }
      if (tokens?.id_token) {
        logoutUrl = client.endSessionUrl({ id_token_hint: tokens.id_token, post_logout_redirect_uri: postLogoutRedirectUri() });
      }
    } catch {
      // IdP unreachable: still end the local session.
    }
    req.session.destroy(() => {
      res.clearCookie(config.session.cookieName, { path: '/' });
      res.json({ success: true, logoutUrl });
    });
  });

  /** What the UI may know about the session. Never includes tokens. */
  app.get('/bff/session', (req, res) => {
    const s = req.session;
    if (!s?.user) return res.json({ authenticated: false });
    res.json({
      authenticated: true,
      user: s.user,
      csrfToken: s.csrfToken,
      absoluteExpiresAt: s.createdAt + config.session.absoluteTimeoutMs,
      idleTimeoutMs: config.session.idleTimeoutMs,
    });
  });

  // ---------------------------------------------------------------------------------------
  // The single API surface: relay to the Spring dispatcher
  // ---------------------------------------------------------------------------------------

  const limitHandler = (req, res) => fail(res, 429, 'RATE_LIMITED', 'Too many requests, please slow down');
  const userKey = (req) => req.session.user.sub;

  const readLimiter = rateLimit({
    windowMs: 60_000,
    limit: config.rateLimit.readsPerMinute,
    keyGenerator: (req) => `${userKey(req)}:all`,
    standardHeaders: 'draft-8',
    legacyHeaders: false,
    handler: limitHandler,
  });
  const writeLimiter = rateLimit({
    windowMs: 60_000,
    limit: config.rateLimit.writesPerMinute,
    keyGenerator: (req) => `${userKey(req)}:${req.body?.operation}`,
    skip: (req) => !isWrite(req.body?.operation),
    standardHeaders: 'draft-8',
    legacyHeaders: false,
    handler: limitHandler,
  });

  const validPayload = (p) => p === undefined || p === null || (typeof p === 'object' && !Array.isArray(p));

  async function relay(req, res, path, body) {
    let token;
    try {
      token = await getAccessToken(req, await getOidcClient());
    } catch (err) {
      if (err instanceof SessionExpiredError) {
        return req.session.destroy(() => fail(res, 401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.'));
      }
      req.log.error({ err }, 'token handling failed');
      return fail(res, 503, 'AUTH_UNAVAILABLE', 'Sign-in service temporarily unavailable');
    }

    const result = await callBackend(path, body, token, req.id);
    if (result.err) req.log.error({ err: result.err.message }, 'backend unreachable');
    if (result.status === 401) {
      return fail(res, 401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.');
    }
    res.status(result.status).json(result.body);
  }

  app.post('/bff/execute', requireAuth, verifyCsrf, readLimiter, writeLimiter, (req, res) => {
    const { operation, payload } = req.body ?? {};
    if (!isAllowed(operation)) {
      req.log.warn({ operation: String(operation).slice(0, 64) }, 'operation blocked at BFF allow-list');
      return fail(res, 400, 'UNSUPPORTED_OPERATION', 'This operation is not available');
    }
    if (!validPayload(payload)) return fail(res, 400, 'INVALID_PAYLOAD', 'Payload must be an object');

    // Re-build the envelope: nothing the browser adds besides operation + payload is forwarded.
    relay(req, res, '/api/execute', { operation, payload: payload ?? {}, meta: { channel: 'web' } });
  });

  app.post('/bff/execute/batch', requireAuth, verifyCsrf, readLimiter, (req, res) => {
    const requests = req.body?.requests;
    if (!Array.isArray(requests) || requests.length === 0 || requests.length > config.batchMaxSize) {
      return fail(res, 400, 'INVALID_BATCH', `A batch must contain 1-${config.batchMaxSize} operations`);
    }
    for (const r of requests) {
      if (!isAllowed(r?.operation) || !validPayload(r?.payload)) {
        return fail(res, 400, 'UNSUPPORTED_OPERATION', 'This operation is not available');
      }
      if (isWrite(r.operation)) {
        return fail(res, 400, 'WRITE_NOT_BATCHABLE', 'State-changing operations cannot be batched');
      }
    }
    relay(req, res, '/api/execute/batch', {
      requests: requests.map((r) => ({ operation: r.operation, payload: r.payload ?? {}, meta: { channel: 'web' } })),
    });
  });

  // Malformed JSON and other errors: generic message, details only in logs.
  app.use((err, req, res, _next) => {
    if (err.type === 'entity.parse.failed') return fail(res, 400, 'INVALID_JSON', 'Request body must be valid JSON');
    if (err.type === 'entity.too.large') return fail(res, 413, 'TOO_LARGE', 'Request too large');
    req.log?.error({ err }, 'unhandled error');
    fail(res, 500, 'INTERNAL_ERROR', 'Something went wrong');
  });

  return app;
}
