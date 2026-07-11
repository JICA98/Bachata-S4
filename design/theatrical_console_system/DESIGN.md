---
name: Theatrical Console System
colors:
  surface: '#1C1C20'
  surface-dim: '#131315'
  surface-bright: '#39393b'
  surface-container-lowest: '#0e0e10'
  surface-container-low: '#1c1b1d'
  surface-container: '#201f21'
  surface-container-high: '#2a2a2c'
  surface-container-highest: '#353437'
  on-surface: '#e5e1e4'
  on-surface-variant: '#c5c7c2'
  inverse-surface: '#e5e1e4'
  inverse-on-surface: '#313032'
  outline: '#8f918d'
  outline-variant: '#444844'
  surface-tint: '#c7c6c4'
  primary: '#ffffff'
  on-primary: '#2f312f'
  primary-container: '#e3e2df'
  on-primary-container: '#646562'
  inverse-primary: '#5e5f5c'
  secondary: '#c5c6ce'
  on-secondary: '#2e3036'
  secondary-container: '#44474d'
  on-secondary-container: '#b3b5bc'
  tertiary: '#ffffff'
  on-tertiary: '#1A1206'
  tertiary-container: '#ffddb5'
  on-tertiary-container: '#8b5a00'
  error: '#E4423F'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e3e2df'
  primary-fixed-dim: '#c7c6c4'
  on-primary-fixed: '#1b1c1a'
  on-primary-fixed-variant: '#464745'
  secondary-fixed: '#e1e2ea'
  secondary-fixed-dim: '#c5c6ce'
  on-secondary-fixed: '#191c21'
  on-secondary-fixed-variant: '#44474d'
  tertiary-fixed: '#ffddb5'
  tertiary-fixed-dim: '#ffb956'
  on-tertiary-fixed: '#2a1800'
  on-tertiary-fixed-variant: '#643f00'
  background: '#131315'
  on-background: '#e5e1e4'
  surface-variant: '#353437'
  surface-raised: '#26262B'
  success: '#3FB871'
  warning: '#E8C547'
typography:
  headline-display:
    fontFamily: Manrope
    fontSize: 64px
    fontWeight: '700'
    lineHeight: '1.05'
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: Manrope
    fontSize: 40px
    fontWeight: '700'
    lineHeight: '1.1'
  headline-md:
    fontFamily: Manrope
    fontSize: 28px
    fontWeight: '600'
    lineHeight: '1.2'
  headline-sm:
    fontFamily: Manrope
    fontSize: 22px
    fontWeight: '600'
    lineHeight: '1.25'
  headline-lg-mobile:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.1'
  body-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '500'
    lineHeight: '1.4'
  body-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '400'
    lineHeight: '1.5'
  body-sm:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '400'
    lineHeight: '1.5'
  label-caps:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: '1.2'
    letterSpacing: 0.08em
  data-mono:
    fontFamily: JetBrains Mono
    fontSize: 18px
    fontWeight: '500'
    lineHeight: '1.3'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
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
  mobile-margin: 16px
---

## Brand & Style

The design system is built on a **Theatrical Professionalism** philosophy, treating digital interfaces like a high-end gallery. It moves away from traditional "gamer-neon" aesthetics in favor of a cinematic "Spotlight" metaphor. The experience is designed to evoke a sense of focused drama, confidence, and premium immersion.

The design style is a hybrid of **Minimalism** and **Tactile/Spotlight** effects. It utilizes "Void Black" backgrounds to eliminate visual noise, allowing content and interactive elements to emerge from the darkness. On mobile, this translates to a high-contrast, focus-heavy interface where depth is communicated through scale and tonal shifts rather than traditional skeuomorphism. The emotional response should be one of "quiet power"—an interface that recedes when you are playing and commands attention only when you are choosing.

## Colors

The palette is a high-contrast dark mode system where color is a functional tool rather than just decoration. 

- **Primary & Secondary:** Used exclusively for content and metadata. The off-white primary ensures readability against the void background without causing eye strain.
- **Stage Amber (Tertiary):** The signature "spotlight" color. It is reserved strictly for interactive focus, primary calls to action, and active states. 
- **Void Black (Neutral):** The canvas of the application. It creates a seamless backdrop that emphasizes content artwork.
- **Surface Tiers:** `surface` and `surface-raised` provide tonal elevation for cards and panels, creating a sense of physical stacking without needing heavy drop shadows.

## Typography

The typography system uses a tri-font strategy to balance character with utility. 

1.  **Manrope (Headlines):** High-personality geometric sans-serif used for screen titles and game headers to reinforce the theatrical brand.
2.  **Inter (Body/UI):** Neutral and highly legible at various distances. Used for all functional UI elements, metadata, and descriptions.
3.  **JetBrains Mono (Data):** Used for performance statistics, technical specifications, and system-level data (FPS, resolution) to provide a "developer-tool" precision feel.

For mobile, `headline-lg-mobile` replaces the standard large headline to maintain hierarchy on smaller viewports. Labels use uppercase styling with increased letter spacing to ensure clarity in navigation bars.

## Layout & Spacing

The design system follows a strict **8px rhythm**. The layout philosophy is a **fixed-fluid hybrid**: content is organized into horizontal "shelves" that scroll vertically.

- **Desktop/TV:** Utilizes a generous `safe-margin` of 64px to account for hardware overscan and maintain a cinematic, "airy" feel. Navigation is handled via a persistent vertical Nav Rail on the left.
- **Mobile:** The `safe-margin` scales down to 16px. The layout transitions to vertical scrolling with horizontal shelves for game cards. The Nav Rail remains persistent but is optimized for thumb reach, potentially pivoting to a bottom bar or a condensed side rail depending on orientation.
- **Grid:** A 12-column grid is used for settings and menus, while the library uses a dynamic shelf-based layout where items are separated by a standard `gutter` of 32px.

## Elevation & Depth

Depth is achieved through **Tonal Layers** and **Dynamic Scale**, as traditional shadows are invisible on the Void Black background.

- **Stacking:** `neutral` (Level 0) sits at the bottom, followed by `surface` (Level 1) for cards, and `surface-raised` (Level 2) for focused or active states.
- **The Spotlight Focus:** When an element is focused, it scales up by **6%**. This physical movement is paired with a **Stage Amber glow** (12-16px blur, low spread) that acts as a digital "spotlight," making the item feel as though it is physically closer to the user.
- **Overlays:** Modals and scrims use a `neutral` backdrop at 80% opacity, effectively "dimming the house lights" to focus entirely on the foreground task.

## Shapes

The shape language is geometric but softened. This design system uses the **Rounded (2)** preset as its baseline to maintain a modern, friendly feel that balances the sharp contrast of the colors.

- **Utility elements (Buttons, Toggles):** Use `rounded.md` (10px) to feel substantial and clickable.
- **Game Cards & Hero Art:** Use `rounded.lg` (16px) to frame artwork elegantly.
- **Large Containers:** Use `rounded.xl` (24px) for modals and major sections.
- **Pills/Tags:** Use `rounded.full` for status indicators and navigation segments.

## Components

- **Buttons:** Primary buttons use `tertiary` fill with `on-tertiary` text. Secondary buttons use `surface` fill with `primary` text. All buttons use `rounded.md`.
- **Game Cards:** These are the centerpiece. They utilize horizontal shelves. In mobile view, cards should be optimized for vertical stacking within the shelf. On focus/touch-hold, they apply the 6% scale and Amber glow.
- **Nav Rail:** A persistent vertical navigation element. Active items are indicated by a Stage Amber vertical bar or icon tint. Mobile versions should ensure the rail is easily accessible via the thumb zone.
- **Input Fields:** Use `surface` as the background with a `secondary` hairline border. On focus, the border changes to `tertiary`.
- **Chips & Tags:** Small, pill-shaped (`rounded.full`) containers using `surface-raised` for the background and `body-sm` typography.
- **Controller Hints:** Use generic geometric shapes (rings, dots) for hardware neutrality, styled in `secondary` or `primary` depending on importance.