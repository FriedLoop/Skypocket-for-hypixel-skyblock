/**
 * Renders the UI icon sets into texture atlases.
 *
 * Why this exists
 * ---------------
 * Minecraft only samples PNG textures, so the SVG sources have to be rasterised ahead of time. This
 * script does that and packs the results into atlases.
 *
 * Why not draw the icons with the painter's primitives
 * ----------------------------------------------------
 * GuiPixels offers rounded rectangles and straight lines only, pushes a device space of its own so a
 * pose rotation never reaches it, and has no polygon or stroke path. That was tried for the footer
 * gear and produced a smudge at 24px.
 *
 * Why resvg and not the Python options
 * ------------------------------------
 * cairosvg and svglib both render through Cairo, and Windows has no libcairo-2.dll - a native
 * dependency pip cannot supply. resvg ships a prebuilt binary, needs no system libraries, and
 * produces the same output on every machine, so the atlases are reproducible rather than dependent
 * on whatever the developer happens to have installed.
 *
 * Two atlases, two sizes
 * ----------------------
 * They are separate files on purpose. The small one serves 24px (footer button, sidebar rows) and
 * the large one serves the 62px module cards. Sharing one file would force the large set to swallow
 * the small set's cell budget, and the two have different needs: a 24px glyph wants margin around it,
 * a 62px card icon can be drawn closer to the edge.
 *
 *   icons.png        128px cells, 6 columns, ordered by IconAtlas.Icon's enum constants
 *   icons-large.png  192px cells, 6 columns, ordered by MODULE_ICONS below
 *
 * 192 for the large set rather than 256: the cards draw at 62px, so 192 is already a 3x reduction.
 * At 256 the atlas would be ~1.8x the memory for no visible gain.
 *
 * Stroke width
 * ------------
 * Left at Lucide's own 2. Thicker strokes were rendered and compared at the real size - 3 does make
 * the more detailed glyphs read better at 24px, but it makes every icon heavier than Lucide intends.
 * The small set is the one that suffered, so its appearance is Lucide's own.
 *
 * Usage
 * -----
 *   node tools/render-icons.mjs
 *
 * Requires tools/lucide-svg/ (fetched by fetch-lucide.mjs) and the npm packages in tools/.
 */

import { Resvg } from '@resvg/resvg-js';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, '..');
const SVG_DIR = join(here, 'lucide-svg');
const TEX_DIR = join(repo, 'versions', '26.2-fabric', 'src', 'main', 'resources',
  'assets', 'skypocket', 'textures', 'gui', 'sprites', 'ui');

/** Stroke width in Lucide's 24-unit space. Lucide's own value; kept as-is. */
const STROKE = 2;

/**
 * Transparent margin around each cell's icon, in pixels.
 *
 * EXPERIMENT: the first attempt at small cells produced icons with transparent pinholes at fixed
 * positions - the icons were drawn from empty pixels in the atlas. The cell was the size of the icon
 * itself, leaving no margin, so any sub-pixel error in the sampled u/v landed in a neighbouring cell
 * or in its blank edge. Padding gives that error somewhere harmless to land.
 *
 * This is a hypothesis under test, not a settled decision.
 */
const PADDING = 4;

/**
 * Gaussian blur applied to each cell before it goes into the atlas, in pixels of the cell.
 *
 * The panel draws these icons smaller than their cells with nearest-neighbour sampling, which turns
 * resvg's antialiased edges back into hard ones - the icons read as staircases while the text, drawn
 * through a linear sampler, did not. The linear-sampler blit overload was tried and had no visible
 * effect on this pipeline, so the softening is baked into the texture instead: with a 1px blur the
 * proportion of partial-alpha edge pixels doubles when sampled down to the display size.
 *
 * 1.0 is the smallest radius that measurably helps; beyond it the icons start to look out of focus
 * rather than smooth.
 */
const BLUR = 1.0;

/**
 * The atlas dimensions the Java side has baked in, as "WxH".
 *
 * Duplicated on purpose, and asserted below. The alternative - deriving the height at runtime from
 * the texture - avoids the duplication but hides the coupling: a set that grows a row silently
 * invalidates every v coordinate, and the only symptom is icons drawn squashed or from the wrong
 * cell. Failing the render is louder and happens once, at the change.
 */
const SMALL_EXPECTED = '480x160';
const LARGE_EXPECTED = '1552x970';

/**
 * The small set: the footer settings button and the sidebar category rows.
 *
 * Order must match the Icon enum in IconAtlas.java - the runtime looks a cell up by ordinal, so
 * inserting here without updating there shifts every icon.
 */
const SMALL = {
  // The cell size is the size the icon is *displayed* at, not a larger source. See renderCell():
  // the icon is rasterised at OVERSAMPLE times this and scaled down in software, because the game
  // blits with nearest-neighbour - a 128px cell drawn at 24px threw away every antialiased pixel
  // and produced the hard edges this set used to have.
  // 72 = 24 x 3, for the same reason as the large set - see the note there.
  cell: 72,
  oversample: 4,
  columns: 6,
  out: join(TEX_DIR, 'icons.png'),
  icons: [
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
  ],
};

/**
 * The large set: one icon per module card, keyed by the module's id.
 *
 * This list is the icon mapping. The order matters only in that it must match
 * ModuleIcons.java's table, which is generated from it - see the comment there.
 *
 * Semantic notes, because these are judgement calls and the next person will wonder:
 *   - The three glow modules share `sparkles`. They are one mechanism (rarity/entity outline) and
 *     three different glyphs implied three different features.
 *   - The two sticker-arrow modules share a downward arrow for the same reason - the difference is
 *     "landed in the ground" vs "stuck in a body", which no icon distinguishes.
 *   - damage_splash_compact is `list-chevrons-down-up`: the point of the feature is merging many
 *     splashes into fewer, and a compress metaphor says that in a way a droplet did not.
 */
const LARGE = {
  // 186 = 62 x 3, an integer multiple of the size the icon is drawn at.
  //
  // The cell cannot be the draw size: the game samples with nearest-neighbour and the panel scales,
  // so a cell that is only slightly larger than the target (74 for 62, a 1.19 ratio) makes some
  // screen pixels skip a source pixel and others sample one twice. On a 1-2px stroke that reads as
  // single-pixel pinholes and bumps at fixed positions - which is exactly what a 74px cell produced.
  // A large integer reduction averages the error away instead, at the cost of the hard edges this
  // was originally meant to fix; that trade is settled elsewhere.
  cell: 186,
  oversample: 4,
  // 8 columns -> 64 cells. The set has 35 icons; the spare room is deliberate, because a grid atlas
  // wastes whatever does not fit the grid and growing 36 -> 64 costs 4MB anyway. Going past 64 means
  // either a second atlas or a packing algorithm, which is a different order of complexity - see the
  // capacity check in build().
  columns: 8,
  out: join(TEX_DIR, 'icons-large.png'),
  icons: [
    { name: 'auto_sprint', file: 'footprints' },
    { name: 'block_outline', file: 'box-select' },
    { name: 'chat_compact', file: 'message-square' },
    { name: 'clock', file: 'clock' },
    { name: 'commissions', file: 'clipboard-list' },
    { name: 'crosshair', file: 'crosshair' },
    { name: 'damage_splash_compact', file: 'list-chevrons-down-up' },
    { name: 'day_count', file: 'calendar-days' },
    { name: 'dragon_glow', file: 'sparkles' },
    { name: 'drill_fuel_bar', file: 'fuel' },
    { name: 'fishing_hotspot_overlay', file: 'fish' },
    { name: 'fps_tps_ping', file: 'activity' },
    { name: 'hand_item', file: 'hand' },
    { name: 'held_item_name', file: 'tag' },
    { name: 'hide_landed_arrows', file: 'arrow-down-to-line' },
    { name: 'hide_other_players_presents', file: 'gift' },
    { name: 'hide_players', file: 'user-x' },
    { name: 'hide_players_near_npcs', file: 'bot' },
    { name: 'hide_spawn_point_players', file: 'map-pin-off' },
    { name: 'hide_stuck_arrows', file: 'arrow-down' },
    { name: 'hitbox', file: 'box' },
    { name: 'item_glow', file: 'sparkles' },
    { name: 'item_pickup_log', file: 'inbox' },
    { name: 'legendary_sea_creature_warning', file: 'fish-symbol' },
    { name: 'mention_alert', file: 'at-sign' },
    { name: 'mining_ability_reminder', file: 'gem' },
    { name: 'mining_break_predict', file: 'timer' },
    { name: 'no_hurt_camera', file: 'camera-off' },
    { name: 'player_glow', file: 'sparkles' },
    { name: 'post_process', file: 'sliders-horizontal' },
    { name: 'scrollable_tooltips', file: 'mouse-pointer-2' },
    { name: 'skill_display', file: 'graduation-cap' },
    { name: 'stats_display', file: 'gauge' },
    { name: 'third_person', file: 'eye' },
    { name: 'thunder_bottle', file: 'flask-conical' },
  ],
};

/** Recolours a Lucide SVG to solid white at the stroke width this set uses. */
function prepare(svg) {
  return svg
    // Lucide inherits currentColor from CSS; a texture cannot, so it is baked in as white and
    // tinted at draw time.
    .replace(/stroke="currentColor"/g, 'stroke="#FFFFFF"')
    .replace(/stroke-width="2"/g, `stroke-width="${STROKE}"`);
}

/**
 * Rasterises one cell: oversample, then reduce in software.
 *
 * <p>The second half is the point. Minecraft blits GUI textures with nearest-neighbour sampling, so
 * a cell larger than the size it is drawn at loses all of its antialiasing - an atlas rendered at
 * 192px and drawn at 62px measured 3% partially-transparent pixels, which is why the icons looked
 * like staircases while the text, drawn through a linear-filtered sampler, did not.
 *
 * <p>Doing the reduction here means the cell arrives at the exact size it is used at, with 28-55%
 * of its edge pixels in between. resvg still rasterises at 4x to give the downscale something to
 * work with; reducing from a bare target-size render would just move the same problem.
 */
async function renderCell(sharp, svg, target, oversample) {
  const big = new Resvg(svg, {
    fitTo: { mode: 'width', value: target * oversample },
    background: 'rgba(0,0,0,0)',
  }).render().asPng();
  const resized = sharp(big).resize(target, target, { kernel: 'lanczos3' });
  // Blur after resizing, not before: blurring first and then reducing would average the blur away.
  return BLUR > 0
    ? resized.blur(BLUR).png().toBuffer()
    : resized.png().toBuffer();
}

async function build(sharp, set, label) {
  const { cell, columns, icons, out } = set;

  // Grid capacity is columns^2. Refusing to render is better than silently truncating: a missing
  // icon falls back to a letter tile at runtime, so an over-capacity set would ship as "some cards
  // lost their icons" with nothing pointing at the cause.
  const capacity = columns * columns;
  if (icons.length > capacity) {
    throw new Error(
      `${label}: ${icons.length} icons do not fit a ${columns}x${columns} grid (${capacity} cells).\n` +
      'Raise `columns`, or split the set into a second atlas.');
  }

  // Rows are computed from the set, so the atlas is exactly as tall as it needs to be. The catch is
  // that the height then depends on the icon count, and the Java side needs it to compute v - so
  // each set declares the dimensions it expects back in AtlasSize.java, and this script fails if they
  // ever disagree. That is why the check below exists rather than a comment asking people to
  // remember: growing a set past its current row count changes every UV, and the failure at runtime
  // is "icons squashed", which does not point here.
  // The cell is the icon plus its margin; the icon is drawn at `cell` and centred inside.
  const paddedCell = cell + PADDING * 2;
  const rows = Math.ceil(icons.length / columns);
  const composites = [];
  const missing = [];

  for (let i = 0; i < icons.length; i++) {
    const icon = icons[i];
    const file = join(SVG_DIR, `${icon.file}.svg`);
    if (!existsSync(file)) {
      missing.push(`${icon.name} (${icon.file}.svg)`);
      continue;
    }
    const svg = prepare(readFileSync(file, 'utf8'));
    composites.push({
      input: await renderCell(sharp, svg, cell, set.oversample),
      left: (i % columns) * paddedCell + PADDING,
      top: Math.floor(i / columns) * paddedCell + PADDING,
    });
  }

  if (missing.length) {
    throw new Error(
      `${label}: missing SVG sources:\n  ${missing.join('\n  ')}\n` +
      'Run tools/fetch-lucide.mjs to download them.');
  }

  const base = sharp({
    create: {
      width: columns * paddedCell,
      height: rows * paddedCell,
      channels: 4,
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    },
  });
  const png = await base.composite(composites).png().toBuffer();

  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, png);

  const expected = label === 'icons.png' ? SMALL_EXPECTED : LARGE_EXPECTED;
  const actual = `${columns * paddedCell}x${rows * paddedCell}`;
  const sizeNote = actual === expected ? '' : `  <-- EXPECTED ${expected}`;
  console.log(`  ${label.padEnd(12)} ${icons.length} icons -> ${actual}${sizeNote}`);
  if (actual !== expected) {
    throw new Error(
      `${label} is ${actual}, but AtlasSize.java declares ${expected}.\n` +
      'Update the constants in both AtlasSize.java (CELL/COLUMNS/ROWS) and here.');
  }
}

/**
 * Writes the id -> cell map as Java, so the lookup table has exactly one source.
 *
 * The alternative - a hardcoded array in Java - drifts the moment an icon is added or removed here,
 * and the failure is silent: the card shows the wrong glyph rather than nothing. Generating the file
 * makes that impossible.
 *
 * The table is a plain String[] indexed by cell, and the runtime builds a reverse map from it. Written
 * this way rather than as a switch so the file stays readable and diffs cleanly.
 */
function writeJavaTable(largeSet) {
  const { icons } = largeSet;
  const cells = icons.length;

  const lines = [];
  lines.push('package dev.skypocket.gui;');
  lines.push('');
  lines.push('// Generated by tools/render-icons.mjs. Do not edit by hand.');
  lines.push('//');
  lines.push('// One entry per cell of icons-large.png, in atlas order. ModuleIcons builds a');
  lines.push('// reverse map from it, so adding an icon means editing the renderer and re-running');
  lines.push('// it - never editing this file.');
  lines.push('final class ModuleIconIds {');
  lines.push('    private ModuleIconIds() {');
  lines.push('    }');
  lines.push('');
  lines.push('    /** Module id for each atlas cell, in cell order. */');
  lines.push('    static final String[] CELLS = {');
  for (let i = 0; i < cells; i++) {
    const id = icons[i].name;
    lines.push(`            "${id}",`.padEnd(56) + `// ${i}`);
  }
  lines.push('    };');
  lines.push('');
  lines.push('    /** Cells in the atlas; larger than CELLS.length, which only covers what is used. */');
  lines.push('    static final int CAPACITY = ' + largeSet.columns * largeSet.columns + ';');
  lines.push('}');
  lines.push('');

  const out = join(repo, 'versions', '26.2-fabric', 'src', 'main', 'java',
    'dev', 'skypocket', 'gui', 'ModuleIconIds.java');
  writeFileSync(out, lines.join('\n'), 'utf8');
  console.log(`  ModuleIconIds.java  ${cells} entries -> ${largeSet.columns * largeSet.columns} cells`);
}

async function main() {
  const sharpMod = await import('sharp').catch(() => null);
  if (!sharpMod) {
    throw new Error('This script needs `sharp`.\nRun: cd tools && npm install');
  }
  const sharp = sharpMod.default;
  await build(sharp, SMALL, 'icons.png');
  await build(sharp, LARGE, 'icons-large.png');
  writeJavaTable(LARGE);
  console.log(`\nwrote atlases to ${TEX_DIR}`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
