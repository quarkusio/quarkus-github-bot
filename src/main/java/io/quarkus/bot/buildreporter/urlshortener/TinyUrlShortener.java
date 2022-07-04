package io.quarkus.bot.buildreporter.urlshortener;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.jboss.logging.Logger;

public class TinyUrlShortener implements UrlShortener {

    private static final Logger LOG = Logger.getLogger(TinyUrlShortener.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(500))
            .build();

    private static final String TINY_URL = "https://tinyurl.com/api-create.php?url=%s";

    @Override
    public String shorten(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(String.format(TINY_URL, URLEncoder.encode(url, StandardCharsets.UTF_8))))
                    .setHeader("User-Agent", "Quarkus Bot")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warn(String.format("Unable to shorten URL %s; status code was %s", url, response.statusCode()));
                return url;
            }
            return response.body();
        } catch (Exception e) {
            LOG.warn(String.format("Unable to shorten URL %s", url), e);
            return url;
        }
    }
}
