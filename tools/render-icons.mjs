/**
 * Renders the UI icon set into one texture atlas.
 *
 * Why this exists
 * ---------------
 * The UI needs small icons: 24px in the sidebar, 24px in the footer. Drawing them with the
 * painter's primitives does not work - GuiPixels offers rounded rectangles and straight lines
 * only, pushes a device space of its own so a pose rotation never reaches it, and has no polygon
 * or stroke path. That was tried and rejected; at 24px the result was a smudge.
 *
 * Minecraft only samples PNG textures, so the SVG sources have to be rasterised ahead of time.
 * This script does that and packs the results into an atlas.
 *
 * Why resvg and not the Python options
 * ------------------------------------
 * cairosvg and svglib both failed on this machine: they render through Cairo and there is no
 * libcairo-2.dll on Windows, which is a native dependency that pip cannot supply. resvg ships as a
 * prebuilt binary, needs no system libraries, and produces the same output on every machine - so
 * the atlas is reproducible rather than depending on whatever the developer happens to have.
 *
 * Stroke width
 * ------------
 * Left at Lucide's own 2. Thicker strokes were rendered and compared at the real size - 3 does make
 * the more detailed glyphs (pickaxe, fish) read better at 24px, but it also makes every icon heavier
 * than Lucide intends, and the lighter weight is what the icons were designed around. The tradeoff
 * was judged worse than the pixelation it fixes.
 *
 * Usage
 * -----
 *   node tools/render-icons.mjs
 *
 * Requires `tools/lucide-svg/` (the Lucide sources, ISC licensed - see ICON-LICENSES.md) and the
 * `@resvg/resvg-js` package. Run `npm install` in tools/ first.
 */

import { Resvg } from '@resvg/resvg-js';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, '..');

/** Pixels per cell in the atlas. Large, so the runtime reduction does the antialiasing. */
const CELL = 128;
/** Columns in the atlas. IconAtlas.java derives the row from the enum ordinal, so keep the order. */
const COLUMNS = 6;
/** Stroke width in Lucide's 24-unit space. Lucide's own value; kept as-is. */
const STROKE = 2;

/**
 * Icons in atlas order. The order must match the Icon enum in IconAtlas.java: the runtime looks a
 * cell up by ordinal, so inserting here without updating there shifts every icon.
 */
const ICONS = [
  { name: 'GEAR', file: 'settings' },
  { name: 'PICKAXE', file: 'pickaxe' },
  { name: 'SWORDS', file: 'swords' },
  { name: 'FISH', file: 'fish' },
  { name: 'RADAR', file: 'radar' },
  { name: 'WIRE_CUBE', file: 'box' },
  { name: 'BELL', file: 'bell' },
  { name: 'ARROWS', file: 'move' },
  { name: 'MONITOR', file: 'monitor' },
  { name: 'LAYERS', file: 'layers' },
];

const OUT = join(repo, 'versions', '26.2-fabric', 'src', 'main', 'resources',
  'assets', 'skypocket', 'textures', 'gui', 'sprites', 'ui', 'icons.png');

/** Recolours a Lucide SVG to solid white, at the stroke width this icon set uses. */
function prepare(svg) {
  return svg
    // Lucide inherits currentColor from CSS; a texture cannot, so it is baked in as white and
    // tinted at draw time by IconAtlas.
    .replace(/stroke="currentColor"/g, 'stroke="#FFFFFF"')
    // The width Lucide designed for is 2; this set uses 3. See the header.
    .replace(/stroke-width="2"/g, `stroke-width="${STROKE}"`);
}

function render(svg, size) {
  const r = new Resvg(svg, {
    fitTo: { mode: 'width', value: size },
    background: 'rgba(0,0,0,0)',
  });
  return r.render();
}

/** Stable across machines: sorts by name, not by filesystem order. */
async function main() {
  const sharp = await import('sharp').catch(() => null);
  if (!sharp) {
    throw new Error(
      'This script needs `sharp` to composite the cells into one PNG.\n' +
      'Run: cd tools && npm install');
  }

  const rows = Math.ceil(ICONS.length / COLUMNS);
  const composites = [];

  ICONS.forEach((icon, i) => {
    const file = join(here, 'lucide-svg', `${icon.file}.svg`);
    if (!existsSync(file)) {
      throw new Error(`Missing SVG: ${file}\nRun tools/fetch-lucide.mjs first.`);
    }
    const svg = prepare(readFileSync(file, 'utf8'));
    const png = render(svg, CELL).asPng();
    composites.push({
      input: png,
      left: (i % COLUMNS) * CELL,
      top: Math.floor(i / COLUMNS) * CELL,
    });
    console.log(`  ${icon.name.padEnd(10)} <- ${icon.file}.svg`);
  });

  const atlas = sharp.default({
    create: {
      width: COLUMNS * CELL,
      height: rows * CELL,
      channels: 4,
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    },
  });
  const out = await atlas.composite(composites).png().toBuffer();

  mkdirSync(dirname(OUT), { recursive: true });
  writeFileSync(OUT, out);
  console.log(`\nwrote ${OUT} (${COLUMNS * CELL}x${rows * CELL}, ${ICONS.length} icons)`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
