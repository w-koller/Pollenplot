---
title: How the watering plan is worked out
summary: The weather, soil and plant figures behind each line's plan, and which of them are Pollenplot's own estimates.
section: water
order: 1
lastChecked: 2026-10-29
sources:
  - Crop evapotranspiration, chapter 8, FAO Irrigation and Drainage Paper 56 | https://www.fao.org/4/x0490e/x0490e0e.htm
  - Soil Water – Tasmania, Soil Quality factsheet | https://www.soilquality.org.au/factsheets/soil-water-tasmania
  - Weather forecast API, Open-Meteo | https://open-meteo.com/en/docs
  - Infiltration rate and infiltration test, annex 2 of FAO Irrigation Water Management, Training manual no. 5 | https://www.fao.org/4/s8684e/s8684e0a.htm
  - Sprinkler irrigation, chapter 5 of FAO Irrigation Water Management, Training manual no. 5 | https://www.fao.org/4/s8684e/s8684e06.htm
  - Cycle-and-Soak Saves Water Outdoors, US EPA WaterSense | https://www.epa.gov/system/files/documents/2022-03/ws-outdoor-cycle-and-soak-brochure.pdf
  - Caring for Container Plants During Heatwaves, University of California Agriculture and Natural Resources | https://ucanr.edu/blog/under-solano-sun/article/caring-container-plants-during-heatwaves
---

Pollenplot keeps a daily soil-water account for each watering line and each zone watered by hand, the way FAO Irrigation and Drainage Paper 56 describes. The plants draw water out each day, rain puts some back, and watering is due once the plants have used the water their roots can readily reach.

## What the plants use

- Each day starts from the reference evapotranspiration (ET₀) for the garden, from Open-Meteo's weather for the last week and the next.
- Each plant uses ET₀ times its plant factor, from the knowledge base: 10% for a very low water need, 25% for low, 50% for moderate and 80% for high.
- That is spread over the plant's canopy, worked out as a circle as wide as the plant grows.
- Plants without a recorded water need or size are left out, and the plan names them.

## What the soil holds

The water a plant can take up without stress is its readily available water (RAW). FAO 56 works it out over the root zone, and the Tasmanian Institute of Agriculture's factsheet gives it for each soil, in millimetres per metre of roots:

- sand, 30 mm;
- loam, 90 mm;
- clay, 50 mm.

A zone with no soil recorded is taken as loam, and potting mix as sand, which holds the least. The depth of roots that counts is Pollenplot's own estimate: 600 mm for trees, 400 mm for shrubs and climbers, 200 mm for lawns, grasses and groundcovers, and 300 mm for the rest.

## Rain and watering

- Rain of less than 2 mm in a day is left out, and 80% of the rest counts, Pollenplot's own allowance for rain that runs off or evaporates.
- Watering is due once the plants have used all their readily available water, and it puts that water back.
- Each way of watering loses some water, so the plan adds Pollenplot's estimate of it: drippers and drip line are taken as 90% efficient, micro-sprays as 75%, sprinklers as 70%, a hose as 80% and a watering can as 90%.
- If watering is due and at least 5 mm of rain has a 60% chance or more of falling tomorrow, watering can wait a day.
- The account starts full on the day you last recorded watering, or a week ago if that was longer ago.

## Lines with plants of different thirst

Every emitter on a line runs for the same time. A plant that needs a third as much water as the thirstiest plant on its line, or less, gets several times what it needs from drippers of the same size, so the plan suggests a smaller dripper or capping its dripper.

## Clay and runoff

Sprays and sprinklers spread water over an area, and soil can only take it in so fast. Pollenplot works out a line's rate as its flow over the zones it waters, its own estimate.

- FAO's irrigation manual gives how fast each soil takes water in once it is wet, its basic infiltration rate: 10 to 20 mm an hour for loam, and 1 to 5 mm an hour for clay.
- It says a sprinkler should put water on more slowly than that, so that water does not pool and run off.
- When a line is faster than the top of that range for the slowest soil it waters, the plan says so.
- The US EPA's WaterSense programme describes cycle-and-soak for such a line: time how long the line runs before water starts to pool, run it in cycles no longer than that, and leave 30 minutes to an hour between them.
- Once you add that time to the line, the plan splits each run that way.

## Pots before a hot day

Pots dry out quickly in heat. The University of California's Agriculture and Natural Resources advises watching the forecast and watering container plants deeply in the early morning of the day before the heat starts, until water runs out of the bottom of the pot.

- A zone of pots, or a zone whose soil is potting mix, counts as pots.
- A day forecast to reach 35 °C or more counts as hot. That figure is Pollenplot's own choice, because the advice gives none.
- On the day before, the Water tab, the Today card and the morning's reminder say to water the pots deeply.
