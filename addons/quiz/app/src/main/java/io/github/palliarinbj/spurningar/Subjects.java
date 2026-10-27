package io.github.palliarinbj.spurningar;

import java.util.HashMap;
import java.util.Map;

/** The subjects' names as shown above a question, by the slug in questions.txt. */
final class Subjects {
    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        NAMES.put("saga-islands", "Saga Íslands");
        NAMES.put("mannkynssaga", "Mannkynssaga");
        NAMES.put("nattura-islands", "Náttúra Íslands");
        NAMES.put("dyr", "Dýraríkið");
        NAMES.put("geimurinn", "Geimurinn");
        NAMES.put("mannslikaminn", "Mannslíkaminn");
        NAMES.put("edlisfraedi", "Eðlisfræði");
        NAMES.put("efnafraedi", "Efnafræði");
        NAMES.put("islensk-tunga", "Íslensk tunga");
        NAMES.put("bokmenntir", "Bókmenntir");
        NAMES.put("landafraedi", "Landafræði");
        NAMES.put("taekni", "Tækni");
        NAMES.put("staerdfraedi", "Stærðfræði");
        NAMES.put("ithrottir", "Íþróttir");
        NAMES.put("listir", "Listir og tónlist");
        NAMES.put("plontur", "Plöntur");
    }

    private Subjects() {}

    static String name(String slug) {
        String name = NAMES.get(slug);
        return name != null ? name : slug;
    }
}
