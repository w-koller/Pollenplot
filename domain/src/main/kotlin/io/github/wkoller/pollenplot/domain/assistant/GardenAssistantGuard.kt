package io.github.wkoller.pollenplot.domain.assistant

/**
 * What a question to the garden assistant is about, for the questions the app answers itself instead of the model.
 * The order is the order they are checked in: a question about a bite is an emergency before it is about a snake.
 */
enum class QuestionKind {
    /** Someone has been bitten or stung, or is in danger: call 000, or the Poisons Information Centre. */
    BITE_OR_STING,

    /** Someone, or a pet, has eaten or touched something that may be poisonous. */
    POISONING,

    /** Whether something identified can be eaten. An identification never decides that. */
    EATING,

    /** How much of a garden or hive chemical to use. The product's label decides that. */
    CHEMICAL_DOSE,

    /** A sign of a notifiable bee disease or an exotic pest, which goes to the hotline. */
    BEE_OR_EXOTIC_PEST,

    /** Touching, moving or catching an animal that may be dangerous, or whether one is safe to handle. */
    HANDLING,

    /** A sick, injured or orphaned wild animal, which goes to a wildlife rescue group. */
    INJURED_WILDLIFE,

    /** Anything else, which the model may answer from the garden's records and the knowledge bases. */
    GENERAL,
}

/**
 * Keeps the language model away from questions where a wrong answer could hurt someone. These get fixed answers from
 * the app, written and checked once, with the numbers to call; only general questions reach the model. The checks
 * look for plain words and phrases, and lean towards a fixed answer when a question could be read either way.
 */
object GardenAssistantGuard {
    fun classify(question: String): QuestionKind {
        val text = normalise(question)
        return when {
            BITES.any { it.containsMatchIn(text) } -> QuestionKind.BITE_OR_STING
            POISONING.any { it.containsMatchIn(text) } -> QuestionKind.POISONING
            EATING.any { it.containsMatchIn(text) } -> QuestionKind.EATING
            CHEMICALS.any { it.containsMatchIn(text) } && DOSES.any { it.containsMatchIn(text) } -> QuestionKind.CHEMICAL_DOSE
            BEE_PESTS.any { it.containsMatchIn(text) } -> QuestionKind.BEE_OR_EXOTIC_PEST
            HANDLING.any { it.containsMatchIn(text) } && (DANGEROUS.any { it.containsMatchIn(text) } || SAFE_TO_HANDLE.containsMatchIn(text)) -> QuestionKind.HANDLING
            INJURED.any { it.containsMatchIn(text) } && WILD.any { it.containsMatchIn(text) } -> QuestionKind.INJURED_WILDLIFE
            else -> QuestionKind.GENERAL
        }
    }

    private fun normalise(text: String): String = text.lowercase().replace('’', '\'').replace(Regex("[^a-z0-9' ]+"), " ").replace(Regex("\\s+"), " ").trim()

    private fun words(vararg words: String): List<Regex> = words.map { Regex("\\b$it\\b") }

    private val BITES = words(
        "bitten", "bit me", "bit my", "bit him", "bit her", "bit them", "bite on", "bites on", "stung", "sting on",
        "got a sting", "envenom\\w*", "can't breathe", "cannot breathe", "trouble breathing", "swollen (face|throat|tongue|lips)",
        "unconscious", "collapsed", "anaphyla\\w*", "emergency", "ambulance", "call 000",
    )

    /** People and pets, whose eating something is a worry, unlike a possum eating the lettuces. */
    private const val WHO = "(i|we|he|she|they|you|my \\w+|our \\w+|the (kids?|children|baby|toddler|dog|cat|puppy|kitten|pets?))"

    private val POISONING = words(
        "$WHO( (has|have|had|just|accidentally|might have|may have|might|may))? (ate|eaten|swallowed|chewed|licked|drank|drunk|touched|rubbed)",
        "poisoned", "poisoning", "overdose", "sap (in|on|got in) (my|his|her|their|the) (eyes?|skin|mouth)",
    )

    private val EATING = words(
        "edible", "safe to eat", "ok to eat", "okay to eat", "fine to eat", "good to eat", "eaten raw",
        "(can|could|should|may) $WHO (eat|cook|taste|try)", "(is|are) (it|this|that|they|these|those)( \\w+)? (safe|ok|okay|good) (to eat|for eating)",
        "forag\\w*", "make (a )?tea (from|with|out of)", "bush tucker",
    )

    private val CHEMICALS = words(
        "pesticides?", "herbicides?", "insecticides?", "fungicides?", "miticides?", "weed ?killers?", "glyphosate",
        "roundup", "poisons?", "baits?", "snail pellets?", "sprays?", "spraying", "chemicals?", "pyrethr\\w*", "neem",
        "copper", "sulphur", "sulfur", "oxalic", "formic", "thymol", "apivar", "apistan", "bayvarol", "amitraz",
        "fluvalinate", "imidacloprid", "metaldehyde", "dimethoate", "fipronil",
    )

    private val DOSES = words(
        "how much", "how many", "how strong", "what strength", "what rate", "rate", "rates", "dose", "doses", "dosage",
        "dilute", "dilution", "mix", "mixing", "ratio", "ml", "millilitres?", "litres?", "grams?", "concentration",
        "per litre", "per hive", "per square metre",
    )

    private val BEE_PESTS = words(
        "varroa", "tropilaelaps", "foulbrood", "afb", "efb", "braula", "tracheal mites?", "asian honey ?bees?",
        "dwarf honey ?bees?", "giant honey ?bees?", "exotic (pest|pests|bee|bees|mite|mites|disease|diseases)",
        "notifiable", "deformed wings?", "mites? (on|in) (my|the) (bees|hive|brood)", "(sunken|perforated|greasy) (brood|cappings?)",
        "ropy", "ropey", "brood (smells|is dying|looks sick)",
    )

    private val HANDLING = words(
        "touch", "touching", "pick (it|him|her|them) up", "pick up", "picking up", "hold", "holding", "handle",
        "handling", "catch", "catching", "(move|moving|remove|removing|relocate|relocating) (it|him|her|them|the|a|an|this|that)",
        "kill", "killing", "get rid of", "pet (it|him|her|them)", "go near",
    )

    private val DANGEROUS = words(
        "snakes?", "pythons?", "brown snakes?", "black snakes?", "tiger snakes?", "taipans?", "funnel ?webs?",
        "redbacks?", "white ?tails?", "spiders?", "wasps?", "hornets?", "bee swarms?", "swarms?", "bats?",
        "flying ?fox(es)?", "cane toads?", "toads?", "fire ants?", "jumping jacks?", "bull ants?", "ticks?",
        "scorpions?", "centipedes?", "magpies?", "swooping", "goannas?", "lace monitors?", "possums?", "venomous",
        "poisonous", "dangerous", "stingers?",
    )

    private val SAFE_TO_HANDLE = Regex("\\b(is it|are they|is this|is that) (safe|ok|okay|dangerous) to (touch|hold|handle|pick up|pet)\\b")

    private val INJURED = words(
        "injured", "hurt", "wounded", "bleeding", "broken (wing|leg)", "hit by a car", "hit by car", "can't fly",
        "cannot fly", "won't fly", "orphaned", "abandoned", "fell out of (its|the|a) nest", "fallen out of (its|the|a) nest",
        "sick", "lying (still|on the ground)", "caught in (a )?net(ting)?", "tangled", "attacked by (a |my |the )?(cat|dog)",
    )

    private val WILD = words(
        "birds?", "chicks?", "fledglings?", "nestlings?", "possums?", "bats?", "flying ?fox(es)?", "lizards?",
        "skinks?", "blue ?tongues?", "dragons?", "frogs?", "turtles?", "echidnas?", "bandicoots?", "kangaroos?",
        "wallab(y|ies)", "wombats?", "koalas?", "gliders?", "owls?", "magpies?", "kookaburras?", "lorikeets?",
        "cockatoos?", "parrots?", "ducks?", "ducklings?", "snakes?", "animals?", "wildlife", "joeys?",
    )
}
