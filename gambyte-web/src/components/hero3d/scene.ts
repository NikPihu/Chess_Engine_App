/**
 * The landing page's 3D still life, rendered live with three.js: a lacquered black king standing
 * and a porcelain queen toppling against it on a glossy board, lit like a studio product shot -
 * a soft white key light, and an ember-red rim light behind the pieces (the reference's mood).
 *
 * Why build the pieces in code: Staunton chess pieces are "surfaces of revolution" (turned on a
 * lathe), so each body is a 2-D profile spun around its axis with LatheGeometry. Only the king's
 * cross and the queen's coronet prongs are extra parts. No model files to download or license.
 *
 * Framework-free on purpose: `createHeroScene(canvas)` returns a disposer, and the React wrapper
 * (Hero3D.tsx) only mounts and unmounts it.
 */
import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';

/** A smooth lathe body from a rough [radius, height] profile (spline-interpolated). */
function lathe(profile: [number, number][], segments = 96): THREE.LatheGeometry {
  const curve = new THREE.SplineCurve(profile.map(([r, y]) => new THREE.Vector2(r, y)));
  const points = curve.getPoints(220).map((p) => new THREE.Vector2(Math.max(0, p.x), p.y));
  const geo = new THREE.LatheGeometry(points, segments);
  geo.computeVertexNormals();
  return geo;
}

/** Shared Staunton base and stem (both pieces use the same turned foot). */
const BASE: [number, number][] = [
  [0.0, 0.0], [0.98, 0.0], [1.02, 0.05], [1.0, 0.13], [0.9, 0.18], [0.92, 0.25], [0.86, 0.31],
  [0.7, 0.38], [0.58, 0.48], [0.5, 0.62],
];

/** King: tall stem, collar, flared crown, domed top; the cross is added separately. */
const KING: [number, number][] = [
  ...BASE,
  [0.44, 0.9], [0.39, 1.3], [0.35, 1.75], [0.34, 2.0], [0.46, 2.06], [0.54, 2.12], [0.46, 2.18],
  [0.38, 2.24], [0.4, 2.32], [0.5, 2.6], [0.6, 2.86], [0.64, 2.94], [0.56, 3.0], [0.38, 3.05],
  [0.22, 3.12], [0.12, 3.2], [0.0, 3.22],
];

/** Queen: slimmer stem, collar, then a wide open cup (the coronet) with a small inner dome. */
const QUEEN: [number, number][] = [
  ...BASE,
  [0.42, 0.9], [0.36, 1.3], [0.32, 1.7], [0.31, 1.9], [0.43, 1.96], [0.5, 2.02], [0.42, 2.08],
  [0.35, 2.14], [0.38, 2.24], [0.5, 2.5], [0.62, 2.7], [0.66, 2.76], [0.6, 2.8], [0.42, 2.78],
  [0.3, 2.8], [0.2, 2.86], [0.1, 2.94], [0.0, 2.96],
];

function buildKing(material: THREE.Material): THREE.Group {
  const g = new THREE.Group();
  const body = new THREE.Mesh(lathe(KING), material);
  g.add(body);
  // The cross: two softly bevelled bars.
  const bar = (w: number, h: number, d: number) => {
    const shape = new THREE.Shape();
    const r = Math.min(w, h) * 0.18;
    shape.moveTo(-w / 2 + r, -h / 2);
    shape.lineTo(w / 2 - r, -h / 2);
    shape.quadraticCurveTo(w / 2, -h / 2, w / 2, -h / 2 + r);
    shape.lineTo(w / 2, h / 2 - r);
    shape.quadraticCurveTo(w / 2, h / 2, w / 2 - r, h / 2);
    shape.lineTo(-w / 2 + r, h / 2);
    shape.quadraticCurveTo(-w / 2, h / 2, -w / 2, h / 2 - r);
    shape.lineTo(-w / 2, -h / 2 + r);
    shape.quadraticCurveTo(-w / 2, -h / 2, -w / 2 + r, -h / 2);
    const geo = new THREE.ExtrudeGeometry(shape, { depth: d, bevelEnabled: true, bevelSize: 0.02, bevelThickness: 0.02, bevelSegments: 4 });
    geo.translate(0, 0, -d / 2);
    return new THREE.Mesh(geo, material);
  };
  const vertical = bar(0.17, 0.62, 0.15);
  vertical.position.y = 3.48;
  const horizontal = bar(0.5, 0.15, 0.15);
  horizontal.position.y = 3.56;
  g.add(vertical, horizontal);
  g.traverse((o) => {
    if (o instanceof THREE.Mesh) {
      o.castShadow = true;
      o.receiveShadow = true;
    }
  });
  return g;
}

function buildQueen(material: THREE.Material): THREE.Group {
  const g = new THREE.Group();
  g.add(new THREE.Mesh(lathe(QUEEN), material));
  // Coronet: nine prongs leaning outwards around the rim, each with a bead on its tip.
  const prongs = 8;
  const prongGeo = new THREE.ConeGeometry(0.085, 0.3, 32);
  const beadGeo = new THREE.SphereGeometry(0.07, 32, 16);
  for (let i = 0; i < prongs; i++) {
    const a = (i / prongs) * Math.PI * 2;
    const holder = new THREE.Group();
    holder.position.set(Math.cos(a) * 0.56, 2.82, Math.sin(a) * 0.56);
    holder.rotation.y = -a;
    const lean = new THREE.Group();
    lean.rotation.z = -0.22; // tip slightly outwards
    const prong = new THREE.Mesh(prongGeo, material);
    prong.position.y = 0.13;
    const bead = new THREE.Mesh(beadGeo, material);
    bead.position.y = 0.29;
    lean.add(prong, bead);
    holder.add(lean);
    g.add(holder);
  }
  // Finial: a ball on top of the inner dome.
  const finial = new THREE.Mesh(new THREE.SphereGeometry(0.13, 48, 24), material);
  finial.position.y = 3.06;
  g.add(finial);
  g.traverse((o) => {
    if (o instanceof THREE.Mesh) {
      o.castShadow = true;
      o.receiveShadow = true;
    }
  });
  return g;
}

/** An 8x8 board texture drawn on a canvas (dark walnut and pale maple tones). */
function boardTexture(): THREE.CanvasTexture {
  const c = document.createElement('canvas');
  c.width = c.height = 1024;
  const ctx = c.getContext('2d')!;
  const s = c.width / 8;
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 8; x++) {
      ctx.fillStyle = (x + y) % 2 ? '#0e0b0a' : '#6f6861';
      ctx.fillRect(x * s, y * s, s, s);
    }
  }
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  return tex;
}

export interface HeroSceneOptions {
  reducedMotion: boolean;
}

/** Builds the scene into a canvas; returns a function that stops and frees everything. */
export function createHeroScene(canvas: HTMLCanvasElement, opts: HeroSceneOptions): () => void {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;

  const scene = new THREE.Scene();
  scene.fog = new THREE.Fog(0x050505, 10, 18); // the board dissolves into the page background

  // Studio reflections: a soft room environment gives glossy surfaces something to reflect.
  const pmrem = new THREE.PMREMGenerator(renderer);
  const envTex = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
  scene.environment = envTex;
  scene.environmentIntensity = 0.7;

  const camera = new THREE.PerspectiveCamera(26, 1, 0.1, 60);
  const lookAt = new THREE.Vector3(0.35, 1.95, 0);

  // ---------------------------------------------------------------- materials
  const lacquer = new THREE.MeshPhysicalMaterial({
    color: 0x17100d, // near-black with a warm brown undertone, like ebonised boxwood
    roughness: 0.22,
    metalness: 0.0,
    clearcoat: 1.0,
    clearcoatRoughness: 0.08,
    sheen: 0.4,
    sheenColor: new THREE.Color(0x8a2a12),
  });
  const porcelain = new THREE.MeshPhysicalMaterial({
    color: 0xf2eee8,
    roughness: 0.18,
    metalness: 0.0,
    clearcoat: 1.0,
    clearcoatRoughness: 0.05,
    ior: 1.5,
    specularIntensity: 0.9,
  });

  // ---------------------------------------------------------------- pieces
  const king = buildKing(lacquer);
  king.position.set(-0.45, 0, -0.2);
  king.rotation.y = 0.4;
  scene.add(king);

  // The queen falls to the right, crossing in front of the king, her base resting on the board.
  const queen = buildQueen(porcelain);
  const queenPivot = new THREE.Group();
  const tilt = 0.82;
  // A fallen piece rests on the rim of its base: lift the pivot by sin(tilt) x base radius so
  // the lowest edge touches the board instead of sinking through it.
  queenPivot.position.set(0.05, Math.sin(tilt) * 1.02 + 0.02, 0.55);
  queenPivot.rotation.set(0.12, -0.35, -tilt);
  queen.position.set(0, 0, 0);
  queenPivot.add(queen);
  scene.add(queenPivot);

  // ---------------------------------------------------------------- board
  const board = new THREE.Mesh(
    new THREE.PlaneGeometry(16, 16),
    new THREE.MeshPhysicalMaterial({ map: boardTexture(), roughness: 0.32, clearcoat: 0.8, clearcoatRoughness: 0.15 }),
  );
  board.material.map!.repeat.set(2, 2);
  board.material.map!.wrapS = board.material.map!.wrapT = THREE.RepeatWrapping;
  board.rotation.x = -Math.PI / 2;
  board.receiveShadow = true;
  scene.add(board);

  // ---------------------------------------------------------------- lights
  scene.add(new THREE.AmbientLight(0xffffff, 0.06));

  // Key: soft white from the front-right, casting the main shadow.
  const key = new THREE.SpotLight(0xfff4ea, 75, 30, Math.PI / 7, 0.85, 1.6);
  key.position.set(4.5, 7.5, 6);
  key.target.position.copy(lookAt);
  key.castShadow = true;
  key.shadow.mapSize.set(2048, 2048);
  key.shadow.bias = -0.0002;
  key.shadow.radius = 6;
  scene.add(key, key.target);

  // Ember rim: strong red-orange from behind and left, outlining both silhouettes.
  const rim = new THREE.SpotLight(0xff3010, 520, 25, Math.PI / 5, 0.6, 1.4);
  rim.position.set(-2.5, 4.2, -5.5);
  rim.target.position.set(0.2, 1.6, 0);
  scene.add(rim, rim.target);

  // A second, warmer kick from the right-back to catch the queen's crown.
  const kick = new THREE.PointLight(0xff4a1a, 40, 12, 1.8);
  kick.position.set(2.8, 3.2, -2.2);
  scene.add(kick);

  // Red edge light at the king's height, behind-right: paints the ember line down his side.
  const edge = new THREE.SpotLight(0xff2a0c, 160, 14, Math.PI / 9, 0.5, 1.5);
  edge.position.set(1.8, 2.4, -3.2);
  edge.target.position.set(-0.45, 1.8, -0.2);
  scene.add(edge, edge.target);

  // Cool fill from the left front so the king's shadow side isn't pure black.
  const fill = new THREE.PointLight(0xbfd4ff, 6, 14, 2);
  fill.position.set(-4, 2.5, 4);
  scene.add(fill);

  // ---------------------------------------------------------------- sizing + loop
  const resize = () => {
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    if (w === 0 || h === 0) return;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    // Keep the pieces framed on tall (phone) screens too.
    camera.fov = w / h < 0.8 ? 38 : 26;
    camera.updateProjectionMatrix();
  };
  const ro = new ResizeObserver(resize);
  ro.observe(canvas);
  resize();

  // Gentle parallax: the camera drifts slowly and follows the pointer a little.
  let pointerX = 0;
  let pointerY = 0;
  const onPointer = (e: PointerEvent) => {
    pointerX = (e.clientX / window.innerWidth) * 2 - 1;
    pointerY = (e.clientY / window.innerHeight) * 2 - 1;
  };
  window.addEventListener('pointermove', onPointer, { passive: true });

  let visible = true;
  const io = new IntersectionObserver(([entry]) => (visible = entry.isIntersecting));
  io.observe(canvas);

  const clock = new THREE.Clock();
  let frame = 0;
  let camX = 0;
  let camY = 0;
  const render = () => {
    frame = requestAnimationFrame(render);
    if (!visible) return;
    const t = clock.getElapsedTime();
    const drift = opts.reducedMotion ? 0 : Math.sin(t * 0.25) * 0.25;
    camX += ((opts.reducedMotion ? 0 : pointerX * 0.6) + drift - camX) * 0.04;
    camY += ((opts.reducedMotion ? 0 : -pointerY * 0.25) - camY) * 0.04;
    camera.position.set(1.6 + camX, 2.7 + camY, 12.5);
    camera.lookAt(lookAt);
    rim.intensity = 520 + (opts.reducedMotion ? 0 : Math.sin(t * 0.8) * 60); // the glow breathes
    renderer.render(scene, camera);
  };
  render();

  return () => {
    cancelAnimationFrame(frame);
    ro.disconnect();
    io.disconnect();
    window.removeEventListener('pointermove', onPointer);
    scene.traverse((o) => {
      if (o instanceof THREE.Mesh) {
        o.geometry.dispose();
        const m = o.material as THREE.Material & { map?: THREE.Texture | null };
        m.map?.dispose();
        m.dispose();
      }
    });
    envTex.dispose();
    pmrem.dispose();
    renderer.dispose();
  };
}
