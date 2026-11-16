type P = { size?: number; className?: string };

const base = (size = 20) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
});

export const Logo = ({ size = 34 }: P) => (
  <svg width={size} height={size} viewBox="0 0 40 40" aria-hidden>
    <rect width="40" height="40" rx="10" fill="#c9a227" />
    <path d="M7 27c4-8 9-12 13-12s9 4 13 12" stroke="#0a1a33" strokeWidth="3" fill="none" strokeLinecap="round" />
    <path d="M12 27v-5M20 27v-9M28 27v-5M6 28h28" stroke="#0a1a33" strokeWidth="2.4" strokeLinecap="round" />
  </svg>
);

export const Home = ({ size }: P) => (<svg {...base(size)}><path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z" /></svg>);
export const Transfer = ({ size }: P) => (<svg {...base(size)}><path d="M7 7h13l-4-4M17 17H4l4 4" /></svg>);
export const Shield = ({ size }: P) => (<svg {...base(size)}><path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z" /><path d="m9 12 2 2 4-4" /></svg>);
export const Clipboard = ({ size }: P) => (<svg {...base(size)}><rect x="6" y="4" width="12" height="17" rx="2" /><path d="M9 4V3h6v1M9 10h6M9 14h6M9 18h4" /></svg>);
export const Lock = ({ size }: P) => (<svg {...base(size)}><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /></svg>);
export const Logout = ({ size }: P) => (<svg {...base(size)}><path d="M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4M10 16l-4-4 4-4M6 12h10" /></svg>);
export const Bank = ({ size }: P) => (<svg {...base(size)}><path d="m3 9 9-5 9 5M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18" /></svg>);
export const Savings = ({ size }: P) => (<svg {...base(size)}><path d="M5 11a7 6 0 0 1 13-2h2v4l-2 1v3h-3v-2H9v2H6v-3a6 6 0 0 1-1-3z" /><circle cx="15" cy="10" r=".6" fill="currentColor" /></svg>);
export const Card = ({ size }: P) => (<svg {...base(size)}><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 10h18M7 15h3" /></svg>);
export const Bill = ({ size }: P) => (<svg {...base(size)}><path d="M6 3h12v18l-3-2-3 2-3-2-3 2z" /><path d="M9 8h6M9 12h6" /></svg>);
export const Check = ({ size }: P) => (<svg {...base(size)}><path d="m5 12 5 5L20 7" /></svg>);
export const Alert = ({ size }: P) => (<svg {...base(size)}><path d="M12 9v4M12 17h.01" /><path d="M10.3 3.9 2 18a2 2 0 0 0 1.7 3h16.6A2 2 0 0 0 22 18L13.7 3.9a2 2 0 0 0-3.4 0z" /></svg>);
export const Info = ({ size }: P) => (<svg {...base(size)}><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7.5h.01" /></svg>);
export const Arrow = ({ size }: P) => (<svg {...base(size)}><path d="M5 12h14M13 6l6 6-6 6" /></svg>);
export const Back = ({ size }: P) => (<svg {...base(size)}><path d="M19 12H5M11 18l-6-6 6-6" /></svg>);
export const Refresh = ({ size }: P) => (<svg {...base(size)}><path d="M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7" /></svg>);
export const Statement = ({ size }: P) => (<svg {...base(size)}><path d="M7 3h7l5 5v13H7z" /><path d="M14 3v5h5M10 13h6M10 17h6" /></svg>);

export const accountIcon = (type: string, size = 20) =>
  type === 'CREDIT' ? <Card size={size} /> : type === 'SAVINGS' ? <Savings size={size} /> : <Bank size={size} />;
