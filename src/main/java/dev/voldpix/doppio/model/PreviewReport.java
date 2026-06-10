package dev.voldpix.doppio.model;

import java.nio.file.Path;

public record PreviewReport(
    Path requestFile,
    PreparedRequest request,
    BodyKind bodyKind,
    ProcessedBody body
) {
    public boolean hasBody() {
        return body != null && body.hasContent();
    }
}
