package dev.voldpix.doppio.model;

import java.nio.file.Path;

public record RunReport(Path requestFile, PreparedRequest request, DoppioResponse response) {
    public boolean isSuccess() {
        return response.isSuccess();
    }
}
