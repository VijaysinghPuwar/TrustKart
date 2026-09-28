package com.vijaysinghpuwar.trustkart.search.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based reading of natural-language product queries, e.g.
 * "quiet mechanical keyboard for programming under $100" becomes
 * category=keyboards, maxPrice=100, terms=[quiet, mechanical, programming].
 *
 * <p>This is deliberately not AI: it is exact, explainable and always available. Semantic search builds on
 * top of it and falls back to it.
 */
public final class QueryInterpreter {

    public static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_TERMS = 8;

    /** A money amount. The lookahead rejects numbers followed by a unit, so "at least 24 GB" is not a price. */
    private static final String AMOUNT = "\\$?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d{1,2})?)\\s*(k)?\\b"
            + "(?!\\s*(?:gb|tb|mb|ghz|mhz|hz|kw|w|watts?|in|inch|inches|mm|cm|ft|cores?|threads?|u|ppm|va|fps|dpi|mp"
            + "|ports?|bays?|gbps|mbps|rpm|tbw|lm|lumens|nm|gen|x)\\b)";
    private static final Pattern BETWEEN = Pattern.compile("\\bbetween\\s+" + AMOUNT + "\\s+(?:and|to|-)\\s+" + AMOUNT);
    private static final Pattern MAX = Pattern.compile(
            "(?:\\bunder|\\bbelow|\\bless than|\\bup to|\\bmax(?:imum)?|\\bat most|<=?)\\s*" + AMOUNT);
    private static final Pattern MIN = Pattern.compile("(?:\\bover|\\babove|\\bmore than|\\bat least|\\bmin(?:imum)?|>=?)\\s*" + AMOUNT);
    private static final Pattern OR_LESS = Pattern.compile(AMOUNT.replace("\\$?", "\\$") + "\\s+or\\s+less");

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "for", "with", "and", "or", "to", "of", "in", "on", "my", "i", "me", "need", "want",
            "looking", "best", "good", "great", "some", "any", "that", "is", "are", "which", "what", "something",
            "around", "budget", "cheap", "affordable", "price", "priced", "dollars", "usd", "new", "buy", "show",
            "find", "get", "please", "like", "under", "below", "over", "above", "less", "more", "than", "between",
            "up", "at", "most", "least", "max", "min");

    /** Phrases mapped to category slugs. Longest phrases are tried first so "graphics card" beats "card". */
    private static final Map<String, String> CATEGORY_PHRASES = new LinkedHashMap<>();

    static {
        String[][] pairs = {
            {"graphics cards", "gpus"}, {"graphics card", "gpus"}, {"video card", "gpus"}, {"gpus", "gpus"}, {"gpu", "gpus"},
            {"processors", "cpus"}, {"processor", "cpus"}, {"cpus", "cpus"}, {"cpu", "cpus"},
            {"motherboards", "motherboards"}, {"motherboard", "motherboards"},
            {"power supply", "power-supplies"}, {"psu", "power-supplies"},
            {"hard drives", "hard-drives"}, {"hard drive", "hard-drives"}, {"hdd", "hard-drives"},
            {"ssds", "ssds"}, {"ssd", "ssds"}, {"ram", "memory"},
            {"cpu cooler", "cooling"}, {"cooler", "cooling"}, {"pc case", "cases"},
            {"gaming laptops", "gaming-laptops"}, {"gaming laptop", "gaming-laptops"},
            {"laptops", "laptops"}, {"laptop", "laptops"}, {"notebook", "laptops"},
            {"gaming pc", "gaming-desktops"}, {"gaming desktop", "gaming-desktops"},
            {"workstations", "workstations"}, {"workstation", "workstations"},
            {"mini pc", "mini-pcs"}, {"desktops", "computers"}, {"desktop", "computers"},
            {"rack server", "rack-servers"}, {"gpu server", "rack-servers"}, {"tower server", "tower-servers"},
            {"servers", "servers"}, {"server", "servers"},
            {"routers", "routers-gateways"}, {"router", "routers-gateways"}, {"firewalls", "firewalls"}, {"firewall", "firewalls"},
            {"switches", "switches"}, {"switch", "switches"}, {"access points", "access-points"}, {"access point", "access-points"},
            {"nas", "nas"}, {"printers", "printers"}, {"printer", "printers"},
            {"monitors", "monitors"}, {"monitor", "monitors"}, {"display", "monitors"},
            {"keyboards", "keyboards"}, {"keyboard", "keyboards"}, {"mice", "mice"}, {"mouse", "mice"},
            {"webcams", "webcams"}, {"webcam", "webcams"}, {"headphones", "headsets"}, {"headset", "headsets"},
            {"microphones", "microphones"}, {"microphone", "microphones"}, {"mic", "microphones"},
            {"speakers", "speakers"}, {"ups", "ups"}, {"battery backup", "ups"},
            {"security keys", "security-keys"}, {"security key", "security-keys"},
            {"security cameras", "security-cameras"}, {"security camera", "security-cameras"}, {"nvr", "security-cameras"},
            {"tablets", "tablets"}, {"tablet", "tablets"}, {"dock", "docks-adapters"}, {"projector", "projectors"},
            {"vr headset", "vr"},
            {"smartphones", "phones"}, {"smartphone", "phones"}, {"phones", "phones"}, {"phone", "phones"},
            {"foldable phone", "foldables"}, {"foldables", "foldables"}, {"foldable", "foldables"},
            {"smartwatches", "smartwatches"}, {"smartwatch", "smartwatches"}, {"smart watch", "smartwatches"},
            {"smart ring", "smart-rings"}, {"earbuds", "earbuds"}, {"soundbars", "home-audio"}, {"soundbar", "home-audio"},
            {"audio interface", "studio-audio"}, {"studio monitors", "studio-audio"},
            {"oled tv", "oled-tvs"}, {"tvs", "tvs"}, {"tv", "tvs"}, {"televisions", "tvs"}, {"television", "tvs"},
            {"mirrorless cameras", "mirrorless-cameras"}, {"mirrorless camera", "mirrorless-cameras"},
            {"lenses", "camera-lenses"}, {"lens", "camera-lenses"}, {"action camera", "action-cameras"},
            {"drones", "drones"}, {"drone", "drones"}, {"cameras", "cameras-drones"}, {"camera", "cameras-drones"},
            {"consoles", "consoles"}, {"console", "consoles"}, {"handheld", "handhelds"},
            {"controllers", "controllers"}, {"controller", "controllers"},
            {"smart home", "smart-home"}, {"smart speaker", "smart-speakers-displays"},
            {"smart display", "smart-speakers-displays"}, {"doorbell", "home-cameras-doorbells"},
            {"thermostat", "thermostats"}, {"smart lights", "smart-lighting"}, {"mesh wifi", "mesh-wifi"},
            {"mesh wi-fi", "mesh-wifi"}, {"ai server", "ai-systems"}, {"accelerator", "ai-accelerators"},
            {"chromebook", "chromebooks"}, {"raid controller", "raid-controllers"}, {"nintendo switch", "consoles"},
        };
        java.util.Arrays.stream(pairs)
                .sorted((a, b) -> Integer.compare(b[0].length(), a[0].length()))
                .forEach(p -> CATEGORY_PHRASES.put(p[0], p[1]));
    }

    private QueryInterpreter() {}

    public static Set<String> knownCategorySlugs() {
        return Set.copyOf(CATEGORY_PHRASES.values());
    }

    public static QueryInterpretation interpret(String raw) {
        String original = raw == null ? "" : raw.strip();
        if (original.length() > MAX_QUERY_LENGTH) {
            original = original.substring(0, MAX_QUERY_LENGTH);
        }
        String text = " " + original.toLowerCase(Locale.ROOT) + " ";

        BigDecimal min = null;
        BigDecimal max = null;
        Matcher between = BETWEEN.matcher(text);
        if (between.find()) {
            min = amount(between.group(1), between.group(2));
            max = amount(between.group(3), between.group(4));
            text = remove(text, between);
        } else {
            Matcher m = MAX.matcher(text);
            if (m.find()) {
                max = amount(m.group(1), m.group(2));
                text = remove(text, m);
            } else {
                Matcher orLess = OR_LESS.matcher(text);
                if (orLess.find()) {
                    max = amount(orLess.group(1), orLess.group(2));
                    text = remove(text, orLess);
                }
            }
            Matcher lo = MIN.matcher(text);
            if (lo.find()) {
                min = amount(lo.group(1), lo.group(2));
                text = remove(text, lo);
            }
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal swap = min;
            min = max;
            max = swap;
        }

        String categorySlug = null;
        String categoryPhrase = null;
        for (Map.Entry<String, String> e : CATEGORY_PHRASES.entrySet()) {
            Matcher m = Pattern.compile("\\b" + Pattern.quote(e.getKey()) + "\\b").matcher(text);
            if (m.find()) {
                categorySlug = e.getValue();
                categoryPhrase = e.getKey();
                text = remove(text, m);
                break;
            }
        }

        List<String> terms = new ArrayList<>();
        for (String token : text.split("[^a-z0-9]+")) {
            if (token.length() >= 2 && !STOPWORDS.contains(token) && !terms.contains(token) && terms.size() < MAX_TERMS) {
                terms.add(token);
            }
        }
        return new QueryInterpretation(original, terms, categorySlug, categoryPhrase, min, max);
    }

    private static String remove(String text, Matcher m) {
        return text.substring(0, m.start()) + " " + text.substring(m.end());
    }

    private static BigDecimal amount(String digits, String k) {
        BigDecimal value = new BigDecimal(digits.replace(",", ""));
        return k == null ? value : value.multiply(BigDecimal.valueOf(1000));
    }
}
