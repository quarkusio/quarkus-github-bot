package io.quarkus.bot.test;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/")
@RegisterRestClient(configKey = "quarkus-status")
public interface QuarkusStatusClient {
    @POST
    @Path("/test-results")
    @ClientHeaderParam(name = "Authorization", value = "{basicAuthString}")
    Response storeTestResults(WorkflowResult results);

    default String basicAuthString() {
        String auth = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.status.auth-string", String.class)
                .orElseThrow(() -> new IllegalStateException("No authorization string provided for quarkus-status client." +
                        " Please set `quarkus.status.auth-string` to the " +
                        "auth string to communicate with the quarkus-status application."));
        return String.format("Basic %s", auth);
    }
}
