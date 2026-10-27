package io.github.palliarinbj.spurningar;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class QuestionBankTest {

    @Test public void aLineIsSubjectAgeQuestionFourAnswersAndAFact() {
        Question q = QuestionBank.parseLine(
                "geimurinn|7|Hvaða reikistjarna er næst sólu?|Merkúríus|Venus|Mars|Jörðin|Merkúríus er næst sólu.");
        assertEquals("geimurinn", q.subject);
        assertEquals(7, q.age);
        assertEquals("Merkúríus", q.correct());
        assertEquals(4, q.answers.length);
        assertEquals("Merkúríus er næst sólu.", q.fact);
    }

    @Test public void theFactIsOptional() {
        assertEquals("", QuestionBank.parseLine("dyr|10|Spurning?|A|B|C|D").fact);
    }

    @Test public void brokenLinesAreSkipped() {
        assertNull(QuestionBank.parseLine("# Saga Íslands"));
        assertNull(QuestionBank.parseLine("#?dyr|10|Spurning?|A|B|C|D|F"));
        assertNull(QuestionBank.parseLine("dyr|9|Spurning?|A|B|C|D|F"));      // not one of the four ages
        assertNull(QuestionBank.parseLine("dyr|tíu|Spurning?|A|B|C|D|F"));
        assertNull(QuestionBank.parseLine("dyr|10|Spurning?|A|B|C"));          // three answers
        assertNull(QuestionBank.parseLine("dyr|10|Spurning?|A|a|C|D|F"));      // two the same
        assertNull(QuestionBank.parseLine("dyr|10|Spurning?|A| |C|D|F"));
        assertNull(QuestionBank.parseLine(""));
    }

    @Test public void aQuestionWrittenTwiceCountsOnce() throws IOException {
        List<Question> all = QuestionBank.parse(new StringReader(
                "# heading\ndyr|7|Spurning?|A|B|C|D|F\ndyr|10|Spurning?|E|F|G|H|F\nplontur|7|Önnur?|A|B|C|D|F\n"));
        assertEquals(2, all.size());
        assertEquals(7, all.get(0).age);
    }

    @Test public void theIdFollowsTheText() {
        Question a = QuestionBank.parseLine("dyr|7|Spurning?|A|B|C|D|F");
        Question b = QuestionBank.parseLine("dyr|10|Spurning?|E|F|G|H|");
        assertEquals(a.id(), b.id());
    }
}
