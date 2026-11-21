# Pollenplot UI audit (Material 3 Expressive)

Audited on 20 November 2026 from the code on `main` (5644dff) and the 244 pictures the screen tests saved that day in
`app/build/screenshots/` (light, dark, large text at a font scale of 2 and wide windows). Each issue has a severity:

- **High**: something a person cannot read, reach or use, or that breaks at large text.
- **Medium**: a gap in the design system or the hierarchy that makes the app look or work worse than it should.
- **Low**: polish or consistency.

The Status column says what became of each issue: Open, or the pull request that fixed it.

## Stack and versions

- The UI is Jetpack Compose throughout, with no XML views: about 24,000 lines in 120 files under `ui/`.
- The Compose BOM is 2026.09.00, and `material3` is pinned to 1.5.0-beta02. That release has every Expressive API
  the redesign needs: `MaterialExpressiveTheme`, `MotionScheme`, `MaterialShapes`, `ButtonGroup`, `FloatingToolbar`,
  `FloatingActionButtonMenu`, `LoadingIndicator`, the emphasised type styles, shaped `ListItem`s and
  `AppBarWithSearch`. No upgrade is needed.
- The app already uses `MaterialExpressiveTheme` with `MotionScheme.expressive()`, `ShortNavigationBar` and
  `WideNavigationRail`, `LargeFlexibleTopAppBar`, `HorizontalFloatingToolbar`, `FloatingActionButtonMenu`,
  `LoadingIndicator`, the wavy progress bar and three `MaterialShapes`.

## Across the app

| Issue | Category | Severity | Status |
|---|---|---|---|
| At a font scale of 2, a tab's top bar, once it has shrunk, breaks its subtitle inside a word ("Your backyar / d" on the Plot), because the title shares the row with four or five action buttons. The open bar is fine. | Large text | High | Fixed in PR 1 |
| Lists that keep the default 24 dp at their end leave their last rows under the floating action button, as on the Wildlife tab before anything is recorded. Bees, Water, the Plot list, Compost and the Wishlist add their own 88 or 96 dp, so there the button only covers rows while they scroll past, as it should. | Touch, layout | High | Fixed in PR 1 |
| The theme keeps the default `Typography()`. Screens use `bodyLarge` and `bodyMedium` 345 times, headline styles 9 times and display styles never, so pages read as one weight of text. | Typography | Medium | Type scale in PR 1; screens in PRs 2 to 4 |
| The theme keeps the default `Shapes()`. 97 shapes are written out by hand (corners of 20, 24, 4, 12, 28, 6, 8, 16, 36 and 10 dp), and `MaterialTheme.shapes` is read twice. | Shape, consistency | Medium | Shape scale and shared components in PR 1; screens in PRs 2 to 4 |
| There are no spacing tokens: `dp` literals use 16 values under 32 dp, among them 3, 6, 10, 14, 18, 22 and 26. | Spacing, consistency | Medium | Tokens and shared components in PR 1; screens in PRs 2 to 4 |
| The pale gold `secondaryContainer` is the Today card, the Rhythms cards, wildlife badges, the calendar's today, selected chips, the weed photos question and the Ask question bubble, so unrelated things look alike. | Colour roles | Medium | Open (PRs 2 to 4) |
| Colours with a meaning live outside the theme: sun, shade and flowering layers in `PlotCanvas.kt`, the Plot grounds in `Colors.kt`, and camera and photo-viewer scrims as `Color.Black` and `Color.White`. | Colour, consistency | Medium | Plot layers, flowering marks and photo viewer in PR 1; camera in PR 4 |
| Navigation uses the default transitions, and only `CompostAnimation` checks the phone's "remove animations" setting. | Motion | Medium | Shared check in PR 1; transitions in PR 2 |
| Every empty state is the same cookie-shaped icon on `primaryContainer` above a long paragraph. | Empty states | Medium | Fixed in PR 1 |
| There are no `@Preview` functions. | Tooling | Low | Shared components in PR 1; screens in PRs 2 to 4 |
| Haptics are used only on the Plot canvas and the Today card. | Feedback | Low | Open |
| The wide layout switches on a hand-made 600 dp test, not `WindowSizeClass`, and no screen has two panes. | Adaptive layout | Low | Open |

What already works and must be kept:

- Status is always an icon and a word, as with Present and Missing on a zone's habitat, Open on a problem and
  Overdue on the Today card.
- `AccessibilityFlowTest` fails when a pressable control has no words for TalkBack.
- `largeText()` stacks labels rather than squeezing them.
- Icon buttons keep the 48 dp touch size that Material gives them.

Measured contrast in the current colours:

- Body text on cards: `onSurfaceVariant` on `surfaceContainerLow` is 8.5:1 in the light theme and 10.1:1 in the
  dark.
- Headings and text buttons: `primary` on the background is 6.1:1 in the light theme and 10.9:1 in the dark.
- Links on gold cards: `primary` on `secondaryContainer` is 4.9:1.
- Field borders: `outline` on the background is 4.3:1 in the light theme. That is above the 3:1 that boundaries need.

## The app frame

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Navigation bar and rail (`AppRoot`) | Tab names shrink to fit at large text, as intended. Moving from tab to page uses the default transition, with no direction. | Motion | Medium | Open |
| Tab frame (`TabScaffold`) | The title and subtitle use the default type, and at large text the subtitle breaks inside a word (see above). | Typography, large text | High | Fixed in PR 1 |
| Page frame (`DetailScaffold`) | A small pinned top bar for every page, so a plant, an animal and a hive all open the same way, with the name repeated below the bar. | Hierarchy | Medium | Open |

## Main tabs

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Plot, canvas | At large text, zone names on the canvas are cut to "Bac…", "H…" and "V…". | Large text | High | Open |
| Plot, canvas | Zone fills are 1.24:1 against the ground, so a zone's edge rests on its outline alone. The outline is clear, so this holds, but it should stay a theme role. | Contrast | Low | Open |
| Plot, canvas | The sun, shade and flowering layer colours are written out in `PlotCanvas.kt` rather than taken from the theme. | Colour, consistency | Medium | Fixed in PR 1 |
| Plot, Today card | A gold card of lines with the same weight. Overdue is an icon and a word inside the line, with no surface of its own. | Hierarchy, colour roles | Medium | Open |
| Plot, list | Rows are cards with corners written out by hand, and the eco badges are 26 dp icons, which is fine because they are not pressable and are read as one description. | Shape | Low | Open |
| Plot, empty | The Add a plant by name button and the Weeds near your garden link sit close to the add button and are not grouped. | Hierarchy | Low | Open |
| Rhythms | All three "This month" cards are gold whatever they say. The lane grid has a pattern for each level as well as colour, which is good. Ten shapes are written out. | Colour roles | Medium | Open |
| Rhythms | A long paragraph comes before the first card. | Hierarchy | Low | Open |
| Wildlife | Before anything is recorded, the floating button covers the last line of the Recorded near you section, because that list ends with 24 dp. | Touch, layout | High | Fixed in PR 1 |
| Wildlife | The year's summary card, the month bars and the Stings notice on `errorContainer` are clear. The group chips are gold like everything else. | Colour roles | Low | Open |
| Bees (honey, native, hotels) | The floating button covers guide rows while they scroll past, and the list ends with room for it. The empty state is the shared one. | Empty states | Low | Fixed in PR 1 |
| Water | The floating button covers the "Remind me when watering is due" switch until the list is scrolled, and the list ends with room for it. | Touch, layout | Low | Open (PR 2) |
| Water | The reference evapotranspiration (ET₀) and rain lines are plain body text with no figure to hold the eye. | Hierarchy | Low | Open |

## Records

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Plant | A small avatar, then many fact cards with the same 20 dp corners, colour and weight. The name is in the top bar and again in the header. | Hierarchy | Medium | Open |
| Plant | The botanical name is italic Roboto, with nothing to mark it as a name from the natural world. | Typography | Low | Open |
| Zone | A gold avatar and a clear list of plants and wildlife. Corners are written out. | Shape | Low | Open |
| Zone habitat | Present and Missing each have an icon and a word. Nothing to fix beyond tokens. | Consistency | Low | Open |
| Honey bee hive | No header picture. The facts are plain lines, and six buttons (two filled and four outlined) wrap into a wall. | Hierarchy, components | Medium | Open |
| Stingless hive, bee hotel | No header picture. Facts are plain lines. | Hierarchy | Medium | Open |
| Animal | A large teal avatar and clear sections, with gold role badges. | Colour roles | Low | Open |
| Sighting | The zone, plant and behaviour choices are long walls of chips. The count stepper has the full touch size. | Hierarchy | Low | Open |
| Problem | The name is in the top bar and again as a heading. The Open status pill has its icon and word. | Hierarchy | Low | Open |
| Compost bin | The bin picture is the app's best illustration, and it stays still with animations off. The disabled Save name button is faint, as disabled buttons are. | Feedback | Low | Open |
| Wish | Delete is a word in the top bar, where other pages use a bin icon. | Consistency | Low | Open |
| Library plant | A large flower-shaped avatar and clear buttons. Corners are written out. | Shape | Low | Open |
| Guide | Readable, with headings, steps and notes. Only tokens to change. | Typography | Low | Open |

## Finding and identifying

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Identify, camera | The overlay's scrims, frame and chips use `Color.Black` and `Color.White` with alpha. They are right for a camera, which is always dark, but they belong in the theme as scrim colours. | Colour, consistency | Low | Open |
| Identify and Review results | Each found plant is a card with its picture and suggestions. "It is an animal" is a text button beside an outlined one, so the two read as different kinds of choice. | Components | Low | Open |
| Review queue | Uses its own frame, not the shared scaffolds. | Consistency | Low | Open |
| Search | Clear. The kind and zone filters are chips, and the results group under headings. | Consistency | Low | Open |
| Weeds | The photo question card is gold, the same as the Today card. | Colour roles | Medium | Open |
| Weed finder, Compare | Photo grids and facts are clear. Corners are written out. | Shape | Low | Open |

## Planning and records over time

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Calendar, month | Today is gold, and the weather sits in the day cells in small text. | Colour roles | Low | Open |
| Calendar, to-do list | Jobs are grouped rows, close to the Expressive grouped list already. | Components | Low | Open |
| Forms: feed, harvest, pot, problem, treatment, hive and hotel records | Radio lists, chips and outlined fields with labels and helper text. They are clear. Spacing and corners are written out. | Consistency | Low | Open |
| Wishlist | Clear. Its empty state is the shared one. | Empty states | Low | Open |

## Settings, setup and help

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Setup | Steps with a wavy progress bar and a clear Start and Skip. The welcome page is a long paragraph. Uses its own frame. | Hierarchy | Low | Open |
| Settings | Grouped cards of rows, which hold up at large text. | Components | Low | Open |
| Models, Backup, Export, Garden location, Scan settings, About and Licences | Cards with a download or action button each, and clear words. Only tokens to change. | Consistency | Low | Open |
| Ask your garden | The question bubble is gold, and the answer is plain text with its sources as chips. | Colour roles | Low | Open |

## Plan

The chosen direction is Field Guide: gum-leaf green on warm paper, wattle gold and banksia rust beside it, Fraunces
for display and headline type and for botanical names, Roboto for everything else, flower and cookie shapes, and
expressive springs. The work lands in four pull requests:

1. Design tokens and the shared components.
2. The frame and the five tabs.
3. The record pages.
4. Finding and identifying, forms, settings and setup.

## Changes made

### PR 1: design tokens and the shared components

**Tokens.** Everything in `ui/theme/` is now a token that screens read from.

- **`Colors.kt`.** The Field Guide light and dark schemes are leaf (hue 134), wattle (hue 88), banksia rust (hue 45)
  and warm paper (hue 88). Each palette is one hue at one chroma, with tones as CIE L\*, so every role sits at the
  tone Material gives it. `PollenColors`, given through `LocalPollenColors` and read as `PollenTheme.colors`, holds
  the colours with a meaning of their own: water, bloom, the photo scrim, and the Plot's sun, shade and flowering
  layers. These keep their hues when the wallpaper's colours are on.
- **`Type.kt`.**
  - Fraunces, bundled in `res/font`, covers display and headline sizes, with optical sizes of 72, 28 and 14,
    softness 50 and no wonky letters. Display and headline styles break lines as headings do.
  - Material's emphasised styles are a step heavier.
  - `TextStyle.botanical()` sets a scientific name in Fraunces italic.
  - Titles, body and labels stay Roboto.
- **`Shapes.kt`.** Corners of 4, 8, 16, 24, 28, 32, 40 and 48 dp for Material's eight sizes. Cards and the floating
  action button are rounder than Material's defaults.
- **`Spacing.kt`.** `Spacing` on a 4 dp step, the floating-button clearance, the readable width, and `IconSize`.
- **`Motion.kt`.** `animationsOn()` reads the phone's animator scale once in the theme. The compost picture now uses
  it.
- **`Theme.kt`.** Wires these together and keeps `MotionScheme.expressive()`.

**Shared components.**

- **Shapes and spacing.** `FactCard`, `LevelTag`, `ActionNotice`, `Badges`, `SegmentedGroup`, `ConnectedChoice`,
  `SectionHeading` and the chip groups read the shape scale and spacing. The water badge takes `PollenColors.water`.
- **Empty states.** The icon sits on a cookie in front of a wattle-gold sun, with a grain of pollen in banksia rust.
  It grows into place once on a slow spatial spring, or is simply there with animations off. The title is
  `headlineSmall`, in Fraunces.
- **Plant avatar.** It is a clover in leaf and a flower `inFlower`, and morphs between the two on the default spatial
  spring through `MorphShape`. No screen passes `inFlower` yet.
- **Loading indicator.** It turns through a flower, a clover, the sun and a cookie.
- **Scaffolds.**
  - At large text, a shrunken tab bar keeps its title to two lines and its subtitle to one, ending in an ellipsis,
    and the compact Plot bar leaves its subtitle out. This fixes the word broken in two.
  - A scaffold with a floating action button gives `LocalFabClearance`, so every `listPadding` list clears the
    button.
- **Photo viewer.** It uses the scrim tokens.
- **Plot canvas.** The sun, shade and flowering layers and the in-flower edge and flower mark read `PollenColors`.
  The edge and mark were tertiary, which is now banksia rust, so they move to bloom pink.

**What changes on screens with no code of their own changed.**

- Tertiary is banksia rust, not teal. Wildlife avatars, sighting rows and the month bars, the gentler weed notices
  and the Ask notice are now warm rust, and water keeps its teal through `PollenColors`.
- Dialog titles, the tab names as each tab opens, and the Wildlife summary heading are in Fraunces, because they use
  headline styles.

**Checks.**

- `ThemeContrastTest` holds every text pairing to 4.5:1 and outlines and marks to 3:1, in both themes. The
  tightest pair is the outline on `surfaceContainerHighest`, at 3.47:1.
- `ComponentGalleryTest` draws the component previews light, dark and at twice the text size, as
  `components*.png`.
- `@PollenPreviews` (light, dark, font scale 2 and 840 dp wide) and `PreviewTheme` are in `ui/preview/`, with
  previews of every shared component.
- About › Licences lists Fraunces and the SIL Open Font License.

**Deferred.**

- The compost picture's browns, greens and soil are pigments of a drawing, so they stay in `CompostAnimation`.
- Screens still write out 90 or so shapes and their own spacing. Pull requests 2 to 4 move them to the tokens one
  screen at a time, along with botanical names in Fraunces italic and the flair moments that need a screen.
