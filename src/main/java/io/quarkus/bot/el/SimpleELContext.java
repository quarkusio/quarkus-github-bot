package io.quarkus.bot.el;

import javax.el.CompositeELResolver;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.StandardELContext;

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
