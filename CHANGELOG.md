# Changelog

## Unreleased

- A review card says plainly how to correct it. “It is another plant” opens a search of the knowledge base and of
  every plant the models can name, and a name Pollenplot does not know can be used as it was typed. “It is an animal”
  looks at the find again as a bird, insect or other animal, and “It is a plant” does the reverse.
- Pollenplot remembers what you said a find is. When the models put the same wrong name first again, the name you
  chose comes first, and a name turned down twice as not a plant counts for half as much.
- A plant added under the wrong name can be changed from its Edit sheet with “Change what this plant is”. Its photos,
  notes and nickname stay with it.
- An animal found before the accurate pack was installed keeps animal names when the pack looks at it. Before, it was
  given plant names.
- One plant gives one card more often. Finds that lie over each other are made one when the models as good as agree:
  one names a plant among the other's first suggestions, or both name the same genus. Finds are compared again after
  the accurate pack and Gemma 4 have looked at them.
- When a photo of one plant still gives several cards, “This photo shows one plant” under the photo keeps the surest
  one, with the others' suggestions after its own.
- Adding plant photos from the phone asks whether each shows one plant or a garden bed. One plant is the first
  choice.
- A photo is kept once. The same picture added twice, or added and later found by the gallery scan, gives its plants
  once.
- The live suggestions on the camera hold steady while the camera is still. Each name's score is averaged over the
  last few frames, a name appears and leaves at different levels, the first name changes only when another has
  clearly led it for two frames, and one blurred frame no longer clears the list.
- The quick models are shown the square in the middle of the frame, as the guide on the camera shows it. Before, the
  middle of a photo that is not square was squashed to fit.
- The database has changed, so this version does not install over 1.0.0-rc.4 and keep its records. Please install it
  afresh.

## 1.0.0-rc.4

- A release candidate no longer closes on launch when Android's own backup has restored an earlier candidate's
  records on installing it again. Those records cannot be opened by this version, so the garden starts afresh with
  setup, as after a fresh install.
- The Plot's top-bar buttons appear once its zones have loaded, so no button moves along the bar as the screen opens.
- The database is the same as in 1.0.0-rc.3, so this version installs over it and keeps its records. A phone that
  had an earlier release candidate, or that restores one from Android's backup, starts afresh with setup.

## 1.0.0-rc.3

- The Plot has no edges. Zones can be moved anywhere, including above and to the left of where the first ones were
  placed, and new zones go beside the others. A grid shows every metre, with a stronger line every 5 m, and while the
  layout is edited a dashed line marks the part of the yard laid out so far.
- While the layout is edited, any zone can be dragged without choosing it first. The place it will snap to is drawn as
  it moves, with how far it moved or its new size, and the view pans when a zone is dragged to the edge.
- The Plot has zoom in, zoom out and Show the whole plot buttons.
- Holding a zone on the Plot shows a preview of it: its plants, its next watering, its badges and the animals seen
  there. Lifting the finger closes it, and screen readers open it with the zone's Preview zone action.
- A photo from the camera is read as one plant, the one in the middle of the frame, and gives one card. The Garden bed
  choice looks for each plant in the photo, as photos from the gallery are.
- A plant found several times in one photo gives one card. Finds of the same species are made one wherever they are in
  the photo, and an unsure find inside a stronger one is taken as part of it. A photo gives at most four cards.
- In flower starts switched off unless there is a sign of flowers. The accurate pack's BioCAP looks for flowers when
  the pack has its flower vectors. A plant that does not usually flower in the month the photo was taken starts as not
  in flower. Otherwise flower colours in the middle of the cut-out count, and sky, mulch, soil and pots no longer do.
  The card says why the switch started where it did.
- Accurate identification pack 2 adds BioCAP's flower vectors. Older packs are removed from the phone once a newer one
  is due.
- `docs/sharing.md` proposes how a household could share one garden.
- Gardens in the Northern Hemisphere see flowering months six months on from the knowledge base's Southern months,
  on the plant page, in the review cards' flower note, in reports and in the assistant's facts. A photo taken there is
  no longer called out of season by the Southern months.
- The Overwatering risk badge shows on a plant that shares a dripper, drip line, spray or sprinkler line with a plant
  that needs three times as much water or more.
- Settings has a Battery saver row. It says whether the phone holds Pollenplot back to save battery, which can delay the
  morning reminders and photo scans, and opens the phone's battery settings.
- The README describes what the app sends online, each permission, the optional downloads and known limits.
- Identify a plant away from home, from the Plot's add menu or the Wishlist. Each find says whether it is native to
  your state, a weed in your area, or not on your area's weed lists, with the sun and water it needs. Save it to the
  wishlist or let it go. These finds never wait in the Review queue, and a photo with nothing saved from it is deleted.
  Plants in your garden and plants recorded near it get no boost in these photos.
- The Wishlist, opened from the Plot, keeps plants you would like to grow: where you saw each one, its price, the zone
  it might go in and the badges that zone would gain, and notes. Plant it adds the plant to that zone and marks the
  wish planted. Wishes are in search, reports, backups and the JSON-LD export.
- A weed's plant page, its card in review and the Weeds screen show native alternatives for your state, from the
  Grow Me Instead booklets, each with Add to wishlist and a link to its booklet.
- Settings and the setup ask whether cats or dogs live at home. Then plants toxic to pets show that badge first, and
  Pollen Rhythms leaves them out of its suggestions.
- Identify has Choose from photos, for a photo on the phone or in a cloud album the photo picker shows, such as Google
  Photos. It also works when the camera is not allowed.
- Photos added with Add plant photos give back the app's access to them once they have been looked at.
- A plant's page shows the photos it was found in, the newest first, each with its date, how sure the models were and
  what else they suggested. Choosing one opens the photo with the plant outlined.
- The Flowering card shows the months a plant was seen in flower in the last year under the months the knowledge base
  expects.
- Review's Add to another plant adds a find to any plant in the garden, plants of the same kind first.
- Edit facts on a plant's page corrects its light, water need, frost tolerance, soils, feeding, flowering months, height
  and width for every plant of that species. Corrected facts are marked Your correction, and the badges, calendar,
  watering, search and the assistant use them. Each can go back to the knowledge base. Corrections are in backups and
  the JSON-LD export as your own statements.
- A potted plant's page has a Pot card: its size, material and potting mix, and when it is due a flush (about every
  four months, as the University of New Hampshire Extension advises) or a look at repotting (every two to three years,
  as Scotts' Australian guide suggests). Record care keeps repotting, potting up, root pruning, topping up and flushing
  in the plant's history. Pots due show on the Today card and in a morning notification, which Settings can turn off.
- A zone holding a plant with pot details counts as pots before a day of 35 °C, whatever kind of zone it is.
- Record a feed, from the Plot's add menu or a zone, keeps the product, the zones it went on, the day and the amount
  as you measured it; Pollenplot never suggests an amount. The list starts with five products whose analysis comes from
  the maker's page, and you can add your own. Each zone shows when it was last fed and with what.
- A product with more than about 4% phosphorus asks first before it goes on a zone with grevilleas, banksias or other
  Proteaceae, as an Australian Native Plants Society article advises.
- Zones with citrus are due a feed every six weeks from spring to autumn, as Gardening Australia's Citrus Care advises,
  on the Today card and in a morning notification. Settings can turn this off, and can turn on your own reminder to
  give a conditioner before a day of 35 °C.
- Log a problem on a plant's page: a pest from a short catalogue (aphids, soft scale, mealybugs, whitefly, two-spotted
  mite, citrus leafminer and snails, each described from a state government page that the problem links), or
  something else by name, with a photo and notes. Pollenplot does not diagnose from the photo.
- Record a treatment and when to look again (seven days unless you choose otherwise, Pollenplot's own choice). Before
  a spray, it asks first when plants nearby are likely in flower, with BeeAware's advice to spray in the evening.
  Follow-ups due show on the Today card and in a morning notification. Problems are in search, backups and the JSON-LD
  export.
- Pollen Rhythms shows the months each group of bees finds nectar and the months it finds pollen, and names the lean
  months, when only a little is in flower.
- A zone's page has a Habitat section: its canopy, midstorey and understorey, worked out from its plants' growth forms,
  the mulch or leaf litter, rocks and logs and dense shrubs marked on it, and its water. Each shows as present or
  missing, after NSW Environment's Backyards for nature. Water for wildlife, such as a bird bath, pond or water for
  bees, can be added to a zone, with when it was last cleaned, and other zones say how far it is to the nearest water.
- A zone with all three layers gets the Layered habitat badge.
- A zone's page lists the honey bee hives, stingless bee hives and bee hotels kept in it, and the months its plants
  give each group of bees nectar and pollen. A zone with honey bee hives asks for water while the yard has none, as the
  bee Code of Practice does. The Plot's tiles show a hive or hotel kept in a zone.
- The Wildlife tab has a Wildlife corridor summary of the layers and features anywhere in your yard.
- Water features are in search, backups and the JSON-LD export.
- A plant's page records what was picked from it, with the weight, how many, a rating out of 5 and notes on the taste,
  for plants with something edible and for plants in a veg patch or orchard. It adds them up by season and says the
  months anything was picked. Harvests are in reports, backups and the JSON-LD export, and their notes never go to the
  assistant.
- A plant's page has a Pruning card: when to prune and the page that says so (roses in July and August after
  Centennial Parklands, citrus after harvest after WA's Department of Primary Industries and Regional Development, and
  grevilleas, bottlebrushes, paperbarks, native rosemaries and native fuchsias after flowering, after the Australian
  Native Plants Society), when it was last pruned, and Record pruning. A plant falls due in its pruning months until a
  prune is recorded, on the Today card and in a morning notification. Pruning months can be set in Edit facts.
- Every tab's top bar opens search. Results can be narrowed to one kind of record or to the records in one zone, and
  search also finds the notes of hive inspections, stingless hive logs, hotel observations and harvests.
- A report opens with a short summary of the garden, and its new Tasks part lists the hive checks, feeds, pot care,
  follow-ups and pruning due as it is prepared. A report can be shared with another app as a PDF or a spreadsheet as
  well as saved, and the Bees and Water tabs open a report with their own parts chosen.
- The database may change between release candidates until 1.0.0. Please uninstall an earlier release candidate before
  installing this one, because its records and backups do not open in this version.

## 1.0.0-rc.2

- A setup when Pollenplot is first opened: set where your garden is, download the identification models (accurate
  identification first), tick the kinds of zone you have and name them, then allow notifications and photo scanning.
  Every step can be skipped, and Settings › Set up again opens it later. Gardens that already have zones or a location
  skip it.
- The Review queue can be cleared at once: Select, then choose all plants or all wildlife, or tick single finds, and
  dismiss them or add them to one zone with the suggestion shown on each card. A plant already growing in that zone
  gets the photo rather than a second plant.
- Add plant photos on the Plot says that those photos are searched for plants. The Wildlife tab has Add wildlife
  photos, which asks what kind of animal the photos show, so photos from the gallery are identified as animals even
  without the accurate pack.
- About links to Pollenplot's source code on GitHub and can share a link to download the app.
- The back gesture no longer shrinks the whole window while you swipe.
- The notification for several added photos with nothing in them shows how many there were.

## 1.0.0-rc.1

- The app's frame, built with Material 3 Expressive: the Plot, Rhythms, Wildlife, Bees and Water tabs, an add menu
  on the Plot, Settings with the theme and wallpaper colours, and About › Licences.
- The Bees tab keeps European honey bees, Australian native bees and bee hotels in separate sections.
- Seasons and months for both hemispheres, ready for flowering calendars and hive tasks.
- A knowledge base of 178 plants for Australian gardens (natives, exotics, edibles, weeds and lawns), with genus and
  family facts that fill gaps, and 30 companion pairings marked as traditional advice or with some evidence.
- The Plot: a canvas of your yard's zones that you can pan, zoom and lay out on a 50 cm grid, with buttons for moving
  and resizing, a list view, and badges for weeds, mixed thirst and pollinator hotspots.
- Plants by name, each with eco-role badges and a page of requirements: light, water, soil and feeding, compost,
  wildlife, forage for honey bees and native bees, flowering, size, safety and companions.
- Search your garden, and every record stored with a UUID that the JSON-LD export uses as its identity.
- Settings › Your garden: the state or territory, which decides what counts as a local native and whose weed rules
  apply.
- Identify with the camera: live suggestions while you point the phone at a plant, then every plant found in the
  photo with its cut-out, the model's suggestions and how sure it is. Add each one to a zone, add the photo to a plant
  you already have, mark it as not a plant, or search for the right plant. Flowers in the photo are recorded for
  Pollen Rhythms, and photos with no plant in them are not kept.
- Plants show their cut-out from a photo on the Plot, in lists and on their page.
- The quick model, AIY Plants V1, runs on the phone with LiteRT and is checked against its published SHA-256 when the
  app is built. Nothing is sent off the phone, and the app contains no Google Play or Play services libraries.
- Add photos: choose photos with the system's photo picker, which needs no permission. Pollenplot looks for plants in
  them in the background and shows how far it has got in the Review queue.
- Photo scanning, off until you turn it on in Settings › Photo scanning: Pollenplot looks for plants in new photos in
  the camera's album or the albums you tick, and in photos from up to a year before. It runs when the battery is not
  low, carries on where it stopped, and tells you when it finds plants. With access to only some photos, it says how to
  allow all of them.
- The Review queue: plants found in photos wait here, with a count on the Plot and a notification that opens it. The
  zone offered for each plant is where the photo's other plants went, or the zone you chose last.
- Search by meaning, an optional 34 MB download in Settings › Models: search also offers plants and zones related to
  what you typed, such as lantana for "poisonous to dogs". The text model runs on the phone with ONNX Runtime and is
  checked against its published SHA-256 before use.
- Search finds other forms of a word, so "weeds" finds a weed.
- Settings › Garden location: search for your suburb or postcode, or use the phone's approximate location. Pollenplot
  rounds the location to about 1 km before it sends it anywhere, and finds your council area and state from it.
- The Weed identifier: each plant's weed status where you live, from your council's weed list (Brisbane City Council
  for now), the Weeds of National Significance and the National Alert List for Environmental Weeds. A plant that is a
  weed has a Weed card with its legal status, what the rules ask of you, whom to report it to, how often it has been
  recorded within 10 km, the council's control methods and links to the council, Weeds Australia and your state's
  weed agency. Suggestions on review cards carry a weed warning before you add them.
- The Weeds screen, from the Plot: the weeds in your garden and the weeds recorded near it on the Atlas of Living
  Australia, a Check a weed camera, and Weeds Australia's identification key. The lists are kept on the phone, so
  checking a plant works offline, and they are brought up to date once a month on Wi-Fi.
- Compare: a plant beside recent photos of it recorded in Australia on the Atlas of Living Australia, each credited
  to its photographer under its licence, with what the council's list says to look for. It opens from the Weed card,
  from a suggestion that is a weed, and from the weeds recorded near the garden.
- A Weeds near you notification when a weed the law restricts, or a national list names, is newly recorded near the
  garden. Tapping it opens the Weeds screen.
- Identification gives plants recorded near the garden a small boost, smaller than the one for plants already in it.
- The Weed finder, for gardens in Brisbane: the council's weed list narrowed by type, flower colour and leaves, as the
  council's own identification tool does. Opened from Check a weed, the photo's suggestions come first.
- About › Data sources credits Brisbane City Council, the Atlas of Living Australia, Weeds Australia, Open-Meteo and
  each state and territory's weed agency.
- Identify animals with the camera: choose what you are photographing, such as a bird, insect, spider, frog or
  fungus. AIY Birds V1 and AIY Insects V1 run on the phone for live suggestions of birds and insects, checked against
  their published SHA-256 when the app is built.
- An animal knowledge base of 82 animals and fungi found in Australian backyards: what each does in a garden, the
  plants it uses, what it needs, what to know before going near it and, where the law asks something, what to do,
  with the official page. Fire ants must be reported in Queensland and New South Wales, for example.
- Sightings: an animal found in a photo is added as a sighting, with its zone, the plant it was on and what it was
  doing, instead of as a plant.
- The Wildlife tab: the species seen in your yard each year by group, how many are native and how many are new, the
  months they were seen in, the animals to know about, such as venomous spiders and animals to report, and the latest
  sightings. Each animal has a page with what Pollenplot knows about it, the plants it was seen on and every
  sighting, and each sighting can be changed or deleted. Sightings can also be added by name, without a photo.
- Each zone's page lists the wildlife seen in it, and a zone where frogs have been seen gets a Frogs seen badge.
- Each plant's page lists the animals seen on it in your yard and the animals that use it, garden pests marked.
- Garden pests seen in your yard come with what they do, what helps, and the animals in the knowledge base that eat
  them, such as ladybirds and hover flies for aphids. When no birds, insects, frogs or reptiles have been seen this
  year, the Wildlife tab has ideas for bringing them into the yard.
- The accurate identification pack is published: BioCAP and 13,644 plants, animals and fungi found in Australia, a
  127 MB download in Settings › Models. On 627 research-grade photos from Australia it names the species first 44% of
  the time and among its first five 72% of the time, and puts the closest species in the right group 96% of the time.
- With the accurate pack, a photo you add or one the gallery scan looks at again while charging is looked at for
  what it shows: a photo of a bird, frog or other animal becomes a sighting to review instead of a plant, and the
  notification counts animals and plants apart.
- A suggestion the knowledge bases do not have is shown by its common name from the accurate pack, with its scientific
  name below, and a sighting of it keeps the scientific name for the records.
- Recorded near you, on the Wildlife tab: the animals recorded most often within 10 km of the garden on the Atlas of
  Living Australia, a group at a time, with those seen in your yard marked. Identification gives them the same small
  boost as plants recorded nearby. They are brought up to date with the weed lists.
- The garden assistant, an optional 2.6 GB download in Settings › Models: Gemma 4 E2B runs on the phone with LiteRT-LM
  and answers questions about a plant or animal from its page, using the knowledge bases and its cut-out, and about
  the whole garden from search, citing the records it used. Nothing you ask leaves the phone.
- For a plant the knowledge base does not have, Gemma 4 can draft its care: light, water, tolerances, soils,
  flowering, size and toxicity to pets, each checked against the allowed values. The draft is always marked as written
  by Gemma 4, can be kept or removed, and never becomes part of the knowledge base.
- Second opinions from Gemma 4, in Settings › Photo scanning: while the phone is charging and not in use, Gemma 4 looks
  again at finds the other models were unsure of, and its choice counts as one vote among theirs.
- Questions where a wrong answer could hurt someone never reach Gemma 4: bites and stings, poisoning, eating anything
  identified, chemical amounts, signs of notifiable bee pests, handling dangerous animals and injured wildlife get
  Pollenplot's own answer, with the number to call: 000, the Poisons Information Centre, the Exotic Plant Pest
  Hotline, or each state's wildlife rescue service. An answer that gives a chemical amount anyway is replaced by the
  label's.
- Honey bees, on the Bees tab: your European honey bee hives with their zone, apiary and its registration number,
  kind of hive, boxes, the queen's year and marking colour, and status. A hive's page shows
  what it is due for and everything recorded for it: inspections (the queen and eggs, the brood, temperament, frames of
  bees and stores, queen cells, signs of pests and disease and what you did), mite checks with mites per 100 bees or
  per day, treatments, feeding, harvests, and colony events such as swarms, splits and requeening.
- Hive reminders follow the Australian Honey Bee Industry Biosecurity Code of Practice: an inspection every fortnight
  in spring and summer, or every week or 10 days if you choose, and every six weeks in autumn, a brood check three
  months after the last, and a mite check in spring, summer and autumn, or every month where Varroa mite is
  established. A check that is overdue says so in words and with an icon.
- In Queensland, which asks for a mite check every month with each check reported on its Bee 123 form, mite checks
  are monthly unless you choose otherwise, and the mite check form links the Bee 123 form.
- A notification on the morning a hive check falls due, on its own Hive tasks channel, opens the Bees tab. Each check
  is told about once, and again only when it next falls due. The reminders can be turned off in the honey bee section.
- An inspection that notes sunken or perforated cappings, ropy brood or twisted larvae explains that these can be signs
  of foulbrood, which must be reported, and asks you to keep the hive's frames and equipment away from your other
  hives until you have spoken with your state or territory.
- The honey bee section links to the Code of Practice and to your state or territory's beekeeper registration page,
  and hives can be found in search.
- Native bees, on the Bees tab: stingless bee hives with their bees, box, where they came from, zone and the way the
  entrance faces, and records of each hive's weight, the bees coming home in a minute, honey, splits and budding,
  pests, swarms, moves and protection from heat and cold. The section also lists the native bees seen in the yard this
  year, from the Wildlife tab's sightings.
- Warnings before hot days: while there is a stingless bee hive, Pollenplot looks at Open-Meteo's forecast for the
  garden each morning and warns on the day before and the day itself when it is forecast to reach 38 °C, or 35 or
  40 °C if you choose. The warning opens the guide's page on cooling a hive.
- The native bee guide: stingless bees, looking after a hive, swarms and pests, splitting and budding, and solitary
  native bees and how they nest, from Cumberland City Council's Keepin-A-Hive guide, Aussie Bee, the Australian
  National Botanic Gardens and Business Queensland.
- Bee hotels, on the Bees tab: each hotel with its zone, the way it faces, its height and its sections, each with its
  material, hole width and depth, and number of holes. Looks at a hotel record the holes sealed with each kind of seal,
  the busy holes, what came out and any pests, and upkeep records cleaning out, new paper liners, freezing nests and
  moving. Who sealed it? says who is most likely inside from what a nest is sealed with, and in spring a hotel not
  cleaned out in six months says so.
- The bee hotel guide: building a hotel, who uses one, and pests and keeping a hotel clean, from Aussie Bee's Bee Hotel
  Guide.
- Pollen Rhythms, the Rhythms tab: what the garden's flowers offer honey bees, stingless bees and solitary bees in
  each month, drawn as three lanes and read aloud as sentences, with how the measure is worked out. Honey bees count
  mass-flowering trees and shrubs most, and the native bees count the variety of flowers. Each group's gaps come with
  plants from the knowledge base that would fill them, natives of your state first and never a weed. The tab also
  shows what is flowering now and next month, and each plant's flowering through the year, expected and seen in
  photos.
- This month, at the top of the Rhythms tab: the rich nectar flowering for those who keep honey bees, what stingless
  bees are foraging on for those who keep them, pollinators seen on the garden's flowers in the last month, and
  plants that flowered at least a week earlier or later than last year.
- Watering, the Water tab: watering lines, each with how it waters, its zones and its flow, and the zones watered by
  hand. Each gets a plan from Open-Meteo's weather for the last week and the next: when watering is due, for how many
  minutes or how many litres, and how much of the water the plants' roots can readily reach they have used. The plan
  keeps a daily soil-water account as FAO Irrigation and Drainage Paper 56 describes, with readily available water by
  soil from the Tasmanian Institute of Agriculture, and its page says which figures are Pollenplot's own estimates.
  I watered today records a watering. The weather is kept for when the phone is offline.
- A line that waters plants of very different thirst says which plant would get several times what it needs from
  drippers of the same size, and suggests a smaller dripper or capping it.
- A notification on the new Watering channel on the mornings when a line or a zone is due for watering.
- Watering times, in the Water tab: the hours watering is planned for, from 5:00 am to 9:00 am until you choose
  others to suit your water authority's rules. Plans and reminders say when to water, and each line and zone lists the
  waterings coming up in the forecast, marking the ones good rain may do instead.
- A spray or sprinkler line that puts water on faster than its soil takes it in, by FAO's figures for loam and clay,
  says so. Once you time how long it runs before water pools, its plan splits each run into shorter runs with 30
  minutes to an hour between them, as the US EPA's WaterSense programme describes.
- Pots before a hot day: when tomorrow is forecast to reach 35 °C or more, the Water tab, the Today card and the
  morning's reminder say to water the pots deeply that morning, until water runs out of the bottom, as the
  University of California's Agriculture and Natural Resources advises. The 35 °C is Pollenplot's own figure.
- The Plot's zones show when each is next watered, and a zone's page says which line waters it, when it is next
  watered and which of its plants get far more than they need.
- The Today card, at the top of the Plot: the waterings due today, honey bee hive checks due or overdue, a hot day
  for stingless bees, bee hotels worth cleaning out this spring, the finds waiting for review and the weeds in the
  garden. Each line opens where it is done.
- The garden report, in Settings › Export data: zones and plants, wildlife, Pollen Rhythms, honey bees, native bees,
  bee hotels and watering, for the last month, 3 months or 12 months, saved as a PDF on A4 pages or as an Excel
  spreadsheet with a sheet for each table, its header row fixed with filters, and numbers and days that sort.
- The garden report's PDF draws the Plot to scale and shows each plant's cut-out with its name, unless you leave the
  pictures out, and its table columns are never narrower than their longest word.
- Settings › Export data saves a copy of your records as files: all of them as JSON-LD, and the sightings as a Darwin
  Core table that biodiversity portals take in, with the garden's place rounded to about 1 km.
- Large text: with the phone's text at up to twice its size, tab names stay whole, choices such as the Bees
  sections stand one above the other rather than being cut short, plant names in lists keep the width with their
  badges under them, Pollen Rhythms puts each name above its months, and a fact's genus or family tag moves under its
  heading. Above the canvas, the Today card takes part of the Plot at most and scrolls inside.
- Every button and control on the main screens has words for a screen reader, and a test checks it.
- Settings › Backup and restore: one file that holds every record, the photos and cut-outs they show, the garden's
  settings and all the records as JSON-LD. With a password, the file is encrypted with AES-256-GCM under a key made
  from the password, and a backup that has been changed or cut short is refused. Restoring reads and checks the whole
  file first and says what it holds, then replaces the phone's records only once you confirm; if they cannot all be
  written, the phone keeps its own. The downloaded models and the photo scanning settings stay as they are on the
  phone.
- The JSON-LD export includes the honey bee records: hives and apiaries as places, mite checks as SOSA observations
  of the hive, and inspections, treatments, feeding, harvests and colony events as schema.org events about it.
- The honey bee guide, in the honey bee section: what every beekeeper must do, setting up a hive, working a hive,
  the beekeeping year, swarms, feeding bees, taking honey, checking for Varroa mite, brood diseases, small hive beetle
  and wax moth, exotic mites to report and, for gardens in Queensland, keeping bees there. Each page says when its
  facts were last checked and links the pages they come from: the Code of Practice, BeeAware, Business Queensland,
  Agriculture Victoria, Plant Health Australia, the Professional Beekeepers pages on Extension AUS and the Food
  Standards Code.
- Six more native bees in the animal knowledge base, from the Australian Museum: the peacock carpenter bee, the
  feathery Leioproctus bee, and the leafcutter, masked, Nomia and furrow bees. They count among the native bees seen
  in the yard.
