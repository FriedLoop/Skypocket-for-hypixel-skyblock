/**
 * Downloads the Lucide SVGs this icon set uses, into tools/lucide-svg/.
 *
 * Why a script instead of committing the files
 * --------------------------------------------
 * The SVG files are small and the build does not read them - only render-icons.mjs does, at
 * development time, to regenerate the atlas. Keeping the fetch step explicit means the version is
 * recorded in one place (LUCIDE_VERSION below) rather than implied by whatever was last downloaded,
 * and it makes the licence position unambiguous: these are unmodified files from one tagged release.
 *
 * The files are committed anyway, so a normal build never needs the network. This script exists for
 * updating the set or auditing where it came from.
 *
 * Source and licence
 * ------------------
 * https://unpkg.com/lucide-static@<version>/icons/<name>.svg
 * Lucide is ISC licensed; each downloaded file carries its own @license comment. See
 * ICON-LICENSES.md next to this script.
 *
 * jsDelivr and raw.githubusercontent.com were both unreachable from this machine (connection
 * reset); unpkg worked. If unpkg ever fails too, the npm package can be installed directly:
 *   npm pack lucide-static@<version>  &&  tar -xzf lucide-static-*.tgz
 */

import { mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = join(here, 'lucide-svg');

/** Pinned. Bump this and re-run both scripts to update the icon set. */
const LUCIDE_VERSION = '1.81.0';

const NAMES = [
  'settings', 'pickaxe', 'swords', 'fish', 'radar',
  'box', 'bell', 'move', 'monitor', 'layers',
];

async function main() {
  mkdirSync(OUT_DIR, { recursive: true });
  let failed = 0;

  for (const name of NAMES) {
    const url = `https://unpkg.com/lucide-static@${LUCIDE_VERSION}/icons/${name}.svg`;
    const dest = join(OUT_DIR, `${name}.svg`);

    if (existsSync(dest)) {
      console.log(`  ${name.padEnd(10)} already present`);
      continue;
    }

    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(30_000) });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const body = await res.text();
      if (!body.includes('<svg')) {
        throw new Error('response is not an SVG');
      }
      writeFileSync(dest, body, 'utf8');
      console.log(`  ${name.padEnd(10)} ${body.length}B`);
    } catch (e) {
      failed++;
      console.error(`  ${name.padEnd(10)} FAILED: ${e.message}`);
      console.error(`             ${url}`);
    }
  }

  if (failed) {
    throw new Error(`${failed} of ${NAMES.length} icons could not be fetched.`);
  }
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
