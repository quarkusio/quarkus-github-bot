package io.quarkus.bot.buildreporter.githubactions.urlshortener;

public interface UrlShortener {

    String shorten(String url);
}
