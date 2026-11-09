/**
 * The user's UI preferences with defaults filled in. Visitors get the defaults; logged-in users
 * get what they saved on the Settings page (stored on the server, so it follows them everywhere).
 */
import { useEffect } from 'react';
import { useMe } from '@/features/auth/useAuth';
import { setSoundEnabled } from '@/lib/sound';
import type { Settings } from '@/lib/types';

export const DEFAULT_SETTINGS: Required<Settings> = {
  boardTheme: 'noir',
  sound: true,
  premoves: true,
  autoQueen: false,
  showCoordinates: true,
};

export function useSettings(): Required<Settings> {
  const { data: me } = useMe();
  const settings = { ...DEFAULT_SETTINGS, ...(me?.settings ?? {}) };
  useEffect(() => {
    setSoundEnabled(settings.sound);
  }, [settings.sound]);
  return settings;
}
