package io.quarkus.bot.graal;

import org.awaitility.classpath.ClassPathResolver;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(ClassPathResolver.class)
public final class SubstituteClassPathResolver {

    @Substitute
    public static boolean existInCP(String className) {
        if ("java.lang.management.ManagementFactory".equals(className)) {
            return false;
        }

        return existsInCP(className, ClassPathResolver.class.getClassLoader())
                || existsInCP(className, Thread.currentThread().getContextClassLoader());
    }

    @Alias
    private static boolean existsInCP(String className, ClassLoader classLoader) {
        return false;
    }
}
