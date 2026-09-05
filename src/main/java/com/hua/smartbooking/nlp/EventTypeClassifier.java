package com.hua.smartbooking.nlp;

import com.hua.smartbooking.enums.EventType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Classifies a calendar event's type from its title using a lightweight TF-IDF
 * vector-space model, a classic Information Retrieval technique applied
 * here to a small multi-class classification problem instead of document search.
 *
 * @author Stavroula Parsali
 */
@Component
public class EventTypeClassifier {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final double MIN_SCORE_THRESHOLD = 0.01;

    private static final Map<EventType, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    static {
        // Declaration order matters: the classify() loop keeps the first category
        // to reach a given score, so ties favor whichever category is listed
        // first here. MEETING is listed first so a tie like "Lab Meeting"
        //  resolves to MEETING, it's a meeting about/for the lab, not the lab session itself.
        CATEGORY_KEYWORDS.put(EventType.MEETING, List.of(
                "meeting", "meetings", "sync", "standup", "call", "catchup",
                "συναντηση", "συσκεψη"
        ));
        CATEGORY_KEYWORDS.put(EventType.LECTURE, List.of(
                "lecture", "lectures", "class", "classes", "course", "seminar",
                "μαθημα", "διαλεξη", "διαλεξεις"
        ));
        CATEGORY_KEYWORDS.put(EventType.LAB, List.of(
                "lab", "labs", "laboratory", "workshop",
                "εργαστηριο", "εργαστηριακη", "εργαστηριακο"
        ));
        CATEGORY_KEYWORDS.put(EventType.OFFICE_HOURS, List.of(
                "office", "hours", "consultation", "advising",
                "γραφειου", "συμβουλευτικη"
        ));
    }

    // idf(term) = log(N / (1 + df)), where N = number of category "documents"
    // and df = number of categories whose keyword list contains the term.
    private static final Map<String, Double> TERM_IDF = computeIdf(CATEGORY_KEYWORDS);

    private static Map<String, Double> computeIdf(Map<EventType, List<String>> categoryKeywords) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (List<String> keywords : categoryKeywords.values()) {
            for (String term : new HashSet<>(keywords)) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        int totalDocuments = categoryKeywords.size();
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            idf.put(entry.getKey(), Math.log((double) totalDocuments / (1.0 + entry.getValue())));
        }
        return idf;
    }

    public EventType classify(String title) {
        if (title == null || title.isBlank()) {
            return EventType.OTHER;
        }

        Map<String, Long> titleTermFrequency = tokenize(title).stream()
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        EventType bestCategory = EventType.OTHER;
        double bestScore = 0.0;

        for (Map.Entry<EventType, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            double score = scoreCategory(titleTermFrequency, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestCategory = entry.getKey();
            }
        }

        return bestScore >= MIN_SCORE_THRESHOLD ? bestCategory : EventType.OTHER;
    }

    private double scoreCategory(Map<String, Long> titleTermFrequency, List<String> categoryKeywords) {
        double score = 0.0;
        for (String keyword : categoryKeywords) {
            Long tf = titleTermFrequency.get(keyword);
            if (tf != null) {
                score += tf * TERM_IDF.getOrDefault(keyword, 0.0);
            }
        }
        return score;
    }

    private List<String> tokenize(String text) {
        String normalized = stripGreekAccents(text.toLowerCase(Locale.ROOT));
        return Arrays.stream(TOKEN_SPLIT.split(normalized))
                .filter(t -> !t.isBlank())
                .toList();
    }

    private String stripGreekAccents(String text) {
        return text
                .replace('ά', 'α').replace('έ', 'ε').replace('ή', 'η')
                .replace('ί', 'ι').replace('ό', 'ο').replace('ύ', 'υ')
                .replace('ώ', 'ω').replace('ΐ', 'ι').replace('ΰ', 'υ');
    }
}