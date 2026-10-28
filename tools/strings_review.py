# Copy for the Review queue, adding photos, scanning the gallery and the notifications about them.
STRINGS = {
    # The Review queue
    "review_title": "Review",
    "review_taken": "Taken on %1$s",
    "review_empty_title": "Nothing to review.",
    "review_empty_body": "Plants found in photos you add, or in new photos while scanning is on, wait here until you "
    "check them.",
    "review_adding_progress": "Looking for plants in photo %1$d of %2$d.",
    "review_adding_waiting": "Pollenplot will look for plants in the photos you added in a moment.",

    # Settings › Photo scanning
    "scan_title": "Photo scanning",
    "scan_intro": "Pollenplot can look for plants in new photos in your gallery, in the background. Photos that show "
    "plants wait in the Review queue for you, and no photo leaves your phone.",
    "scan_switch": "Look for plants in new photos",
    "scan_switch_body": "Pollenplot checks new photos when the battery is not low, and tells you when it finds plants.",
    "scan_denied": "Pollenplot cannot see your photos. Please allow photos in the app's settings to scan them.",
    "scan_partial": "Pollenplot can see only the photos you chose, so it cannot find new ones. Please allow all photos "
    "to scan new photos as they are taken.",
    "scan_partial_allow": "Allow all photos",
    "scan_notifications_off": "Notifications are off, so Pollenplot cannot tell you when it finds plants. They still "
    "wait in the Review queue.",
    "scan_notifications_allow": "Allow notifications",
    "scan_running": "Pollenplot is looking for plants in your photos now.",
    "scan_idle": "Pollenplot looks again whenever new photos arrive.",
    "scan_now": "Look for plants now",
    "scan_albums": "Albums",
    "scan_albums_body": "Pollenplot scans the albums you tick. At first that is the camera's own album.",
    "scan_back": "Older photos",
    "scan_back_body": "When scanning is turned on, Pollenplot also looks at photos taken this long before.",
    "scan_back_choice": "How far back",
    "scan_back_none": "None",
    "scan_back_week": "1 week",
    "scan_back_month": "1 month",
    "scan_back_three_months": "3 months",
    "scan_back_year": "1 year",

    # Notifications
    "notify_scans_channel": "Plants found in photos",
    "notify_scans_channel_description": "Tells you when Pollenplot finds plants or animals in your photos.",
    "notify_found_both_title": "Pollenplot found %1$s and %2$s in your photos.",
    "notify_found_both_body": "Please review them to add them to your garden and your sightings.",
    "notify_none_body": "Please try photos taken closer to the plants, in good light.",
}

PLURALS = {
    "review_waiting": {"one": "%d plant is waiting for review.", "other": "%d plants are waiting for review."},
    "scan_album_count": {"one": "%d photo", "other": "%d photos"},
    "notify_found_title": {
        "one": "Pollenplot found %d plant in your photos.",
        "other": "Pollenplot found %d plants in your photos.",
    },
    "notify_found_body": {
        "one": "Please review it to add it to your garden.",
        "other": "Please review them to add them to your garden.",
    },
    "notify_found_animals_title": {
        "one": "Pollenplot found %d animal in your photos.",
        "other": "Pollenplot found %d animals in your photos.",
    },
    "notify_found_animals_body": {
        "one": "Please review it to add it to your sightings.",
        "other": "Please review them to add them to your sightings.",
    },
    "count_plants": {"one": "%d plant", "other": "%d plants"},
    "count_animals": {"one": "%d animal", "other": "%d animals"},
    "notify_none_title": {
        "one": "No plant was found in the photo you added.",
        "other": "No plants were found in the %d photos you added.",
    },
}
