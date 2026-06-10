package dev.voldpix.doppio.curl;

import dev.voldpix.doppio.model.HttpMethod;
import dev.voldpix.doppio.request.GeneratedBodyKind;

import java.util.List;

public record CurlImport(
    HttpMethod method,
    String url,
    List<String> headers,
    List<String> queryParams,
    GeneratedBodyKind bodyKind,
    String body
) {
    public CurlImport {
        headers = List.copyOf(headers == null ? List.of() : headers);
        queryParams = List.copyOf(queryParams == null ? List.of() : queryParams);
        bodyKind = bodyKind == null ? GeneratedBodyKind.NONE : bodyKind;
    }

    public boolean hasBody() {
        return body != null && !body.isBlank() && bodyKind != GeneratedBodyKind.NONE;
    }
}
