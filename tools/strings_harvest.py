# Copy for harvests from plants and for pruning: the plant page's cards, the harvest form, the reminders and the
# report's part.
STRINGS = {
    "harvest_title": "Harvests",
    "harvest_none": "No harvests recorded yet.",
    "harvest_grams": "%1$s\u00a0g",
    "harvest_kilograms": "%1$s\u00a0kg",
    "harvest_spring": "spring",
    "harvest_summer": "summer",
    "harvest_autumn": "autumn",
    "harvest_winter": "winter",
    "harvest_season": "%1$s %2$s",
    "harvest_gave": ", which gave %1$s",
    "harvest_months": "You picked from it in %1$s.",
    "harvest_months_all": "You picked from it in every month.",
    "harvest_rated": "Rated %1$d out of 5",
    "harvest_rating_choice": "%1$d out of 5",
    "harvest_record": "Record a harvest",
    "harvest_record_title": "Record a harvest",
    "harvest_edit_title": "Harvest",
    "harvest_weight": "Weight in kilograms (optional)",
    "harvest_weight_help": "For example, 0.4 for 400\u00a0g.",
    "harvest_weight_error": "Please enter the weight as a number, such as 0.4.",
    "harvest_how_many": "How many were picked (optional)",
    "harvest_count_error": "Please enter a whole number, such as 12.",
    "harvest_rating": "Rating",
    "harvest_rating_help": "Please choose how good it was, from 1 to 5, or leave it without a rating.",
    "harvest_notes": "Taste and notes",
    "harvest_save": "Save",
    "harvest_delete": "Delete",
    "harvest_delete_title": "Delete this harvest?",
    "harvest_delete_body": "It is removed from this plant's harvests and from Pollenplot.",

    "prune_title": "Pruning",
    "prune_due": "Due for a prune",
    "prune_months": "Prune it in %1$s.",
    "prune_months_all": "Prune it in any month.",
    "prune_own": "These are your own months, set in Edit facts.",
    "prune_after_flowering": "That is the month after its flowering ends, because these shrubs are pruned after flowering.",
    "prune_after_flowering_all_year": "Please prune it after flowering. It flowers all year, so Pollenplot "
    "cannot tell when its flowering ends. You can set your own pruning months in Edit facts.",
    "prune_after_flowering_unknown": "Please prune it after it flowers. Its flowering months are not recorded, so "
    "Pollenplot cannot remind you. You can set your own pruning months in Edit facts.",
    "prune_after_harvest": "Please prune it after the harvest. The source gives no months, so Pollenplot does not remind "
    "you. You can set your own pruning months in Edit facts.",
    "prune_no_time": "No pruning time is recorded for this plant. You can set your own pruning months in Edit facts.",
    "prune_last": "Last pruned on %1$s.",
    "prune_never": "No pruning recorded yet.",
    "prune_record": "Record pruning",
    "prune_source": "Pruning times come from %1$s.",

    "event_pruned": "Pruned",
    "event_tip_pruned": "Tip-pruned",
    "facts_pruning_months": "Pruning months",
    "today_prune": "Please prune %1$s.",
    "settings_prune_reminders": "Remind me to prune",
    "settings_prune_reminders_body": "Pollenplot sends a notification in the morning when a plant is in its pruning "
    "months and no pruning is recorded yet.",

    "report_part_harvests": "Harvests",
    "report_harvest_totals": "Harvests by plant",
    "col_harvests": "Harvests",
    "col_weight_kg": "Weight in kg",
    "col_picked": "Number picked",
    "col_rating": "Rating out of 5",
}

PLURALS = {
    "harvest_season_line": {
        "one": "In %1$s you recorded %2$d harvest%3$s.",
        "other": "In %1$s you recorded %2$d harvests%3$s.",
    },
    "harvest_pieces": {"one": "%d piece", "other": "%d pieces"},
    "notify_prune_title": {"one": "%d plant is due a prune", "other": "%d plants are due a prune"},
    "backup_plant_harvests": {"one": "%d harvest", "other": "%d harvests"},
}
