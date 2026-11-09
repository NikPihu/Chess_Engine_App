/**
 * The frame around every page.
 *
 * The top bar follows the reference's "technical grid": cells separated by hairlines with small
 * "+" crosshairs at the joints. On phones the links move into a full-screen sheet.
 */
import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import {
  Crown,
  LogOut,
  Menu,
  Settings,
  Shield,
  Users,
  X,
} from 'lucide-react';
import clsx from 'clsx';
import { useLogout, useMe } from '@/features/auth/useAuth';
import { Logo } from './Logo';
import { NotificationBell } from '@/features/social/NotificationBell';

const NAV = [
  { to: '/', label: 'Play' },
  { to: '/puzzles', label: 'Puzzles' },
  { to: '/learn', label: 'Learn' },
  { to: '/tournaments', label: 'Tournaments' },
  { to: '/tv', label: 'Watch' },
  { to: '/analysis', label: 'Analysis' },
];

/** A small "+" at a grid joint. */
function Cross({ className }: { className?: string }) {
  return (
    <span aria-hidden className={clsx('pointer-events-none absolute text-[10px] leading-none text-noir-500', className)}>
      +
    </span>
  );
}

export function Layout({ children }: { children: React.ReactNode }) {
  const { data: me } = useMe();
  const logout = useLogout();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const landing = pathname === '/' && !me;

  useEffect(() => setOpen(false), [pathname]);
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <div className="flex min-h-screen flex-col">
      <header
        className={clsx(
          'sticky top-0 z-40 transition-colors duration-300',
          scrolled || !landing ? 'border-b border-white/[0.06] bg-noir-950/75 backdrop-blur-xl' : 'bg-transparent',
        )}
      >
        <div className="relative mx-auto flex h-16 max-w-[1440px] items-stretch px-4 sm:px-6">
          <Link to="/" className="relative flex items-center pr-6 lg:border-r lg:border-white/[0.06] lg:pr-10" aria-label="Gambyte home">
            <Logo />
            <Cross className="-right-[3.5px] -bottom-[6px] hidden lg:block" />
          </Link>

          <nav className="hidden flex-1 items-stretch lg:flex">
            {NAV.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  clsx(
                    'group relative flex flex-1 items-center justify-center border-r border-white/[0.06] text-[13px] transition',
                    isActive ? 'text-noir-50' : 'text-noir-400 hover:text-noir-50',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    {label}
                    <span
                      className={clsx(
                        'absolute inset-x-6 bottom-0 h-px origin-center bg-ember-500 transition-transform duration-300',
                        isActive ? 'scale-x-100' : 'scale-x-0 group-hover:scale-x-50',
                      )}
                    />
                    <Cross className="-right-[3.5px] -bottom-[6px]" />
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-1 pl-4 lg:pl-6">
            {me ? (
              <>
                {!me.premium && (
                  <Link to="/premium" className="btn-ghost hidden px-3 text-gold xl:inline-flex">
                    <Crown size={15} /> Premium
                  </Link>
                )}
                <NotificationBell />
                <Link to="/friends" className="btn-ghost hidden px-2.5 sm:inline-flex" aria-label="Friends">
                  <Users size={17} />
                </Link>
                {me.role !== 'USER' && (
                  <Link to="/admin" className="btn-ghost hidden px-2.5 sm:inline-flex" aria-label="Moderation">
                    <Shield size={17} />
                  </Link>
                )}
                <Link
                  to={`/@/${me.username}`}
                  className="ml-1 hidden items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] py-1 pr-3.5 pl-1 text-sm transition hover:border-white/20 sm:flex"
                >
                  <span className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-ember-400 to-ember-700 text-xs font-semibold text-white uppercase">
                    {me.username[0]}
                  </span>
                  {me.username}
                  {me.premium && <Crown size={13} className="text-gold" />}
                </Link>
              </>
            ) : (
              <>
                <Link to="/login" className="btn-ghost hidden sm:inline-flex">
                  Log in
                </Link>
                <Link to="/register" className="btn-primary px-5">
                  Sign up
                </Link>
              </>
            )}
            <button className="btn-ghost px-2.5 lg:hidden" aria-label="Menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
              {open ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>
        </div>
      </header>

      {/* Mobile sheet */}
      {open && (
        <div className="fixed inset-0 top-16 z-30 animate-fade bg-noir-950/95 backdrop-blur-xl lg:hidden">
          <nav className="flex flex-col px-6 py-6">
            {NAV.map(({ to, label }, i) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  clsx('flex items-baseline gap-4 border-b border-white/[0.06] py-4 text-3xl font-light tracking-tight',
                    isActive ? 'text-noir-50' : 'text-noir-300')
                }
              >
                <span className="eyebrow">{String(i + 1).padStart(2, '0')}/</span>
                {label}
              </NavLink>
            ))}
            {me && (
              <div className="mt-6 flex flex-wrap gap-2">
                <Link to={`/@/${me.username}`} className="btn-secondary">Profile</Link>
                <Link to="/friends" className="btn-secondary"><Users size={15} /> Friends</Link>
                <Link to="/settings" className="btn-secondary"><Settings size={15} /> Settings</Link>
                {!me.premium && <Link to="/premium" className="btn-secondary text-gold"><Crown size={15} /> Premium</Link>}
                <button className="btn-ghost" onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/') })}>
                  <LogOut size={15} /> Log out
                </button>
              </div>
            )}
          </nav>
        </div>
      )}

      <main className={clsx('mx-auto w-full flex-1', landing ? '' : 'max-w-[1440px] px-4 py-6 sm:px-6 lg:py-10')}>{children}</main>

      <footer className="border-t border-white/[0.06]">
        <div className="mx-auto grid max-w-[1440px] gap-8 px-4 py-10 sm:px-6 md:grid-cols-[1.5fr_1fr_1fr_1fr]">
          <div>
            <Logo />
            <p className="mt-3 max-w-xs text-sm text-noir-400">
              Chess with our own engine. Play, train and get better - free, forever.
            </p>
          </div>
          <FooterCol title="Play" links={[['Quick pairing', '/'], ['Tournaments', '/tournaments'], ['Watch', '/tv'], ['Bots', '/bots']]} />
          <FooterCol title="Train" links={[['Puzzles', '/puzzles'], ['Puzzle Rush', '/puzzles/rush'], ['Lessons', '/learn'], ['Analysis', '/analysis']]} />
          <FooterCol title="Gambyte" links={[['Leaderboard', '/leaderboard'], ['Premium', '/premium'], ['Settings', '/settings'], ['API', '/swagger-ui.html']]} />
        </div>
        <div className="mx-auto flex max-w-[1440px] items-center justify-between border-t border-white/[0.06] px-4 py-5 sm:px-6">
          <span className="eyebrow">© Gambyte</span>
          {me && (
            <button className="eyebrow hover:text-noir-100" onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/') })}>
              Log out
            </button>
          )}
        </div>
      </footer>
    </div>
  );
}

function FooterCol({ title, links }: { title: string; links: [string, string][] }) {
  return (
    <div>
      <p className="eyebrow mb-3">{title}</p>
      <ul className="space-y-2 text-sm">
        {links.map(([label, href]) => (
          <li key={href}>
            {href.startsWith('/swagger') ? (
              <a href={href} className="text-noir-300 transition hover:text-noir-50">{label}</a>
            ) : (
              <Link to={href} className="text-noir-300 transition hover:text-noir-50">{label}</Link>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
