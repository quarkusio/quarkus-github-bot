package io.quarkus.bot.violation;

/**
 * Represents a violation of editorial rules in a pull request.
 */
public record EditorialViolation(String message, io.quarkus.bot.violation.EditorialViolation.ViolationType type) {

    public static EditorialViolation TitleViolation(String message) {
        return new EditorialViolation(message, ViolationType.TITLE);
    }

    public static EditorialViolation BodyViolation(String message) {
        return new EditorialViolation(message, ViolationType.BODY);
    }

    public enum ViolationType {
        TITLE,
        BODY
    }
}