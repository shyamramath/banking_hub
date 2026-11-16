import type { NextConfig } from 'next';

// The UI never talks to Spring Boot or Keycloak tokens directly. /auth/* and /bff/* are proxied
// to the Express BFF so the session cookie is first-party (same origin as the UI).
const BFF_URL = process.env.BFF_URL ?? 'http://localhost:4000';
const isDev = process.env.NODE_ENV !== 'production';

// Next.js needs inline scripts for hydration. For a stricter policy, switch to nonce-based CSP
// in proxy.ts (see Next.js docs: "Content Security Policy").
const csp = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ''}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data:",
  "font-src 'self'",
  "connect-src 'self'",
  "frame-ancestors 'none'",
  "form-action 'self'",
  "base-uri 'self'",
  "object-src 'none'",
].join('; ');

const nextConfig: NextConfig = {
  output: 'standalone',
  poweredByHeader: false,
  async rewrites() {
    return [
      { source: '/auth/:path*', destination: `${BFF_URL}/auth/:path*` },
      { source: '/bff/:path*', destination: `${BFF_URL}/bff/:path*` },
    ];
  },
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'Content-Security-Policy', value: csp },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=(), payment=()' },
          ...(isDev ? [] : [{ key: 'Strict-Transport-Security', value: 'max-age=63072000; includeSubDomains; preload' }]),
        ],
      },
    ];
  },
};

export default nextConfig;
