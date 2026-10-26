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
