package dev.voldpix.doppio.request;

import dev.voldpix.doppio.model.HttpMethod;

import java.util.List;

public record RequestGenerationOptions(
    HttpMethod method,
    GeneratedBodyKind bodyKind,
    boolean bearer,
    List<String> headers,
    List<String> queryParams
) {
    public RequestGenerationOptions {
        method = method == null ? HttpMethod.POST : method;
        bodyKind = bodyKind == null ? defaultBodyKind(method) : bodyKind;
        headers = List.copyOf(headers == null ? List.of() : headers);
        queryParams = List.copyOf(queryParams == null ? List.of() : queryParams);
    }

    public static RequestGenerationOptions defaults() {
        return new RequestGenerationOptions(HttpMethod.POST, null, false, List.of(), List.of());
    }

    private static GeneratedBodyKind defaultBodyKind(HttpMethod method) {
        return switch (method) {
            case GET, DELETE -> GeneratedBodyKind.NONE;
            case POST, PUT, PATCH -> GeneratedBodyKind.JSON;
        };
    }
}
