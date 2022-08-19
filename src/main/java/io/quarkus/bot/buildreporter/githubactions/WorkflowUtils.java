package io.quarkus.bot.buildreporter.githubactions;

public final class WorkflowUtils {

    public static String getFilePath(String moduleName, String fullClassName) {
        String classPath = fullClassName.replace(".", "/");
        int dollarIndex = classPath.indexOf('$');
        if (dollarIndex > 0) {
            classPath = classPath.substring(0, dollarIndex);
        }
        return moduleName + "/src/test/java/" + classPath + ".java";
    }

    private WorkflowUtils() {
    }
}
