---
version: alpha
name: Bachata S4
description: Dark, controller-first console shell for an Android emulator front-end. Spotlighted, precise, a little theatrical — built for the 10-foot living-room experience and navigated entirely with a gamepad.

colors:
  primary: "#F5F4F1"
  secondary: "#8B8D94"
  tertiary: "#E8A33D"
  on-tertiary: "#1A1206"
  neutral: "#0A0A0C"
  surface: "#1C1C20"
  surface-raised: "#26262B"
  success: "#3FB871"
  warning: "#E8C547"
  error: "#E4423F"

typography:
  headline-display:
    fontFamily: Manrope
    fontSize: 64px
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: Manrope
    fontSize: 40px
    fontWeight: 700
    lineHeight: 1.1
  headline-md:
    fontFamily: Manrope
    fontSize: 28px
    fontWeight: 600
    lineHeight: 1.2
  headline-sm:
    fontFamily: Manrope
    fontSize: 22px
    fontWeight: 600
    lineHeight: 1.25
  body-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: 500
    lineHeight: 1.4
  body-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: 400
    lineHeight: 1.5
  label-caps:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: 0.08em
  data-mono:
    fontFamily: JetBrains Mono
    fontSize: 18px
    fontWeight: 500
    lineHeight: 1.3

rounded:
  none: 0px
  sm: 6px
  md: 10px
  lg: 16px
  xl: 24px
  full: 9999px

spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  xxxl: 64px
  gutter: 32px
  safe-margin: 64px

components:
  nav-rail:
    backgroundColor: "{colors.neutral}"
    width: 96px
  nav-rail-item-focused:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.tertiary}"
    rounded: "{rounded.md}"
  game-card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.lg}"
    width: 220px
    height: 293px
  game-card-focused:
    backgroundColor: "{colors.surface-raised}"
    rounded: "{rounded.lg}"
  hero-banner:
    backgroundColor: "{colors.neutral}"
    textColor: "{colors.primary}"
    height: 45%
  button-primary:
    backgroundColor: "{colors.tertiary}"
    textColor: "{colors.on-tertiary}"
    typography: "{typography.label-caps}"
    rounded: "{rounded.md}"
    padding: 16px
  button-primary-focused:
    backgroundColor: "{colors.tertiary}"
    textColor: "{colors.on-tertiary}"
    rounded: "{rounded.md}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    rounded: "{rounded.md}"
    padding: 16px
  settings-row:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.md}"
    padding: 20px
  settings-row-focused:
    backgroundColor: "{colors.surface-raised}"
    rounded: "{rounded.md}"
  toggle-on:
    backgroundColor: "{colors.tertiary}"
  toggle-off:
    backgroundColor: "{colors.secondary}"
  slider-fill:
    backgroundColor: "{colors.tertiary}"
  slider-track:
    backgroundColor: "{colors.surface-raised}"
  modal:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.lg}"
    padding: 32px
  tab-active:
    textColor: "{colors.tertiary}"
    typography: "{typography.label-caps}"
  tab-inactive:
    textColor: "{colors.secondary}"
    typography: "{typography.label-caps}"
  action-bar:
    backgroundColor: "{colors.neutral}"
    textColor: "{colors.secondary}"
    typography: "{typography.label-caps}"
  focus-ring:
    borderColor: "{colors.tertiary}"
---

# Bachata S4 — DESIGN.md

## Overview

Bachata S4 borrows its personality from the ballroom floor — spotlighted, precise, a little theatrical — and applies it to a jet-black, controller-first console shell. The logo's grayscale silhouette dancers and scattered chrome "X" marks set the visual key: near-black canvas, sculptural white/gray forms, and a single warm light source cutting through the dark, like a stage spot finding its dancers.

The product is a games front-end, used primarily on a TV or tablet from a couch, gamepad in hand — not a phone held at arm's length. Every screen is designed first for **10-foot viewing and D-pad/analog-stick navigation**; touch is a fully supported secondary input, never the only path to an action.

Personality: confident, uncluttered, a little dramatic in its lighting, never cluttered or "gamer-neon." Audience: emulation and retro-console enthusiasts who already know console UI conventions (shelves of cover art, a persistent side rail, a bottom action legend) and expect Bachata S4 to feel as considered as the hardware it's replacing.

## Colors

The palette is almost entirely drawn from the app icon: void black, chalk white, and graphite grays. A single warm accent — Stage Amber — stands in for the spotlight, and is the *only* color used to mean "this is interactive, this is focused, this is the one thing to look at."

- **Primary (#F5F4F1) — "Spotlight White":** Headlines, primary text and icons sitting on the dark canvas. Never used as a background.
- **Secondary (#8B8D94) — "Ash Gray":** Supporting text, captions, metadata, hairline borders, unfocused icon fills. The quiet, resting state of the UI.
- **Tertiary (#E8A33D) — "Stage Amber":** The sole accent and the sole driver of interactivity — focus rings, the primary CTA, progress fill, selected tab, active toggle. If it's amber, it's the one thing the controller is pointed at.
- **Neutral (#0A0A0C) — "Void Black":** App canvas and background, edge to edge, matching the logo's backdrop.
- **Surface (#1C1C20) — "Charcoal Panel":** Resting background for cards, rows, and panels — one step up from Void Black so content reads as grouped, not floating.
- **Surface Raised (#26262B):** The panel color once an item is focused or pressed, used together with the amber ring, never alone.
- **Success (#3FB871):** Controller connected, save-state written, download complete.
- **Warning (#E8C547):** Low battery, unsaved changes, compatibility caution.
- **Error (#E4423F):** Failed load, incompatible file, corrupted save.

## Typography

Two families split the same job the reference spec describes: a characterful display face for personality, a workhorse face for reading, plus a monospace face reserved for machine-generated numbers — the console's own instrumentation.

- **Manrope (headlines):** Geometric and confident at large sizes. Used for the hero game title, screen titles, and section headers — the handful of moments per screen that should feel designed rather than typed.
- **Inter (body & UI):** Chosen specifically for legibility at a distance — open counters and a tall x-height hold up at 10-foot viewing better than a condensed or grotesk face would. Carries every label, row, and description.
- **JetBrains Mono (data):** Reserved for numbers that come from the system, not from a writer — playtime, resolution, frame rate, save-slot timestamps. Its fixed width keeps columns of stats aligned without extra layout work.

Because this is a 10-foot UI, the whole scale runs larger than a typical phone app — nothing on screen should require leaning forward.

| Token | Face | Size | Weight | Use |
|---|---|---|---|---|
| `headline-display` | Manrope | 64px | 700 | Hero game title on Library backdrop |
| `headline-lg` | Manrope | 40px | 700 | Screen titles ("Game Library", "Settings") |
| `headline-md` | Manrope | 28px | 600 | Section headers ("Continue Playing") |
| `headline-sm` | Manrope | 22px | 600 | Card and modal titles |
| `body-lg` | Inter | 24px | 500 | Focused row/list primary text |
| `body-md` | Inter | 20px | 400 | Descriptions, settings help text |
| `body-sm` | Inter | 17px | 400 | Secondary text, metadata |
| `label-caps` | Inter | 16px | 600 | Nav labels, tabs, buttons, controller hints — always uppercase, tracked |
| `data-mono` | JetBrains Mono | 18px | 500 | Playtime, resolution, FPS, timestamps |

## Layout

The grid is built around one persistent structural element — a left navigation rail — and everything else living in a full-bleed content well to its right. Content is organized into horizontally-scrolling shelves (à la a console dashboard), which map naturally onto D-pad up/down (shelf-to-shelf) and left/right (item-to-item within a shelf).

Because this app is frequently viewed through a TV's overscan, every persistent chrome element — the nav rail, the bottom action bar, screen titles — respects a **safe margin** and is never placed flush against the physical screen edge.

- Base spacing unit: 8px, on an 8px grid throughout.
- `safe-margin` (64px) is the minimum inset from any physical screen edge for persistent chrome.
- `gutter` (32px) separates shelves, columns, and major content blocks.
- Card grids use a fixed column width with `gutter` spacing rather than a fluid percentage grid, so focus can move predictably between neighbors — predictable spatial navigation matters more than filling every pixel of a large TV panel.

## Elevation & Depth

Depth is tonal, not shadow-based — a heavy drop shadow barely reads against a near-black canvas. Hierarchy comes from three flat layers (`neutral` → `surface` → `surface-raised`) plus one deliberate glow effect reserved for focus:

- Resting content sits on `surface` panels, one step brighter than the `neutral` canvas behind them.
- The focused element steps up again to `surface-raised` and gains a soft outer glow in `tertiary` amber (12–16px blur, low spread) — this glow is the app's signature "spotlight" moment and is used *only* for focus, never for generic emphasis.
- Hero art areas use a bottom-anchored gradient (`neutral` at 0% opacity fading to 100%) purely for text legibility over cover art, not as a depth cue.

## Shapes

Corners are moderately rounded — soft enough to feel contemporary, restrained enough to keep the geometric, engineered read of the logomark's numeral "4."

- Game cards and hero art: `rounded.lg` (16px).
- Buttons, rows, toggles, chips: `rounded.md` (10px).
- Pills, tags, the controller-hint action bar segments: `rounded.full`.
- The focus ring always matches the radius of the element it wraps, offset 2px outward — it should read as a halo, not a second box.

## Components

- **Nav Rail:** Persistent 96px icon rail (Home, Library, Downloads, Settings, Profile). Collapsed by default; the focused item expands to show its `label-caps` text and takes the `nav-rail-item-focused` styling. Always reachable with one Left press from anywhere in the main content well.
- **Game Card:** 3:4 box-art tile. Resting state at 85% brightness with no border. Focused state: full brightness, `surface-raised` backdrop, amber ring, 6% scale-up, title revealed in `label-caps` beneath the art.
- **Hero Banner:** Top ~45% of the Library screen — blurred/duotoned key art of whichever card currently has focus, with `headline-display` title, a `data-mono` meta row (last played, playtime, compatibility), and a primary CTA.
- **Button (Primary / Secondary):** Primary uses the `tertiary` fill with `on-tertiary` text for maximum contrast — reserved for the single most important action on screen. Secondary uses a `surface` fill with `primary` text for everything else.
- **Settings Row:** One focusable unit combining a `body-lg` label, a `body-sm` description, and a control (toggle, slider, or stepper) right-aligned. Sliders and steppers respond directly to Left/Right without needing a separate "enter" step, keeping adjustment depth shallow.
- **Toggle / Slider:** Track in `surface-raised`, fill/thumb in `tertiary` when active. Sliders always show their current value in `data-mono` while focused.
- **Modal:** `surface` panel, centered, dimmed `neutral` scrim behind it at 80% opacity. Focus is trapped inside until dismissed; destructive actions default focus to the safe/cancel option.
- **Tab Bar:** Horizontal `label-caps` tabs (used inside Settings' category switcher); active tab gets a `tertiary` underline and text color, inactive tabs use `secondary`.
- **Action Bar:** A slim bar pinned to the bottom safe-margin, always visible, listing the 2–4 controller actions available on the current screen (e.g. "Confirm · Launch  Context · Options  Back · Exit"). This is the player's constant reminder of what the gamepad currently does.
- **Toast:** Reserved for non-critical, ambient confirmations (controller connected, save-state written). Auto-dismisses; never used for anything the player must act on — critical information always uses a Modal instead, since a toast can be missed from couch distance.
- **On-Screen Keyboard:** A full-screen, grid-navigable keyboard (not a phone-style thumb keyboard) for search, renaming save states, and network credentials.

## Do's and Don'ts

- Do give every interactive element a focused state that is reachable and operable by D-pad/stick alone, before any touch handling is added.
- Do land on one sensible default focus point when a screen opens (e.g., the last-played title in Library), and restore the player's prior focus position when they navigate back to a screen.
- Do keep all persistent chrome inside the 64px TV-safe margin.
- Do maintain WCAG AA contrast (4.5:1 body text, 3:1 large text/icons) against both `neutral` and `surface` backgrounds.
- Do pair the amber focus indicator with a scale and glow change, not color alone, so focus is legible without relying on color perception.
- Don't design any affordance that depends on hover — there is no hover state on a console; there is only focused and not-focused.
- Don't require a touch-only gesture (swipe-to-dismiss, pinch, long-press) as the *sole* way to trigger an action — always give it a mapped controller equivalent.
- Don't nest focus more than two levels deep (rail → shelf → card) without an explicit, testable spatial map; avoid focus traps and orphaned unreachable elements.
- Don't mix more than two type weights on a single screen.
- Don't reproduce another platform's system typography, button iconography, or chrome — Bachata S4's visual identity is its own, derived from its own mark, and should read as compatible with generic Bluetooth/USB gamepads rather than tied to one controller brand.

## Navigation & Focus System

This is the section that makes Bachata S4 a console app rather than a touch app with a gamepad bolted on. Every screen is authored as an explicit spatial focus graph — up/down/left/right neighbors are defined per element, not inferred at runtime.

**Input roles** (map to standard Android Gamepad API buttons; label these generically in-app rather than after one specific controller brand, since players will pair many kinds of Bluetooth gamepads):

| Role | Typical input | Behavior |
|---|---|---|
| Navigate | D-Pad / Left Stick | Move focus between elements |
| Confirm | Face button (Android `BUTTON_A`) | Activate the focused element |
| Back | Face button (Android `BUTTON_B`) | Cancel / dismiss / go up one level |
| Context action | Face button (Android `BUTTON_X`) | Secondary action on the focused item (favorite, delete save) |
| Item menu | Face button (Android `BUTTON_Y`) | Open quick options for the focused item |
| Switch section | Shoulder buttons (L1/R1) | Change tab / jump between shelves |
| Fast scroll | Triggers (L2/R2) | Jump 10 items at a time in long lists |
| System menu | Start/Options | Open the Quick Menu overlay from anywhere, including mid-game |
| Search | Select/Touchpad (if present) | Open the on-screen keyboard |

**Rules of the graph:**

1. Focus never disappears — if content is removed or a list is filtered, focus lands on the nearest remaining neighbor, never on nothing.
2. Every branch has a way back — Back always resolves to a predictable parent, and modals trap focus until explicitly dismissed.
3. Focus state is visual, not just logical — the amber ring/glow from **Elevation & Depth** must render every time focus changes; a focus change with no visible feedback is treated as a bug.
4. Touch and pointer input remain first-class fallbacks (e.g., for setup, or players without a paired controller yet), but the focus graph must be fully navigable with zero touches.

## Screens

### Game Library

The default landing screen. Nav rail on the left; hero banner up top showing whichever card currently has focus; below it, vertically-stacked horizontal shelves — *Continue Playing*, *All Games*, *Favorites*, *Recently Added*. Up/Down moves between shelves, Left/Right moves within one. The action bar at the bottom always reads "Confirm · Launch   Context · Favorite   Menu · Info   Back · Exit." An empty library states its case plainly — "No games yet. Confirm to add your first one." — with focus already on the add action.

### Settings

A two-pane master-detail layout. The left pane is a vertical `label-caps` category list (General, Controllers, Graphics, Audio, Storage, Network, About); the right pane holds that category's settings rows. Left/Right switches between the category list and the rows pane; Up/Down moves within whichever pane has focus. Sliders and steppers respond to Left/Right directly on the row, without an extra "enter the control" step, so adjusting a value never takes more than one extra press. Destructive settings ("Clear all save data," "Reset controller bindings") always open a confirmation Modal with focus defaulting to Cancel.

### Quick Menu (overlay)

Summoned by Start/Options from any screen, including mid-emulation. A `surface` panel slides in from the right over a dimmed, blurred backdrop of whatever was behind it — Resume, Save State, Load State, Reconnect Controller, Exit to Library. Designed to be reachable and dismissible in two button presses or fewer.

## Iconography

Icons are single-weight line glyphs, 2px stroke, rounded joins, drawn on a 24/32px grid — geometric enough to sit comfortably next to Manrope's headlines. Controller-hint glyphs in the action bar use simple, generic shapes and letters (a filled dot, a ring, plain "A"/"B" style labels) rather than any specific manufacturer's registered button symbols, so the hint bar reads correctly regardless of which gamepad a player has paired.

## Agent Prompt Guide

Guidance for any coding agent implementing this system:

1. Build focus as an explicit, declarative graph (Compose `FocusRequester` chain, or Android View `nextFocusUpId`/`DownId`/`LeftId`/`RightId`) — never rely on default tab order for a grid of cards.
2. Wire Confirm/Back globally and consistently: `DPAD_CENTER`/`BUTTON_A`/Enter → confirm; `BUTTON_B`/Escape → back. Don't reinterpret these per screen.
3. A component isn't complete until it ships a focused visual state (`surface-raised` + amber ring/glow per **Elevation & Depth**) — treat an unfocused-only component as unfinished, not as a later pass.
4. If targeting Android TV in addition to handheld/tablet, follow Leanback-style TV focus conventions and test with a physical Bluetooth gamepad, not just simulated key events.
5. Always reference tokens from this file's front matter (`{colors.*}`, `{typography.*}`, `{spacing.*}`, `{rounded.*}`) — no hardcoded hex values or raw pixel sizes in component code.
6. Keep touch targets ≥ 48dp everywhere, even though the controller is the primary input — the touch fallback must stay fully usable on its own.
