/**
 * The frame around every page: top navigation (with a mobile menu), the page, and a footer.
 */
import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import {
  Bot,
  Crown,
  GraduationCap,
  LogOut,
  Menu,
  Puzzle,
  Settings,
  Shield,
  Swords,
  Trophy,
  Tv,
  Users,
  X,
} from 'lucide-react';
import clsx from 'clsx';
import { useLogout, useMe } from '@/features/auth/useAuth';
import { Logo } from './Logo';
import { NotificationBell } from '@/features/social/NotificationBell';

const NAV = [
  { to: '/', label: 'Play', icon: Swords },
  { to: '/puzzles', label: 'Puzzles', icon: Puzzle },
  { to: '/learn', label: 'Learn', icon: GraduationCap },
  { to: '/tournaments', label: 'Tournaments', icon: Trophy },
  { to: '/tv', label: 'Watch', icon: Tv },
  { to: '/bots', label: 'Bots', icon: Bot },
];

export function Layout({ children }: { children: React.ReactNode }) {
  const { data: me } = useMe();
  const logout = useLogout();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    clsx(
      'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition',
      isActive ? 'bg-ink-800 text-white' : 'text-ink-300 hover:bg-ink-800 hover:text-white',
    );

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-30 border-b border-ink-800 bg-ink-900/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-7xl items-center gap-4 px-4">
          <Link to="/" className="flex items-center gap-2" aria-label="Gambyte home">
            <Logo />
          </Link>
          <nav className="hidden gap-1 md:flex">
            {NAV.map(({ to, label, icon: Icon }) => (
              <NavLink key={to} to={to} end={to === '/'} className={linkClass}>
                <Icon size={16} />
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            {me ? (
              <>
                {!me.premium && (
                  <Link to="/premium" className="btn-ghost hidden text-amber-300 sm:inline-flex">
                    <Crown size={16} /> Premium
                  </Link>
                )}
                <NotificationBell />
                <Link to={`/@/${me.username}`} className="btn-ghost">
                  {me.premium && <Crown size={14} className="text-amber-300" />}
                  {me.username}
                </Link>
                <Link to="/friends" className="btn-ghost hidden px-2 sm:inline-flex" aria-label="Friends">
                  <Users size={18} />
                </Link>
                {me.role !== 'USER' && (
                  <Link to="/admin" className="btn-ghost hidden px-2 sm:inline-flex" aria-label="Moderation">
                    <Shield size={18} />
                  </Link>
                )}
                <Link to="/settings" className="btn-ghost hidden px-2 sm:inline-flex" aria-label="Settings">
                  <Settings size={18} />
                </Link>
                <button
                  className="btn-ghost hidden px-2 sm:inline-flex"
                  aria-label="Log out"
                  onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/') })}
                >
                  <LogOut size={18} />
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="btn-ghost">
                  Log in
                </Link>
                <Link to="/register" className="btn-primary">
                  Sign up
                </Link>
              </>
            )}
            <button
              className="btn-ghost px-2 md:hidden"
              aria-label="Menu"
              onClick={() => setOpen((o) => !o)}
            >
              {open ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>
        </div>
        {open && (
          <nav className="grid gap-1 border-t border-ink-800 p-2 md:hidden" onClick={() => setOpen(false)}>
            {NAV.map(({ to, label, icon: Icon }) => (
              <NavLink key={to} to={to} end={to === '/'} className={linkClass}>
                <Icon size={16} />
                {label}
              </NavLink>
            ))}
            {me && (
              <>
                <NavLink to="/friends" className={linkClass}>
                  <Users size={16} /> Friends
                </NavLink>
                <NavLink to="/settings" className={linkClass}>
                  <Settings size={16} /> Settings
                </NavLink>
                <button className={linkClass({ isActive: false })} onClick={() => logout.mutate()}>
                  <LogOut size={16} /> Log out
                </button>
              </>
            )}
          </nav>
        )}
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6">{children}</main>

      <footer className="border-t border-ink-800 py-6 text-center text-xs text-ink-400">
        <p>
          Gambyte · chess with our own engine ·{' '}
          <Link className="hover:text-ink-100" to="/leaderboard">
            Leaderboard
          </Link>{' '}
          ·{' '}
          <a className="hover:text-ink-100" href="/swagger-ui.html">
            API
          </a>
        </p>
      </footer>
    </div>
  );
}
