package dev.voldpix.doppio.model;

import dev.voldpix.doppio.expect.Expectation;

import java.nio.file.Path;
import java.util.List;

public record PreviewReport(
    Path requestFile,
    PreparedRequest request,
    BodyKind bodyKind,
    ProcessedBody body,
    List<Expectation> expectations
) {
    public PreviewReport {
        expectations = List.copyOf(expectations);
    }

    public boolean hasBody() {
        return body != null && body.hasContent();
    }
}
