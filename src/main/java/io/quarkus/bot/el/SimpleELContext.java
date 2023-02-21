package io.quarkus.bot.el;

import jakarta.el.CompositeELResolver;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;

public class SimpleELContext extends StandardELContext {

    private static final ELResolver DEFAULT_RESOLVER = new CompositeELResolver();

    public SimpleELContext(ExpressionFactory expressionFactory) throws NoSuchMethodException, SecurityException {
        super(expressionFactory);
        putContext(ExpressionFactory.class, expressionFactory);

        getFunctionMapper().mapFunction("", "matches", Matcher.class.getDeclaredMethod("matches", String.class, String.class));
    }

    @Override
    public void addELResolver(ELResolver cELResolver) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support addELResolver.");
    }

    @Override
    public ELResolver getELResolver() {
        return DEFAULT_RESOLVER;
    }
}
