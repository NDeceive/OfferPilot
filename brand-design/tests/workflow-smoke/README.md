# Environment smoke test

This neutral geometry sample is not an OfferPilot logo concept.

Test sequence:

1. Render `source-shapes.svg` to PNG.
2. Vectorize the PNG with the local `image-to-svg` toolchain.
3. Reject any SVG containing embedded raster data.
4. Render the traced SVG for visual inspection.
5. Run icon and font metadata searches and retain license information.

Generated test artifacts are kept here as environment evidence.

## Result — 2026-09-28

- Source rendered successfully to `source-shapes.png`.
- Raster vectorized successfully to `traced-shapes.svg`.
- Inspection: 1 path, 9 path commands, valid viewBox, 0 embedded images, 0 data URIs, 0 scripts, 0 foreign objects, and 0 filters.
- The rendered trace preserves the circle and chevron silhouette, but the chevron corners became slightly rounded. Result: toolchain pass, final-craft gate correctly requires manual geometry refinement.
- Iconify search returned scoped results with collection-level license metadata.
- Fontsource search returned Latin and Chinese-capable candidates with family metadata; family-specific license verification remains mandatory before adoption.
