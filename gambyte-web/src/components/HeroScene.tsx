/**
 * The landing page's cinematic scene, drawn entirely in code (no photo):
 * a board in 3D perspective fading into the dark, a black king standing and a porcelain-white
 * queen toppling against it, rim-lit by an ember glow from behind. The composition echoes the
 * reference design's hero photograph while being our own artwork.
 *
 * The pieces are hand-drawn Staunton silhouettes with gradients (obsidian king, porcelain queen)
 * so they read as sculpted objects rather than flat icons. Everything scales with the viewport.
 */

/** Obsidian king, lit from the right by ember light. */
function King() {
  return (
    <svg viewBox="0 0 100 200" className="h-full w-full overflow-visible">
      <defs>
        <linearGradient id="k-body" x1="0" x2="1">
          <stop offset="0" stopColor="#3b3836" />
          <stop offset="0.12" stopColor="#0d0b0b" />
          <stop offset="0.55" stopColor="#1d1716" />
          <stop offset="0.8" stopColor="#5a1d0b" />
          <stop offset="1" stopColor="#ff7a50" />
        </linearGradient>
        <linearGradient id="k-rim" x1="0" x2="1">
          <stop offset="0" stopColor="#ffffff" stopOpacity="0.25" />
          <stop offset="0.5" stopColor="#ffffff" stopOpacity="0" />
          <stop offset="1" stopColor="#ff9f7f" stopOpacity="0.9" />
        </linearGradient>
        <linearGradient id="k-spec" x1="0" x2="1">
          <stop offset="0" stopColor="#fff" stopOpacity="0" />
          <stop offset="0.5" stopColor="#fff" stopOpacity="0.18" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </linearGradient>
      </defs>
      <g fill="url(#k-body)" stroke="url(#k-rim)" strokeWidth="0.9">
        <rect x="46" y="2" width="8" height="26" rx="1.5" />
        <rect x="38" y="9" width="24" height="8" rx="1.5" />
        <path d="M28 33 Q50 24 72 33 L65 58 L35 58 Z" />
        <ellipse cx="50" cy="61" rx="22" ry="5" />
        <path d="M37 65 C40 92 33 122 26 150 L74 150 C67 122 60 92 63 65 Z" />
        <ellipse cx="50" cy="153" rx="30" ry="6" />
        <path d="M16 162 Q50 151 84 162 L86 178 Q50 188 14 178 Z" />
        <ellipse cx="50" cy="181" rx="38" ry="7" />
      </g>
      {/* specular streak */}
      <path d="M52 70 C54 95 52 120 50 146 L56 146 C58 120 59 95 56 70 Z" fill="url(#k-spec)" />
    </svg>
  );
}

/** Porcelain queen, ember-tinted on the side facing the light. */
function Queen() {
  const tips = [16, 31, 50, 69, 84];
  return (
    <svg viewBox="0 0 100 200" className="h-full w-full overflow-visible">
      <defs>
        <linearGradient id="q-body" x1="0" x2="1">
          <stop offset="0" stopColor="#ff7a50" />
          <stop offset="0.18" stopColor="#f3d6c8" />
          <stop offset="0.5" stopColor="#ffffff" />
          <stop offset="1" stopColor="#8d867d" />
        </linearGradient>
      </defs>
      <g fill="url(#q-body)">
        {tips.map((x) => (
          <circle key={x} cx={x} cy={x === 50 ? 14 : 22 + Math.abs(50 - x) / 6} r="5" />
        ))}
        <path d="M14 26 L30 50 L31 26 L42 50 L50 18 L58 50 L69 26 L70 50 L86 26 L76 62 L24 62 Z" />
        <ellipse cx="50" cy="65" rx="27" ry="5.5" />
        <path d="M36 70 C40 96 34 124 26 152 L74 152 C66 124 60 96 64 70 Z" />
        <ellipse cx="50" cy="155" rx="30" ry="6" />
        <path d="M16 164 Q50 153 84 164 L86 180 Q50 190 14 180 Z" />
        <ellipse cx="50" cy="183" rx="38" ry="7" />
      </g>
    </svg>
  );
}

export function HeroScene() {
  return (
    <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden" aria-hidden>
      {/* Ember back-light, the scene's only strong colour */}
      <div className="absolute top-[42%] left-[54%] h-[62vmin] w-[48vmin] -translate-x-1/2 -translate-y-1/2 animate-glow rounded-full bg-[radial-gradient(circle,rgba(229,83,45,0.6)_0%,rgba(198,63,28,0.28)_40%,transparent_72%)] blur-3xl" />
      {/* Soft white key light behind, as in a studio shot */}
      <div className="absolute top-[18%] left-[50%] h-[55vmin] w-[26vmin] -translate-x-1/2 rotate-[8deg] rounded-full bg-white/[0.07] blur-[70px]" />

      {/* The board, laid down in perspective and fading into darkness */}
      <div className="absolute inset-x-0 bottom-[-18%] flex justify-center [perspective:900px]">
        <div
          className="grid aspect-square w-[min(120vw,1200px)] grid-cols-8 [transform:rotateX(66deg)] [mask-image:radial-gradient(ellipse_48%_46%_at_50%_42%,#000_25%,transparent_70%)]"
          style={{ transformOrigin: '50% 60%' }}
        >
          {Array.from({ length: 64 }, (_, i) => {
            const dark = (Math.floor(i / 8) + i) % 2 === 1;
            return <div key={i} className={dark ? 'bg-[#0e0c0c]' : 'bg-[#2b2522]'} />;
          })}
        </div>
      </div>

      {/* King standing, queen falling against it */}
      <div className="absolute bottom-[2%] left-[74%] h-[46vh] w-[30vh] -translate-x-1/2 opacity-55 sm:left-[62%] sm:h-[62vh] sm:w-[40vh] sm:opacity-80 lg:bottom-[3%] lg:left-[53%] lg:h-[min(80vh,800px)] lg:w-[min(52vh,520px)] lg:opacity-100">
        <div className="absolute bottom-0 left-[2%] h-[96%] w-[58%] [filter:drop-shadow(14px_0_26px_rgba(229,83,45,.45))_drop-shadow(0_40px_40px_rgba(0,0,0,.95))]">
          <King />
        </div>
        <div
          className="absolute bottom-[-2%] left-[34%] h-[78%] w-[44%] [filter:drop-shadow(-10px_0_30px_rgba(229,83,45,.55))_drop-shadow(0_30px_30px_rgba(0,0,0,.9))]"
          style={{ transform: 'rotate(-34deg)', transformOrigin: '50% 98%' }}
        >
          <Queen />
        </div>
        {/* Contact shadow on the board */}
        <div className="absolute -bottom-2 left-[8%] h-6 w-[90%] rounded-[50%] bg-black/90 blur-md" />
      </div>

      {/* Vignette and fade into the page */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_85%_70%_at_50%_45%,transparent_35%,#050505_92%)]" />
      <div className="absolute inset-x-0 bottom-0 h-48 bg-gradient-to-t from-noir-950 to-transparent" />
    </div>
  );
}
