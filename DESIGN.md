---
name: Familhas Loja
description: Agência de viagens em família vista da janela do avião, céu azul de voo com amarelo do "Voe" como único acento.
colors:
  night: "#0b1b45"
  primary: "#1d4fd7"
  primary-hover: "#143ba8"
  dusk: "#1f55c8"
  sky: "#9fd0ff"
  ground: "#f3f7fd"
  surface: "#ffffff"
  border: "#d3dcee"
  muted: "#4a5778"
  on-sky: "#e8f0ff"
  voe-yellow: "#ffc933"
  voe-yellow-hover: "#ffd75e"
  accent-orange: "#c2410c"
  danger: "#b42318"
  success: "#1a7f37"
  whatsapp: "#128c4a"
  focus: "#1f6feb"
typography:
  display:
    fontFamily: "Bricolage Grotesque, Hanken Grotesk, system-ui, sans-serif"
    fontSize: "5rem"
    fontWeight: 600
    lineHeight: 1.02
    letterSpacing: "-0.035em"
  headline:
    fontFamily: "Bricolage Grotesque, Hanken Grotesk, system-ui, sans-serif"
    fontSize: "2rem"
    fontWeight: 600
    lineHeight: 1.15
    letterSpacing: "-0.025em"
  title:
    fontFamily: "Bricolage Grotesque, Hanken Grotesk, system-ui, sans-serif"
    fontSize: "1.375rem"
    fontWeight: 600
    lineHeight: 1.15
  body:
    fontFamily: "Hanken Grotesk, system-ui, -apple-system, Segoe UI, Roboto, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Hanken Grotesk, system-ui, -apple-system, Segoe UI, Roboto, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 600
    lineHeight: 1.5
rounded:
  card: "1.25rem"
  panel: "2rem"
  search-mobile: "1.75rem"
  pill: "999px"
spacing:
  xs: "0.25rem"
  sm: "0.5rem"
  md: "1rem"
  lg: "1.5rem"
  xl: "2.5rem"
  tap: "2.75rem"
  container: "72rem"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    padding: "0.5rem 1.5rem"
    height: "2.75rem"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.primary}"
    rounded: "{rounded.pill}"
    padding: "0.5rem 1.5rem"
    height: "2.75rem"
  button-search:
    backgroundColor: "{colors.voe-yellow}"
    textColor: "{colors.night}"
    rounded: "{rounded.pill}"
    padding: "0.5rem 2.5rem"
    height: "3.5rem"
  button-search-hover:
    backgroundColor: "{colors.voe-yellow-hover}"
  button-light:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.night}"
    rounded: "{rounded.pill}"
    padding: "0.5rem 1.5rem"
    height: "2.75rem"
  search-pill:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.night}"
    rounded: "{rounded.pill}"
    padding: "0.5rem 0.5rem 0.5rem 1.5rem"
  package-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.night}"
    rounded: "{rounded.card}"
    padding: "1rem"
  destination-tile:
    textColor: "{colors.surface}"
    rounded: "{rounded.card}"
    padding: "1rem"
    height: "11rem"
  field-input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.night}"
    rounded: "{rounded.pill}"
    padding: "0.5rem 1rem"
    height: "2.75rem"
  site-footer:
    backgroundColor: "{colors.night}"
    textColor: "{colors.on-sky}"
    rounded: "1.5rem"
---

# Design System: Familhas Loja

## Overview

**Creative North Star: "A Janela do Avião"**

The store is the descent from a plane window. The first viewport is a rounded full-screen window onto a real flight clip, and the whole page is that sky seen while scrolling: the window opens as the visitor scrolls and the clip advances (60 frames drawn on a fixed canvas, scrubbed by scroll). Sections after the hero flow directly over the moving sky, with white headings and no boxes. The result is airy, blue, and human, a small agency that answers on WhatsApp, not a catalogue grid.

Density is low and mobile first. One cool light ground carries the interior pages (search, package detail, quote, checkout, 404); the sky is the signature of the home. Color is strict: night and royal blue for structure, white for surfaces, and the yellow of the logo's "Voe" as the single warm accent, reserved for the search action, the highlighted words in the hero title, and text selection.

**Key Characteristics:**
- Fixed canvas backdrop scrubbed by scroll, clipped to a rounded window that opens with scroll; static first frame when reduced motion is requested.
- Pill-shaped controls everywhere (buttons, search, selects, nav links); containers at 1.25rem or 2rem radius.
- Display type in Bricolage Grotesque 600 with tight tracking; body in Hanken Grotesk.
- Depth by soft blue-tinted shadows and the photographic sky, never by hard offsets.
- AA contrast, 44px touch targets, 3px blue focus ring.

## Colors

A cool sky palette: deep night navy and royal blue on a pale cold ground, one yellow accent borrowed from the logo.

### Primary
- **Royal Flight Blue** (`primary`, #1d4fd7): primary buttons, links, prices, scrollbar thumb, active tints. Hover deepens to **Deep Altitude Blue** (`primary-hover`, #143ba8).
- **Dusk Blue** (`dusk`, #1f55c8): rail scrollbar and the human-contact block background token; a supporting blue, not a second primary.

### Secondary
- **Voe Yellow** (`voe-yellow`, #ffc933): the "Voe" of the logo, the emphasized words of the hero title, the search submit button, and `::selection`. Hover is **Lifted Yellow** (`voe-yellow-hover`, #ffd75e). Text on yellow is always night navy.

### Tertiary
- **Seat Alert Orange** (`accent-orange`, #c2410c): only the "last seats" notice on package cards. Functional, not decorative.

### Neutral
- **Night Navy** (`night`, #0b1b45): body text, header and footer fill, dark overlays on the sky, text on yellow and white buttons.
- **Cold Ground** (`ground`, #f3f7fd): page background of interior screens.
- **Cloud White** (`surface`, #ffffff): cards, search pill, inputs, light button.
- **Hairline Mist** (`border`, #d3dcee): card and divider strokes.
- **Slate Muted** (`muted`, #4a5778): secondary text on light surfaces.
- **Sky Tint** (`on-sky`, #e8f0ff): body copy over the dimmed sky and footer text.
- **Horizon Blue** (`sky`, #9fd0ff): light stop of illustration gradients.
- Status colors: `danger` #b42318, `success` #1a7f37, `whatsapp` #128c4a, `focus` #1f6feb.

### Named Rules
**The Single Yellow Rule.** Yellow is the logo's colour and the one warm voice; it appears on the search action and highlighted hero words, never as a background for large areas or as a second CTA colour.

**The Night On Yellow Rule.** Any text on yellow is night navy (#0b1b45); white on yellow fails contrast.

## Typography

**Display Font:** Bricolage Grotesque (with Hanken Grotesk, system-ui, sans-serif), weights 500 to 800 loaded, 600 used.
**Body Font:** Hanken Grotesk (with system-ui, -apple-system, Segoe UI, Roboto, sans-serif), weights 400 to 700.

**Character:** Bricolage brings a slightly quirky, friendly grotesque voice to titles; Hanken keeps the reading text neutral and clear. Titles are tightly tracked and balanced.

### Hierarchy
- **Display** (600, clamp(2.5rem, 8vw + 0.5rem, 5rem) on mobile, clamp(1.75rem, 3.5vw, 3.1rem) on desktop inside the window glass, 1.02, -0.035em): the hero title only; max 14ch on mobile; "sem complicação" in Voe Yellow.
- **Headline** (600, clamp(1.5rem, 2vw + 0.75rem, 2rem), 1.15, -0.025em): section titles (h2). Over the sky they are white with a soft navy text shadow.
- **Page title** (600, clamp(1.75rem, 4vw + 0.75rem, 2.5rem), -0.025em): h1 on interior pages.
- **Title** (600, 1.375rem, 1.15): card titles and price figure; tile labels use 1.5rem display.
- **Body** (400, 1rem, 1.5): all running text; hero subtitle is 1.125rem on mobile, about 1rem on desktop; keep lines under 36rem.
- **Label** (600, 0.8125rem to 0.875rem): field labels, "A partir de", seat notice.

### Named Rules
**The Display Is Display Rule.** Bricolage is for titles, prices and tile labels; never for buttons, fields or body copy.

## Layout

Mobile first; breakpoints only enlarge. The site container is 72rem wide with 1rem gutters. Spacing scale is 0.25 / 0.5 / 1 / 1.5 / 2.5rem. Interior sections use a vertical `stack` rhythm (1rem gap, 1.5rem block padding).

The hero is a rounded window inset 0.5rem (1rem on desktop) from the viewport edge, min-height 38rem (42rem desktop), with the copy block centered; on desktop the copy narrows to about 34vw so it sits inside the glass of the window, and the search pill sits at the foot. The header floats transparent over the hero on the home route and is solid night navy elsewhere.

Offers are a horizontal scroll-snap rail on mobile (cards at min(80vw, 19rem)) and an auto-fill grid (minimum 18rem) from 48rem. Style tiles are an auto-fit grid, minimum 12rem. Breakpoint: 48rem is the only structural break.

## Elevation & Depth

Hybrid: flat surfaces with soft, blue-tinted ambient shadows, plus the photographic depth of the sky behind everything. No hard offset shadows.

### Shadow Vocabulary
- **Card lift** (`box-shadow: 0 8px 24px rgb(11 27 69 / 0.14)`): package cards and destination tiles.
- **Card rest** (`box-shadow: 0 2px 10px rgb(11 27 69 / 0.06)`): generic `.card` on interior pages.
- **Search float** (`box-shadow: 0 16px 40px rgb(7 18 46 / 0.35)`): the white search pill over the sky.
- **Legibility glow** (`text-shadow: 0 2px 24px rgb(7 18 46 / 0.5)`): headings and copy directly over video; the sky also carries a navy gradient dim that deepens from 0.2 to 0.5 as the window opens.

### Named Rules
**The Dim Before Text Rule.** Any white text placed over the sky relies on the navy dim layer plus a text shadow; do not remove either.

## Shapes

Soft and rounded, pill-forward. Buttons, inputs, selects, nav links: fully round (999px). Cards and tiles: 1.25rem. Large framing shapes (hero window, human-contact block): 2rem. Footer: 1.5rem. Mobile search panel: 1.75rem, which becomes a pill on desktop. Cards have a 1px mist border except media cards, which are borderless and clip their media. Illustrated media uses a mountain-ridge clip-path silhouette over a vertical sky gradient.

## Components

### Buttons
- **Shape:** pill (999px), 2px border in the fill colour, min-height 2.75rem, weight 600.
- **Primary:** Royal Flight Blue fill, white text, padding 0.5rem 1.5rem; hover Deep Altitude Blue.
- **Ghost:** transparent, blue text and border; hover a 10% blue wash. On sky and in the header it turns white with a 70 to 80% white border.
- **Search (yellow):** Voe Yellow fill, night text, 3.5rem tall on desktop; only the hero search uses it.
- **Light:** white fill, night text, for the WhatsApp call over the sky.
- **Focus:** global 3px #1f6feb outline with 2px offset. Disabled: 60% opacity.

### Search pill (signature)
White panel with two borderless transparent selects ("Para onde?", "Pessoas") with small muted labels, and the yellow button. Two-column rounded panel on mobile, a single pill row on desktop.

### Cards / Containers
- **Package card:** white, 1.25rem radius, no border, card-lift shadow, 16:9 media on top, 1rem body padding. The whole card is clickable via the title link; focus ring wraps the card. Price in Royal Flight Blue at 1.375rem; "last seats" in orange.
- **Destination tile:** 11rem min-height, 1.25rem radius, display-face label bottom-left in white with a text shadow; hover brightens 8%.

### Inputs / Fields
Pill (999px), white, 1px muted border, min-height 2.75rem, bold label above, errors in danger red at 0.875rem.

### Navigation
Header: Familhas lockup left, nav pills (Início, Buscar) in white bold, active page as a white 18% wash pill, ghost "Entrar/Sair" right. Transparent over the hero on home, solid night elsewhere. Greeting hidden on mobile. Footer: night navy block with 1.5rem radius and light blue text.

### Provisional (pending real assets)
- **Brand lockup:** a text stand-in ("Voe" in yellow, small, above "Familhas" in white Bricolage 700); replace with the official logo file when available.
- **Dusk media:** `.dusk-media` with variants v1 to v3 (steel blue and amber, plum and orange, teal and sand) are authored illustrations standing in for destination photography; replace with real photos.

## Do's and Don'ts

### Do:
- **Do** keep sections flowing over the sky on the home, with white headings and no boxed panels.
- **Do** use pills for every interactive control and 1.25rem for content containers.
- **Do** put night navy text on yellow and white text over the dimmed sky.
- **Do** keep touch targets at least 2.75rem and the 3px focus ring.
- **Do** respect reduced motion: show the first frame still, no scrubbing.

### Don't:
- **Don't** put the search form in a white card on a flat colour block; it floats at the window's foot.
- **Don't** use yellow for anything beyond the logo, hero emphasis and the search action.
- **Don't** use hard offset shadows, glyph icons or invented testimonials, numbers or destination photos.
- **Don't** set Bricolage on controls or body text.
