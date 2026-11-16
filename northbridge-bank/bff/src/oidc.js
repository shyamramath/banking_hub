import { Issuer, custom } from 'openid-client';
import { config, redirectUri, postLogoutRedirectUri } from './config.js';

custom.setHttpOptionsDefaults({ timeout: 5000 });

/**
 * Builds the OIDC client. When the BFF reaches the IdP over a private network (Docker:
 * http://keycloak:8080) but the browser and the token "iss" use the public URL
 * (http://localhost:8080), we read discovery from the internal URL and keep the
 * browser-facing endpoints (authorize, logout) on the public URL.
 */
export async function createOidcClient() {
  const publicIssuer = config.oidc.issuer.replace(/\/$/, '');
  const internalIssuer = (config.oidc.internalIssuer || publicIssuer).replace(/\/$/, '');

  const res = await fetch(`${internalIssuer}/.well-known/openid-configuration`, {
    signal: AbortSignal.timeout(5000),
  });
  if (!res.ok) throw new Error(`OIDC discovery failed: HTTP ${res.status}`);
  const meta = await res.json();

  const toPublic = (url) => url && url.replace(internalIssuer, publicIssuer);
  const toInternal = (url) => url && url.replace(publicIssuer, internalIssuer);

  const issuer = new Issuer({
    ...meta,
    issuer: publicIssuer,
    // Front channel: the user's browser is redirected here.
    authorization_endpoint: toPublic(meta.authorization_endpoint),
    end_session_endpoint: toPublic(meta.end_session_endpoint),
    // Back channel: server-to-server calls from the BFF.
    token_endpoint: toInternal(meta.token_endpoint),
    jwks_uri: toInternal(meta.jwks_uri),
    userinfo_endpoint: toInternal(meta.userinfo_endpoint),
    revocation_endpoint: toInternal(meta.revocation_endpoint),
    introspection_endpoint: toInternal(meta.introspection_endpoint),
  });

  const client = new issuer.Client({
    client_id: config.oidc.clientId,
    client_secret: config.oidc.clientSecret,
    redirect_uris: [redirectUri()],
    post_logout_redirect_uris: [postLogoutRedirectUri()],
    response_types: ['code'],
    token_endpoint_auth_method: 'client_secret_basic',
    id_token_signed_response_alg: 'RS256',
  });
  client[custom.clock_tolerance] = 5;
  return client;
}

/** Decode (NOT verify) a JWT payload. Only used on tokens we received directly from the IdP. */
export function decodeJwtPayload(token) {
  try {
    return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
  } catch {
    return {};
  }
}
