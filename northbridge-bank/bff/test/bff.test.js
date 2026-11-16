// Runs the real Express app against a mocked OIDC client and a stub backend.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import request from 'supertest';

const received = [];
const backend = http.createServer((req, res) => {
  let body = '';
  req.on('data', (c) => (body += c));
  req.on('end', () => {
    received.push({ url: req.url, auth: req.headers.authorization, cid: req.headers['x-correlation-id'], body: JSON.parse(body) });
    res.setHeader('content-type', 'application/json');
    res.end(JSON.stringify({ success: true, operation: 'x', data: { ok: true } }));
  });
});

const jwt = (payload) =>
  ['e30', Buffer.from(JSON.stringify(payload)).toString('base64url'), 'sig'].join('.');

const ACCESS = jwt({ sub: 'u-alice', scope: 'openid accounts:read transfers:write', realm_access: { roles: ['customer', 'offline_access'] } });

const mockClient = {
  authorizationUrl: (p) => `http://idp.test/auth?state=${p.state}&code_challenge=${p.code_challenge}&m=${p.code_challenge_method}`,
  callbackParams: (req) => Object.fromEntries(new URL(req.url, 'http://x').searchParams),
  callback: async (_uri, params, checks) => {
    if (params.state !== checks.state) throw new Error('state mismatch');
    assert.ok(checks.code_verifier, 'PKCE verifier must be sent');
    return {
      access_token: ACCESS,
      refresh_token: 'refresh-1',
      id_token: 'id-1',
      expires_at: Math.floor(Date.now() / 1000) + 300,
      scope: 'openid accounts:read transfers:write',
      claims: () => ({ sub: 'u-alice', preferred_username: 'alice', name: 'Alice Carter' }),
    };
  },
  revoke: async () => {},
  endSessionUrl: () => 'http://idp.test/logout',
};

let app;
let agent;
let csrf;

before(async () => {
  await new Promise((r) => backend.listen(0, r));
  process.env.BACKEND_URL = `http://127.0.0.1:${backend.address().port}`;
  process.env.APP_ORIGIN = 'http://localhost:3000';
  const { createApp } = await import('../src/app.js');
  const { pino } = await import('pino');
  app = createApp({ getOidcClient: async () => mockClient, logger: pino({ level: 'silent' }) });
  agent = request.agent(app);
});

after(() => backend.close());

test('API rejects anonymous callers', async () => {
  const res = await request(app).post('/bff/execute').send({ operation: 'accounts.list' });
  assert.equal(res.status, 401);
});

test('session endpoint reports anonymous', async () => {
  const res = await request(app).get('/bff/session');
  assert.deepEqual(res.body, { authenticated: false });
});

test('login uses PKCE S256 and a session cookie that is HttpOnly + SameSite', async () => {
  const start = await agent.get('/auth/login?returnTo=/accounts/acc-1');
  assert.equal(start.status, 302);
  const location = new URL(start.headers.location);
  assert.equal(location.searchParams.get('m'), 'S256');
  const cookie = start.headers['set-cookie'][0];
  assert.match(cookie, /HttpOnly/);
  assert.match(cookie, /SameSite=Lax/);

  const cb = await agent.get(`/auth/callback?code=abc&state=${location.searchParams.get('state')}`);
  assert.equal(cb.status, 302);
  assert.equal(cb.headers.location, '/accounts/acc-1');
  assert.notEqual(cb.headers['set-cookie'][0].split(';')[0], cookie.split(';')[0], 'session id must rotate on login');
});

test('session exposes user + csrf token but never tokens', async () => {
  const res = await agent.get('/bff/session');
  assert.equal(res.body.authenticated, true);
  assert.equal(res.body.user.username, 'alice');
  assert.deepEqual(res.body.user.roles, ['customer']);
  assert.ok(res.body.csrfToken);
  assert.ok(!JSON.stringify(res.body).includes(ACCESS), 'access token must not reach the browser');
  csrf = res.body.csrfToken;
});

test('open redirect after login is prevented', async () => {
  const start = await request(app).get('/auth/login?returnTo=//evil.example');
  assert.equal(start.status, 302);
  // returnTo is sanitised before it is stored; verified indirectly via safeReturnTo unit below
  const { safeReturnTo } = await import('../src/security.js');
  assert.equal(safeReturnTo('//evil.example'), '/dashboard');
  assert.equal(safeReturnTo('https://evil.example'), '/dashboard');
  assert.equal(safeReturnTo('/transfer'), '/transfer');
});

test('POST without CSRF token is blocked', async () => {
  const res = await agent.post('/bff/execute').send({ operation: 'accounts.list' });
  assert.equal(res.status, 403);
  assert.equal(res.body.error.code, 'CSRF');
});

test('POST from a foreign origin is blocked even with the token', async () => {
  const res = await agent.post('/bff/execute').set('x-csrf-token', csrf).set('origin', 'https://evil.example')
    .send({ operation: 'accounts.list' });
  assert.equal(res.status, 403);
});

test('operations outside the allow-list never reach the backend', async () => {
  const before = received.length;
  const res = await agent.post('/bff/execute').set('x-csrf-token', csrf).send({ operation: 'admin.users.delete' });
  assert.equal(res.status, 400);
  assert.equal(res.body.error.code, 'UNSUPPORTED_OPERATION');
  assert.equal(received.length, before);
});

test('relay forwards the user token and rebuilds the envelope', async () => {
  const res = await agent.post('/bff/execute').set('x-csrf-token', csrf).set('origin', 'http://localhost:3000')
    .send({ operation: 'account.details', payload: { accountId: 'acc-1' }, meta: { role: 'admin' }, userId: 'someone-else' });
  assert.equal(res.status, 200);
  const last = received.at(-1);
  assert.equal(last.url, '/api/execute');
  assert.equal(last.auth, `Bearer ${ACCESS}`);
  assert.ok(last.cid);
  assert.deepEqual(last.body, { operation: 'account.details', payload: { accountId: 'acc-1' }, meta: { channel: 'web' } });
});

test('state-changing operations cannot be batched', async () => {
  const res = await agent.post('/bff/execute/batch').set('x-csrf-token', csrf)
    .send({ requests: [{ operation: 'accounts.list' }, { operation: 'transfer.create', payload: {} }] });
  assert.equal(res.status, 400);
  assert.equal(res.body.error.code, 'WRITE_NOT_BATCHABLE');
});

test('read batch is relayed', async () => {
  const res = await agent.post('/bff/execute/batch').set('x-csrf-token', csrf)
    .send({ requests: [{ operation: 'profile.get' }, { operation: 'accounts.list' }] });
  assert.equal(res.status, 200);
  assert.equal(received.at(-1).url, '/api/execute/batch');
  assert.equal(received.at(-1).body.requests.length, 2);
});

test('logout destroys the session', async () => {
  const res = await agent.post('/auth/logout').set('x-csrf-token', csrf);
  assert.equal(res.status, 200);
  assert.equal(res.body.logoutUrl, 'http://idp.test/logout');
  const after = await agent.get('/bff/session');
  assert.equal(after.body.authenticated, false);
});
