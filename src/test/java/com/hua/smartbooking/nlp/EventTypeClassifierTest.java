package com.hua.smartbooking.nlp;

import com.hua.smartbooking.enums.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EventTypeClassifierTest {

    private final EventTypeClassifier classifier = new EventTypeClassifier();

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "Algorithms Lecture, LECTURE",
            "Οργάνωση Δεδομένων - Διάλεξη 5, LECTURE",
            "LECTURE: Intro to AI, LECTURE",
            "Networks Lab Session, LAB",
            "Εργαστήριο Βάσεων Δεδομένων, LAB",
            "Office Hours - Prof. Papadopoulos, OFFICE_HOURS",
            "Ώρες Γραφείου, OFFICE_HOURS",
            "Office-Hours (Q&A), OFFICE_HOURS",
            "Weekly Team Sync, MEETING",
            "Standup Call, MEETING",
            "Σύσκεψη Ομάδας Πτυχιακής, MEETING",
            "Lab Meeting, MEETING",
            "Doctor's Appointment, OTHER"
    })
    void classifiesTitlesIntoExpectedCategory(String title, EventType expected) {
        assertThat(classifier.classify(title)).isEqualTo(expected);
    }

    @Test
    void nullTitleFallsBackToOther() {
        assertThat(classifier.classify(null)).isEqualTo(EventType.OTHER);
    }

    @Test
    void blankTitleFallsBackToOther() {
        assertThat(classifier.classify("   ")).isEqualTo(EventType.OTHER);
    }

    @Test
    void unrelatedTitleWithNoKeywordOverlapFallsBackToOther() {
        assertThat(classifier.classify("Riyadh Air Metropolitano Stadium")).isEqualTo(EventType.OTHER);
    }

    @Test
    void meetingWinsTieOverLabWhenBothKeywordsPresent() {
        // "Lab Meeting" matches one keyword in LAB and one in MEETING with equal
        // score. MEETING is declared first in the classifier and should win ties.
        assertThat(classifier.classify("Lab Meeting")).isEqualTo(EventType.MEETING);
    }
}