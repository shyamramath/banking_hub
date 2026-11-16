import { pino } from 'pino';
import session from 'express-session';
import RedisStore from 'connect-redis';
import { createClient } from 'redis';

import { config } from './config.js';
import { createApp } from './app.js';
import { createOidcClient } from './oidc.js';

const logger = pino({ level: process.env.LOG_LEVEL || 'info' });

// The IdP may still be starting (docker compose). Resolve the client lazily and retry on failure.
let clientPromise;
function getOidcClient() {
  if (!clientPromise) {
    clientPromise = createOidcClient().catch((err) => {
      clientPromise = undefined;
      throw err;
    });
  }
  return clientPromise;
}

async function createSessionStore() {
  if (!config.session.redisUrl) {
    if (config.production) throw new Error('REDIS_URL is required in production');
    logger.warn('REDIS_URL not set - using in-memory sessions (dev only, lost on restart)');
    return new session.MemoryStore();
  }
  const redis = createClient({ url: config.session.redisUrl });
  redis.on('error', (err) => logger.error({ err: err.message }, 'redis error'));
  await redis.connect();
  return new RedisStore({ client: redis, prefix: 'nb:sess:' });
}

const app = createApp({ getOidcClient, sessionStore: await createSessionStore(), logger });

app.listen(config.port, () => {
  logger.info({ port: config.port, backend: config.backendUrl, issuer: config.oidc.issuer }, 'BFF listening');
  getOidcClient()
    .then(() => logger.info('OIDC discovery complete'))
    .catch((err) => logger.warn({ err: err.message }, 'IdP not reachable yet - will retry on first login'));
});
