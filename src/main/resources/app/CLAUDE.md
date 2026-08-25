# UI apps (`src/main/resources/app/`)

Conventions for the MCP App HTML files and the vendored Entur CSS in this directory.

- **Design tokens:** the apps style against Entur Linje tokens (https://linje.entur.no). Only
  `@entur/tokens` is vendored — plain CSS custom properties, framework-agnostic. The React
  `@entur/*` component packages are deliberately NOT used; there is no build step to consume them.
- `@entur/travel` is also vendored, giving `trip` and `departures` the real TravelHeader,
  TravelTag and LegBone rather than hand-rolled lookalikes. Its `.eds-*` class names are an
  implementation detail, not a public API, so the version is pinned and an upgrade needs the
  rendered output checked. Colour is passed through the components' own `--background-color` /
  `--text-color` custom properties, which is the stable half of the contract.
- `entur-tokens.css` and `entur-travel.css` are **generated — do not hand-edit**. Regenerate with
  `scripts/update-entur-css.py`, which inlines the packages' bare-specifier `@import`s (browsers
  cannot resolve them), derives a `prefers-color-scheme` fallback, strips each component package's
  duplicated copy of the base tokens, and adds an `--app-feedback-*` layer for the mode-aware
  surfaces Linje only ships in `@entur/alert`.
- Stylesheets are inlined by `AppHtmlLoader` at markers in each app's `<head>`:
  `<!--ENTUR_TOKENS-->` in all four, `<!--ENTUR_TRAVEL-->` in `trip-map` and `departures-board`.
  A relative `<link>` cannot be used: the host renders the app in a sandboxed iframe with no origin
  to resolve against.
- `scripts/preview-apps.py` renders the apps in a normal browser (light and dark) using real tool
  results pulled over `/mcp`, which is the only practical way to eyeball UI changes.
- **Colour mode:** apps set `data-color-mode` on `<html>` from `app.getHostContext()?.theme` and keep
  it current via `app.onhostcontextchanged`. With no attribute, the generated `prefers-color-scheme`
  block decides, so theming works before the host reports anything and without JS.
- Token rules worth knowing, all enforced by `EnturCssTest`:
  - Only `--basecolors-*` are mode-aware. `--colors-validation-*` and its `-tint`/`-contrast`
    variants are fixed light-mode values — use `--app-feedback-*-fill` / `--app-feedback-*-text`.
  - Transport fills are saturated in light mode and pastel in dark, so labels on them use
    `--basecolors-frame-default` (inverts with the surface), never `--basecolors-text-light`.
  - Leaflet writes colours into SVG presentation attributes, which do not accept `var()`. Vector
    layers resolve tokens through the local `cssVar()` helper and re-render on colour-mode change.
  - TravelTag fills are named after OTP's own mode strings
    (`--components-travel-traveltag-standard-fill-{bus,rail,water,air,…}`), so most of the mode
    mapping is a pass-through; `coach`→`bus`, `subway`→`metro`, `foot`→`walk`.
