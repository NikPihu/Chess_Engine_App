/**
 * React wrapper for the 3D hero scene. three.js (~150 kB gzipped) is loaded only here, on the
 * landing page, after first paint; until it is ready (or if WebGL is unavailable) the lighter
 * code-drawn scene is shown, so the page never waits on the 3D.
 */
import { useEffect, useRef, useState } from 'react';
import { HeroScene } from '@/components/HeroScene';

function webglAvailable(): boolean {
  try {
    const c = document.createElement('canvas');
    return Boolean(c.getContext('webgl2') || c.getContext('webgl'));
  } catch {
    return false;
  }
}

export function Hero3D() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!webglAvailable()) {
      setFailed(true);
      return;
    }
    let dispose: (() => void) | undefined;
    let cancelled = false;
    import('./scene')
      .then(({ createHeroScene }) => {
        if (cancelled || !canvasRef.current) return;
        dispose = createHeroScene(canvasRef.current, {
          reducedMotion: window.matchMedia('(prefers-reduced-motion: reduce)').matches,
        });
        // Fade in after the first frames are drawn.
        requestAnimationFrame(() => requestAnimationFrame(() => setReady(true)));
      })
      .catch(() => setFailed(true));
    return () => {
      cancelled = true;
      dispose?.();
    };
  }, []);

  if (failed) return <HeroScene />;

  return (
    <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden" aria-hidden>
      {/* Ember back-glow behind the pieces (bloom-like, cheaper than post-processing) */}
      <div className="absolute top-[45%] left-[56%] h-[60vmin] w-[42vmin] -translate-x-1/2 -translate-y-1/2 animate-glow rounded-full bg-[radial-gradient(circle,rgba(229,83,45,0.45)_0%,rgba(198,63,28,0.18)_45%,transparent_72%)] blur-3xl" />
      {!ready && <HeroScene />}
      <canvas
        ref={canvasRef}
        className={`absolute inset-0 h-full w-full transition-opacity duration-[1400ms] max-md:translate-y-[12%] ${ready ? 'opacity-100 max-md:opacity-55' : 'opacity-0'}`}
      />
      {/* Vignette and fade into the page */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_90%_75%_at_55%_45%,transparent_45%,#050505_96%)]" />
      <div className="absolute inset-x-0 bottom-0 h-40 bg-gradient-to-t from-noir-950 to-transparent" />
    </div>
  );
}
