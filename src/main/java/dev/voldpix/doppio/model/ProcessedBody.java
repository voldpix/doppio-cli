package dev.voldpix.doppio.model;

public record ProcessedBody(String content, String contentType) {
    public boolean hasContent() {
        return content != null && !content.isBlank();
    }
}
