# Third-party icon licences

## Lucide

The ClickGUI's icon set is drawn from [Lucide](https://lucide.dev), used under the ISC licence.

- **Version:** 1.81.0 (`lucide-static`)
- **Files:** `tools/lucide-svg/*.svg` — unmodified copies, fetched by `tools/fetch-lucide.mjs`
- **Used for:** the footer settings button, and the sidebar category icons
- **Modification:** the renderer (`tools/render-icons.mjs`) recolours the stroke from
  `currentColor` to white so it can be tinted at draw time. The stroke width is left at Lucide's
  own 2, and the shapes are untouched. The recolouring is applied at render time; the SVG files in
  this repository are the originals.

Each downloaded SVG carries its own licence comment,
`<!-- @license lucide-static v1.81.0 - ISC -->`, at the top of the file.

### The licence text

```
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

### A note on the derived files

A subset of Lucide's icons are themselves derived from [Feather](https://feathericons.com) and are
MIT licensed rather than ISC. Lucide's licence page lists them individually. None of the icons used
here appear in that subset: `settings`, `pickaxe`, `swords`, `fish`, `radar`, `box`, `bell`, `move`
and `monitor` are all original Lucide work.

## What is not third-party

Everything else in `assets/skypocket/textures/gui/sprites/ui/icons.png` is a raster of the SVGs
above — there is no hand-drawn artwork in it.
