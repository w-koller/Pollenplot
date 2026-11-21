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
| The theme keeps the default `Typography()`. Screens use `bodyLarge` and `bodyMedium` 345 times, headline styles 9 times and display styles never, so pages read as one weight of text. | Typography | Medium | Fixed in PRs 1 to 4 |
| The theme keeps the default `Shapes()`. 97 shapes are written out by hand (corners of 20, 24, 4, 12, 28, 6, 8, 16, 36 and 10 dp), and `MaterialTheme.shapes` is read twice. | Shape, consistency | Medium | Fixed in PRs 1 to 4; corners left are circles and the Plot's drawing |
| There are no spacing tokens: `dp` literals use 16 values under 32 dp, among them 3, 6, 10, 14, 18, 22 and 26. | Spacing, consistency | Medium | Fixed in PRs 1 to 4; sizes left are pictures, heights and touch targets |
| The pale gold `secondaryContainer` is the Today card, the Rhythms cards, wildlife badges, the calendar's today, selected chips, the weed photos question and the Ask question bubble, so unrelated things look alike. | Colour roles | Medium | Fixed in PRs 2 and 4 |
| Colours with a meaning live outside the theme: sun, shade and flowering layers in `PlotCanvas.kt`, the Plot grounds in `Colors.kt`, and camera and photo-viewer scrims as `Color.Black` and `Color.White`. | Colour, consistency | Medium | Fixed in PRs 1 and 4 |
| Navigation uses the default transitions, and only `CompostAnimation` checks the phone's "remove animations" setting. | Motion | Medium | Shared check in PR 1; transitions in PR 2 |
| Every empty state is the same cookie-shaped icon on `primaryContainer` above a long paragraph. | Empty states | Medium | Fixed in PR 1 |
| There are no `@Preview` functions. | Tooling | Low | Fixed in PRs 1 to 4 |
| Haptics are used only on the Plot canvas and the Today card. | Feedback | Low | Seen in flower in PR 3; the rest open |
| The wide layout switches on a hand-made 600 dp test, not `WindowSizeClass`, and no screen has two panes. | Adaptive layout | Low | Size classes in PR 2; no two-pane layouts yet |

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
| Navigation bar and rail (`AppRoot`) | Tab names shrink to fit at large text, as intended. Moving from tab to page uses the default transition, with no direction. | Motion | Medium | Fixed in PR 2 |
| Tab frame (`TabScaffold`) | The title and subtitle use the default type, and at large text the subtitle breaks inside a word (see above). | Typography, large text | High | Fixed in PR 1 |
| Page frame (`DetailScaffold`) | A small pinned top bar for every page, so a plant, an animal and a hive all open the same way, with the name repeated below the bar. | Hierarchy | Medium | Fixed in PR 3 |

## Main tabs

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Plot, canvas | At large text, zone names on the canvas are cut to "Bac…", "H…" and "V…". | Large text | High | Fixed in PR 2 |
| Plot, canvas | Zone fills are 1.24:1 against the ground, so a zone's edge rests on its outline alone. The outline is clear, so this holds, but it should stay a theme role. | Contrast | Low | Open |
| Plot, canvas | The sun, shade and flowering layer colours are written out in `PlotCanvas.kt` rather than taken from the theme. | Colour, consistency | Medium | Fixed in PR 1 |
| Plot, Today card | A gold card of lines with the same weight. Overdue is an icon and a word inside the line, with no surface of its own. | Hierarchy, colour roles | Medium | Fixed in PR 2 |
| Plot, list | Rows are cards with corners written out by hand, and the eco badges are 26 dp icons, which is fine because they are not pressable and are read as one description. | Shape | Low | Fixed in PR 2 |
| Plot, empty | The Add a plant by name button and the Weeds near your garden link sit close to the add button and are not grouped. | Hierarchy | Low | Open |
| Rhythms | All three "This month" cards are gold whatever they say. The lane grid has a pattern for each level as well as colour, which is good. Ten shapes are written out. | Colour roles | Medium | Fixed in PR 2 |
| Rhythms | A long paragraph comes before the first card. | Hierarchy | Low | Open |
| Wildlife | Before anything is recorded, the floating button covers the last line of the Recorded near you section, because that list ends with 24 dp. | Touch, layout | High | Fixed in PR 1 |
| Wildlife | The year's summary card, the month bars and the Stings notice on `errorContainer` are clear. The group chips are gold like everything else. | Colour roles | Low | Fixed in PR 2 |
| Bees (honey, native, hotels) | The floating button covers guide rows while they scroll past, and the list ends with room for it. The empty state is the shared one. | Empty states | Low | Fixed in PR 1 |
| Water | The floating button covers the "Remind me when watering is due" switch until the list is scrolled, and the list ends with room for it. | Touch, layout | Low | No change needed |
| Water | The reference evapotranspiration (ET₀) and rain lines are plain body text with no figure to hold the eye. | Hierarchy | Low | Fixed in PR 2 |

## Records

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Plant | A small avatar, then many fact cards with the same 20 dp corners, colour and weight. The name is in the top bar and again in the header. | Hierarchy | Medium | Fixed in PR 3 |
| Plant | The botanical name is italic Roboto, with nothing to mark it as a name from the natural world. | Typography | Low | Fixed in PR 3 |
| Zone | A gold avatar and a clear list of plants and wildlife. Corners are written out. | Shape | Low | Fixed in PR 3 |
| Zone habitat | Present and Missing each have an icon and a word. Nothing to fix beyond tokens. | Consistency | Low | Fixed in PR 3 |
| Honey bee hive | No header picture. The facts are plain lines, and six buttons (two filled and four outlined) wrap into a wall. | Hierarchy, components | Medium | Fixed in PR 3 |
| Stingless hive, bee hotel | No header picture. Facts are plain lines. | Hierarchy | Medium | Fixed in PR 3 |
| Animal | A large teal avatar and clear sections, with gold role badges. | Colour roles | Low | Fixed in PRs 2 and 3 |
| Sighting | The zone, plant and behaviour choices are long walls of chips. The count stepper has the full touch size. | Hierarchy | Low | Chips in PR 4; each keeps its full touch size, so the walls stay |
| Problem | The name is in the top bar and again as a heading. The Open status pill has its icon and word. | Hierarchy | Low | Fixed in PR 3 |
| Compost bin | The bin picture is the app's best illustration, and it stays still with animations off. The disabled Save name button is faint, as disabled buttons are. | Feedback | Low | Tokens in PR 3; nothing else needed |
| Wish | Delete is a word in the top bar, where other pages use a bin icon. | Consistency | Low | Fixed in PR 3 |
| Library plant | A large flower-shaped avatar and clear buttons. Corners are written out. | Shape | Low | Fixed in PR 3 |
| Guide | Readable, with headings, steps and notes. Only tokens to change. | Typography | Low | Fixed in PR 3 |

## Finding and identifying

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Identify, camera | The overlay's scrims, frame and chips use `Color.Black` and `Color.White` with alpha. They are right for a camera, which is always dark, but they belong in the theme as scrim colours. | Colour, consistency | Low | Fixed in PR 4 |
| Identify and Review results | Each found plant is a card with its picture and suggestions. "It is an animal" is a text button beside an outlined one, so the two read as different kinds of choice. | Components | Low | Fixed in PR 4 |
| Review queue | Uses its own frame, not the shared scaffolds. | Consistency | Low | Open |
| Search | Clear. The kind and zone filters are chips, and the results group under headings. | Consistency | Low | Tokens and chips in PR 4; nothing else needed |
| Weeds | The photo question card is gold, the same as the Today card. | Colour roles | Medium | Fixed in PR 4 |
| Weed finder, Compare | Photo grids and facts are clear. Corners are written out. | Shape | Low | Fixed in PR 4 |

## Planning and records over time

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Calendar, month | Today is gold, and the weather sits in the day cells in small text. | Colour roles | Low | Fixed in PR 4 |
| Calendar, to-do list | Jobs are grouped rows, close to the Expressive grouped list already. | Components | Low | No change needed |
| Forms: feed, harvest, pot, problem, treatment, hive and hotel records | Radio lists, chips and outlined fields with labels and helper text. They are clear. Spacing and corners are written out. | Consistency | Low | Fixed in PR 4 |
| Wishlist | Clear. Its empty state is the shared one. | Empty states | Low | No change needed; its pictures in PR 3 |

## Settings, setup and help

| Screen | Issue | Category | Severity | Status |
|---|---|---|---|---|
| Setup | Steps with a wavy progress bar and a clear Start and Skip. The welcome page is a long paragraph. Uses its own frame. | Hierarchy | Low | Welcome fixed in PR 4; its own frame is open |
| Settings | Grouped cards of rows, which hold up at large text. | Components | Low | Tokens and notices in PR 4 |
| Models, Backup, Export, Garden location, Scan settings, About and Licences | Cards with a download or action button each, and clear words. Only tokens to change. | Consistency | Low | Fixed in PR 4 |
| Ask your garden | The question bubble is gold, and the answer is plain text with its sources as chips. | Colour roles | Low | Fixed in PR 4 |

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

### PR 2: the frame and the five tabs

**Colour by meaning.** Across the tabs, colour now says what a thing is about. Plants are leaf green (primary), bees
and pollination wattle gold (secondary), animals banksia rust (tertiary), water blue-green (`PollenColors.water`), and
warnings use the error colours. Every one of these also has its icon and its words. A new `BadgeTone.ANIMAL` carries
the rust for animals' roles, the Wildlife tab's group counts and a zone's Frogs seen. A plant's worth to wildlife,
such as Pollinator magnet, stays gold.

**Frame.**

- `components/WindowSize.kt` reads Material's window size classes from `androidx.window`, which `material3` already
  brings at 1.5.0 and is now declared, as `graphics-shapes` is.
- A medium window has the tabs in the rail as before. An expanded one, 840 dp and wider, opens the rail out with each
  label beside its icon.
- `nav/Transitions.kt` gives the navigation host the motion scheme's springs:
  - A page opened from another slides in an eighth of the width as it fades in, and Back reverses it.
  - Moving between tabs fades through.
  - With animations off there is no transition.

**Plot.**

- **Today card.** It is a neutral page with "Today" in Fraunces, and each job's icon sits on a dot in its kind's
  colour. An overdue hive check, a hot day or pots before heat stands on its own tile in the error colours, and the
  word Overdue or the heat warning is still in its line. It closes up on the spatial spring when a job is done.
- **Zone tiles.** At large text a name takes up to two lines and shrinks only as far as its standard size, and the
  zone kind's icon gives way. Plants in flower show as flowers, from `ZoneView.flowering`. That is the one data
  addition: the ids `GardenViews` already worked out for the in-flower count.
- **Zone cards.** They use the theme's card shape, and their plants also show in flower.
- **Lists.** Scientific names in `PlantRow` and `SpeciesRow` are in Fraunces italic.
- **Spacing.** The canvas and the screen use the shape and spacing tokens, and the empty Plot and the list use
  `listPadding`.

**Rhythms.** This month's cards each take their kind's colour: honey bees' nectar gold, stingless bees' flowers green,
pollinators seen rust, and flowering earlier or later neutral.

**Wildlife.** The group counts and the ideas for groups not seen are rust.

**Bees.** These use spacing tokens and `listPadding`.

**Water.**

- The day's weather is a card in water's colours, with the reference evapotranspiration (ET₀) line as its title.
- The pots-before-heat notice is in the error colours.
- The list uses `listPadding`.

**Previews.** `TabPreviews.kt` adds the Today card with a job of every kind, and the four Rhythms cards. Both are in
`ComponentGalleryTest`'s pictures.

**Deferred.**

- The Plot's empty state still has its three buttons in a column under the picture.
- Rhythms still opens with its long paragraph.
- No screen has two panes on a tablet yet.

### PR 3: the record pages

**Shared pieces.**

- **`RecordHero`** (`components/RecordHero.kt`) opens a record's page like a plate in a field guide: the picture
  large on a sun shape, the name in Fraunces, the scientific name in Fraunces italic, a line or two of what it is and
  where, and its badges, all centred. The sun grows and turns into place once, on the slow spatial spring, and is still
  with the phone's animations off.
- **Top bar title.** `DetailScaffold` takes `titleShown`, from `heroGone` for a list or a scrolling column. On a page
  with a hero, the bar leaves the name to the hero and fades it in once the hero has scrolled away, on one line or on
  two with large text. While it is hidden, TalkBack does not read it, because the hero's heading says the same.
- **`FactGroup`** joins related fact cards under a heading: large corners outside the group, small ones inside, and a
  2 dp gap, as Material 3 Expressive's grouped lists do. `FactCard` takes the shape it is given.
- **`GroupedItem`** (`components/GroupedList.kt`) wraps Material's `SegmentedListItem` and
  `ListItemDefaults.segmentedShapes`, so the Expressive list API is reached in one place. Its rows are on
  `surfaceContainerLow`, because the segmented colours' own container is the page's paper and the group did not show.
- **`RecordMenu`** is the add menu's floating button with its own words ("Open the record menu"). It opens into the
  kinds of record a page can add. `AddMenu` and `RecordMenu` share one `FabMenu`.
- `RoleBadges` and the new `BadgeRow` can centre their badges under a hero.

**Plant.**

- The hero has the plant's cut-out or growth form, which is a flower while the plant is in flower now and a clover
  otherwise. "In flower now" is `FloweringSeen.inFlowerNow`, the rule the Plot's zones use, from data the page already
  loads. Recording "Seen in flower today" gives a confirm vibration, and the picture turns into a flower if it was not
  one already.
- The facts are three groups: Growing conditions (light, water, soil and feeding, compost), Wildlife and bees
  (wildlife, bee forage, flowering) and Size and safety. Companion plants stay a card of their own.
- The flowering months use the flowering colour the Plot uses (`PollenColors.bloom`), the best months strongest and in
  bold, and the plant's badges are in the hero.
- A companion to keep apart has its warning icon in the error colour, with its words as before.

**Library plant.** The same hero and fact groups, with the Add and Save buttons centred under it.

**Animal.** A rust hero with its role badges. Scientific names of the plants it uses are in Fraunces italic.

**Zone.** A hero with the zone's kind on a gold cookie and its conditions and badges centred. Watering, feeding and the
habitat card use the spacing tokens.

**Honey bee hive, stingless bee hive and bee hotel.**

- A gold hero with what the hive or hotel is, where, and its latest state.
- The record buttons move into the record menu: six kinds for a honey bee hive, three for a stingless hive and two for
  a hotel, each with an icon. The tests open the menu with `recordFromMenu` in `TestGarden.kt`.
- Each kind of record is a grouped list, its delete buttons as before. A hive's due checks, through `TaskRow`'s
  `group`, and a hotel's sections are grouped the same way. The Bees tab's own rows are unchanged.

**Problem.** A rust hero with the plant and day, and the Open or Resolved status as its badge. The catalogue's card is
headed "About this problem" rather than repeating the name, and the treatments are one group of joined cards.

**Wish.** A hero with the wish's status and badges, Delete is a bin icon like other pages, and the wish's picture is a
plant's (leaf green, a clover) in the wishlist too.

**Guide.** The title is large in Fraunces with the page's summary under it, headings are a step larger, bullets and
step numbers are leaf green, and italic text, which the guides keep for species names, is in Fraunces italic.

**Sighting, compost bin.** Both use the spacing tokens. A sighting's page is mostly its form, so its header stays
compact, with the scientific name in Fraunces italic. A hero there pushed the zone and behaviour choices below the
first screen.

**Previews.** `RecordPreviews.kt` has a plant's hero in flower, a hive's hero, a group of fact cards and a grouped list
of inspections. `ComponentGalleryTest` saves them as `records*.png`.

**Deferred.**

- The sighting's zone, plant and behaviour chips, with the other forms, to PR 4.
- The plant page's edit sheet, with the other forms, to PR 4.

### PR 4: finding and identifying, forms, settings and setup

**Choices are leaf green.** Material's chosen filter chip and checked list item take `secondaryContainer`, which in
Field Guide is the bees' wattle gold, so every form, filter and setting lit up gold.

- `ChoiceChip` (`components/Choices.kt`) is `FilterChip` with leaf green for a chosen chip. All 29 filter chips use it,
  and the Wildlife tab's group chips keep their rust.
- `ToggleRow` is a form's switch row, leaf green while it is on. It replaces the zone editor's shelter switches and the
  inspection form's Queen seen and Eggs seen.
- `checkedRowColors()` gives eight other rows that can be checked the same leaf green: setup's zone ticks and pets, the
  library's add mode and picks, the review card's In flower switch, monthly mite checks and the Plot's grid. The
  settings' segmented groups set it in `SegmentedGroup`'s colours, so their switches and album ticks follow.

**The other gold, by meaning.**

- **Neutral notes.** `ActionNotice`, the photos question on the Weeds and Library screens, the review card's offer to
  add a find to a plant already in the garden, and the progress card while photos are added are on
  `surfaceContainerHigh`, like a note in the notebook.
- **Calendar.** Days with jobs are leaf green, and today keeps its outline in leaf green.
- **Ask.** The question is in a leaf-green bubble, and an answer Pollenplot gives itself is a note unless it is about
  danger, which stays in the error colours.
- **Weed card.** A weed the law does not restrict is a note, and a restricted one stays in the error colours.
  Tertiary is banksia rust, the animals' colour, since PR 1.
- **Plot.** Pools and water tanks among the structures are in water's blue-green. They were tertiary, which was
  pond teal before PR 1.

**Camera.** The Identify overlay's scrims, frame, chips and pills use `PollenColors.scrim` and `onScrim` rather than
`Color.Black` and `Color.White`, and the frame takes the theme's extra-large corners.

**Review.** "It is an animal" is an outlined button with a bird, like "It is another plant" beside it, and its menu
gives each group its icon. A find's scientific name is in Fraunces italic.

**Setup.** The welcome shows the empty states' picture with a flower: the cookie, the gold sun and the grain of pollen,
shared as `Vignette`. Under it are the first sentence, the four steps as a grouped list with icons, and a note that every
step can be skipped. That replaces two long paragraphs.

**Tokens.** The forms, settings, search, calendar, weeds, review, Ask and setup screens use the shape scale and the
spacing and icon-size tokens. What is left as `dp` is a measurement: a picture, a height, a touch target or the
Plot's drawing.

**Previews.** The choices preview adds two `ToggleRow`s, one on and one off.

**Deferred.**

- The Review queue and Setup keep their own frames rather than the shared scaffolds.
- The sighting's choices stay as chips. Each has its full touch size, and a list of the yard's zones and plants is long
  by nature.
- The compost picture's pigments stay its own.

## Where it stands

The four pull requests bring every screen onto Field Guide: one theme of colour roles, type, shapes, spacing and motion,
and components that use only those. Colour says what a thing is about everywhere, and each colour still has its icon
and words beside it:

- leaf green for plants and for choices made;
- wattle gold for bees and pollination;
- banksia rust for animals;
- water's blue-green;
- the error colours for warnings.

`ThemeContrastTest` holds every pair to WCAG AA in both schemes. The screen tests draw each screen in the light, in the
dark, at twice the text size and in a wide window, and `ComponentGalleryTest` draws the shared pieces.

### Before and after, screen by screen

| Screen | Before | After |
|---|---|---|
| Top bars | The default type. At large text a shrunken tab bar broke a word in two. | Fraunces titles. At large text the shrunken bar keeps to two lines. A record page leaves its name to its hero until that has scrolled away. |
| Empty states | A cookie icon and a paragraph, the same on every screen. | The icon on a cookie in front of a gold sun with a grain of pollen, growing into place. |
| Navigation | The default page transition. A rail without labels. | Pages slide a little on the motion scheme's springs, tabs fade through, and nothing moves with animations off. The rail opens out with labels on an expanded window. |
| Plot | A gold Today card of lines of the same weight. Zone names cut to "Bac…" at large text. | Today is a page with a coloured dot for each job and urgent jobs on their own tiles. Zone names take two lines. Plants in flower show as flowers. |
| Rhythms | Three gold cards whatever they said. | Each card in its kind's colour. |
| Wildlife | Gold group chips, and the last line under the floating button. | Rust counts and ideas, and the list clears the button. |
| Bees | Lists that the button covered while they scrolled. | The same lists, ending with room for the button. |
| Water | Plain lines for the weather, and the button over the reminder switch. | A water-coloured weather card and an error-coloured notice for pots before heat. |
| Plant | A small avatar, then many fact cards alike, and the name twice. | A specimen hero that is a flower while the plant flowers, three groups of joined fact cards, flowering months in the flowering pink, and a vibration when it is seen in flower. |
| Animal, zone, library plant, problem, wish | A small header under a bar that already named the record. | A hero, with badges centred, and the bar's name only once it has scrolled away. |
| Hives and bee hotel | No header picture, plain lines and walls of up to six buttons. | A gold hero, a record menu instead of the buttons, and grouped lists of what is due and recorded. |
| Guide | The title only in the bar. | A Fraunces title with the page's summary, larger headings and species names in Fraunces italic. |
| Identify and Review | A camera overlay in fixed black and white, and two kinds of button for the same choice. | Scrim colours from the theme, and matching buttons for "another plant" and "an animal". |
| Forms | Gold chosen chips and switch rows, and spacing written out. | Leaf-green choices and the spacing tokens. |
| Calendar | Gold days with jobs. | Leaf-green days with jobs, with today outlined. |
| Settings and its screens | Gold notices. | Notes on the raised surface, and the tokens. |
| Setup | A welcome of two long paragraphs. | The app's picture, a sentence and the four steps as a grouped list. |
| Ask | A gold question bubble. | A leaf-green question and neutral notes for Pollenplot's own answers. |

### What comes next

- **Checks on a phone**, which the screen tests cannot make:
  - animations off;
  - wallpaper colours on and off;
  - text at twice its size;
  - landscape;
  - a tablet's rail;
  - the hero's grow-in and title fade;
  - the vibration;
  - the record menu with TalkBack.
- **Two-pane layouts** on expanded windows, such as the Plot's list beside a zone, or a hive beside its records.
- **The Plot's empty state** groups its three buttons, and **Rhythms** shortens its opening paragraph.
- **Haptics** beyond the Plot, the Today card and Seen in flower.
- **Frames.** The Review queue and Setup move onto the shared scaffolds.
