package dev.voldpix.doppio.model;

public record BodyBlock(BodyKind kind, String content) {
    public BodyBlock {
        kind = kind == null ? BodyKind.JSON : kind;
    }

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }
}
