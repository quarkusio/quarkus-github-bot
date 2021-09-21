package io.quarkus.bot.test;

public class TestResult {
    private String name;
    private boolean successful;

    public TestResult(String name, boolean successful) {
        this.name = name;
        this.successful = successful;
    }

    public String getName() {
        return name;
    }

    public boolean isSuccessful() {
        return successful;
    }
}
