package dev.voldpix.doppio.model;

import java.util.List;

public record DoppioRequest(
    HttpMethod method,
    String url,
    List<Header> headers,
    List<QueryParam> queryParams,
    String body
) {
    public DoppioRequest {
        headers = List.copyOf(headers);
        queryParams = List.copyOf(queryParams);
    }

    public boolean hasBody() {
        return body != null && !body.isBlank();
    }

    public DoppioRequest withBody(String nextBody) {
        return new DoppioRequest(method, url, headers, queryParams, nextBody);
    }
}
