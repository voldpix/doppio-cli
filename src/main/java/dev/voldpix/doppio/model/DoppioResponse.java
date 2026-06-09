package dev.voldpix.doppio.model;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DoppioResponse(
    int statusCode,
    Map<String, List<String>> headers,
    String body,
    Duration duration
) {
    public DoppioResponse {
        var copiedHeaders = new LinkedHashMap<String, List<String>>();
        headers.forEach((key, value) -> copiedHeaders.put(key, List.copyOf(value)));
        headers = Map.copyOf(copiedHeaders);
        body = body == null ? "" : body;
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}
