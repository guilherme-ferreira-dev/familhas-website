package br.com.familhas.crm.itinerary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conferência do modelo mestre (JSON): a proposta não traz horários, números de voo, companhias aéreas nem estrelas
 * de hotel. A tela mostra os avisos antes de imprimir ou enviar; a pessoa decide e corrige. Não bloqueia, porque o
 * texto é livre e pode haver falso positivo (ex.: "10h de caminhada").
 */
public final class ItineraryRules {

    private static final Pattern TIME = Pattern.compile("\\b([01]?\\d|2[0-3])(:[0-5]\\d|h[0-5]\\d|h)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FLIGHT = Pattern.compile("\\b(?:voo\\s*)?([A-Z]{2}|[A-Z]\\d|\\d[A-Z])\\s?\\d{3,4}\\b");
    private static final Pattern STARS = Pattern.compile("\\b\\d\\s*estrelas?\\b|★", Pattern.CASE_INSENSITIVE);
    private static final List<String> AIRLINES = List.of("latam", "gol", "azul", "tap", "avianca", "copa airlines",
            "american airlines", "united", "delta", "air france", "klm", "emirates", "qatar", "iberia",
            "aerolineas", "aerolíneas", "lufthansa", "turkish", "ita airways", "jetsmart", "flybondi", "sky airline");

    private ItineraryRules() {
    }

    public static List<String> warnings(ItinerarySections sections) {
        List<String> texts = new ArrayList<>();
        if (sections.overview() != null) {
            texts.add(sections.overview());
        }
        if (sections.days() != null) {
            sections.days().forEach(d -> {
                texts.add(d.title() == null ? "" : d.title());
                texts.add(d.description() == null ? "" : d.description());
            });
        }
        if (sections.included() != null) {
            texts.addAll(sections.included());
        }
        if (sections.investmentNotes() != null) {
            texts.add(sections.investmentNotes());
        }
        Set<String> warnings = new LinkedHashSet<>();
        for (String text : texts) {
            find(TIME, text).forEach(t -> warnings.add("Horário no texto (\"" + t + "\"): o modelo não mostra horários."));
            find(FLIGHT, text).forEach(f -> warnings.add("Número de voo (\"" + f + "\"): o modelo não mostra voos."));
            find(STARS, text).forEach(s -> warnings.add("Estrelas de hotel (\"" + s + "\"): o modelo não mostra."));
            String lower = text.toLowerCase(Locale.ROOT);
            for (String airline : AIRLINES) {
                if (Pattern.compile("\\b" + Pattern.quote(airline) + "\\b").matcher(lower).find()) {
                    warnings.add("Companhia aérea citada (" + airline.toUpperCase(Locale.ROOT)
                            + "): o modelo não cita companhias.");
                }
            }
        }
        return List.copyOf(warnings);
    }

    private static List<String> find(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group().strip());
        }
        return found;
    }
}
