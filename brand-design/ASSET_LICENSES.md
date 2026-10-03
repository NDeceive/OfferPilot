# Asset license ledger

No new font, icon, illustration, or logo-reference asset has been downloaded into this workspace during environment setup.

| Asset or source | Intended use | License | Commercial use | Modification | Redistribution / attribution | Trademark risk | Status |
|---|---|---|---|---|---|---|---|
| Existing OfferPilot logo files in `前端/assets` and `前端/public` | Incumbent product identity | Project provenance not documented here | Verify before external redistribution | Internal editable SVG exists | Verify ownership/source record | Product mark | Existing; not modified |
| `simple-icons` npm dependency | Job/company icons already used by UI | CC0-1.0 for project data | Generally permitted under copyright license | Permitted by CC0 | Brand guidelines may still apply | High: names/logos are trademarks | Existing dependency; UI use only |
| Iconify search API | Metadata-first icon discovery | Iconify software mostly MIT; each set has its own license | Depends on collection | Depends on collection | Check collection metadata | Do not convert UI icons into product logo | Approved discovery source |
| Lucide | UI icon candidate | ISC | Yes | Yes | Preserve notices as required | Low for generic UI icons | Not downloaded |
| Tabler Icons | UI icon candidate | MIT | Yes | Yes | Preserve license notice | Low for generic UI icons | Not downloaded |
| Phosphor Icons | UI icon candidate | MIT | Yes | Yes | Preserve license notice | Low for generic UI icons | Not downloaded |
| Iconoir | UI icon candidate | MIT | Yes | Yes | Preserve license notice | Low for generic UI icons | Not downloaded |
| IconPark Outline | UI icon candidate | Apache-2.0 | Yes | Yes | Preserve license/notice | Low for generic UI icons | Not downloaded |
| Remix Icon | UI icon candidate | Apache-2.0 | Yes | Yes | Preserve license/notice | Low for generic UI icons | Not downloaded |
| Heroicons | UI icon candidate | MIT | Yes | Yes | Preserve license notice | Low for generic UI icons | Not downloaded |
| Fontsource | Font metadata and package discovery | Platform code MIT; font licenses vary | Family-specific | Family-specific | Bundle family license | Font name/license risk varies | Approved discovery source |
| gilbarbara/logos | Logo structure reference | CC0-1.0 repository | Copyright license permits use | Copyright license permits modification | Trademark rights remain | High | Reference only; not downloaded |
| Simple Icons dataset | Logo structure reference | CC0-1.0 data | Copyright license permits use | Copyright license permits modification | Trademark rights remain | High | Reference only |
| Open Doodles | Future illustration candidate | CC0 | Yes | Yes | No attribution required by CC0 | Low | Not downloaded |
| Humaaans | Future illustration candidate | Site states CC0 | Yes | Yes | Preserve evidence of terms | Low | Not downloaded |

## Adoption rule

- 2026-10-01: `前端/src/assets/generated/interview-coach.png` — OpenAI built-in image generation, generated for this project from a text prompt; transparent 1024×1536 PNG. No external reference portrait or stock asset used. Original: `C:/Users/31318/.codex/generated_images/01a0f66e-f8ba-74a3-a09e-2bc8994fb0bc/exec-b9d68f71-1148-4ae6-8041-addaa83d2526.png`. Generated asset, not a third-party licensed stock photo.

Before an asset is added, replace “Not downloaded” with the exact file/package, version, creator, source URL, license URL or included license file, and the date checked. This ledger is operational documentation, not legal advice.

## 2026-10-03 — Stage 02 local logo reference research

Scope: local annotated comparison board only; none of these marks is adopted into OfferPilot identity, product UI, or marketing. Display dataset geometry is not a claim of latest official artwork or permission for commercial trademark use. Creators/rightsholders of brand marks remain the respective brands; reference data is maintained by the named library contributors.

| Local files under `references/stage-02/assets/` | Exact source | Copyright/license evidence | Modification | Commercial / redistribution status | Trademark and provenance |
|---|---|---|---|---|---|
| `claude.svg`, `deepseek.svg`, `tiktok.svg`, `nike.svg`, `fedex.svg`, `duolingo.svg` and inspection PNGs | Existing `simple-icons` 16.32.0 package; [versioned repository](https://github.com/simple-icons/simple-icons/tree/16.32.0/icons), per-icon sources preserved in `reference-notes.json` and `source-manifest.json` | Package CC0-1.0 copied as `simple-icons-LICENSE.md`; disclaimer copied as `simple-icons-DISCLAIMER.md`. Six icon metadata entries contain no individual license field; do not infer absence means free brand use. | Source SVG geometry unchanged; local PNG rendering and HTML embedding. Monochrome dataset variants, not approved brand one-color specifications. | Not cleared for commercial branding or public redistribution; reference-only | High brand trademark relevance; FedEx outline Ex and Duolingo container are dataset versions, explicitly labeled |
| `openai-icon.svg`, `tiktok-original.svg` and OpenAI inspection PNG | [OpenAI reference SVG](https://raw.githubusercontent.com/gilbarbara/logos/main/logos/openai-icon.svg), [TikTok reference SVG](https://raw.githubusercontent.com/gilbarbara/logos/main/logos/tiktok-icon.svg); gilbarbara/logos contributors | [Repository CC0 text](https://raw.githubusercontent.com/gilbarbara/logos/main/LICENSE.txt), copied as `logos-license.txt`; [OpenAI guidelines](https://openai.com/brand/), [TikTok guidelines](https://developers.tiktok.com/docs/en/getting-started-design-guidelines) remain separate | SVG source unchanged; local PNG rendering and embedded reference display | Not cleared for commercial branding or public redistribution; reference-only | Brand rights remain; OpenAI asset is repository snapshot, not guaranteed latest official mark |
| `offerpilot-current.svg`, embedded pure-black diagnostic comparison | Project `前端/src/components/ui/LogoIcon.vue`; project-owned incumbent source | Existing project provenance applies | Extraction replaces Vue size bindings with 40 px; diagnostic display additionally replaces gradient strokes and removes opacity; source component unchanged | Internal design evaluation only | User confirms green line mark is current; blue-purple mark excluded |

Checked 2026-10-03. SHA-256 hashes of reference SVGs and source metadata are preserved in `references/stage-02/assets/source-manifest.json`. Official pages are linked for verification; research observations are distinct from official explanations and user preferences.

## 2026-10-03 — Stage 03 original route schematics

- Files: `explorations/stage-03/assets/A1.svg` through `E2.svg` (10 diagrams), embedded in `creative-routes.html`; geometry recorded in `routes.json`.
- Creator/provenance: original path geometry authored by Codex for this project from the agreed design brief. No third-party brand paths, icon geometry, fonts, or raster reference artwork reused.
- Purpose: concept-route evaluation only. These are schematic SVG drafts, not approved final brand assets. Editable paths, no embedded raster, gradients, or effects.
- Rights/license: no external asset license introduced. Brand originality and trademark availability remain subject to further review; original authorship alone is not a trademark clearance claim.
- Incumbent comparison: monochrome diagnostic rendering from project `LogoIcon.vue`; same project provenance and extraction conditions as Stage 02. Product source unchanged.

## 2026-10-03 — User-directed portal / m cabin refinement

- `explorations/stage-03/portal-refinement/portal-01A-narrow-v3.png`: built-in imagegen edit of user-selected leftmost 01A in v2, requesting a modest overall width reduction while preserving the structure and weight. Original output: `C:/Users/31318/.codex/generated_images/01a10206-856c-7cd2-bc8c-50c419cd5ca4/exec-77201062-dff0-43fb-9f84-57333d746f0c.png`. Exact prompt saved as `portal-01A-narrow-v3-prompt.txt`. Raster proportion draft pending review; no production assets replaced.

- `explorations/stage-03/portal-refinement/user-reference.png`: copied from the user-supplied historical logo `1659d83130b43cff8b1a94eb321e8c6b.png`. Reference supplied for this redesign; source ownership/history not independently verified. No external stock/reference brand logo used.
- `explorations/stage-03/portal-refinement/portal-lightweight-v1.png`: generated using built-in imagegen, editing the supplied reference into a single three-variant monochrome comparison board. Original output: `C:/Users/31318/.codex/generated_images/01a10206-856c-7cd2-bc8c-50c419cd5ca4/exec-cd864c53-875d-47b1-bbdb-8fa884f4f831.png`.
- Status: raster concept exploration only, not final SVG, not selected production identity, not trademark clearance. Prompt retained alongside output; no product assets replaced.

- `explorations/stage-03/portal-refinement/portal-01-refinement-v2.png`: built-in imagegen edit using v1 comparison board, explicitly locking the user-selected leftmost 01 structure. One board with 01A control and 01B–01D lighter frame/doors/negative-space studies. Original: `C:/Users/31318/.codex/generated_images/01a10206-856c-7cd2-bc8c-50c419cd5ca4/exec-c0b4d4f6-b23d-49cc-bb78-fb750d8bb709.png`. Raster draft; no third-party source introduced. Exact prompt preserved as `portal-01-refinement-v2-prompt.txt`.

## 2026-10-03 — Stage 04 selected 01A vector reconstruction

## 2026-10-04 — Stage 05 wordmark pairing drafts

- Folder: explorations/stage-05-wordmark. Reuses project-authored Stage 04 symbol paths. Nine editable-text SVG combination drafts and a rendered comparison board.
- Fonts: local Microsoft YaHei, Segoe UI, DengXian, Bahnschrift, SimHei, Arial. No font binaries copied or redistributed. Typeface rights and final production use have not been verified; these are pairing previews, not licensed final wordmark deliverables. Outlining alone does not establish usage rights.
- Rendering: existing @resvg/resvg-js. No external artwork introduced. Product assets unchanged. Selection and limitations in REVIEW.md.

- Folder: explorations/stage-04-01A. Original manual three-path reconstruction based on user-selected portal-01A-narrow-v3.png; proportion variants and micro-size compensation authored for this task. No third-party mark paths reused.
- SVGs contain editable geometry with no embedded raster or external references; audit in svg-audit.json. PNG previews rendered with existing @resvg/resvg-js (MPL-2.0). Review board uses local system font; no font file redistributed.
- Status: review candidates, not approved production replacements or trademark clearance. Findings and limitations recorded in REVIEW.md.


## 2026-10-04 — Selected A bold revision
- explorations/stage-05-wordmark/A-bold-v2: user-selected A pairing; Segoe UI main/sub wordmarks changed to weight 700. Original symbol and Chinese pairing reused. Editable-text SVG and locally rendered PNG, no redistributed fonts. Same preliminary font-rights status as stage five. Product assets unchanged.


## 2026-10-04 — 01A / A Bold delivery candidate
- finals/01A-A-bold: project-authored symbol and selected A Bold combinations, outlined with existing @resvg/resvg-js. Windows-supplied Microsoft YaHei and Segoe UI rendered as fixed logo words; no font binaries distributed. Microsoft font FAQ permits logo/graphic creation subject to software-use conditions: https://learn.microsoft.com/en-us/typography/fonts/font-faq (checked 2026-10-04). No general font embedding/self-hosting right claimed.
- 16 production SVG candidates are path-only. Pixel comparison maximum: 4 differing RGBA channels per image (rounding), not substantial redesign. Draft source text SVGs retained separately. Review board labels remain text. No product replacement or trademark clearance.


## 2026-10-04 — Stage 07 product integration
- Ten path-only assets from finals/01A-A-bold copied into frontend src/assets/brand; public/favicon.svg uses micro-green and assets/logo.svg uses symbol-green. No new third-party visual or font asset. Shared logo components consume these resources.
- Local source integration authorized by stage-seven request. Layout-only browser checks and limitations are recorded in tests/stage-07/ACCEPTANCE.md. No deployment or trademark clearance claimed.


## 2026-10-04 — Stage 08 versioned release candidate
- finals/v1.0.0-rc1 and offerpilot-brand-v1.0.0-rc1.zip reuse Stage 06 SVG/PNG assets, preserve editable text workfiles, and add Stage 07 evidence plus brand usage documentation. No new visual/font source introduced. Production SVGs are path-only; no font binaries shipped. 12 integrated resource copies match SHA-256. Final visual confirmation pending; no publication or trademark clearance.


- 2026-10-04: `前端/src/assets/generated/teacher-task-icons.png` — OpenAI built-in image generation; transparent member, expression and job-training symbols approved in the teacher task visual review. Copied from `designs/teacher-task-visual-review/assets/task-icons-v2.png`; no third-party stock artwork.

## 2026-10-04 — Approved final v1.0.0
- User explicitly confirmed final design. finals/v1.0.0 and offerpilot-brand-v1.0.0.zip are current approved assets: 17 SVG, 45 PNG. Existing provenance/usage conditions unchanged; no new third-party assets. 13 local product resource copies match. No deployment or trademark clearance claimed.

