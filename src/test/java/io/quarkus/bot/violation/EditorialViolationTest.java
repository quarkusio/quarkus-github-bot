package io.quarkus.bot.violation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EditorialViolationTest {

    @Test
    void testTitleViolation() {
        String message = "Title should not end with a dot";
        EditorialViolation violation = EditorialViolation.TitleViolation(message);

        assertEquals(message, violation.message());
        assertEquals(EditorialViolation.ViolationType.TITLE, violation.type());
    }

    @Test
    void testBodyViolation() {
        String message = "Description should not be empty";
        EditorialViolation violation = EditorialViolation.BodyViolation(message);

        assertEquals(message, violation.message());
        assertEquals(EditorialViolation.ViolationType.BODY, violation.type());
    }

    @Test
    void testDirectConstructor() {
        String message = "Test message";
        EditorialViolation violation = new EditorialViolation(message, EditorialViolation.ViolationType.TITLE);

        assertEquals(message, violation.message());
        assertEquals(EditorialViolation.ViolationType.TITLE, violation.type());
    }
}