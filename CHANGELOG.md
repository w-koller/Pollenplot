# Changelog

## 0.1.0 (in progress)

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
- An animal knowledge base of 76 animals and fungi found in Australian backyards: what each does in a garden, the
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
- The Plot's zones show when each is next watered, and a zone's page says which line waters it, when it is next
  watered and which of its plants get far more than they need.
- The Today card, at the top of the Plot: the waterings due today, honey bee hive checks due or overdue, a hot day
  for stingless bees, bee hotels worth cleaning out this spring, the finds waiting for review and the weeds in the
  garden. Each line opens where it is done.
- The garden report, in Settings › Export data: zones and plants, wildlife, Pollen Rhythms, honey bees, native bees,
  bee hotels and watering, for the last month, 3 months or 12 months, saved as a PDF on A4 pages or as an Excel
  spreadsheet with a sheet for each table, its header row fixed with filters, and numbers and days that sort.
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
- The honey bee guide, in the honey bee section: what every beekeeper must do, checking for Varroa mite, brood
  diseases, small hive beetle and wax moth, exotic mites to report and, for gardens in Queensland, keeping bees there.
  Each page says when its facts were last checked and links the pages they come from: the Code of Practice, BeeAware,
  Business Queensland and Plant Health Australia.
