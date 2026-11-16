import crypto from 'node:crypto';
import { config } from './config.js';

export const newCsrfToken = () => crypto.randomBytes(32).toString('base64url');

const safeEqual = (a, b) => {
  const ab = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  return ab.length === bb.length && crypto.timingSafeEqual(ab, bb);
};

/** 401 unless there is a logged-in, non-expired session. */
export function requireAuth(req, res, next) {
  const s = req.session;
  if (!s?.user || !s?.tokens) {
    return res.status(401).json({ success: false, error: { code: 'UNAUTHENTICATED', message: 'Please sign in' } });
  }
  // Absolute lifetime, independent of the rolling idle timeout on the cookie.
  if (Date.now() - s.createdAt > config.session.absoluteTimeoutMs) {
    return s.destroy(() =>
      res.status(401).json({ success: false, error: { code: 'SESSION_EXPIRED', message: 'Your session has expired' } }),
    );
  }
  next();
}

/**
 * CSRF defence for cookie-authenticated, state-changing requests:
 *  1. SameSite=Lax cookie (set in session config)
 *  2. Origin / Referer must be our UI origin
 *  3. Synchronizer token: X-CSRF-Token header must match the token stored in the session
 */
export function verifyCsrf(req, res, next) {
  let origin = req.get('origin') || null;
  if (!origin && req.get('referer')) {
    try {
      origin = new URL(req.get('referer')).origin;
    } catch {
      origin = 'invalid';
    }
  }
  if (origin && origin !== config.appOrigin) {
    req.log?.warn({ origin }, 'csrf: origin mismatch');
    return res.status(403).json({ success: false, error: { code: 'CSRF', message: 'Request blocked' } });
  }
  const sent = req.get('x-csrf-token');
  if (!sent || !req.session?.csrfToken || !safeEqual(sent, req.session.csrfToken)) {
    req.log?.warn('csrf: token missing or invalid');
    return res.status(403).json({ success: false, error: { code: 'CSRF', message: 'Request blocked' } });
  }
  next();
}

/** Only allow relative, same-site return paths after login (prevents open redirects). */
export function safeReturnTo(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return '/dashboard';
  }
  return value.slice(0, 200);
}
