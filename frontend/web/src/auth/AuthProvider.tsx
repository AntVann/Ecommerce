import { createContext, useContext, useEffect, useMemo, useState, type PropsWithChildren } from 'react';
import { api, session } from '../api/client';

type Role = 'CUSTOMER' | 'SELLER' | 'ADMIN';
type AuthContext = { authenticated: boolean; roles: Role[]; login: (email: string, password: string) => Promise<void>; register: (email: string, password: string) => Promise<void>; logout: () => Promise<void>; refresh: () => Promise<void> };
const Context = createContext<AuthContext | null>(null);
function rolesFromToken(token: string | null): Role[] { if (!token) return []; try { const payload = JSON.parse(atob(token.split('.')[1])); const roles = payload.roles || payload.role || []; return (Array.isArray(roles) ? roles : [roles]).filter((role): role is Role => ['CUSTOMER', 'SELLER', 'ADMIN'].includes(role)); } catch { return []; } }
export function AuthProvider({ children }: PropsWithChildren) {
  const [authenticated, setAuthenticated] = useState(Boolean(session.token));
  const [roles, setRoles] = useState<Role[]>(rolesFromToken(session.token));
  useEffect(() => { api.refresh().then(() => { setAuthenticated(true); setRoles(rolesFromToken(session.token)); }).catch(() => undefined); }, []);
  const value = useMemo<AuthContext>(() => ({
    authenticated,
    roles,
    login: async (email, password) => { await api.login(email, password); try { await api.mergeCart(); } catch { /* no guest cart is a valid login state */ } setAuthenticated(true); setRoles(rolesFromToken(session.token)); },
    register: (email, password) => api.register(email, password),
    logout: async () => { try { await api.logout(); } finally { setAuthenticated(false); setRoles([]); } },
    refresh: async () => { await api.refresh(); setAuthenticated(true); setRoles(rolesFromToken(session.token)); }
  }), [authenticated, roles]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}
export function useAuth() { const value = useContext(Context); if (!value) throw new Error('useAuth must be used within AuthProvider'); return value; }
