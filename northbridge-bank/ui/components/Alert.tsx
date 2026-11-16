import { Alert as AlertIcon, Check, Info } from './Icons';
import type { ApiError } from '@/lib/api';

type Kind = 'error' | 'success' | 'info' | 'warn';

export function Alert({ kind, title, children }: { kind: Kind; title?: string; children?: React.ReactNode }) {
  const icon = kind === 'success' ? <Check /> : kind === 'info' ? <Info /> : <AlertIcon />;
  return (
    <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {icon}
      <div>
        {title && <b>{title}</b>}
        {children}
      </div>
    </div>
  );
}

export function ErrorAlert({ error }: { error: ApiError | Error | null }) {
  if (!error) return null;
  const e = error as ApiError;
  return (
    <Alert kind="error" title={e.message || 'Something went wrong'}>
      {e.details?.length ? (
        <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
          {e.details.map((d) => (
            <li key={d.field}>
              <code>{d.field}</code> {d.issue}
            </li>
          ))}
        </ul>
      ) : null}
      {e.correlationId && <div className="subtle" style={{ marginTop: 4 }}>Reference: {e.correlationId}</div>}
    </Alert>
  );
}
