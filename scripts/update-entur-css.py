#!/usr/bin/env python3
"""
Vendors Entur Linje CSS into src/main/resources/app/.

The UI apps have no build step, so the published @entur/* CSS cannot be consumed
through a bundler. Two things have to be fixed up for a browser to use it:

  1. Bare specifiers. @entur/tokens' base.css and semantic.css pull their
     dependencies in with `@import '@entur/tokens/dist/semantic.css'`, which only a
     bundler can resolve. The files are inlined in dependency order instead.

  2. No mode-aware fallback. Linje switches palettes on a `data-color-mode`
     attribute, so with no attribute set a page is always light. Every
     [data-color-mode='dark'] block is mirrored into a prefers-color-scheme query,
     guarded by :not([data-color-mode='light']) so an explicit host theme still
     wins. Dark mode then works with JavaScript disabled.

Component packages additionally ship a full resolved copy of the base tokens
(that is why Linje mandates a strict import order). Those copies are stripped so
entur-tokens.css stays the single source of truth for the base layer.

Outputs:
    app/entur-tokens.css   design tokens, needed by every app
    app/entur-travel.css   TravelHeader / TravelTag / LegBone, used by trip + departures

Usage:
    scripts/update-entur-css.py                    # regenerate everything
    scripts/update-entur-css.py --only tokens      # just one output
    scripts/update-entur-css.py --tokens 4.0.2     # bump a pinned version
"""

import argparse
import os
import re
import sys
import urllib.request

APP_DIR = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir,
                 "src", "main", "resources", "app")
)

# Pinned deliberately: these are component *implementation* details, not a public
# API, so an unreviewed upgrade can change the markup contract underneath us.
TOKENS_VERSION = "4.0.1"
TRAVEL_VERSION = "8.0.2"

# @entur/tokens, in dependency order.
TOKEN_FILES = ["primitive", "semantic", "transport", "base", "data", "styles"]

# Feedback surfaces and text that Linje only ships inside component packages
# (@entur/alert's --components-alert-alertbox-*). The apps need them without taking
# on that package, so they are rebuilt here from the primitive scale, which is where
# @entur/alert's own values come from. The --app- prefix marks them as ours.
APP_FEEDBACK = """
/* ---- feedback surfaces and text (generated, see script) ---- */
[data-color-mode='light'],
:root {
  --app-feedback-negative-fill: var(--coral-20);
  --app-feedback-warning-fill: var(--canary-20);
  --app-feedback-success-fill: var(--mint-20);
  --app-feedback-negative-text: var(--colors-validation-lava);
  --app-feedback-success-text: var(--colors-validation-mint);
  --app-feedback-warning-text: var(--canary-90);
}

[data-color-mode='dark'] {
  --app-feedback-negative-fill: var(--coral-100);
  --app-feedback-warning-fill: var(--canary-100);
  --app-feedback-success-fill: var(--mint-100);
  --app-feedback-negative-text: var(--colors-validation-lava-contrast);
  --app-feedback-success-text: var(--colors-validation-mint-contrast);
  --app-feedback-warning-text: var(--colors-validation-canary-contrast);
}
"""

APP_FEEDBACK_NEEDS = [
    "--coral-20", "--coral-100", "--canary-20", "--canary-100", "--canary-90",
    "--mint-20", "--mint-100",
    "--colors-validation-lava", "--colors-validation-lava-contrast",
    "--colors-validation-mint", "--colors-validation-mint-contrast",
    "--colors-validation-canary-contrast",
]

COLOR_SCHEME = """
/* Keep native UI (scrollbars, form controls) in step with the resolved mode. */
:root { color-scheme: light dark; }
:root[data-color-mode='light'] { color-scheme: light; }
:root[data-color-mode='dark'] { color-scheme: dark; }
"""


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as resp:
        return resp.read().decode("utf-8")


def declared(css):
    """Custom properties this CSS defines."""
    return set(re.findall(r"(--[a-zA-Z0-9-]+)\s*:", css))


def referenced(css):
    return set(re.findall(r"var\((--[a-zA-Z0-9-]+)", css))


def mirror_dark_blocks(css, what):
    """
    Copy every [data-color-mode='dark'] block into a prefers-color-scheme query.

    The :not([data-color-mode='light']) guard also raises specificity above the
    plain :root rules, so the media block wins regardless of source order.
    """
    blocks = re.findall(r"\[data-color-mode=['\"]?dark['\"]?\]\s*\{(.*?)\n\}", css, re.S)
    if not blocks:
        sys.exit(f"{what}: no [data-color-mode='dark'] blocks found - layout changed?")
    body = "".join(
        "  :root:not([data-color-mode='light']) {%s}\n" % b.rstrip() for b in blocks
    )
    return (
        "\n/* ---- prefers-color-scheme fallback (generated) ---- */\n"
        "@media (prefers-color-scheme: dark) {\n" + body + "}\n"
    )


def build_tokens(version):
    parts = []
    for name in TOKEN_FILES:
        css = fetch(f"https://unpkg.com/@entur/tokens@{version}/dist/{name}.css")
        css = re.sub(r"^\s*@import\s+[^;]+;\s*$", "", css, flags=re.M)
        parts.append(f"/* ---- @entur/tokens/dist/{name}.css ---- */\n{css.strip()}\n")
    body = "\n".join(parts)

    for token in APP_FEEDBACK_NEEDS:
        if not re.search(r"^\s*%s\s*:" % token, body, re.M):
            sys.exit(f"{token} missing - upstream palette changed, --app-feedback-* would dangle")
    body += APP_FEEDBACK

    header = f"""/*
 * Entur Linje design tokens - VENDORED, DO NOT EDIT BY HAND.
 *
 * Generated from @entur/tokens@{version} by scripts/update-entur-css.py
 * Source: https://linje.entur.no/tokens
 *
 * Colour mode: tokens default to light. An ancestor (normally <html>) carrying
 * data-color-mode="dark" switches to the dark palette; the prefers-color-scheme
 * block at the end applies the same palette when no attribute is set, so dark
 * mode degrades gracefully with JavaScript disabled.
 */
"""
    return header + "\n" + body + mirror_dark_blocks(body, "tokens") + COLOR_SCHEME


def build_component(pkg, version, base_tokens, description):
    css = fetch(f"https://unpkg.com/@entur/{pkg}@{version}/dist/styles.css")
    have = declared(base_tokens)

    # Drop the package's copy of the base tokens; keep --components-* and any local
    # custom property the component sets on itself (--background-color and friends).
    def keep(match):
        name = match.group(1)
        if name.startswith("--components-") or name not in have:
            return match.group(0)
        return ""

    stripped = re.sub(r"^\s*(--[a-zA-Z0-9-]+)\s*:[^;]+;\s*$", keep, css, flags=re.M)
    stripped = re.sub(r"[^{}]+\{\s*\}", "", stripped)   # rules emptied by the above
    stripped = re.sub(r"\n\s*\n+", "\n", stripped).strip()

    missing = referenced(stripped) - declared(stripped) - have
    header = f"""/*
 * Entur Linje {description} - VENDORED, DO NOT EDIT BY HAND.
 *
 * Generated from @entur/{pkg}@{version} by scripts/update-entur-css.py
 * Source: https://linje.entur.no/komponenter
 *
 * Load AFTER entur-tokens.css. The package ships its own resolved copy of the base
 * tokens; that copy is stripped here so entur-tokens.css stays the single source of
 * truth, leaving the --components-* layer and the component rules.
 *
 * These are .eds-* class names. Unlike the tokens they are an implementation detail
 * rather than a public API, so the version above is pinned and an upgrade needs the
 * rendered output checked (scripts/preview-apps.py).
"""
    if missing:
        header += (
            " *\n * Unresolved by design - these belong to component packages this project does\n"
            " * not vendor, and are only referenced by variants the apps do not use:\n"
            + "".join(f" *   {m}\n" for m in sorted(missing))
        )
    header += " */\n"
    return header + "\n" + stripped + "\n" + mirror_dark_blocks(stripped, pkg)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--tokens", default=TOKENS_VERSION, help="@entur/tokens version")
    ap.add_argument("--travel", default=TRAVEL_VERSION, help="@entur/travel version")
    ap.add_argument("--only", choices=["tokens", "travel"], help="regenerate one output")
    opts = ap.parse_args()

    print(f"Fetching @entur/tokens@{opts.tokens} ...")
    tokens = build_tokens(opts.tokens)
    if opts.only in (None, "tokens"):
        path = os.path.join(APP_DIR, "entur-tokens.css")
        open(path, "w").write(tokens)
        print(f"  wrote {path} ({len(tokens)} bytes)")

    if opts.only in (None, "travel"):
        print(f"Fetching @entur/travel@{opts.travel} ...")
        travel = build_component("travel", opts.travel, tokens,
                                 "travel components (TravelHeader, TravelTag, LegBone)")
        path = os.path.join(APP_DIR, "entur-travel.css")
        open(path, "w").write(travel)
        print(f"  wrote {path} ({len(travel)} bytes)")


if __name__ == "__main__":
    main()
