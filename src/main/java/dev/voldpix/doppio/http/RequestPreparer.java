package dev.voldpix.doppio.http;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.DoppioRequest;
import dev.voldpix.doppio.model.ErrorKind;
import dev.voldpix.doppio.model.PreparedRequest;
import dev.voldpix.doppio.model.QueryParam;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class RequestPreparer {
    public PreparedRequest prepare(DoppioRequest request) throws DoppioException {
        return new PreparedRequest(
            request.method(),
            mergeQueryParams(request),
            request.headers(),
            request.body()
        );
    }

    private URI mergeQueryParams(DoppioRequest request) throws DoppioException {
        var base = URI.create(request.url());
        if (request.queryParams().isEmpty()) {
            return base;
        }

        var params = new LinkedHashMap<String, String>();
        if (base.getRawQuery() != null && !base.getRawQuery().isBlank()) {
            for (var pair : base.getRawQuery().split("&")) {
                var parts = pair.split("=", 2);
                var key = decode(parts[0]);
                var value = parts.length == 2 ? decode(parts[1]) : "";
                params.put(key, value);
            }
        }

        for (QueryParam queryParam : request.queryParams()) {
            params.put(queryParam.name(), queryParam.value());
        }

        var query = buildQuery(params);
        var rebuilt = new StringBuilder();
        rebuilt.append(base.getScheme()).append("://").append(base.getRawAuthority());
        if (base.getRawPath() != null) {
            rebuilt.append(base.getRawPath());
        }
        if (!query.isEmpty()) {
            rebuilt.append('?').append(query);
        }
        if (base.getRawFragment() != null) {
            rebuilt.append('#').append(base.getRawFragment());
        }

        try {
            return URI.create(rebuilt.toString());
        } catch (IllegalArgumentException e) {
            throw new DoppioException(ErrorKind.PARSE, "Unable to build request URL", e);
        }
    }

    private String buildQuery(Map<String, String> params) {
        var query = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return query.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
