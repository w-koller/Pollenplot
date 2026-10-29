# Copy for the Water tab: watering lines, each line's and each hand-watered zone's plan, the weather it is worked out
# from, and the line editor.
STRINGS = {
    "water_et0": "Today's reference evapotranspiration (ET₀) is %1$s mm.",
    "water_rain_week": "%1$s mm of rain counted in the last week.",
    "water_weather_credit": "Weather data by Open-Meteo.com.",
    "water_weather_updated": "The weather was last updated at %1$s.",
    "water_update": "Update the weather",
    "water_no_place": "Please set your garden's location in Settings › Garden location to get a watering plan.",
    "water_no_weather": "There is no weather yet. Pollenplot will fetch it when the phone is online.",
    "water_lines": "Watering lines",
    "water_add_line": "Add a watering line",
    "water_by_hand": "Watered by hand",
    "water_due": "Please water today, about %1$s L in all.",
    "water_wait": "Watering is due, but %1$s mm of rain is likely tomorrow, so it can wait a day.",
    "water_next": "The next watering is due on %1$s.",
    "water_not_this_week": "No watering is needed in the coming week.",
    "water_nothing": "None of the plants here has a recorded water need and size, so Pollenplot cannot work out a plan.",
    "water_no_weather_plan": "A plan needs the weather first.",
    "water_uncounted": "%1$s are not counted, because their water need or size is not recorded.",
    "water_used": "The plants have used %1$d%% of the water their roots can readily reach.",
    "water_per_plant": "%1$s, about %2$s L",
    "water_last": "Last watered on %1$s",
    "water_never": "No watering recorded yet",
    "water_watered": "I watered today",
    "water_how": "How the plan is worked out",

    "water_reminders": "Remind me when watering is due",
    "water_reminders_body": "Pollenplot looks at the weather each morning and sends a notification when a line or a "
    "zone is due for watering.",
    "water_reminders_off": "Notifications are off, so Pollenplot cannot remind you when watering is due. Please allow "
    "them to get reminders.",
    "notify_water_channel": "Watering",
    "notify_water_channel_description": "Reminders on the mornings a watering line or a zone is due for watering.",
    "notify_water_title_one": "%1$s is due for watering",
    "notify_water_litres": "%1$s, about %2$s L in all",

    "water_new_line": "New watering line",
    "water_line": "Watering line",
    "water_edit_line": "Edit line",
    "water_method": "How it waters",
    "water_method_drip_line": "Drip line",
    "water_method_drippers": "Drippers",
    "water_method_sprays": "Micro-sprays",
    "water_method_sprinkler": "Sprinkler",
    "water_method_hose": "Hose",
    "water_method_can": "Watering can",
    "water_zones": "Zones it waters",
    "water_flow": "Flow of the whole line in litres an hour",
    "water_flow_hint": "Please add up the drippers' rates, such as 10 drippers at 4 L an hour for 40 L an hour.",
    "water_delete_title": "Delete this line?",
    "water_delete_body": "The line and the waterings recorded for it will be removed.",
}

PLURALS = {
    "notify_water_title": {"one": "%d watering is due today", "other": "%d waterings are due today"},
    "notify_water_minutes": {"one": "%1$s, for %2$d minute", "other": "%1$s, for %2$d minutes"},
    "water_due_minutes": {
        "one": "Please water today, for %1$d minute, which puts on about %2$s L.",
        "other": "Please water today, for %1$d minutes, which puts on about %2$s L.",
    },
    "water_pairing": {
        "one": "%1$s needs much less water than %2$s. With drippers of the same size it would get about %3$d time what "
        "it needs, so please give it a smaller dripper or cap its dripper.",
        "other": "%1$s needs much less water than %2$s. With drippers of the same size it would get about %3$d times "
        "what it needs, so please give it a smaller dripper or cap its dripper.",
    },
}
