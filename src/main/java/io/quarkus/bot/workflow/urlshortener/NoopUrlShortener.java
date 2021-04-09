package io.quarkus.bot.workflow.urlshortener;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NoopUrlShortener implements UrlShortener {

    @Override
    public String shorten(String url) {
        return url;
    }
}
