package dev.skypocket.skyblock;

import java.util.Set;

/**
 * Exact Hypixel spawn messages for legendary-or-above sea creatures.
 *
 * <p>Hypixel does not put a rarity word in most spawn messages; the message is the
 * only stable key. Keep these strings exact: case, punctuation and the curly
 * apostrophe/em dash are part of the match. Do not trim or lowercase the incoming
 * component text.
 */
public final class SeaCreatureMessages {
    private static final Set<String> LEGENDARY_OR_ABOVE = Set.of(
            // LEGENDARY
            "An Abyssal Miner breaks out of the water!",
            "A long snout breaks the surface of the water. It's an Alligator!",
            "A garish set of tentacles arise. It's a Blue Ringed Octopus!",
            "A Fiery Scuttler inconspicuously waddles up to you, friends in tow.",
            "Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!",
            "The spirit of a long lost Phantom Fisher has come to haunt you.",
            "A Puddle Jumper is preparing for liftoff\u2014cast your rod into it and hold on tight!",
            "Something zips through the air - it\u2019s a Silkbreeze!",
            "The Loch Emperor arises from the depths.",
            "The Water Hydra has come to test your strength.",
            "What is this creature!?",
            // MYTHIC
            "Bow down before the Frog Prince... or pay the hefty price!",
            "A Giant Isopod was dredged up from the depths!",
            "This can't be! The manifestation of death himself!",
            "You have angered a legendary creature... Lord Jawbus has arrived.",
            "You've caused a disturbance in the loch. Could it be... Nessie?",
            "WOAH! A Plhlegblast appeared.",
            "The sky darkens and the air thickens. The end times are upon us: Ragnarok is here.",
            "WOAH! A Reindrake was summoned from the depths!",
            "You hear a massive rumble as Thunder emerges.",
            "A massive Titanoboa surfaces. Its body stretches as far as the eye can see.",
            "The water bubbles and froths. A massive form emerges- you have disturbed the Wiki Tiki! You shall pay the price."
    );

    private SeaCreatureMessages() {
    }

    /** Exact match; Hypixel sends the message without colour codes. */
    public static boolean isLegendaryOrAbove(String message) {
        return message != null && LEGENDARY_OR_ABOVE.contains(message);
    }
}
