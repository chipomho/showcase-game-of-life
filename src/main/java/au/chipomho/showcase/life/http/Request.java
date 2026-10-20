package au.chipomho.showcase.life.http;

import java.util.Map;

/**
 * @param pathParameters the values of the {@code {name}} parts of the route's path
 * @param body           the request body as text, empty if there was none
 */
public record Request(Map<String, String> pathParameters, Query query, String body) {

    public Request {
        pathParameters = Map.copyOf(pathParameters);
    }

    public String path(String name) {
        return pathParameters.get(name);
    }
}
