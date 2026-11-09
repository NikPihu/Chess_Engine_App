/**
 * All pages and their URLs. Pages are loaded lazily (code splitting): the first visit downloads
 * only what the current page needs, which keeps the landing page fast on phones.
 */
import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from '@/components/Layout';
import { Spinner } from '@/components/Spinner';
import { useMe } from '@/features/auth/useAuth';

const HomePage = lazy(() => import('@/pages/HomePage'));
const LoginPage = lazy(() => import('@/features/auth/LoginPage'));
const RegisterPage = lazy(() => import('@/features/auth/RegisterPage'));
const ForgotPasswordPage = lazy(() => import('@/features/auth/ForgotPasswordPage'));
const ResetPasswordPage = lazy(() => import('@/features/auth/ResetPasswordPage'));
const VerifyEmailPage = lazy(() => import('@/features/auth/VerifyEmailPage'));
const GamePage = lazy(() => import('@/features/game/GamePage'));
const ProfilePage = lazy(() => import('@/features/profile/ProfilePage'));
const SettingsPage = lazy(() => import('@/features/profile/SettingsPage'));
const LeaderboardPage = lazy(() => import('@/pages/LeaderboardPage'));
const AnalysisPage = lazy(() => import('@/features/analysis/AnalysisPage'));
const PuzzlePage = lazy(() => import('@/features/puzzle/PuzzlePage'));
const PuzzleRushPage = lazy(() => import('@/features/puzzle/PuzzleRushPage'));
const LearnPage = lazy(() => import('@/features/learn/LearnPage'));
const LessonPage = lazy(() => import('@/features/learn/LessonPage'));
const ReviewPage = lazy(() => import('@/features/analysis/ReviewPage'));
const PremiumPage = lazy(() => import('@/features/premium/PremiumPage'));
const FriendsPage = lazy(() => import('@/features/social/FriendsPage'));
const TvPage = lazy(() => import('@/features/social/TvPage'));
const TournamentsPage = lazy(() => import('@/features/tournament/TournamentsPage'));
const TournamentPage = lazy(() => import('@/features/tournament/TournamentPage'));
const BotsPage = lazy(() => import('@/features/bots/BotsPage'));
const AdminPage = lazy(() => import('@/features/admin/AdminPage'));
const ChallengePage = lazy(() => import('@/features/lobby/ChallengePage'));
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'));

/** Sends visitors to the login page for pages that need an account. */
function RequireLogin({ children }: { children: React.ReactNode }) {
  const { data: me, isLoading } = useMe();
  if (isLoading) return <Spinner />;
  if (!me) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />;
  return <>{children}</>;
}

function RequireStaff({ children }: { children: React.ReactNode }) {
  const { data: me, isLoading } = useMe();
  if (isLoading) return <Spinner />;
  if (!me || me.role === 'USER') return <Navigate to="/" replace />;
  return <>{children}</>;
}

export function App() {
  return (
    <Layout>
      <Suspense fallback={<Spinner />}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/game/:id" element={<GamePage />} />
          <Route path="/challenge/:id" element={<ChallengePage />} />
          <Route path="/@/:username" element={<ProfilePage />} />
          <Route path="/leaderboard" element={<LeaderboardPage />} />
          <Route path="/analysis" element={<AnalysisPage />} />
          <Route path="/review/:id" element={<RequireLogin><ReviewPage /></RequireLogin>} />
          <Route path="/puzzles" element={<PuzzlePage />} />
          <Route path="/puzzles/rush" element={<RequireLogin><PuzzleRushPage /></RequireLogin>} />
          <Route path="/learn" element={<LearnPage />} />
          <Route path="/learn/:slug" element={<LessonPage />} />
          <Route path="/premium" element={<PremiumPage />} />
          <Route path="/friends" element={<RequireLogin><FriendsPage /></RequireLogin>} />
          <Route path="/tv" element={<TvPage />} />
          <Route path="/tournaments" element={<TournamentsPage />} />
          <Route path="/tournaments/:id" element={<TournamentPage />} />
          <Route path="/bots" element={<BotsPage />} />
          <Route path="/settings" element={<RequireLogin><SettingsPage /></RequireLogin>} />
          <Route path="/admin" element={<RequireStaff><AdminPage /></RequireStaff>} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Suspense>
    </Layout>
  );
}
