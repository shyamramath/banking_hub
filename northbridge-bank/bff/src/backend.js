import { config } from './config.js';

/**
 * Relays an operation (or a batch) to the Spring Boot dispatcher with the USER's access token.
 * The BFF never adds identity headers of its own: the backend trusts only the JWT.
 */
export async function callBackend(path, body, accessToken, correlationId) {
  let res;
  try {
    res = await fetch(`${config.backendUrl}${path}`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        accept: 'application/json',
        authorization: `Bearer ${accessToken}`,
        'x-correlation-id': correlationId,
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(config.backendTimeoutMs),
    });
  } catch (err) {
    return { status: 502, body: { success: false, error: { code: 'UPSTREAM_UNAVAILABLE', message: 'Service temporarily unavailable' } }, err };
  }

  const text = await res.text();
  let json;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = { success: false, error: { code: 'UPSTREAM_ERROR', message: 'Unexpected response from service' } };
  }
  return { status: res.status, body: json };
}
