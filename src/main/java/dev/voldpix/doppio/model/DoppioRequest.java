package dev.voldpix.doppio.model;

import java.util.List;

public record DoppioRequest(
    String name,
    HttpMethod method,
    String url,
    List<Header> headers,
    List<QueryParam> queryParams,
    BodyBlock body
) {
    public DoppioRequest {
        headers = List.copyOf(headers);
        queryParams = List.copyOf(queryParams);
    }

    public DoppioRequest(
        HttpMethod method,
        String url,
        List<Header> headers,
        List<QueryParam> queryParams,
        BodyBlock body
    ) {
        this(null, method, url, headers, queryParams, body);
    }

    public boolean hasBody() {
        return body != null && body.hasContent();
    }
}
