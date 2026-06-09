package dev.voldpix.doppio.model;

import java.net.URI;
import java.util.List;

public record PreparedRequest(
    HttpMethod method,
    URI uri,
    List<Header> headers,
    String body
) {
    public PreparedRequest {
        headers = List.copyOf(headers);
    }

    public boolean hasBody() {
        return body != null && !body.isBlank();
    }
}
